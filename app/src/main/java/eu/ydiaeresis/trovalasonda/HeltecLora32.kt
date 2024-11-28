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
import android.util.Log
import eu.ydiaeresis.trovalasonda.FullscreenActivity.Companion.TAG
import kotlinx.coroutines.sync.Mutex
import java.nio.BufferUnderflowException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.time.Instant
import java.util.Timer
import java.util.TimerTask
import java.util.UUID

class HeltecLora32(cb:ReceiverCallback,name:String,val context:Context,device:BluetoothDevice):Receiver(cb,name) {
    private var connected=false
    private var timeLastSeen:Instant=Instant.now()
    private var burstKillCharacteristic:BluetoothGattCharacteristic?=null
    private var typeFreqCharacteristic:BluetoothGattCharacteristic?=null
    private var muteCharacteristic:BluetoothGattCharacteristic?=null
    private var batteryCharacteristic:BluetoothGattCharacteristic?=null
    private var latitudeCharacteristic:BluetoothGattCharacteristic?=null
    private var longitudeCharacteristic:BluetoothGattCharacteristic?=null
    private var altitudeCharacteristic:BluetoothGattCharacteristic?=null
    private var velCharacteristic:BluetoothGattCharacteristic?=null
    private var serialCharacteristic:BluetoothGattCharacteristic?=null
    private var rssiCharacteristic:BluetoothGattCharacteristic?=null
    private var otaCharacteristic:BluetoothGattCharacteristic?=null
    private var versionCharacteristic:BluetoothGattCharacteristic?=null
    private val mutexOta=Mutex()
    private val timer=Timer()
    private val bluetoothGattCallback=object:BluetoothGattCallback() {
        private var characteristicsToRegister:ArrayDeque<BluetoothGattCharacteristic?> =ArrayDeque()
        private val characteristicsToRead:ArrayDeque<BluetoothGattCharacteristic?> =ArrayDeque()
        private val characteristicsToWrite:ArrayDeque<BluetoothGattCharacteristic?> =ArrayDeque()

        @SuppressLint("MissingPermission") //TODO:
        private fun registerCharacteristic(gatt:BluetoothGatt?) {
            if (characteristicsToRegister.isEmpty()) {
                characteristicsToRead.addAll(arrayOf(typeFreqCharacteristic,batteryCharacteristic,
                    muteCharacteristic,serialCharacteristic,latitudeCharacteristic,
                    longitudeCharacteristic,altitudeCharacteristic,velCharacteristic!!,versionCharacteristic))
                if (versionCharacteristic==null) {
                    Log.w(TAG,"versionCharacteristic is null!!")
                }
                bluetoothGatt.readCharacteristic(burstKillCharacteristic)
                return
            }
            while (!characteristicsToRegister.isEmpty()) {
                val ch=characteristicsToRegister.removeFirst()
                if (ch!=null) {
                    gatt?.setCharacteristicNotification(ch,true)
                    val descriptor=ch.getDescriptor(CLIENT_CONFIG_DESCRIPTOR)
//                    Log.i(TAG,"registrazione notifiche per caratteristica ${ch.uuid}, descriptor $descriptor")
                    if (descriptor!=null) {
                        descriptor.value=BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                        if (!(gatt?.writeDescriptor(descriptor))!!) Log.e(TAG,
                            "registrazione non avvenuta!!!")
                        break
                    }
                    else Log.w(TAG,"Caratteristica da registrare nulla??")
                }
            }
        }

        @SuppressLint("MissingPermission")
        @Deprecated("Deprecated in Java")
        override fun onCharacteristicRead(
            gatt:BluetoothGatt?,
            characteristic:BluetoothGattCharacteristic?,
            status:Int,
        ) {
            super.onCharacteristicRead(gatt,characteristic,status)
            val value=characteristic!!.value
//            Log.i(TAG,"onCharacteristicRead "+characteristic.uuid.toString()+"/"+value.toString())
            when (characteristic.uuid) {
                BURSTKILL_UUID -> {
                    val v=ByteBuffer.wrap(value).order(ByteOrder.LITTLE_ENDIAN)
                    try {
                        cb.onBurstKill(v.get(0),v.getInt(1))
                    }
                    catch (_:IndexOutOfBoundsException) {}
                }

                TYPEFREQ_UUID -> {
                    val v=ByteBuffer.wrap(value).order(ByteOrder.LITTLE_ENDIAN)
                    val type=v.get(0).toInt()
                    val freq=v.getInt(1)/1000F
                    cb.onTypeAndFreq(type,freq)
                }

                SERIAL_UUID -> {
                    val v=characteristic.value.toString(Charsets.UTF_8)
                    if (v.isNotEmpty())  {
                        cb.onSerial(v)
                    }
                }

                BAT_UUID -> {
                    val v=ByteBuffer.wrap(characteristic.value).order(ByteOrder.LITTLE_ENDIAN)
                        .getInt()
                    cb.onBattery(0,v)
                }

                MUTE_UUID -> {
                    val v=ByteBuffer.wrap(characteristic.value).order(ByteOrder.LITTLE_ENDIAN)
                        .getChar().toInt()
                    cb.onMute(v==1)
                }

                LAT_UUID -> {
                    try {
                        val v=ByteBuffer.wrap(characteristic.value).order(ByteOrder.LITTLE_ENDIAN)
                            .getFloat().toDouble()
                        cb.onLatitude(v)
                    } catch (_:BufferUnderflowException) {}
                }

                LON_UUID -> {
                    try {
                        val v=ByteBuffer.wrap(characteristic.value).order(ByteOrder.LITTLE_ENDIAN)
                            .getFloat().toDouble()
                        cb.onLongitude(v)
                    } catch (_:BufferUnderflowException) {}
                }

                ALT_UUID -> {
                    try {
                        val v=ByteBuffer.wrap(characteristic.value).order(ByteOrder.LITTLE_ENDIAN)
                            .getFloat().toDouble()
                        cb.onAltitude(v)
                    } catch (_:BufferUnderflowException) {}
                }

                VEL_UUID -> {
                    try {
                        val v=ByteBuffer.wrap(characteristic.value).order(ByteOrder.LITTLE_ENDIAN)
                            .getFloat()
                        cb.onVelocity(v)
                    } catch (_:BufferUnderflowException) {}
                }

                VERSION_UUID -> {
                    cb.onVersion(characteristic.getStringValue(0))
                }
            }
            while (!characteristicsToRead.isEmpty()) {
                val ch=characteristicsToRead.removeFirst()
                if (ch!=null) {
                    bluetoothGatt.readCharacteristic(ch)
                    break
                }
                else Log.w(TAG,"Caratteristica da leggere nulla??")
            }
        }

        @SuppressLint("MissingPermission")
        override fun onCharacteristicWrite(
            gatt:BluetoothGatt?,
            characteristic:BluetoothGattCharacteristic?,
            status:Int,
        ) {
            super.onCharacteristicWrite(gatt,characteristic,status)
            while (!characteristicsToWrite.isEmpty()) {
                val ch=characteristicsToWrite.removeFirst()
                if (ch!=null) {
                    bluetoothGatt.writeCharacteristic(ch)
                    break;
                }
                else Log.w(TAG,"Caratteristica da scrivere vuota??")
            }
            when (characteristic?.uuid) {
                OTA_UUID -> mutexOta.unlock()
                MUTE_UUID -> {
                    val v=ByteBuffer.wrap(characteristic?.value!!).order(ByteOrder.LITTLE_ENDIAN)
                        .getChar().toInt()
                    cb.onMute(v==1)
                }
            }
        }

        override fun onDescriptorWrite(
            gatt:BluetoothGatt?,
            descriptor:BluetoothGattDescriptor?,
            status:Int,
        ) {
            super.onDescriptorWrite(gatt,descriptor,status)
            if (connected) {
//                if (descriptor!=null) Log.i(TAG,"onDescriptorWrite ${descriptor.uuid}")
                registerCharacteristic(gatt)
            }
        }

        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(gatt:BluetoothGatt?,status:Int,newState:Int) {
            Log.i(TAG,"OnConnectionStateChange status=$status, newState=$newState")
            if (newState==BluetoothProfile.STATE_CONNECTED) {
                connected=true
//                if (device.bondState==BluetoothDevice.BOND_NONE)
//                    device.createBond()
                bluetoothGatt.discoverServices()
            } else if (newState==BluetoothProfile.STATE_DISCONNECTED) {
                connected=false
                gatt?.disconnect()
                gatt?.close()
                cb.onDisconnected()
                timer.cancel()
            }
        }

        override fun onMtuChanged(gatt:BluetoothGatt?,mtu:Int,status:Int) {
            super.onMtuChanged(gatt,mtu,status)
            Log.i(TAG,"MTU:$mtu")
        }

        @SuppressLint("MissingPermission")
        override fun onServicesDiscovered(gatt:BluetoothGatt?,status:Int) {
            if (status==BluetoothGatt.GATT_SUCCESS) {
                if (bluetoothGatt.services!=null) for (svc in bluetoothGatt.services!!) {
                    Log.i(TAG,"SVC: ${svc.uuid} (${svc.characteristics.size})")
                    when (svc.uuid) {
                        SERVICE_UUID -> {
                            characteristicsToRegister.addAll(svc.characteristics)
                            registerCharacteristic(gatt)
                            for (ch in svc.characteristics) {
//                                Log.i(TAG,"\tCHR: "+ch.uuid.toString())
                                when (ch.uuid) {
                                    BURSTKILL_UUID -> burstKillCharacteristic=ch
                                    TYPEFREQ_UUID -> typeFreqCharacteristic=ch
                                    MUTE_UUID -> muteCharacteristic=ch
                                    BAT_UUID -> batteryCharacteristic=ch
                                    LAT_UUID -> latitudeCharacteristic=ch
                                    LON_UUID -> longitudeCharacteristic=ch
                                    ALT_UUID -> altitudeCharacteristic=ch
                                    VEL_UUID -> velCharacteristic=ch
                                    SERIAL_UUID -> serialCharacteristic=ch
                                    RSSI_UUID -> rssiCharacteristic=ch
                                    VERSION_UUID -> versionCharacteristic=ch
                                }
                            }
                        }

                        OTA_SERVICE_UUID -> {
                            for (ch in svc.characteristics) when (ch.uuid) {
                                OTA_UUID -> otaCharacteristic=ch
                            }
                        }
                    }
                }
                //bluetoothGatt.requestMtu(4096)
            }
        }

        @Deprecated("Deprecated in Java")
        override fun onCharacteristicChanged(
            gatt:BluetoothGatt,
            characteristic:BluetoothGattCharacteristic,
        ) {
            timeLastSeen=Instant.now()
            when (characteristic.uuid) {
                LAT_UUID -> {
                    val v=ByteBuffer.wrap(characteristic.value).order(ByteOrder.LITTLE_ENDIAN)
                        .getFloat().toDouble()
                    cb.onLatitude(v)
                }

                LON_UUID -> {
                    val v=ByteBuffer.wrap(characteristic.value).order(ByteOrder.LITTLE_ENDIAN)
                        .getFloat().toDouble()
                    cb.onLongitude(v)
                }

                ALT_UUID -> {
                    val v=ByteBuffer.wrap(characteristic.value).order(ByteOrder.LITTLE_ENDIAN)
                        .getFloat().toDouble()
                    cb.onAltitude(v)
                }

                VEL_UUID -> {
                    val v=ByteBuffer.wrap(characteristic.value).order(ByteOrder.LITTLE_ENDIAN)
                        .getFloat()
                    cb.onVelocity(v)
                }

                RSSI_UUID -> {
                    val v=ByteBuffer.wrap(characteristic.value).order(ByteOrder.LITTLE_ENDIAN)
                        .getInt()
                    cb.onRSSI(-v/2F)
                }

                BAT_UUID -> {
                    val v=ByteBuffer.wrap(characteristic.value).order(ByteOrder.LITTLE_ENDIAN)
                        .getInt()
                    cb.onBattery(0,v)
                }

                SERIAL_UUID -> {
                    val v=characteristic.value.toString(Charsets.UTF_8)
                    cb.onSerial(v)
                }
            }
        }
    }
    @SuppressLint("MissingPermission")
    var bluetoothGatt:BluetoothGatt=device.connectGatt(context,false,bluetoothGattCallback)

    init {
        timer.schedule(object:TimerTask() {
            @SuppressLint("MissingPermission")
            override fun run() {
                if (Instant.now().epochSecond-timeLastSeen.epochSecond>5000) {
                    Log.i(TAG,"Disconnessione dopo 5 secondi")
                    bluetoothGatt.disconnect()
                    bluetoothGatt.close()
                    cb.onDisconnected()
                    timer.cancel()
                }
            }
        },5000,5000)
    }

    override fun getFirmwareName():String ="TrovaLaSondaFw"

    @SuppressLint("MissingPermission")
    override fun setTypeAndFrequency(type:Int,frequency:Float) {
        typeFreqCharacteristic!!.value=
            ByteBuffer.allocate(5).order(ByteOrder.LITTLE_ENDIAN).put((type).toByte()).putInt(Math.round(frequency*1000)).array()

        bluetoothGatt.writeCharacteristic(typeFreqCharacteristic!!)
        cb.onTypeAndFreq(type,frequency)
    }

    @SuppressLint("MissingPermission")
    override fun setMute(mute:Boolean) {
        muteCharacteristic!!.value=ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN)
            .putInt(if (mute) 1 else 0).array()
        bluetoothGatt.writeCharacteristic(muteCharacteristic)
    }

    override fun requestSettings()=false

    @SuppressLint("MissingPermission")
    override fun requestVersion() {
        bluetoothGatt.readCharacteristic(versionCharacteristic)
    }

    override fun sendSettings(settings:List<Pair<String,Any>>) {}

    @SuppressLint("MissingPermission")
    override suspend fun startOTA(otaLength:Int) {
        val res=bluetoothGatt.requestConnectionPriority(CONNECTION_PRIORITY_HIGH)
        Log.i(TAG,"requestConnectionPriority: $res")
        val bytes=
            ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).putInt(0x4853).putInt(otaLength).array()
        mutexOta.lock()
        otaCharacteristic!!.value=bytes
        bluetoothGatt.writeCharacteristic(otaCharacteristic)
    }

    @SuppressLint("MissingPermission")
    override suspend fun stopOTA() {
        mutexOta.lock()
        otaCharacteristic!!.value=ByteArray(0)
        bluetoothGatt.writeCharacteristic(otaCharacteristic)
    }
    override fun getOtaChunkSize():Int = 509

    @SuppressLint("MissingPermission")
    override suspend fun otaChunk(buf:ByteArray) {
        mutexOta.lock()
        Log.i(TAG,"Invio %02X %02X %02X %02X ...".format(buf[0],buf[1],buf[2],buf[3]))
        otaCharacteristic!!.value=buf
        bluetoothGatt.writeCharacteristic(otaCharacteristic)
    }

    companion object {
        private val SERVICE_UUID=UUID.fromString("79ee1705-f663-4674-8774-55042fc215f5")
        private val OTA_SERVICE_UUID=UUID.fromString("0410c8a6-2c9c-4d6a-9f0e-4bc0ff7e0f7e")
        private val OTA_UUID=UUID.fromString("63fa4cbe-3a81-463f-aa84-049dea77a209")
        private val VERSION_UUID=UUID.fromString("2bc3ed96-a00a-4c9a-84af-7e1283835d71")
        private val CLIENT_CONFIG_DESCRIPTOR=UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
        private val LAT_UUID=UUID.fromString("fc62efe0-eb5d-4cb0-93d3-01d4fb083e18")
        private val LON_UUID=UUID.fromString("c8666b42-954a-420f-b235-6baaba740840")
        private val ALT_UUID=UUID.fromString("1bfdccfe-80f4-46d0-844f-ad8410001989")
        private val VEL_UUID=UUID.fromString("9cb28ac2-fb89-4714-954b-e9292dedce60")
        private val BAT_UUID=UUID.fromString("4578ee77-f50f-4584-b59c-46264c56d949")
        private val RSSI_UUID=UUID.fromString("e482dfeb-774f-4f8b-8eea-87a752326fbd")
        private val TYPEFREQ_UUID=UUID.fromString("66bf4d7f-2b21-468d-8dce-b241c7447cc6")
        private val BURSTKILL_UUID=UUID.fromString("b4da41fe-3194-42e7-8bbb-2e11d3ff6f6d")
        private val SERIAL_UUID=UUID.fromString("539fd1f8-f427-4ddc-99d2-80f51616baab")
        private val MUTE_UUID=UUID.fromString("a8b47819-eb1a-4b5c-8873-6258ddfe8055")
    }
}

