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
import androidx.annotation.RequiresApi
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
    private var timeLastSeen=Instant.now()
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
    private var otaTxCharacteristic:BluetoothGattCharacteristic?=null
    private var otaRxCharacteristic:BluetoothGattCharacteristic?=null
    private var versionCharacteristic:BluetoothGattCharacteristic?=null
    private var cryptoCharacteristic:BluetoothGattCharacteristic?=null
    private val mutexOta=Mutex()
    private val timer=Timer()
    private val bluetoothGattCallback=object:BluetoothGattCallback() {
        private var characteristicsToRegister:ArrayDeque<BluetoothGattCharacteristic?> =ArrayDeque()
        private val characteristicsToRead:ArrayDeque<BluetoothGattCharacteristic?> =ArrayDeque()

        @RequiresApi(Build.VERSION_CODES.O)
        @SuppressLint("MissingPermission")
        private fun registerCharacteristic(gatt:BluetoothGatt?) {
            if (characteristicsToRegister.isEmpty()) {
                characteristicsToRead.addAll(arrayOf(typeFreqCharacteristic,
                    batteryCharacteristic,
                    muteCharacteristic,
                    serialCharacteristic,
                    latitudeCharacteristic,
                    longitudeCharacteristic,
                    altitudeCharacteristic,
                    velCharacteristic!!,
                    versionCharacteristic))
                if (versionCharacteristic==null) Log.e(TAG,
                    "versionCharacteristic is null!!")
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
                        if (!gatt?.writeDescriptor(descriptor)!!)
                            Log.e(TAG,"registrazione non avvenuta!!! ${ch.uuid}")
                        break
                    }
                    else Log.e(TAG,"Caratteristica da registrare nulla??")
                }
            }
        }

        @RequiresApi(Build.VERSION_CODES.O)
        @SuppressLint("MissingPermission")
        @Deprecated("Deprecated in Java")
        override fun onCharacteristicRead(
            gatt:BluetoothGatt?,
            characteristic:BluetoothGattCharacteristic?,
            status:Int,
        ) {
            super.onCharacteristicRead(gatt,characteristic,status)
            val value=characteristic!!.value
            if (value!=null) {
                try {
                    val v=ByteBuffer.wrap(value).order(ByteOrder.LITTLE_ENDIAN)
                    when (characteristic.uuid) {
                        BURSTKILL_UUID -> {
                            try {
                                cb.onBurstKill(v.get(0),v.getShort(1).toInt())
                            } catch (_:IndexOutOfBoundsException) {
                            }
                        }

                        TYPEFREQ_UUID -> {
                            val type=v.get(0).toInt()
                            val freq=v.getInt(1)/1000F
                            cb.onTypeAndFreq(type,freq)
                        }

                        SERIAL_UUID -> {
                            val serial=characteristic.value.toString(Charsets.UTF_8)
                            if (serial.isNotEmpty()) cb.onSerial(serial)
                        }

                        BAT_UUID -> cb.onBattery(0,v.getInt())

                        MUTE_UUID -> cb.onMute(v.getChar().toInt()==1)

                        LAT_UUID -> {
                            try {
                                cb.onLatitude(v.getDouble())
                            } catch (_:BufferUnderflowException) {
                            }
                        }

                        LON_UUID -> {
                            try {
                                cb.onLongitude(v.getDouble())
                            } catch (_:BufferUnderflowException) {
                            }
                        }

                        ALT_UUID -> {
                            try {
                                cb.onAltitude(v.getFloat().toDouble())
                            } catch (_:BufferUnderflowException) {
                            }
                        }

                        VEL_UUID -> {
                            try {
                                cb.onVelocity(v.getFloat())
                            } catch (_:BufferUnderflowException) {
                            }
                        }

                        VERSION_UUID -> cb.onVersion(characteristic.getStringValue(0))
                    }
                }
                catch (_:BufferUnderflowException) {}
                catch (ex:IndexOutOfBoundsException) {
                    Log.w(TAG,"exception while processing characteristic ${characteristic.uuid}, $ex")
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
            when (characteristic?.uuid) {
                MUTE_UUID -> {
                    val v=ByteBuffer.wrap(characteristic?.value!!).order(ByteOrder.LITTLE_ENDIAN)
                        .getChar().toInt()
                    cb.onMute(v==1)
                }
            }
            super.onCharacteristicWrite(gatt,characteristic,status)
        }

        @RequiresApi(Build.VERSION_CODES.O)
        override fun onDescriptorWrite(
            gatt:BluetoothGatt?,
            descriptor:BluetoothGattDescriptor?,
            status:Int,
        ) {
            super.onDescriptorWrite(gatt,descriptor,status)
            if (connected) {
                if (descriptor!=null) Log.d(TAG,"onDescriptorWrite ${descriptor.uuid} $status")
                registerCharacteristic(gatt)
            }
        }

        @RequiresApi(Build.VERSION_CODES.O)
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(gatt:BluetoothGatt?,status:Int,newState:Int) {
            Log.i(TAG,"OnConnectionStateChange status=$status, newState=$newState")
            if (newState==BluetoothProfile.STATE_CONNECTED) {
                connected=true
                bluetoothGatt.requestMtu(CHUNK_SIZE+3)
            } else if (newState==BluetoothProfile.STATE_DISCONNECTED) {
                connected=false
                gatt?.disconnect()
                gatt?.close()
                cb.onDisconnected()
                timer.cancel()
            }
        }

        @RequiresApi(Build.VERSION_CODES.O)
        @SuppressLint("MissingPermission")
        override fun onMtuChanged(gatt:BluetoothGatt?,mtu:Int,status:Int) {
            super.onMtuChanged(gatt,mtu,status)
            Log.d(TAG,"onMtuChanged:$mtu")
            bluetoothGatt.discoverServices()
        }

        @RequiresApi(Build.VERSION_CODES.O)
        @SuppressLint("MissingPermission")
        override fun onServicesDiscovered(gatt:BluetoothGatt?,status:Int) {
            if (status==BluetoothGatt.GATT_SUCCESS) {
                if (bluetoothGatt.services!=null) for (svc in bluetoothGatt.services!!) {
                    Log.d(TAG,"SVC: ${svc.uuid} (${svc.characteristics.size})")
                    when (svc.uuid) {
                        SERVICE_UUID -> {
                            for (ch in svc.characteristics) {
                                when (ch.uuid) {
                                    BURSTKILL_UUID -> {
                                        burstKillCharacteristic=ch
                                        characteristicsToRegister.add(ch)
                                    }
                                    TYPEFREQ_UUID -> typeFreqCharacteristic=ch
                                    MUTE_UUID -> muteCharacteristic=ch
                                    BAT_UUID -> {
                                        batteryCharacteristic=ch
                                        characteristicsToRegister.add(ch)
                                    }
                                    LAT_UUID -> {
                                        latitudeCharacteristic=ch
                                        characteristicsToRegister.add(ch)
                                    }
                                    LON_UUID -> {
                                        longitudeCharacteristic=ch
                                        characteristicsToRegister.add(ch)
                                    }
                                    ALT_UUID -> {
                                        altitudeCharacteristic=ch
                                        characteristicsToRegister.add(ch)
                                    }
                                    VEL_UUID -> {
                                        velCharacteristic=ch
                                        characteristicsToRegister.add(ch)
                                    }
                                    SERIAL_UUID -> {
                                        serialCharacteristic=ch
                                        characteristicsToRegister.add(ch)
                                    }
                                    RSSI_UUID -> {
                                        rssiCharacteristic=ch
                                        characteristicsToRegister.add(ch)
                                    }
                                    VERSION_UUID -> versionCharacteristic=ch
                                    CRYPTO_UUID -> cryptoCharacteristic=ch
                                }
                            }
                        }

                        OTA_SERVICE_UUID -> {
                            for (ch in svc.characteristics) when (ch.uuid) {
                                OTA_TX_UUID -> otaTxCharacteristic=ch
                                OTA_RX_UUID -> {
                                    otaRxCharacteristic=ch
                                    characteristicsToRegister.add(ch)
                                }
                            }
                        }
                    }
                }
            }
            registerCharacteristic(gatt)
        }

        @Deprecated("Deprecated in Java")
        override fun onCharacteristicChanged(
            gatt:BluetoothGatt,
            characteristic:BluetoothGattCharacteristic,
        ) {
            timeLastSeen=Instant.now()
            val v=ByteBuffer.wrap(characteristic.value).order(ByteOrder.LITTLE_ENDIAN)
            when (characteristic.uuid) {
                OTA_RX_UUID -> mutexOta.unlock()
                LAT_UUID -> cb.onLatitude(v.getDouble())
                LON_UUID -> cb.onLongitude(v.getDouble())
                ALT_UUID -> cb.onAltitude(v.getFloat().toDouble())
                VEL_UUID -> cb.onVelocity((v.getFloat()*3.6).toFloat())
                RSSI_UUID -> cb.onRSSI(-v.getInt()/2F)
                BAT_UUID -> cb.onBattery(0,v.getInt())
                SERIAL_UUID -> cb.onSerial(characteristic.value.toString(Charsets.UTF_8))
                BURSTKILL_UUID -> {
                    val status=v.get()
                    val t=v.getShort(1).toInt()
                    cb.onBurstKill(status,t)
                }
                CRYPTO_UUID -> {
                    cb.onCrypto(v.getShort().toInt(),0,0)
                }
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    @SuppressLint("MissingPermission")
    var bluetoothGatt:BluetoothGatt=device.connectGatt(context,false,bluetoothGattCallback,
        BluetoothDevice.TRANSPORT_AUTO, BluetoothDevice.PHY_LE_2M_MASK, Handler(Looper.getMainLooper()))

    init {
        timer.schedule(object:TimerTask() {
            @RequiresApi(Build.VERSION_CODES.O)
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
    override val sondeTypes:List<String>
        get() {
            return listOf("RS41","M20","M10","DFM09","DFM17")
        }

    @RequiresApi(Build.VERSION_CODES.O)
    @SuppressLint("MissingPermission")
    override fun setTypeAndFrequency(type:Int,frequency:Float) {
        typeFreqCharacteristic!!.value=
            ByteBuffer.allocate(5).order(ByteOrder.LITTLE_ENDIAN).put((type).toByte()).putInt(Math.round(frequency*1000)).array()

        bluetoothGatt.writeCharacteristic(typeFreqCharacteristic!!)
        cb.onTypeAndFreq(type,frequency)
    }

    @RequiresApi(Build.VERSION_CODES.O)
    @SuppressLint("MissingPermission")
    override fun setMute(mute:Boolean) {
        muteCharacteristic!!.value=ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN)
            .putInt(if (mute) 1 else 0).array()
        bluetoothGatt.writeCharacteristic(muteCharacteristic)
    }

    override fun requestSettings()=false

    @RequiresApi(Build.VERSION_CODES.O)
    @SuppressLint("MissingPermission")
    override fun requestVersion() {
        bluetoothGatt.readCharacteristic(versionCharacteristic)
    }

    override fun sendSettings(settings:List<Pair<String,Any>>) {}

    @RequiresApi(Build.VERSION_CODES.O)
    @SuppressLint("MissingPermission")
    override suspend fun startOTA(otaLength:Int) {
        val res=bluetoothGatt.requestConnectionPriority(CONNECTION_PRIORITY_HIGH)
        Log.d(TAG,"requestConnectionPriority: $res")
        val bytes=
            ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).putInt(0x4853).putInt(otaLength).array()
        mutexOta.lock()
        otaTxCharacteristic!!.value=bytes
        otaTxCharacteristic!!.writeType=BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
        bluetoothGatt.writeCharacteristic(otaTxCharacteristic!!)
    }

    @RequiresApi(Build.VERSION_CODES.O)
    @SuppressLint("MissingPermission")
    override suspend fun stopOTA() {
        //mutexOta.lock()
        otaTxCharacteristic!!.value=ByteArray(0)
        otaTxCharacteristic!!.writeType=BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
        bluetoothGatt.writeCharacteristic(otaTxCharacteristic)
    }

    override fun getOtaChunkSize():Int = CHUNK_SIZE

    @RequiresApi(Build.VERSION_CODES.O)
    @SuppressLint("MissingPermission")
    override suspend fun otaChunk(buf:ByteArray) {
        mutexOta.lock()
        //Log.d(TAG,"Invio %02X %02X %02X %02X ...".format(buf[0],buf[1],buf[2],buf[3]))
        otaTxCharacteristic!!.value=buf
        otaTxCharacteristic!!.writeType=BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
        bluetoothGatt.writeCharacteristic(otaTxCharacteristic!!)
    }

    companion object {
        private const val CHUNK_SIZE = 512
        private val SERVICE_UUID=UUID.fromString("79ee1705-f663-4674-8774-55042fc215f5")
        private val OTA_SERVICE_UUID=UUID.fromString("0410c8a6-2c9c-4d6a-9f0e-4bc0ff7e0f7e")
        private val OTA_TX_UUID=UUID.fromString("63fa4cbe-3a81-463f-aa84-049dea77a209")
        private val OTA_RX_UUID=UUID.fromString("4f0227ff-dca1-4484-99f9-155cba7f3d86")
        private val VERSION_UUID=UUID.fromString("2bc3ed96-a00a-4c9a-84af-7e1283835d71")
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
        private val CRYPTO_UUID=UUID.fromString("f5208a75-777d-47fe-ae8c-b3530d3244b7")
    }
}

