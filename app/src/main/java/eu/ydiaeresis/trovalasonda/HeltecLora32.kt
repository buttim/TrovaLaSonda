package eu.ydiaeresis.trovalasonda

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGatt.CONNECTION_PRIORITY_HIGH
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import eu.ydiaeresis.trovalasonda.FullscreenActivity.Companion.TAG
import kotlinx.coroutines.sync.Mutex
import java.nio.BufferUnderflowException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.Charset
import java.time.Instant
import java.util.Timer
import java.util.TimerTask
import java.util.UUID
import kotlin.math.roundToInt
import android.content.ContextWrapper
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch

//todo?
//https://stackoverflow.com/questions/62155016/bluez-and-service-characteristics-cache-issue-with-android

class HeltecLora32(
    cb: ReceiverCallback,
    name: String,
    val context: Context,
    device: BluetoothDevice
) :
    Receiver(cb, name) {
    private var connected = false
    private var timeLastSeen = Instant.now()

    //private var packetCharacteristic:BluetoothGattCharacteristic?=null
    private var typeFreqCharacteristic: BluetoothGattCharacteristic? = null
    private var muteCharacteristic: BluetoothGattCharacteristic? = null

    //private var batteryCharacteristic:BluetoothGattCharacteristic?=null
    //private var rssiCharacteristic:BluetoothGattCharacteristic?=null
    private var otaTxCharacteristic: BluetoothGattCharacteristic? = null

    //private var otaRxCharacteristic:BluetoothGattCharacteristic?=null
    private var versionCharacteristic: BluetoothGattCharacteristic? = null
    private val mutexOta = Mutex()
    private val timer = Timer()
    private var bleSetupManager: BleSetupManager? = null
    private val bleRegistrationSequence = listOf(
        BleTask(SERVICE_UUID, PACKET_UUID, true),
        BleTask(SERVICE_UUID, BAT_UUID, true),
        BleTask(SERVICE_UUID, RSSI_UUID, true),
        BleTask(
            SERVICE_UUID, TYPEFREQ_UUID, false, requiresInitialRead = true,
            { ch -> typeFreqCharacteristic = ch },
            this::processCharacteristicData
        ),
        BleTask(
            SERVICE_UUID, MUTE_UUID, false, requiresInitialRead = true,
            { ch -> muteCharacteristic = ch },
            this::processCharacteristicData
        ),
        BleTask(
            SERVICE_UUID, VERSION_UUID, false, requiresInitialRead = true,
            { ch -> versionCharacteristic = ch },
            this::processCharacteristicData
        ),
        BleTask(
            OTA_SERVICE_UUID, OTA_TX_UUID, true, requiresInitialRead = false,
            { ch -> otaTxCharacteristic = ch }),
        BleTask(OTA_SERVICE_UUID, OTA_RX_UUID, false),
    )

    private fun readPacket(v: ByteBuffer) {
        if (v.remaining() == 0) return
        val frame = v.int
        val lat = v.double
        val lng = v.double
        val alt = v.float.toDouble()
        val hVel = v.float
        val vVel = v.float
        val bkTime = v.short.toUShort()
        val bkStatus = v.get()
        val cpuTemp = v.get()
        val radioTemp = v.get()
        val encrypted = v.get() > 0
        val serial = Charset.forName("UTF-8").decode(v.asReadOnlyBuffer()).toString()
        cb.onSerial(serial)
        cb.onPacket(lat, lng, alt, vVel, hVel)
//            cb.onLatitude(lat)
//            cb.onLongitude(lng)
//            cb.onAltitude(alt)
//            cb.onVelocity(hVel)
//            cb.onVerticalSpeed(vVel)
        val noBK: UShort = 0xFFFFu
        if (bkTime != noBK) cb.onBurstKill(bkStatus, bkTime.toInt())
    }

    private fun processCharacteristicData(uuid: UUID, value: ByteArray) {
        Log.d(TAG, "processCharacteristic $uuid $value")
        try {
            val v = ByteBuffer.wrap(value).order(ByteOrder.LITTLE_ENDIAN)
            when (uuid) {
                PACKET_UUID -> readPacket(v)
                RSSI_UUID -> cb.onRSSI(v.getInt() / 2F)
                TYPEFREQ_UUID -> {
                    val type = v.get(0).toInt()
                    val freq = v.getInt(1) / 1000F
                    cb.onTypeAndFreq(type, freq)
                }

                BAT_UUID -> cb.onBattery(0, v.getInt())
                MUTE_UUID -> cb.onMute(v.getChar().toInt() == 1)
                VERSION_UUID -> cb.onVersion(String(value, Charsets.UTF_8))
            }
        } catch (_: BufferUnderflowException) {
        } catch (ex: IndexOutOfBoundsException) {
            Log.w(
                TAG,
                "exception while processing characteristic ${uuid}, $ex"
            )
        }
    }

    @Suppress("unused")
    private val bluetoothGattCallback = object : BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        @Deprecated("Deprecated in Java")
        override fun onCharacteristicRead(
            gatt: BluetoothGatt?,
            characteristic: BluetoothGattCharacteristic?,
            status: Int,
        ) {
            super.onCharacteristicRead(gatt, characteristic, status)
            if (characteristic != null) {
                bleSetupManager?.handleCharacteristicReadResult(characteristic, status)
                val value = characteristic.value
                if (value != null)
                    processCharacteristicData(characteristic.uuid, value)
            }

        }

        @SuppressLint("MissingPermission")
        override fun onCharacteristicWrite(
            gatt: BluetoothGatt?,
            characteristic: BluetoothGattCharacteristic?,
            status: Int,
        ) {
            when (characteristic?.uuid) {
                MUTE_UUID -> {
                    val v = ByteBuffer.wrap(characteristic?.value!!).order(ByteOrder.LITTLE_ENDIAN)
                        .getChar().toInt()
                    cb.onMute(v == 1)
                }
            }
            super.onCharacteristicWrite(gatt, characteristic, status)
        }

        override fun onDescriptorWrite(
            gatt: BluetoothGatt?,
            descriptor: BluetoothGattDescriptor?,
            status: Int,
        ) {
            super.onDescriptorWrite(gatt, descriptor, status)
            bleSetupManager?.handleDescriptorWriteResult(status)
        }

        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
            Log.d(TAG, "OnConnectionStateChange status=$status, newState=$newState")
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                connected = true
                bluetoothGatt.discoverServices()
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                connected = false
                gatt?.disconnect()
                gatt?.close()
                cb.onDisconnected()
                timer.cancel()
            }
        }


        @SuppressLint("MissingPermission")
        override fun onMtuChanged(gatt: BluetoothGatt?, mtu: Int, status: Int) {
            super.onMtuChanged(gatt, mtu, status)
            Log.d(TAG, "onMtuChanged:$mtu")
            bleSetupManager?.handleMtuChangedResult(mtu, status)
        }

        private fun getCoroutineScope(): CoroutineScope {
            var currentContext = context
            while (currentContext is ContextWrapper) {
                if (currentContext is LifecycleOwner)
                    return currentContext.lifecycleScope
                currentContext = currentContext.baseContext
            }
            if (currentContext is LifecycleOwner)
                return currentContext.lifecycleScope

            // Fallback: If the context is a background Service or Application Context
            // which doesn't have a UI lifecycle, return a safe MainScope
            return MainScope()
        }

        @SuppressLint("MissingPermission")
        override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS)
                getCoroutineScope().launch {
                    bleSetupManager?.executeSetupSequence()
                }
        }

        @Deprecated("Deprecated in Java")
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
        ) {
            timeLastSeen = Instant.now()
            val v = ByteBuffer.wrap(characteristic.value).order(ByteOrder.LITTLE_ENDIAN)
            when (characteristic.uuid) {
                OTA_RX_UUID -> mutexOta.unlock()
                RSSI_UUID -> cb.onRSSI(v.getInt() / 2F)
                BAT_UUID -> cb.onBattery(0, v.getInt())
                PACKET_UUID -> readPacket(v)
            }
        }
    }

    @SuppressLint("MissingPermission")
    var bluetoothGatt: BluetoothGatt = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        // Android 8.0+ (API 26): Use your optimized high-speed configuration
        device.connectGatt(
            context,
            false,
            bluetoothGattCallback,
            BluetoothDevice.TRANSPORT_AUTO,
            BluetoothDevice.PHY_LE_2M_MASK,
            Handler(Looper.getMainLooper())
        )
    } else {
        // Android 7.0 & 7.1 (API 24/25): Fallback to the standard BLE configuration
        // (It will default to the 1M PHY and use the main thread internally)
        device.connectGatt(
            context,
            false,
            bluetoothGattCallback,
            BluetoothDevice.TRANSPORT_AUTO
        )
    }


    init {
        bleSetupManager = BleSetupManager(bluetoothGatt, bleRegistrationSequence)
        timer.schedule(object : TimerTask() {
            @SuppressLint("MissingPermission")
            override fun run() {
                if (Instant.now().epochSecond - timeLastSeen.epochSecond > 5000) {
                    Log.i(TAG, "Disconnessione dopo 5 secondi")
                    cb.onDisconnected()
                    timer.cancel()
                    close()
                }
            }
        }, 5000, 5000)
    }


    @SuppressLint("MissingPermission")
    override fun close() {
        bluetoothGatt.disconnect()
        bluetoothGatt.close()
    }

    override fun getFirmwareName(): String = "TrovaLaSondaFw"
    override val sondeTypes: List<String>
        get() {
            return listOf("RS41", "M20", "M10", "DFM09", "DFM17", "RD41")
        }

    override val hasVerticalSpeed: Boolean
        get() = false


    @SuppressLint("MissingPermission")
    override fun setTypeAndFrequency(type: Int, frequency: Float) {
        if (typeFreqCharacteristic != null) {
            typeFreqCharacteristic?.value =
                ByteBuffer.allocate(5).order(ByteOrder.LITTLE_ENDIAN).put((type).toByte())
                    .putInt((frequency * 1000).roundToInt()).array()

            bluetoothGatt.writeCharacteristic(typeFreqCharacteristic)
            cb.onTypeAndFreq(type, frequency)
        }
    }


    @SuppressLint("MissingPermission")
    override fun setMute(mute: Boolean) {
        muteCharacteristic!!.value =
            ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(if (mute) 1 else 0).array()
        bluetoothGatt.writeCharacteristic(muteCharacteristic)
    }

    override fun requestSettings() = false

    @SuppressLint("MissingPermission")
    override fun requestVersion() {
        bluetoothGatt.readCharacteristic(versionCharacteristic)
    }

    override fun sendSettings(settings: List<Pair<String, Any>>) {}


    @SuppressLint("MissingPermission")
    override suspend fun startOTA(otaLength: Int) {
        val res = bluetoothGatt.requestConnectionPriority(CONNECTION_PRIORITY_HIGH)
        Log.d(TAG, "requestConnectionPriority: $res")
        val bytes =
            ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).putInt(0x4853).putInt(otaLength)
                .array()
        mutexOta.lock()
        otaTxCharacteristic!!.value = bytes
        otaTxCharacteristic!!.writeType = BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
        bluetoothGatt.writeCharacteristic(otaTxCharacteristic!!)
    }

    @SuppressLint("MissingPermission")
    override suspend fun stopOTA() {
        //mutexOta.lock()
        otaTxCharacteristic!!.value = ByteArray(0)
        otaTxCharacteristic!!.writeType = BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
        bluetoothGatt.writeCharacteristic(otaTxCharacteristic)
    }

    override fun getOtaChunkSize(): Int = CHUNK_SIZE

    @SuppressLint("MissingPermission")
    override suspend fun otaChunk(buf: ByteArray) {
        mutexOta.lock()
        //Log.d(TAG,"Invio %02X %02X %02X %02X ...".format(buf[0],buf[1],buf[2],buf[3]))
        otaTxCharacteristic!!.value = buf
        otaTxCharacteristic!!.writeType = BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
        bluetoothGatt.writeCharacteristic(otaTxCharacteristic!!)
    }

    companion object {
        private const val CHUNK_SIZE = 512
        private val SERVICE_UUID = UUID.fromString("177fba78-7843-40a6-801b-a4cd8d7f5c11")
        private val PACKET_UUID = UUID.fromString("4dee4a71-2e7e-4018-9656-b60f1e562047")
        private val BAT_UUID = UUID.fromString("4578ee77-f50f-4584-b59c-46264c56d949")
        private val RSSI_UUID = UUID.fromString("e482dfeb-774f-4f8b-8eea-87a752326fbd")
        private val TYPEFREQ_UUID = UUID.fromString("66bf4d7f-2b21-468d-8dce-b241c7447cc6")
        private val MUTE_UUID = UUID.fromString("a8b47819-eb1a-4b5c-8873-6258ddfe8055")
        private val VERSION_UUID = UUID.fromString("2bc3ed96-a00a-4c9a-84af-7e1283835d71")
        private val OTA_SERVICE_UUID = UUID.fromString("0410c8a6-2c9c-4d6a-9f0e-4bc0ff7e0f7e")
        private val OTA_TX_UUID = UUID.fromString("63fa4cbe-3a81-463f-aa84-049dea77a209")
        private val OTA_RX_UUID = UUID.fromString("4f0227ff-dca1-4484-99f9-155cba7f3d86")
    }
}

