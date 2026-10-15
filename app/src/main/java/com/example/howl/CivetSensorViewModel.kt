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
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.util.UUID
import kotlin.math.roundToInt

enum class CivetSensorConnectionStatus {
    Disconnected,
    Scanning,
    Connecting,
    Connected,
    Error
}

enum class PowerIntensityType {
    TriggerMode,  // 触发模式：只有达到最大值/最小值时才改变输出
    MappingMode   // 映射模式：压力值按比例映射到输出强度
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

    private val _civetSensorEnable = MutableStateFlow(false)
    val civetSensorEnable: StateFlow<Boolean> = _civetSensorEnable.asStateFlow()

    private var opossumViewModel: OpossumViewModel? = null

    private val _opossumMapped = MutableStateFlow(0)
    val opossumMapped: StateFlow<Int> = _opossumMapped.asStateFlow()

    private var opossumBaseA = 0
    private var opossumBaseB = 0

    private val _denialToZero = MutableStateFlow(true)
    val denialToZero: StateFlow<Boolean> = _denialToZero.asStateFlow()
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

    private val _powerIntensityType = MutableStateFlow(PowerIntensityType.TriggerMode)
    val powerIntensityType: StateFlow<PowerIntensityType> = _powerIntensityType.asStateFlow()

    // 存储触发前的原始强度值（仅在 Trigger Mode 中使用）
    private var triggeredStoredPowerA = 0
    private var triggeredStoredPowerB = 0
    private var triggeredStoredOpossumA = 0
    private var triggeredStoredOpossumB = 0

    fun setPowerIntensityType(type: PowerIntensityType) {
        _powerIntensityType.value = type
        if (type == PowerIntensityType.TriggerMode) {
            // 切换到触发模式时，禁用映射功能并保存当前强度
            stopCooldown()

            // 保存当前的 Power 和 Opossum 强度值
            triggeredStoredPowerA = basePowerA
            triggeredStoredPowerB = basePowerB
            triggeredStoredOpossumA = opossumBaseA
            triggeredStoredOpossumB = opossumBaseB
        } else {
            // 切换回映射模式时，重置存储的值
            triggeredStoredPowerA = 0
            triggeredStoredPowerB = 0
            triggeredStoredOpossumA = 0
            triggeredStoredOpossumB = 0


        }
        // 如果开启了要触发一次
        if (_civetSensorEnable.value) {
            applyMappedPower();
        }
    }

    fun getIsTriggerMode(): Boolean {
        return _powerIntensityType.value == PowerIntensityType.TriggerMode
    }

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

        // 初始化 basePowerA/B（在触发模式下，用户调整后会自动切换到 Mapping Mode）
        MainOptions.getChannelPower(0).let {
            basePowerA = it
            _targetPowerA.update { it }
            _currentAppliedPowerA.update { it }
        }
        MainOptions.getChannelPower(1).let {
            basePowerB = it
            _targetPowerB.update { it }
            _currentAppliedPowerB.update { it }
        }
    }

    fun setChannelAPower(power: Int) {
        // 当用户在触发模式下调整 channel power 时，同步更新 basePowerA/B
        basePowerA = power
        _targetPowerA.update { power }
        _currentAppliedPowerA.update { power }
        MainOptions.setChannelPower(0, power)
    }

    fun setChannelBPower(power: Int) {
        // 当用户在触发模式下调整 channel power 时，同步更新 basePowerB/B
        basePowerB = power
        _targetPowerB.update { power }
        _currentAppliedPowerB.update { power }
        MainOptions.setChannelPower(1, power)
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    suspend fun attemptConnection() {
        _connectionStatus.update { CivetSensorConnectionStatus.Scanning }
        scanForCivetSensor()
    }

    @SuppressLint("MissingPermission")
    fun disconnect() {
        stopPressureReporting()
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

            // 如果 autoApplyOnConnect 为 true，连接时自动应用 mapping 设置
            if (_civetSensorEnable.value) {
                applyPowerMapping()
                applyOpossumMapping()
            }

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
                    _displayPressure.update { pressure }
                    addPressureToHistory(pressure)
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

    fun setDenialToZero(enabled: Boolean) {
        _denialToZero.value = enabled
    }

    fun setOpossumViewModel(vm: OpossumViewModel) {
        opossumViewModel = vm
    }

    fun setOpossumMappingEnabled(enabled: Boolean) {
        _opossumMappingEnabled.value = enabled
        if (enabled) {
            // 只有在未连接或 autoApplyOnConnect 为 true 时才立即应用
            if (_connectionStatus.value != CivetSensorConnectionStatus.Connected || _civetSensorEnable.value) {
                opossumViewModel?.let { vm ->
                    opossumBaseA = vm.channelAAmp.value
                    opossumBaseB = vm.channelBAmp.value
                    vm.onManualChannelChange = { a, b ->
                        updateOpossumBaseFromManual(a, b)
                    }
                }
                resetDenialsCount()
                applyMappedPower()
            }
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

    fun setAutoApplyOnConnect(enabled: Boolean) {
        _civetSensorEnable.value = enabled
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
            val mapped =
                intensityRange.start + t * (intensityRange.endInclusive - intensityRange.start)
            mapped.coerceIn(0f, 200f).toInt()
        } else 0
        opossumBaseA = (a - opossumDelta).coerceIn(0, 200)
        opossumBaseB = (b - opossumDelta).coerceIn(0, 200)
    }

    fun setPowerMappingEnabled(enabled: Boolean) {
        _powerMappingEnabled.value = enabled
        if (enabled) {
            // 只有在未连接或 autoApplyOnConnect 为 true 时才立即应用
            if (_connectionStatus.value != CivetSensorConnectionStatus.Connected || _civetSensorEnable.value) {
                basePowerA = MainOptions.getChannelPower(0)
                basePowerB =
                    if (isPowerSyncEnabled()) basePowerA else MainOptions.getChannelPower(1)
                resetDenialsCount()
                applyMappedPower()
            }
        } else {
            _targetPowerA.update { basePowerA }
            _targetPowerB.update { basePowerB }
            _currentAppliedPowerA.update { basePowerA }
            _currentAppliedPowerB.update { basePowerB }
            MainOptions.setChannelPower(0, basePowerA)
            MainOptions.setChannelPower(1, basePowerB)
            stopCooldown()
        }
    }

    fun setTestPressure(pressure: Float) {
        val coerced = pressure.coerceIn(0f, 99.9f)
        _testPressure.value = coerced
        _rawPressure.update { coerced }
        _displayPressure.update { coerced }
        addPressureToHistory(coerced)
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

    private fun incrementDenialCount() {
        _denialsCount.update { it + 1 }
        HLog.d("CivetSensor", "Denial count: ${_denialsCount.value}")
    }

    private fun startCooldown(
        pressure: Float = 0f,
        pRange: ClosedFloatingPointRange<Float>? = null
    ) {
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
            HLog.d("CivetSensor", "Cooldown finished, checking pressure...")

            // 冷却结束后，检查压力是否已经恢复到最小值以下
            val currentPressure = _rawPressure.value
            val pRange = _pressureRange.value
            val minPressure = pRange.start
            val maxPressure = pRange.endInclusive

            if (currentPressure <= maxPressure || currentPressure <= minPressure) {
                HLog.d(
                    "CivetSensor",
                    "Trigger mode: RECOVERED at pressure=$currentPressure, count=${_denialsCount.value}"
                )

                // 恢复到触发前存储的 Power 强度
                MainOptions.setChannelPower(0, triggeredStoredPowerA)
                MainOptions.setChannelPower(1, triggeredStoredPowerB)
                _currentAppliedPowerA.update { triggeredStoredPowerA }
                _currentAppliedPowerB.update { triggeredStoredPowerB }

                // 恢复到触发前存储的 Opossum 强度
                opossumViewModel?.let { vm ->
                    vm.setChannels(triggeredStoredOpossumA, triggeredStoredOpossumB)
                }
                _opossumMapped.update { 0 }
            }
        }
    }

    private fun stopCooldown() {
        cooldownJob?.cancel()
        cooldownJob = null
        isCoolingDown = false
        _cooldownRemaining.value = 0
        denialsTriggered = false
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

    private val _currentAppliedPowerA = MutableStateFlow(0)
    private val _currentAppliedPowerB = MutableStateFlow(0)
    private val _targetPowerA = MutableStateFlow(0)
    private val _targetPowerB = MutableStateFlow(0)
    private var stepAccumulatorA = 0f
    private var stepAccumulatorB = 0f
    private var basePowerA = 0
    private var basePowerB = 0

    private fun isPowerSyncEnabled(): Boolean = Prefs.powerSyncEnabled.value

    fun applyMappedPower() {
        // 如果 autoApplyOnConnect 为 true 再进行处理
        if (!_civetSensorEnable.value) {
            return
        }
        val powerIntensityType = _powerIntensityType.value
        val opossumMapping = _opossumMappingEnabled.value
        val powerMapping = _powerMappingEnabled.value

        val pressure = _rawPressure.value
        val pRange = _pressureRange.value
        val maxPressure = pRange.endInclusive

        // 触发模式：只处理最大值和最小值的触发逻辑
        if (powerIntensityType == PowerIntensityType.TriggerMode) {
            val pressure = _rawPressure.value
            val pRange = _pressureRange.value
            val maxPressure = pRange.endInclusive

            // 压力达到最大值，触发 denial（停止输出）
            if (pressure >= maxPressure && !isCoolingDown) {
                HLog.d(
                    "CivetSensor",
                    "Trigger mode: DENIAL TRIGGERED at pressure=$pressure, max=${maxPressure}"
                )

                // 在调用 startCooldown 之前获取当前的 MainOptions power 值
                val currentPowerA = MainOptions.getChannelPower(0)
                val currentPowerB = MainOptions.getChannelPower(1)

                triggeredStoredPowerA = currentPowerA
                triggeredStoredPowerB = currentPowerB
                triggeredStoredOpossumA = opossumBaseA
                triggeredStoredOpossumB = opossumBaseB
                HLog.d(
                    "CivetSensor",
                    "Trigger mode: Stored values - Power(A=$triggeredStoredPowerA, B=$triggeredStoredPowerB), Opossum(A=$triggeredStoredOpossumA, B=$triggeredStoredOpossumB)"
                )

                // 停止 Power 输出
                MainOptions.setChannelPower(0, 0)
                MainOptions.setChannelPower(1, 0)
                _currentAppliedPowerA.update { 0 }
                _currentAppliedPowerB.update { 0 }

                // 停止 Opossum 输出
                opossumViewModel?.let { vm ->
                    vm.setChannels(0, 0)
                }
                _opossumMapped.update { 0 }

                // 增加 denial 计数并启动冷却
                incrementDenialCount()
                HLog.d(
                    "CivetSensor",
                    "Trigger mode: Cooldown starting - count=${_denialsCount.value}, time=${_cooldownTime.value}s"
                )
                startCooldown(pressure, pRange)
                return
            }

            // 冷却期间，保持停止状态（不做任何操作）
            if (isCoolingDown) {
                HLog.d(
                    "CivetSensor",
                    "Trigger mode: COOLING DOWN ${_cooldownRemaining.value}s, count=${_denialsCount.value}"
                )
                return
            }

            // 冷却完成且压力恢复到最小值以下，恢复输出
            if (pressure <= pRange.start) {
                HLog.d(
                    "CivetSensor",
                    "Trigger mode: RECOVERED at pressure=$pressure, min=${pRange.start}, count=${_denialsCount.value}"
                )

                // 恢复到触发前存储的 Power 强度
                MainOptions.setChannelPower(0, triggeredStoredPowerA)
                MainOptions.setChannelPower(1, triggeredStoredPowerB)
                _currentAppliedPowerA.update { triggeredStoredPowerA }
                _currentAppliedPowerB.update { triggeredStoredPowerB }

                // 恢复到触发前存储的 Opossum 强度
                opossumViewModel?.let { vm ->
                    vm.setChannels(triggeredStoredOpossumA, triggeredStoredOpossumB)
                }
                _opossumMapped.update { 0 }
            }
            return
        }

        // 映射模式：原有的逻辑
        if (!powerMapping && !opossumMapping) {
            stopCooldown()
            return
        }


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
                startCooldown(pressure, pRange)
                HLog.d(
                    "CivetSensor",
                    "Denial triggered: count=${_denialsCount.value}, limit=$denialsLimit, cooldown=${_cooldownTime.value}s"
                )
                return
            }
        } else if (denialsTriggered && isCoolingDown) {
            // Cooling down: do nothing, waiting for cooldown to finish
            return
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
                _opossumMapped.update { opossumBaseA + opossumDelta }
            }
        } else {
            // Not cooling down, continue to normal operation
        }

        // Normal operation
        if (powerMapping) {
            _targetPowerA.update { powerTargetA }
            _targetPowerB.update { powerTargetB }
            _currentAppliedPowerA.update { powerTargetA }
            _currentAppliedPowerB.update { powerTargetB }
            MainOptions.setChannelPower(0, powerTargetA)
            MainOptions.setChannelPower(1, powerTargetB)
        }

        if (opossumMapping) {
            opossumViewModel?.let { vm ->
                vm.setChannels(opossumTargetA, opossumTargetB)
            }
            _opossumMapped.update { opossumDelta }
        }

    }

    /**
     * 连接时应用 Power Mapping 设置
     */
    private fun applyPowerMapping() {
        if (_powerMappingEnabled.value) {
            basePowerA = MainOptions.getChannelPower(0)
            basePowerB = if (isPowerSyncEnabled()) basePowerA else MainOptions.getChannelPower(1)
            resetDenialsCount()
            applyMappedPower()
        }
    }

    /**
     * 连接时应用 Opossum Mapping 设置
     */
    private fun applyOpossumMapping() {
        if (_opossumMappingEnabled.value) {
            opossumViewModel?.let { vm ->
                opossumBaseA = vm.channelAAmp.value
                opossumBaseB = vm.channelBAmp.value
                vm.onManualChannelChange = { a, b ->
                    updateOpossumBaseFromManual(a, b)
                }
            }
            resetDenialsCount()
            applyMappedPower()
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
        stopCooldown()
        stopPressureReporting()
        bluetoothGatt?.disconnect()
        bluetoothGatt?.close()
        bluetoothGatt = null
    }
}
