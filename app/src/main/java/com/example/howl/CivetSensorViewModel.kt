package com.example.howl

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
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
import kotlinx.coroutines.runBlocking
import java.util.UUID
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

enum class CivetSensorConnectionStatus {
    Disconnected,
    Scanning,
    Connecting,
    Connected,
    Error
}

class CivetSensorViewModel : ViewModel() {

    private val CIVET_DEVICE_NAME = "47L124000"

    private val _connectionStatus = MutableStateFlow(CivetSensorConnectionStatus.Disconnected)
    val connectionStatus: StateFlow<CivetSensorConnectionStatus> = _connectionStatus.asStateFlow()

    private val _rawPressure = MutableStateFlow(0f)
    val rawPressure: StateFlow<Float> = _rawPressure.asStateFlow()

    private val _displayPressure = MutableStateFlow(0f)
    val displayPressure: StateFlow<Float> = _displayPressure.asStateFlow()

    private val _pressureHistory = MutableStateFlow(List(200) { 0f })
    val pressureHistory: StateFlow<List<Float>> = _pressureHistory.asStateFlow()

    private fun addPressureToHistory(pressure: Float) {
        _pressureHistory.update { list ->
            list.drop(1) + pressure
        }
    }

    private val smoothingAlpha = 0.3f

    private val _pressureRange = MutableStateFlow(0f..99f)
    val pressureRange: StateFlow<ClosedFloatingPointRange<Float>> = _pressureRange.asStateFlow()

    private val _powerRange = MutableStateFlow(20f..80f)
    val powerRange: StateFlow<ClosedFloatingPointRange<Float>> = _powerRange.asStateFlow()

    private val _opossumIntensityRange = MutableStateFlow(0f..100f)
    val opossumIntensityRange: StateFlow<ClosedFloatingPointRange<Float>> =
        _opossumIntensityRange.asStateFlow()

    private val _powerMappingEnabled = MutableStateFlow(false)
    val powerMappingEnabled: StateFlow<Boolean> = _powerMappingEnabled.asStateFlow()

    private val _opossumMappingEnabled = MutableStateFlow(false)
    val opossumMappingEnabled: StateFlow<Boolean> = _opossumMappingEnabled.asStateFlow()

    private var opossumViewModel: OpossumViewModel? = null

    private val _opossumMapped = MutableStateFlow(0)
    val opossumMapped: StateFlow<Int> = _opossumMapped.asStateFlow()

    private var opossumBaseA = 0
    private var opossumBaseB = 0

    private val _denialToZero = MutableStateFlow(false)
    val denialToZero: StateFlow<Boolean> = _denialToZero.asStateFlow()

    private val _smoothingTime = MutableStateFlow(1.0f)
    val smoothingTime: StateFlow<Float> = _smoothingTime.asStateFlow()

    private val _screenRotation = MutableStateFlow(1)
    val screenRotation: StateFlow<Int> = _screenRotation.asStateFlow()

    private val _batteryLevel = MutableStateFlow(-1)
    val batteryLevel: StateFlow<Int> = _batteryLevel.asStateFlow()

    private val _testPressure = MutableStateFlow(0f)
    val testPressure: StateFlow<Float> = _testPressure.asStateFlow()

    private val _denials = MutableStateFlow(0)
    val denials: StateFlow<Int> = _denials.asStateFlow()

    private val _denialsCount = MutableStateFlow(0)
    val denialsCount: StateFlow<Int> = _denialsCount.asStateFlow()

    private val _cooldownTime = MutableStateFlow(10f)
    val cooldownTime: StateFlow<Float> = _cooldownTime.asStateFlow()

    private val _cooldownRemaining = MutableStateFlow(10)
    val cooldownRemaining: StateFlow<Int> = _cooldownRemaining.asStateFlow()

    private var denialsTriggered = false
    private var cooldownJob: Job? = null
    private var isCoolingDown = false

    private var bluetoothGatt: BluetoothGatt? = null
    private var bluetoothDevice: BluetoothDevice? = null
    private var contextRef: Context? = null

    companion object {
        const val PERMISSION_BLUETOOTH_SCAN = "android.permission.BLUETOOTH_SCAN"
        const val PERMISSION_BLUETOOTH_CONNECT = "android.permission.BLUETOOTH_CONNECT"

        val ALL_BLE_PERMISSIONS = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN)
        } else {
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }

        val SENSOR_SERVICE_UUID = UUID.fromString("0000180C-0000-1000-8000-00805f9b34fb")
        val WRITE_CHARACTERISTIC_UUID = UUID.fromString("0000150A-0000-1000-8000-00805f9b34fb")
        val NOTIFY_CHARACTERISTIC_UUID = UUID.fromString("0000150B-0000-1000-8000-00805f9b34fb")
        val BATTERY_SERVICE_UUID = UUID.fromString("0000180A-0000-1000-8000-00805f9b34fb")
        val BATTERY_CHARACTERISTIC_UUID = UUID.fromString("00001500-0000-1000-8000-00805f9b34fb")
        val STANDARD_BATTERY_CHARACTERISTIC_UUID =
            UUID.fromString("00002A19-0000-1000-8000-00805f9b34fb")
        val CCCD_DESCRIPTOR_UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
    }

    fun initialize(context: Context) {
        this.contextRef = context
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    suspend fun attemptConnection() {
        _connectionStatus.update { CivetSensorConnectionStatus.Scanning }
        scanForCivetSensor()
    }

    @SuppressLint("MissingPermission")
    fun disconnect() {
        stopPressureReporting()
        stopSmoothingLoop()
        _targetPowerA.update { 0 }
        _targetPowerB.update { 0 }
        _currentAppliedPowerA.update { 0 }
        _currentAppliedPowerB.update { 0 }
        bluetoothGatt?.disconnect()
        bluetoothGatt?.close()
        bluetoothGatt = null
        bluetoothDevice = null
        _connectionStatus.update { CivetSensorConnectionStatus.Disconnected }
    }

    @SuppressLint("MissingPermission")
    @kotlinx.coroutines.ExperimentalCoroutinesApi
    suspend fun scanForCivetSensor() {
        val context = contextRef ?: return

        val bluetoothManager =
            context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        val adapter = bluetoothManager?.adapter ?: return

        if (!adapter.isEnabled) {
            _connectionStatus.update { CivetSensorConnectionStatus.Error }
            HLog.d("CivetSensor", "Bluetooth is off.")
            return
        }

        val locationManager =
            context.getSystemService(Context.LOCATION_SERVICE) as? android.location.LocationManager
        if (locationManager != null && !androidx.core.location.LocationManagerCompat.isLocationEnabled(
                locationManager
            )
        ) {
            HLog.d("CivetSensor", "Warning: Location services are disabled.")
        }

        val scanner = adapter.bluetoothLeScanner
        val scanSettings = android.bluetooth.le.ScanSettings.Builder()
            .setScanMode(android.bluetooth.le.ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        val timeoutHandler = Handler(Looper.getMainLooper())

        val scanCallback = object : android.bluetooth.le.ScanCallback() {
            override fun onScanResult(type: Int, result: android.bluetooth.le.ScanResult?) {
                result?.let {
                    bluetoothDevice = it.device
                    val address = it.device.address
                    val advertisedName = it.scanRecord?.deviceName ?: it.device.name ?: "<unknown>"

                    HLog.d("CivetSensor", "Scan saw BLE device $address ($advertisedName)")

                    if (isCivetDevice(advertisedName)) {
                        HLog.d("CivetSensor", "Found Civet sensor, connecting...")
                        timeoutHandler.removeCallbacksAndMessages(null)
                        scanner?.stopScan(this)
                        android.os.Handler(Looper.getMainLooper()).post {
                            runBlocking {
                                connectToDevice(it.device)
                            }
                        }
                    }
                }
            }

            override fun onScanFailed(errorCode: Int) {
                HLog.d("CivetSensor", "Scan failed: $errorCode")
                _connectionStatus.update { CivetSensorConnectionStatus.Error }
            }
        }

        HLog.d("CivetSensor", "Starting BLE scan for Civet sensor...")
        scanner?.startScan(null, scanSettings, scanCallback)

        timeoutHandler.postDelayed({
            HLog.d("CivetSensor", "Scan timeout")
            scanner?.stopScan(scanCallback)
            _connectionStatus.update { CivetSensorConnectionStatus.Error }
        }, 10000L)
    }

    @SuppressLint("MissingPermission")
    @Suppress("DEPRECATION")
    private suspend fun connectToDevice(device: BluetoothDevice) {
        _connectionStatus.update { CivetSensorConnectionStatus.Connecting }
        bluetoothDevice = device
        val context = contextRef ?: return
        bluetoothGatt =
            device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
    }

    private val gattCallback = object : android.bluetooth.BluetoothGattCallback() {

        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                disconnect()
                _connectionStatus.update { CivetSensorConnectionStatus.Error }
                return
            }

            if (newState == BluetoothGatt.STATE_CONNECTED) {
                HLog.d("CivetSensor", "Connected, discovering services...")
                gatt.discoverServices()
            } else if (newState == BluetoothGatt.STATE_DISCONNECTED) {
                disconnect()
                _connectionStatus.update { CivetSensorConnectionStatus.Disconnected }
            }
        }

        @SuppressLint("MissingPermission")
        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                disconnect()
                _connectionStatus.update { CivetSensorConnectionStatus.Error }
                return
            }

            HLog.d("CivetSensor", "Services discovered")
            for (service in gatt.services) {
                HLog.d("CivetSensor", "  Service: ${service.uuid}")
                for (char in service.characteristics) {
                    HLog.d("CivetSensor", "    Char: ${char.uuid}, props=${char.properties}")
                }
            }

            _connectionStatus.update { CivetSensorConnectionStatus.Connected }

            subscribeToNotifyCharacteristic(gatt)
            subscribeToBatteryCharacteristic(gatt)

            Handler(Looper.getMainLooper()).postDelayed({
                if (_connectionStatus.value == CivetSensorConnectionStatus.Connected) {
                    HLog.d("CivetSensor", "Retrying battery read after delay")
                    gatt.getService(BATTERY_SERVICE_UUID)?.let { svc ->
                        val custom = svc.getCharacteristic(BATTERY_CHARACTERISTIC_UUID)
                        val standard = svc.getCharacteristic(STANDARD_BATTERY_CHARACTERISTIC_UUID)
                        val char = custom ?: standard
                        if (char != null) {
                            readBatteryValue(gatt, char)
                        }
                    }
                }
            }, 2000L)
        }

        @Suppress("DEPRECATION")
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic
        ) {
            val data = characteristic.value ?: return
            HLog.d(
                "CivetSensor",
                "Characteristic changed: ${characteristic.uuid}, len=${data.size}"
            )

            when (characteristic.uuid) {
                NOTIFY_CHARACTERISTIC_UUID -> parseNotifyMessage(data)
                BATTERY_CHARACTERISTIC_UUID, STANDARD_BATTERY_CHARACTERISTIC_UUID -> parseBatteryLevel(
                    data
                )
            }
        }

        @SuppressLint("MissingPermission")
        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                val uuid = characteristic.uuid
                if (uuid == BATTERY_CHARACTERISTIC_UUID || uuid == STANDARD_BATTERY_CHARACTERISTIC_UUID) {
                    val data = characteristic.value ?: return
                    parseBatteryLevel(data)
                    HLog.d("CivetSensor", "Battery read success from $uuid: ${data[0]}%")
                } else {
                    HLog.d("CivetSensor", "Characteristic read success: $uuid")
                }
            } else {
                HLog.d("CivetSensor", "Characteristic read failed: status=$status")
            }
        }

        override fun onDescriptorWrite(
            gatt: BluetoothGatt,
            descriptor: BluetoothGattDescriptor,
            status: Int
        ) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                HLog.d(
                    "CivetSensor",
                    "Descriptor write success for ${descriptor.characteristic.uuid}"
                )
                if (descriptor.uuid == CCCD_DESCRIPTOR_UUID) {
                    val parentChar = descriptor.characteristic
                    when (parentChar.uuid) {
                        NOTIFY_CHARACTERISTIC_UUID -> startPressureReporting()
                        BATTERY_CHARACTERISTIC_UUID, STANDARD_BATTERY_CHARACTERISTIC_UUID -> readBatteryValue(
                            gatt,
                            parentChar
                        )
                    }
                }
            } else {
                HLog.d("CivetSensor", "Descriptor write failed: $status")
            }
        }
    }

    @SuppressLint("MissingPermission")
    @Suppress("DEPRECATION")
    private fun subscribeToBatteryCharacteristic(gatt: BluetoothGatt) {
        val service = gatt.getService(BATTERY_SERVICE_UUID)
        if (service == null) {
            HLog.d("CivetSensor", "Battery service (0x180A) not found")
            tryReadBatteryFromSensorService(gatt)
            return
        }

        val characteristic = service.getCharacteristic(BATTERY_CHARACTERISTIC_UUID)
            ?: service.getCharacteristic(STANDARD_BATTERY_CHARACTERISTIC_UUID)

        if (characteristic == null) {
            HLog.d("CivetSensor", "Battery characteristic not found in service")
            tryReadBatteryFromSensorService(gatt)
            return
        }

        HLog.d(
            "CivetSensor",
            "Found battery characteristic: ${characteristic.uuid}, properties: ${characteristic.properties}"
        )
        val notifyOk = gatt.setCharacteristicNotification(characteristic, true)
        HLog.d("CivetSensor", "setCharacteristicNotification for battery: $notifyOk")

        val cccDescriptor = characteristic.getDescriptor(CCCD_DESCRIPTOR_UUID)
        if (cccDescriptor != null) {
            cccDescriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            val writeOk = gatt.writeDescriptor(cccDescriptor)
            HLog.d("CivetSensor", "Battery CCCD write initiated: $writeOk")
        } else {
            HLog.d("CivetSensor", "Battery CCCD descriptor not found, reading directly")
            readBatteryValue(gatt, characteristic)
        }
    }

    @SuppressLint("MissingPermission")
    @Suppress("DEPRECATION")
    private fun tryReadBatteryFromSensorService(gatt: BluetoothGatt) {
        val sensorService = gatt.getService(SENSOR_SERVICE_UUID) ?: return
        val batteryChar = sensorService.getCharacteristic(STANDARD_BATTERY_CHARACTERISTIC_UUID)
            ?: sensorService.getCharacteristic(BATTERY_CHARACTERISTIC_UUID)
        if (batteryChar != null) {
            HLog.d("CivetSensor", "Trying battery char from sensor service: ${batteryChar.uuid}")
            readBatteryValue(gatt, batteryChar)
        }
    }

    @SuppressLint("MissingPermission")
    @Suppress("DEPRECATION")
    private fun readBatteryValue(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
        val ok = gatt.readCharacteristic(characteristic)
        HLog.d("CivetSensor", "readCharacteristic battery: success=$ok")
    }

    private fun parseBatteryLevel(data: ByteArray) {
        if (data.isNotEmpty()) {
            val level = data[0].toInt() and 0xFF
            _batteryLevel.update { level }
            HLog.d("CivetSensor", "Battery level: $level%")
        }
    }

    @SuppressLint("MissingPermission")
    @Suppress("DEPRECATION")
    private fun subscribeToNotifyCharacteristic(gatt: BluetoothGatt) {
        val characteristic = gatt.getService(SENSOR_SERVICE_UUID)
            ?.getCharacteristic(NOTIFY_CHARACTERISTIC_UUID) ?: run {
            HLog.d("CivetSensor", "Notify characteristic not found")
            return
        }

        if (gatt.setCharacteristicNotification(characteristic, true)) {
            val cccDescriptor = characteristic.getDescriptor(CCCD_DESCRIPTOR_UUID)
            if (cccDescriptor != null) {
                cccDescriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                gatt.writeDescriptor(cccDescriptor)
                HLog.d("CivetSensor", "CCCD descriptor write initiated")
            } else {
                HLog.d("CivetSensor", "CCCD descriptor not found on characteristic")
            }
        } else {
            HLog.d("CivetSensor", "setCharacteristicNotification failed")
        }
    }

    private fun parseNotifyMessage(data: ByteArray) {
        if (data.size < 1) return

        when (data[0].toInt() and 0xFF) {
            0xD0 -> {
                if (data.size >= 10) {
                    val pressure = parsePressureValue(data)
                    _rawPressure.update { pressure }
                    _displayPressure.update { current ->
                        val smoothed = smoothingAlpha * pressure + (1f - smoothingAlpha) * current
                        val result = if (kotlin.math.abs(smoothed) < 0.05f) 0f else smoothed
                        addPressureToHistory(result)
                        result
                    }
                    applyMappedPower()
                }
            }

            else -> {
                HLog.d("CivetSensor", "Unknown message HEAD: ${data[0].toInt() and 0xFF}")
            }
        }
    }

    private fun parsePressureValue(data: ByteArray): Float {
        val lowByte = data[8].toInt() and 0xFF
        val highByte = data[9].toInt() and 0xFF
        val unsignedValue = (highByte shl 8) or lowByte
        val signedValue = if (unsignedValue >= 0x8000) unsignedValue - 0x10000 else unsignedValue
        val pressure = signedValue / 100f
        HLog.d(
            "CivetSensor",
            "Pressure bytes: [$lowByte, $highByte], signed=$signedValue, pressure=$pressure kPa"
        )
        return pressure.coerceIn(-99.99f, 99.99f)
    }

    @SuppressLint("MissingPermission")
    @Suppress("DEPRECATION")
    private fun writeCommand(command: ByteArray) {
        val gatt = bluetoothGatt ?: return
        val writeChar = gatt.getService(SENSOR_SERVICE_UUID)
            ?.getCharacteristic(WRITE_CHARACTERISTIC_UUID) ?: run {
            HLog.d("CivetSensor", "Write characteristic not found")
            return
        }

        writeChar.value = command
        gatt.writeCharacteristic(writeChar)
        HLog.v("CivetSensor", "Wrote command: ${command.joinToString(",") { "0x%02X".format(it) }}")
    }

    @SuppressLint("MissingPermission")
    private fun startPressureReporting() {
        if (_connectionStatus.value != CivetSensorConnectionStatus.Connected) return
        val command = ByteArray(17)
        command[0] = 0x50.toByte()
        command[1] = 0x01
        command[2] = 0xD0.toByte()
        writeCommand(command)
        HLog.d("CivetSensor", "Sent 50 command: start pressure reporting")
    }

    @SuppressLint("MissingPermission")
    private fun stopPressureReporting() {
        val command = ByteArray(17)
        command[0] = 0x50.toByte()
        command[1] = 0x01
        command[2] = 0x00
        writeCommand(command)
        HLog.d("CivetSensor", "Sent 50 command: stop pressure reporting")
    }

    fun resetBaseline() {
        val command = ByteArray(13)
        command[0] = 0x66.toByte()
        command[10] = 0x00
        command[11] = 0x00
        command[12] = 0x02
        writeCommand(command)
        HLog.d("CivetSensor", "Sent 66 command: reset pressure on device")
    }

    fun flipScreenRotation() {
        val newRotation = if (_screenRotation.value == 1) 3 else 1
        val command = ByteArray(13)
        command[0] = 0x66.toByte()
        command[10] = newRotation.toByte()
        command[11] = 0x00
        command[12] = 0x00
        writeCommand(command)
        _screenRotation.value = newRotation
        HLog.d("CivetSensor", "Sent 66 command: flip screen to orientation $newRotation")
    }

    private fun isCivetDevice(deviceName: String): Boolean {
        return deviceName == CIVET_DEVICE_NAME || deviceName.contains(
            CIVET_DEVICE_NAME,
            ignoreCase = true
        )
    }

    fun getStatusText(): String {
        return when (_connectionStatus.value) {
            CivetSensorConnectionStatus.Disconnected -> "Disconnected"
            CivetSensorConnectionStatus.Scanning -> "Scanning..."
            CivetSensorConnectionStatus.Connecting -> "Connecting..."
            CivetSensorConnectionStatus.Connected -> {
                val battery = _batteryLevel.value
                if (battery >= 0) "Connected · ${battery}%" else "Connected"
            }

            CivetSensorConnectionStatus.Error -> "Error"
        }
    }

    fun setPressureRange(range: ClosedFloatingPointRange<Float>) {
        _pressureRange.value = range
    }

    fun setPowerRange(range: ClosedFloatingPointRange<Float>) {
        _powerRange.value = range
    }

    fun setOpossumIntensityRange(range: ClosedFloatingPointRange<Float>) {
        _opossumIntensityRange.value = range
    }

    fun setSmoothingTime(time: Float) {
        _smoothingTime.value = time
    }

    fun setDenialToZero(enabled: Boolean) {
        _denialToZero.value = enabled
    }

    fun setOpossumViewModel(vm: OpossumViewModel) {
        opossumViewModel = vm
    }

    fun setOpossumMappingEnabled(enabled: Boolean) {
        _opossumMappingEnabled.value = enabled
        if (enabled) {
            opossumViewModel?.let { vm ->
                opossumBaseA = vm.channelAAmp.value
                opossumBaseB = vm.channelBAmp.value
                vm.onManualChannelChange = { a, b ->
                    updateOpossumBaseFromManual(a, b)
                }
            }
            resetDenialsCount()
            applyMappedPower()
        } else {
            opossumViewModel?.let { vm ->
                vm.onManualChannelChange = null
                if (vm.connectionStatus.value == OpossumConnectionStatus.Connected) {
                    vm.setChannels(opossumBaseA, opossumBaseB)
                }
            }
            _opossumMapped.update { 0 }
        }
    }

    /**
     * 滑块手动调整时更新存储的基础值（新 base = 当前值 - 当前 delta）
     */
    private fun updateOpossumBaseFromManual(a: Int, b: Int) {
        val pressure = _displayPressure.value
        val pRange = _pressureRange.value
        val maxPressure = pRange.endInclusive
        val opossumDelta = if (maxPressure > 0f) {
            val intensityRange = _opossumIntensityRange.value
            val t = (pressure / maxPressure).coerceIn(0f, 1f)
            val mapped = intensityRange.start + t * (intensityRange.endInclusive - intensityRange.start)
            mapped.coerceIn(0f, 200f).toInt()
        } else 0
        opossumBaseA = (a - opossumDelta).coerceIn(0, 200)
        opossumBaseB = (b - opossumDelta).coerceIn(0, 200)
    }

    fun setPowerMappingEnabled(enabled: Boolean) {
        _powerMappingEnabled.value = enabled
        if (enabled) {
            basePowerA = MainOptions.getChannelPower(0)
            basePowerB = if (isPowerSyncEnabled()) basePowerA else MainOptions.getChannelPower(1)
            resetDenialsCount()
            applyMappedPower()
        } else {
            _targetPowerA.update { basePowerA }
            _targetPowerB.update { basePowerB }
            _currentAppliedPowerA.update { basePowerA }
            _currentAppliedPowerB.update { basePowerB }
            MainOptions.setChannelPower(0, basePowerA)
            MainOptions.setChannelPower(1, basePowerB)
            stopSmoothingLoop()
            stopCooldown()
        }
    }

    fun setTestPressure(pressure: Float) {
        val coerced = pressure.coerceIn(0f, 99.9f)
        _testPressure.value = coerced
        _rawPressure.update { coerced }
        _displayPressure.update {
            val smoothed = smoothingAlpha * coerced + (1f - smoothingAlpha) * it
            val result = if (kotlin.math.abs(smoothed) < 0.05f) 0f else smoothed
            addPressureToHistory(result)
            result
        }
        applyMappedPower()
    }

    fun setDenials(value: Int) {
        _denials.value = value.coerceIn(0, 999)
    }

    fun setCooldownTime(seconds: Float) {
        _cooldownTime.value = seconds.coerceIn(0f, 60f)
    }

    fun resetDenialsCount() {
        _denialsCount.value = 0
        denialsTriggered = false
        stopCooldown()
    }

    private fun startCooldown() {
        stopCooldown()
        val cooldownSeconds = _cooldownTime.value
        if (cooldownSeconds <= 0f) return

        isCoolingDown = true
        _cooldownRemaining.value = cooldownSeconds.toInt()

        cooldownJob = viewModelScope.launch {
            val stepMs = 1000L
            var remainingMs = (cooldownSeconds * 1000).toLong()
            while (remainingMs > 0) {
                delay(stepMs)
                remainingMs -= stepMs
                _cooldownRemaining.value = (remainingMs / 1000).coerceAtLeast(0).toInt()
            }
            isCoolingDown = false
            _cooldownRemaining.value = 0
            cooldownJob = null
            HLog.d("CivetSensor", "Cooldown finished, reapplying mapped power")
            applyMappedPower()
        }
    }

    private fun stopCooldown() {
        cooldownJob?.cancel()
        cooldownJob = null
        isCoolingDown = false
        _cooldownRemaining.value = 0
    }

    fun togglePowerMapping() {
        _powerMappingEnabled.update { !it }
    }

    fun calculateMappedPower(): Int {
        val pressure = _rawPressure.value
        val pRange = _pressureRange.value
        val powerMin = _powerRange.value.start
        val powerMax = _powerRange.value.endInclusive

        val pressureSpan = pRange.endInclusive - pRange.start
        if (pressureSpan <= 0f) return 0

        val ratio = (pressure - pRange.start).coerceIn(0f, pressureSpan) / pressureSpan
        val delta = ratio * (powerMax - powerMin)
        return delta.roundToInt().coerceIn(0, 200)
    }

    private var smoothingJob: Job? = null
    private val _currentAppliedPowerA = MutableStateFlow(0)
    private val _currentAppliedPowerB = MutableStateFlow(0)
    private val _targetPowerA = MutableStateFlow(0)
    private val _targetPowerB = MutableStateFlow(0)
    private var stepAccumulatorA = 0f
    private var stepAccumulatorB = 0f
    private var basePowerA = 0
    private var basePowerB = 0

    private fun isPowerSyncEnabled(): Boolean = Prefs.powerSyncEnabled.value

    private fun startSmoothingLoop() {
        stopSmoothingLoop()
        smoothingJob = viewModelScope.launch {
            val stepMs = 50L
            while (isActive) {
                val smoothTime = _smoothingTime.value.coerceAtLeast(0.05f)
                val totalSteps = (smoothTime * 1000f / stepMs).coerceAtLeast(1f)
                val fraction = 1f / totalSteps

                // Channel A
                val currentA = _currentAppliedPowerA.value
                val targetA = _targetPowerA.value
                if (currentA != targetA) {
                    val diffA = targetA - currentA
                    stepAccumulatorA += diffA * fraction
                    val stepA = stepAccumulatorA.toInt()
                    if (stepA != 0) {
                        stepAccumulatorA -= stepA
                        var nextA = currentA + stepA
                        if ((stepA > 0 && nextA > targetA) || (stepA < 0 && nextA < targetA)) {
                            nextA = targetA
                            stepAccumulatorA = 0f
                        }
                        _currentAppliedPowerA.update { nextA }
                        MainOptions.setChannelPower(0, nextA)
                    }
                }

                // Channel B
                val currentB = _currentAppliedPowerB.value
                val targetB = _targetPowerB.value
                if (currentB != targetB) {
                    val diffB = targetB - currentB
                    stepAccumulatorB += diffB * fraction
                    val stepB = stepAccumulatorB.toInt()
                    if (stepB != 0) {
                        stepAccumulatorB -= stepB
                        var nextB = currentB + stepB
                        if ((stepB > 0 && nextB > targetB) || (stepB < 0 && nextB < targetB)) {
                            nextB = targetB
                            stepAccumulatorB = 0f
                        }
                        _currentAppliedPowerB.update { nextB }
                        MainOptions.setChannelPower(1, nextB)
                    }
                }

                delay(stepMs)
            }
        }
    }

    private fun stopSmoothingLoop() {
        smoothingJob?.cancel()
        smoothingJob = null
    }

    fun applyMappedPower() {
        val opossumMapping = _opossumMappingEnabled.value
        val powerMapping = _powerMappingEnabled.value

        if (!powerMapping && !opossumMapping) {
            stopSmoothingLoop()
            stopCooldown()
            return
        }

        val pressure = _rawPressure.value
        val pRange = _pressureRange.value
        val maxPressure = pRange.endInclusive

        // Calculate mapped delta for Opossum (to be added to base)
        val opossumDelta = if (opossumMapping) {
            val intensityRange = _opossumIntensityRange.value
            val t = (pressure / maxPressure).coerceIn(0f, 1f)
            val mapped =
                intensityRange.start + t * (intensityRange.endInclusive - intensityRange.start)
            mapped.coerceIn(0f, 200f).toInt()
        } else 0

        val opossumTargetA = (opossumBaseA + opossumDelta).coerceIn(0, 200)
        val opossumTargetB = (opossumBaseB + opossumDelta).coerceIn(0, 200)

        // Calculate mapped delta for Power (same delta added to each channel base)
        val powerDelta = if (powerMapping) calculateMappedPower() else 0
        val powerTargetA = (basePowerA + powerDelta).coerceIn(0, 200)
        val powerTargetB = if (isPowerSyncEnabled()) powerTargetA
            else (basePowerB + powerDelta).coerceIn(0, 200)

        // Handle denial trigger (shared between both mappings)
        if (pressure >= maxPressure && !denialsTriggered) {
            val denialsLimit = _denials.value
            val currentCount = _denialsCount.value

            if (denialsLimit == 0 || currentCount < denialsLimit) {
                val denialPowerA = if (_denialToZero.value) 0 else basePowerA
                val denialPowerB = if (_denialToZero.value) 0 else
                    if (isPowerSyncEnabled()) basePowerA else basePowerB

                if (powerMapping) {
                    _targetPowerA.update { denialPowerA }
                    _targetPowerB.update { denialPowerB }
                    _currentAppliedPowerA.update { denialPowerA }
                    _currentAppliedPowerB.update { denialPowerB }
                    MainOptions.setChannelPower(0, denialPowerA)
                    MainOptions.setChannelPower(1, denialPowerB)
                }

                if (opossumMapping) {
                    val denialA = if (_denialToZero.value) 0 else opossumBaseA
                    val denialB = if (_denialToZero.value) 0 else opossumBaseB
                    opossumViewModel?.let { vm ->
                        vm.setChannels(denialA, denialB)
                    }
                    _opossumMapped.update { denialA }
                }

                _denialsCount.update { it + 1 }
                denialsTriggered = true
                startCooldown()
                HLog.d(
                    "CivetSensor",
                    "Denial triggered: count=${_denialsCount.value}, limit=$denialsLimit, cooldown=${_cooldownTime.value}s"
                )
                return
            } else {
                if (powerMapping) {
                    _powerMappingEnabled.value = false
                    _targetPowerA.update { basePowerA }
                    _targetPowerB.update { basePowerB }
                    _currentAppliedPowerA.update { basePowerA }
                    _currentAppliedPowerB.update { basePowerB }
                    MainOptions.setChannelPower(0, basePowerA)
                    MainOptions.setChannelPower(1, basePowerB)
                    stopSmoothingLoop()
                }
                if (opossumMapping) {
                    _opossumMappingEnabled.value = false
                    opossumViewModel?.let { vm ->
                        vm.setChannels(opossumBaseA, opossumBaseB)
                    }
                    _opossumMapped.update { 0 }
                }
                HLog.d("CivetSensor", "Denials limit reached, mapping stopped")
                return
            }
        } else if (pressure < maxPressure && !isCoolingDown) {
            denialsTriggered = false
        }

        // Cooling down: restore base values
        if (isCoolingDown) {
            if (powerMapping) {
                _targetPowerA.update { basePowerA }
                _targetPowerB.update { basePowerB }
                _currentAppliedPowerA.update { basePowerA }
                _currentAppliedPowerB.update { basePowerB }
                MainOptions.setChannelPower(0, basePowerA)
                MainOptions.setChannelPower(1, basePowerB)
            }
            if (opossumMapping) {
                opossumViewModel?.let { vm ->
                    vm.setChannels(opossumBaseA, opossumBaseB)
                }
                _opossumMapped.update { opossumBaseA }
            }
            return
        }

        // Normal operation
        if (powerMapping) {
            _targetPowerA.update { powerTargetA }
            _targetPowerB.update { powerTargetB }
            if (smoothingJob?.isActive != true) {
                startSmoothingLoop()
            }
        }

        if (opossumMapping) {
            opossumViewModel?.let { vm ->
                vm.setChannels(opossumTargetA, opossumTargetB)
            }
            _opossumMapped.update { opossumDelta }
        }
    }

    fun checkPermissions(onResult: (Boolean) -> Unit) {
        val missingPermissions = ALL_BLE_PERMISSIONS.filter {
            ContextCompat.checkSelfPermission(
                contextRef ?: return,
                it
            ) != PackageManager.PERMISSION_GRANTED
        }

        if (missingPermissions.isEmpty()) {
            onResult(true)
        } else {
            HLog.d("CivetSensor", "Requesting permissions: ${missingPermissions.toString()}")
            onResult(false)
        }
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    override fun onCleared() {
        super.onCleared()
        stopSmoothingLoop()
        stopCooldown()
        stopPressureReporting()
        bluetoothGatt?.disconnect()
        bluetoothGatt?.close()
        bluetoothGatt = null
    }
}
