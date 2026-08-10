package com.example.howl

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.annotation.RequiresPermission
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.UUID

enum class OpossumConnectionStatus {
    Disconnected,
    Scanning,
    Connecting,
    Connected,
    Error
}

class OpossumViewModel : ViewModel() {

    private val OPOSSUM_DEVICE_NAME = "47L127000"

    private val _connectionStatus = MutableStateFlow(OpossumConnectionStatus.Disconnected)
    val connectionStatus: StateFlow<OpossumConnectionStatus> = _connectionStatus.asStateFlow()

    private val _batteryLevel = MutableStateFlow(-1)
    val batteryLevel: StateFlow<Int> = _batteryLevel.asStateFlow()

    private val _channelAAmp = MutableStateFlow(0)
    val channelAAmp: StateFlow<Int> = _channelAAmp.asStateFlow()

    private val _channelBAmp = MutableStateFlow(0)
    val channelBAmp: StateFlow<Int> = _channelBAmp.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _selectedActivity = MutableStateFlow(ActivityType.LICKS)
    val selectedActivity: StateFlow<ActivityType> = _selectedActivity.asStateFlow()

    private val _activityChangeProbability = MutableStateFlow(Prefs.activityChangeProbability.value)
    val activityChangeProbability: StateFlow<Float> = _activityChangeProbability.asStateFlow()

    private val _playerSyncEnabled = MutableStateFlow(false)
    val playerSyncEnabled: StateFlow<Boolean> = _playerSyncEnabled.asStateFlow()

    private val _swapChannels = MutableStateFlow(false)
    val swapChannels: StateFlow<Boolean> = _swapChannels.asStateFlow()

    private val _syncChannels = MutableStateFlow(false)
    val syncChannels: StateFlow<Boolean> = _syncChannels.asStateFlow()

    /** 滑块手动调整时的回调，参数为 (effectiveA, effectiveB) */
    var onManualChannelChange: ((Int, Int) -> Unit)? = null

    private var playerSyncJob: Job? = null
    private var lastSyncedChannelA = -1
    private var lastSyncedChannelB = -1

    private var outputOpossum: OutputOpossum? = null

    private var bluetoothGatt: BluetoothGatt? = null
    private var bluetoothDevice: BluetoothDevice? = null
    private var contextRef: Context? = null

    // BLE write queue - Android BLE can only handle one write at a time
    private val writeQueue = ArrayDeque<Pair<ByteArray, Int>>()
    private var isWriting = false
    private val writeLock = Any()

    private var currentMtu = 23

    companion object {
        const val PERMISSION_BLUETOOTH_SCAN = "android.permission.BLUETOOTH_SCAN"
        const val PERMISSION_BLUETOOTH_CONNECT = "android.permission.BLUETOOTH_CONNECT"

        val ALL_BLE_PERMISSIONS = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN)
        } else {
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }

        val MAIN_SERVICE_UUID = UUID.fromString("0000180C-0000-1000-8000-00805f9b34fb")
        val WRITE_CHARACTERISTIC_UUID = UUID.fromString("0000150A-0000-1000-8000-00805f9b34fb")
        val NOTIFY_CHARACTERISTIC_UUID = UUID.fromString("0000150B-0000-1000-8000-00805f9b34fb")
        val BATTERY_SERVICE_UUID = UUID.fromString("0000180A-0000-1000-8000-00805f9b34fb")
        val BATTERY_CHARACTERISTIC_UUID = UUID.fromString("00001500-0000-1000-8000-00805f9b34fb")
        val CCCD_DESCRIPTOR_UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
    }

    fun initialize(context: Context) {
        this.contextRef = context
    }

    fun getStatusText(): String {
        return when (_connectionStatus.value) {
            OpossumConnectionStatus.Disconnected -> "Disconnected"
            OpossumConnectionStatus.Scanning -> "Scanning..."
            OpossumConnectionStatus.Connecting -> "Connecting..."
            OpossumConnectionStatus.Connected -> {
                val battery = _batteryLevel.value
                if (battery >= 0) "Connected · ${battery}%" else "Connected"
            }
            OpossumConnectionStatus.Error -> "Error"
        }
    }

    fun checkPermissions(onResult: (Boolean) -> Unit) {
        val ctx = contextRef ?: return
        val missingPermissions = ALL_BLE_PERMISSIONS.filter {
            ContextCompat.checkSelfPermission(ctx, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missingPermissions.isEmpty()) {
            onResult(true)
        } else {
            HLog.d("Opossum", "Requesting permissions: ${missingPermissions.toString()}")
            onResult(false)
        }
    }

    @SuppressLint("MissingPermission")
    @RequiresPermission(Manifest.permission.BLUETOOTH_SCAN)
    fun attemptConnection() {
        val context = contextRef ?: return
        val bluetooth = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        val adapter = bluetooth?.adapter

        if (adapter == null || !adapter.isEnabled) {
            _connectionStatus.update { OpossumConnectionStatus.Error }
            HLog.d("Opossum", "Bluetooth not available or disabled")
            return
        }

        _connectionStatus.update { OpossumConnectionStatus.Scanning }

        val scanner = adapter.bluetoothLeScanner
        val scanSettings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        val timeoutHandler = Handler(Looper.getMainLooper())

        val scanCallback = object : ScanCallback() {
            override fun onScanResult(type: Int, result: ScanResult?) {
                result?.let {
                    val advertisedName = it.scanRecord?.deviceName ?: it.device.name ?: "<unknown>"
                    HLog.d("Opossum", "Scan found: ${it.device.address} ($advertisedName)")
                    if (advertisedName == OPOSSUM_DEVICE_NAME) {
                        timeoutHandler.removeCallbacksAndMessages(null)
                        scanner?.stopScan(this)
                        bluetoothDevice = it.device
                        connectDevice(it.device)
                    }
                }
            }

            override fun onScanFailed(errorCode: Int) {
                HLog.d("Opossum", "Scan failed: $errorCode")
                _connectionStatus.update { OpossumConnectionStatus.Error }
            }
        }

        HLog.d("Opossum", "Starting BLE scan for $OPOSSUM_DEVICE_NAME...")
        scanner?.startScan(null, scanSettings, scanCallback)

        timeoutHandler.postDelayed({
            if (_connectionStatus.value == OpossumConnectionStatus.Scanning) {
                scanner?.stopScan(scanCallback)
                _connectionStatus.update { OpossumConnectionStatus.Error }
                HLog.d("Opossum", "Scan timeout, device not found")
            }
        }, 10000L)
    }

    @SuppressLint("MissingPermission")
    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    private fun connectDevice(device: BluetoothDevice) {
        _connectionStatus.update { OpossumConnectionStatus.Connecting }
        HLog.d("Opossum", "Connecting to ${device.name} (${device.address})")
        bluetoothGatt = device.connectGatt(contextRef, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
    }

    @SuppressLint("MissingPermission")
    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    fun disconnect() {
        stopPlayback()
        stopPlayerSync()
        stopPressureReporting()
        synchronized(writeLock) {
            writeQueue.clear()
            isWriting = false
        }
        bluetoothGatt?.disconnect()
        bluetoothGatt?.close()
        bluetoothGatt = null
        bluetoothDevice = null
        _connectionStatus.update { OpossumConnectionStatus.Disconnected }
        HLog.d("Opossum", "Disconnected")
    }

    @SuppressLint("MissingPermission")
    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                disconnect()
                _connectionStatus.update { OpossumConnectionStatus.Error }
                return
            }
            if (newState == BluetoothGatt.STATE_CONNECTED) {
                HLog.d("Opossum", "Connected, discovering services...")
                gatt.discoverServices()
            } else if (newState == BluetoothGatt.STATE_DISCONNECTED) {
                disconnect()
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                disconnect()
                _connectionStatus.update { OpossumConnectionStatus.Error }
                return
            }

            HLog.d("Opossum", "Services discovered")
            for (service in gatt.services) {
                HLog.d("Opossum", "  Service: ${service.uuid}")
                for (char in service.characteristics) {
                    HLog.d("Opossum", "    Char: ${char.uuid}, props=${char.properties}")
                }
            }

            // Request MTU FIRST, before any other GATT operations.
            // Android BLE does not support concurrent GATT operations,
            // so requestMtu must complete before writeDescriptor calls.
            val mtuOk = gatt.requestMtu(144)
            HLog.d("Opossum", "requestMtu(144) returned: $mtuOk")
        }

        override fun onMtuChanged(gatt: BluetoothGatt?, mtu: Int, status: Int) {
            HLog.d("Opossum", "MTU changed: mtu=$mtu, status=$status")
            if (status == BluetoothGatt.GATT_SUCCESS) {
                currentMtu = mtu
                HLog.d("Opossum", "MTU negotiated: $mtu")
            } else {
                HLog.d("Opossum", "MTU negotiation failed, using default: $currentMtu")
            }

            // MTU negotiation complete, now subscribe to characteristics
            gatt?.let {
                subscribeToNotifyCharacteristic(it)
                subscribeToBatteryCharacteristic(it)
            }

            _connectionStatus.update { OpossumConnectionStatus.Connected }

            Handler(Looper.getMainLooper()).postDelayed({
                if (_connectionStatus.value == OpossumConnectionStatus.Connected) {
                    gatt?.getService(BATTERY_SERVICE_UUID)?.let { svc ->
                        val custom = svc.getCharacteristic(BATTERY_CHARACTERISTIC_UUID)
                        if (custom != null) {
                            readBatteryValue(gatt, custom)
                        }
                    }
                }
            }, 2000L)
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic
        ) {
            val data = characteristic.value ?: return
            HLog.d("Opossum", "Characteristic changed: ${characteristic.uuid}, len=${data.size}")

            when (characteristic.uuid) {
                NOTIFY_CHARACTERISTIC_UUID -> parseNotifyMessage(data)
                BATTERY_CHARACTERISTIC_UUID -> parseBatteryLevel(data)
            }
        }

        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                if (characteristic.uuid == BATTERY_CHARACTERISTIC_UUID) {
                    val data = characteristic.value ?: return
                    parseBatteryLevel(data)
                    HLog.d("Opossum", "Battery read success: ${data[0]}%")
                }
            }
        }

        override fun onCharacteristicWrite(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            HLog.v("Opossum", "Write done: status=$status, remaining=${writeQueue.size}")
            processNextWrite()
        }

        override fun onDescriptorWrite(
            gatt: BluetoothGatt,
            descriptor: BluetoothGattDescriptor,
            status: Int
        ) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                HLog.d("Opossum", "Descriptor write success for ${descriptor.characteristic.uuid}")
                if (descriptor.uuid == CCCD_DESCRIPTOR_UUID) {
                    val parentChar = descriptor.characteristic
                    when (parentChar.uuid) {
                        NOTIFY_CHARACTERISTIC_UUID -> {
                            HLog.d("Opossum", "Notify CCCD enabled, sending start command")
                            startPressureReporting()
                        }
                        BATTERY_CHARACTERISTIC_UUID -> readBatteryValue(gatt, parentChar)
                    }
                }
            } else {
                HLog.d("Opossum", "Descriptor write failed: $status")
            }
        }
    }

    @SuppressLint("MissingPermission")
    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    private fun subscribeToNotifyCharacteristic(gatt: BluetoothGatt) {
        val characteristic = gatt.getService(MAIN_SERVICE_UUID)
            ?.getCharacteristic(NOTIFY_CHARACTERISTIC_UUID) ?: run {
            HLog.d("Opossum", "Notify characteristic not found")
            return
        }

        if (gatt.setCharacteristicNotification(characteristic, true)) {
            val cccDescriptor = characteristic.getDescriptor(CCCD_DESCRIPTOR_UUID)
            if (cccDescriptor != null) {
                cccDescriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                gatt.writeDescriptor(cccDescriptor)
                HLog.d("Opossum", "Notify CCCD descriptor write initiated")
            }
        } else {
            HLog.d("Opossum", "setCharacteristicNotification failed")
        }
    }

    @SuppressLint("MissingPermission")
    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    private fun subscribeToBatteryCharacteristic(gatt: BluetoothGatt) {
        val service = gatt.getService(BATTERY_SERVICE_UUID) ?: run {
            HLog.d("Opossum", "Battery service not found")
            return
        }

        val characteristic = service.getCharacteristic(BATTERY_CHARACTERISTIC_UUID) ?: run {
            HLog.d("Opossum", "Battery characteristic not found")
            return
        }

        HLog.d("Opossum", "Found battery characteristic: ${characteristic.uuid}")
        val notifyOk = gatt.setCharacteristicNotification(characteristic, true)
        HLog.d("Opossum", "setCharacteristicNotification for battery: $notifyOk")

        val cccDescriptor = characteristic.getDescriptor(CCCD_DESCRIPTOR_UUID)
        if (cccDescriptor != null) {
            cccDescriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            gatt.writeDescriptor(cccDescriptor)
        } else {
            readBatteryValue(gatt, characteristic)
        }
    }

    @SuppressLint("MissingPermission")
    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    private fun readBatteryValue(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
        val ok = gatt.readCharacteristic(characteristic)
        HLog.d("Opossum", "readCharacteristic battery: success=$ok")
    }

    private fun parseBatteryLevel(data: ByteArray) {
        if (data.isNotEmpty()) {
            val level = data[0].toInt() and 0xFF
            _batteryLevel.update { level }
            HLog.d("Opossum", "Battery level: $level%")
        }
    }

    private fun parseNotifyMessage(data: ByteArray) {
        if (data.size < 1) return

        when (data[0].toInt() and 0xFF) {
            0xB3 -> {
                if (data.size >= 3) {
                    val channelA = data[1].toInt() and 0xFF
                    val channelB = data[2].toInt() and 0xFF
                    // B3 response contains physical channel values;
                    // when swap is active, map them back to the logical sliders
                    if (_swapChannels.value) {
                        _channelAAmp.update { channelB }
                        _channelBAmp.update { channelA }
                    } else {
                        _channelAAmp.update { channelA }
                        _channelBAmp.update { channelB }
                    }
                    HLog.d("Opossum", "B3 response: A=$channelA, B=$channelB (swap=${_swapChannels.value})")
                    // Per protocol: after receiving B3 message, immediately send B2 to update display
                    sendB2Command(channelA, channelB)
                }
            }
            0xD0 -> {
                HLog.d("Opossum", "D0 response (key state)")
            }
            else -> {
                HLog.d("Opossum", "Unknown message HEAD: ${data[0].toInt() and 0xFF}")
            }
        }
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    private fun sendB2Command(channelA: Int, channelB: Int) {
        val displayCommand = ByteArray(24)
        displayCommand[0] = 0xB2.toByte()
        val fixedData = byteArrayOf(
            0xFF.toByte(), 0xFF.toByte(), 0x00,
            0xFF.toByte(),0xFF.toByte(),0xFF.toByte(),0xFF.toByte(),
            0xFF.toByte(),0xFF.toByte(),0xFF.toByte(),0xFF.toByte(),
            0xFF.toByte(),0xFF.toByte(),0xFF.toByte(),0xFF.toByte(),
            0xFF.toByte(),0xFF.toByte(),0xFF.toByte(),0xFF.toByte(),
            0x08, 0x09
        )
        System.arraycopy(fixedData, 0, displayCommand, 1, fixedData.size)
        displayCommand[22] = channelA.toByte()
        displayCommand[23] = channelB.toByte()
        val hexStr = displayCommand.joinToString("") { "%02X".format(it) }
        HLog.d("Opossum", "Sending B2: A=$channelA, B=$channelB, hex=$hexStr")
        writeCommand(displayCommand)
    }

    @SuppressLint("MissingPermission")
    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    private fun writeCommand(command: ByteArray, writeType: Int = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT) {
        synchronized(writeLock) {
            if (isWriting) {
                writeQueue.addLast(command to writeType)
                return
            }
            isWriting = true
        }
        actuallyWrite(command, writeType)
    }

    @SuppressLint("MissingPermission")
    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    private fun actuallyWrite(command: ByteArray, writeType: Int) {
        val gatt = bluetoothGatt ?: run {
            synchronized(writeLock) { isWriting = false }
            return
        }
        val writeChar = gatt.getService(MAIN_SERVICE_UUID)
            ?.getCharacteristic(WRITE_CHARACTERISTIC_UUID) ?: run {
            HLog.d("Opossum", "Write characteristic not found")
            processNextWrite()
            return
        }

        HLog.v("Opossum", "Writing ${command.size} bytes, MTU=$currentMtu, writeType=$writeType")
        if (command.size > currentMtu) {
            HLog.w("Opossum", "WARNING: command size ${command.size} > MTU $currentMtu, will be truncated!")
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val result = gatt.writeCharacteristic(
                writeChar,
                command,
                writeType
            )
            HLog.v("Opossum", "writeCharacteristic result: $result")
            if (result != BluetoothGatt.GATT_SUCCESS) {
                HLog.w("Opossum", "writeCharacteristic failed: $result")
                processNextWrite()
            }
        } else {
            writeChar.value = command
            val success = gatt.writeCharacteristic(writeChar)
            HLog.v("Opossum", "writeCharacteristic returned: $success")
            if (!success) {
                HLog.w("Opossum", "writeCharacteristic returned false")
                processNextWrite()
            }
        }
    }

    private fun processNextWrite() {
        val next: Pair<ByteArray, Int>?
        synchronized(writeLock) {
            if (writeQueue.isEmpty()) {
                isWriting = false
                return
            }
            next = writeQueue.removeFirst()
        }
        actuallyWrite(next!!.first, next.second)
    }

    @SuppressLint("MissingPermission")
    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    fun sendBleCommand(command: ByteArray, writeType: Int = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT) {
        if (_connectionStatus.value != OpossumConnectionStatus.Connected) return
        writeCommand(command, writeType)
    }

    @SuppressLint("MissingPermission")
    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    private fun startPressureReporting() {
        if (_connectionStatus.value != OpossumConnectionStatus.Connected) return
        val command = ByteArray(3)
        command[0] = 0x50.toByte()
        command[1] = 0x01
        command[2] = 0x01
        writeCommand(command)
        HLog.d("Opossum", "Sent 50 command: enable key state reporting")
    }

    @SuppressLint("MissingPermission")
    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    private fun stopPressureReporting() {
        val command = ByteArray(3)
        command[0] = 0x50.toByte()
        command[1] = 0x01
        command[2] = 0x00
        writeCommand(command)
    }

    fun setChannelA(value: Int) {
        val clamped = value.coerceIn(0, 200)
        _channelAAmp.update { clamped }
        if (_syncChannels.value) {
            _channelBAmp.update { clamped }
        }
        onManualChannelChange?.invoke(_channelAAmp.value, _channelBAmp.value)
        if (_connectionStatus.value != OpossumConnectionStatus.Connected) return
        outputOpossum?.channelAAmp = clamped
        if (_syncChannels.value) {
            outputOpossum?.channelBAmp = clamped
        }
        viewModelScope.launch {
            val (a, b) = getEffectiveChannels()
            sendB3Command(a, b)
        }
    }

    fun setChannelB(value: Int) {
        val clamped = value.coerceIn(0, 200)
        _channelBAmp.update { clamped }
        if (_syncChannels.value) {
            _channelAAmp.update { clamped }
        }
        onManualChannelChange?.invoke(_channelAAmp.value, _channelBAmp.value)
        if (_connectionStatus.value != OpossumConnectionStatus.Connected) return
        outputOpossum?.channelBAmp = clamped
        if (_syncChannels.value) {
            outputOpossum?.channelAAmp = clamped
        }
        viewModelScope.launch {
            val (a, b) = getEffectiveChannels()
            sendB3Command(a, b)
        }
    }

    fun setChannels(a: Int, b: Int) {
        val clampedA = a.coerceIn(0, 200)
        val clampedB = b.coerceIn(0, 200)
        if (_syncChannels.value) {
            _channelAAmp.update { clampedA }
            _channelBAmp.update { clampedA }
        } else {
            _channelAAmp.update { clampedA }
            _channelBAmp.update { clampedB }
        }
        if (_connectionStatus.value != OpossumConnectionStatus.Connected) return
        if (_syncChannels.value) {
            outputOpossum?.channelAAmp = clampedA
            outputOpossum?.channelBAmp = clampedA
        } else {
            outputOpossum?.channelAAmp = clampedA
            outputOpossum?.channelBAmp = clampedB
        }
        viewModelScope.launch {
            val (effA, effB) = getEffectiveChannels()
            sendB3Command(effA, effB)
        }
    }

    fun setSyncChannels(enabled: Boolean) {
        _syncChannels.value = enabled
        if (enabled) {
            val current = _channelAAmp.value
            _channelBAmp.update { current }
            if (_connectionStatus.value == OpossumConnectionStatus.Connected) {
                outputOpossum?.channelBAmp = current
                outputOpossum?.channelAAmp = current
                viewModelScope.launch {
                    val (a, b) = getEffectiveChannels()
                    sendB3Command(a, b)
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    private suspend fun sendB3Command(channelA: Int, channelB: Int) {
        val command = ByteArray(3)
        command[0] = 0xB3.toByte()
        command[1] = channelA.toByte()
        command[2] = channelB.toByte()
        HLog.d("Opossum", "Sending B3: A=$channelA, B=$channelB")
        writeCommand(command)
    }

    fun setSelectedActivity(type: ActivityType) {
        _selectedActivity.value = type
        if (outputOpossum?.isPlaying() == true) {
            outputOpossum?.currentActivityType = type
        }
    }

    fun setActivityChangeProbability(probability: Float) {
        val clamped = probability.coerceIn(0f, 1f)
        _activityChangeProbability.value = clamped
        Prefs.activityChangeProbability.value = clamped
        Prefs.activityChangeProbability.save()
        outputOpossum?.activityChangeProbability = clamped
    }

    fun startPlayback() {
        // Stop Player Sync if active - mutually exclusive
        if (_playerSyncEnabled.value) {
            _playerSyncEnabled.update { false }
            stopPlayerSync()
        }

        // Send initial B3+B2 to set channel intensities before starting playback
        // (skipped automatically if not connected - sendB3Command checks connection)
        viewModelScope.launch {
            sendB3Command(_channelAAmp.value, _channelBAmp.value)
        }

        outputOpossum = OutputOpossum().apply {
            this.currentActivityType = _selectedActivity.value
            this.activityChangeProbability = _activityChangeProbability.value
            this.channelAAmp = _channelAAmp.value
            this.channelBAmp = _channelBAmp.value
            this.onActivityChanged = { newType ->
                _selectedActivity.value = newType
            }
        }

        outputOpossum?.start { command ->
            viewModelScope.launch {
                sendBleCommand(command, BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE)
            }
        }
        _isPlaying.update { true }
        HLog.d("Opossum", "Playback started with activity: ${_selectedActivity.value.name}")
    }

    fun stopPlayback() {
        outputOpossum?.stop()
        outputOpossum = null
        _isPlaying.update { false }
        HLog.d("Opossum", "Playback stopped")
    }

    fun setPlayerSyncEnabled(enabled: Boolean) {
        if (enabled == _playerSyncEnabled.value) return
        _playerSyncEnabled.update { enabled }

        if (enabled) {
            stopPlayback()
            startPlayerSync()
        } else {
            stopPlayerSync()
        }
    }

    private fun startPlayerSync() {
        HLog.d("Opossum", "Player Sync started (connected=${_connectionStatus.value == OpossumConnectionStatus.Connected})")
        lastSyncedChannelA = -1
        lastSyncedChannelB = -1

        playerSyncJob = viewModelScope.launch {
            val pulses = mutableListOf<Pulse>()
            val pulseIntervalMs = 25L

            while (isActive && _playerSyncEnabled.value) {
                // Only send B0 waveform data when Player is actively playing
                if (Player.playerState.value.isPlaying) {
                    pulses.clear()
                    val currentPos = Player.getCurrentPosition()

                    // Sample 4 pulses at 25ms intervals
                    for (i in 0 until 4) {
                        val time = currentPos + (i * pulseIntervalMs / 1000.0)
                        val pulse = Player.getPulseAtTime(time)
                        pulses.add(pulse)
                    }

                    // Scale Player pulses by current channel intensities
                    val chA = _channelAAmp.value
                    val chB = _channelBAmp.value
                    val scaledPulses = pulses.map {
                        Pulse(
                            ampA = (it.ampA * chA / 200.0f).coerceIn(0f, 1f),
                            ampB = (it.ampB * chB / 200.0f).coerceIn(0f, 1f),
                            freqA = it.freqA,
                            freqB = it.freqB
                        )
                    }

                    // Build B0 command from scaled pulses
                    val b0Command = buildB0FromPulses(scaledPulses)
                    if (_connectionStatus.value == OpossumConnectionStatus.Connected) {
                        writeCommand(b0Command, BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE)
                    }
                }

                // Send B3 when channel values change
                val chA = _channelAAmp.value
                val chB = _channelBAmp.value
                if (chA != lastSyncedChannelA || chB != lastSyncedChannelB) {
                    lastSyncedChannelA = chA
                    lastSyncedChannelB = chB
                    HLog.d("Opossum", "Player Sync: B3 A=$chA B=$chB")
                    if (_connectionStatus.value == OpossumConnectionStatus.Connected) {
                        sendB3Command(chA, chB)
                    }
                }

                delay(pulseIntervalMs * 4)
            }
            HLog.d("Opossum", "Player Sync loop ended")
        }
    }

    private fun stopPlayerSync() {
        playerSyncJob?.cancel()
        playerSyncJob = null
        HLog.d("Opossum", "Player Sync stopped")
    }

    fun setSwapChannels(enabled: Boolean) {
        _swapChannels.update { enabled }
        HLog.d("Opossum", "Swap channels: $enabled")
        // Immediately send B3 with swapped values
        viewModelScope.launch {
            val (a, b) = getEffectiveChannels()
            sendB3Command(a, b)
        }
    }

    private fun getEffectiveChannels(): Pair<Int, Int> {
        return if (_swapChannels.value) {
            _channelBAmp.value to _channelAAmp.value
        } else {
            _channelAAmp.value to _channelBAmp.value
        }
    }

    private fun buildB0FromPulses(pulses: List<Pulse>): ByteArray {
        var ampA = ByteArray(4)
        var ampB = ByteArray(4)
        for (i in 0 until 4) {
            ampA[i] = (pulses[i].ampA * 100).toInt().coerceIn(0, 100).toByte()
            ampB[i] = (pulses[i].ampB * 100).toInt().coerceIn(0, 100).toByte()
        }
        if (_swapChannels.value) {
            val tmp = ampA
            ampA = ampB
            ampB = tmp
        }
        return byteArrayOf(
            0xB0.toByte(),
            0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
            *ampA,
            0x00, 0x00, 0x00, 0x00,
            *ampB
        )
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    override fun onCleared() {
        super.onCleared()
        stopPressureReporting()
        bluetoothGatt?.disconnect()
        bluetoothGatt?.close()
        bluetoothGatt = null
    }
}
