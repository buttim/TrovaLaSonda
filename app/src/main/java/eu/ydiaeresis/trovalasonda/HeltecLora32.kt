package eu.ydiaeresis.trovalasonda

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.util.Log
import eu.ydiaeresis.trovalasonda.FullscreenActivity.Companion.TAG
import java.nio.BufferUnderflowException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID


class HeltecLora32(cb:ReceiverCallback,name:String,val context:Context,device:BluetoothDevice):Receiver(cb,name) {
    private var connected=false
    private var freqCharacteristic:BluetoothGattCharacteristic?=null
    private var typeCharacteristic:BluetoothGattCharacteristic?=null
    private var muteCharacteristic:BluetoothGattCharacteristic?=null
    private var batteryCharacteristic:BluetoothGattCharacteristic?=null
    private var latitudeCharacteristic:BluetoothGattCharacteristic?=null
    private var longitudeCharacteristic:BluetoothGattCharacteristic?=null
    private var altitudeCharacteristic:BluetoothGattCharacteristic?=null
    private var serialCharacteristic:BluetoothGattCharacteristic?=null
    private var otaCharacteristic:BluetoothGattCharacteristic?=null
    private val bluetoothGattCallback=object:BluetoothGattCallback() {
        var characteristicsToRegister:ArrayDeque<BluetoothGattCharacteristic> =ArrayDeque()
        val characteristicsToRead:ArrayDeque<BluetoothGattCharacteristic> =ArrayDeque()
        val characteristicsToWrite:ArrayDeque<BluetoothGattCharacteristic> =ArrayDeque()

        @SuppressLint("MissingPermission")
        fun writeMultipleCharacteristics(chs:ArrayDeque<BluetoothGattCharacteristic>) {
            if (!chs.isEmpty()) {
                val ch=chs.removeFirst()
                characteristicsToWrite.addAll(chs)
                bluetoothGatt.writeCharacteristic(ch)
            }
        }

        @SuppressLint("MissingPermission") //TODO:
        private fun registerCharacteristic(gatt:BluetoothGatt?) {
            if (characteristicsToRegister.isEmpty()) {
                characteristicsToRead.addAll(arrayOf(typeCharacteristic!!,
                    batteryCharacteristic!!,
                    muteCharacteristic!!,
                    serialCharacteristic!!,
                    latitudeCharacteristic!!,
                    longitudeCharacteristic!!,
                    altitudeCharacteristic!!))
                bluetoothGatt.readCharacteristic(freqCharacteristic)
                return
            }
            val ch=characteristicsToRegister.removeFirst()
            gatt?.setCharacteristicNotification(ch,true)
            val descriptor=ch.getDescriptor(CLIENT_CONFIG_DESCRIPTOR)
            Log.i(TAG,
                "registrazione notifiche per caratteristica ${ch.uuid}, descriptor $descriptor")
            if (descriptor!=null) {
                descriptor.value=BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                if (!(gatt?.writeDescriptor(descriptor))!!) Log.e(TAG,
                    "registrazione non avvenuta!!!")
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
            Log.i(TAG,"onCharacteristicRead "+characteristic.uuid.toString()+"/"+value.toString())
            when (characteristic.uuid) {
                FREQ_UUID -> {
                    val v=ByteBuffer.wrap(value).order(ByteOrder.LITTLE_ENDIAN).getInt()
                    cb.onFrequency(v/1000F)
                    bluetoothGatt.readCharacteristic(typeCharacteristic)
                }

                TYPE_UUID -> {
                    val v=ByteBuffer.wrap(value).order(ByteOrder.LITTLE_ENDIAN).getInt()
                    cb.onType(SondeType.entries.first {x -> x.value==v})
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
                    cb.onBattery(v)
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
                    } catch (ex:BufferUnderflowException) {
                        Log.i(TAG,"Latitude not available")
                    }
                }

                LON_UUID -> {
                    try {
                        val v=ByteBuffer.wrap(characteristic.value).order(ByteOrder.LITTLE_ENDIAN)
                            .getFloat().toDouble()
                        cb.onLongitude(v)
                    } catch (ex:BufferUnderflowException) {
                        Log.i(TAG,"Longitude not available")
                    }
                }

                ALT_UUID -> {
                    try {
                        val v=ByteBuffer.wrap(characteristic.value).order(ByteOrder.LITTLE_ENDIAN)
                            .getFloat().toDouble()
                        cb.onAltitude(v)
                    } catch (ex:BufferUnderflowException) {
                        Log.i(TAG,"Altitude not available")
                    }
                }
            }
            if (!characteristicsToRead.isEmpty()) {
                val ch=characteristicsToRead.removeFirst()
                bluetoothGatt.readCharacteristic(ch)
            }
        }

        @SuppressLint("MissingPermission")
        override fun onCharacteristicWrite(
            gatt:BluetoothGatt?,
            characteristic:BluetoothGattCharacteristic?,
            status:Int,
        ) {
            super.onCharacteristicWrite(gatt,characteristic,status)
            if (!characteristicsToWrite.isEmpty()) {
                val ch=characteristicsToWrite.removeFirst()
                bluetoothGatt.writeCharacteristic(ch)
            }
        }

        override fun onDescriptorWrite(
            gatt:BluetoothGatt?,
            descriptor:BluetoothGattDescriptor?,
            status:Int,
        ) {
            super.onDescriptorWrite(gatt,descriptor,status)
            if (connected) {
                if (descriptor!=null) Log.i(TAG,"onDescriptorWrite ${descriptor.uuid}")

                registerCharacteristic(gatt)
            }
        }

        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(gatt:BluetoothGatt?,status:Int,newState:Int) {
            if (newState==BluetoothProfile.STATE_CONNECTED) {
                connected=true
                bluetoothGatt.discoverServices()
                bluetoothGatt.requestMtu(512)////////////////
            } else if (newState==BluetoothProfile.STATE_DISCONNECTED) {
                connected=false
                gatt?.disconnect()
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
                    Log.i(TAG,"SVC: "+svc.uuid.toString()) //TODO
                    if (svc.uuid.equals(SERVICE_UUID)) {
                        characteristicsToRegister.addAll(svc.characteristics)
                        registerCharacteristic(gatt)
                        for (ch in svc.characteristics) {
                            //Log.i(MainActivity.TAG,"\tCHR: "+ch.uuid.toString())
                            when (ch.uuid) {
                                FREQ_UUID -> freqCharacteristic=ch
                                TYPE_UUID -> typeCharacteristic=ch
                                MUTE_UUID -> muteCharacteristic=ch
                                BAT_UUID -> batteryCharacteristic=ch
                                LAT_UUID -> latitudeCharacteristic=ch
                                LON_UUID -> longitudeCharacteristic=ch
                                ALT_UUID -> altitudeCharacteristic=ch
                                SERIAL_UUID -> serialCharacteristic=ch
                            }
                        }
                    }
                    else if (svc.uuid.equals(OTA_SERVICE_UUID)) {
                        for (ch in svc.characteristics)
                            if (ch.uuid.equals(OTA_CHR))
                                otaCharacteristic=ch
                    }
                }
            } else {
                Log.w(TAG,"onServicesDiscovered received: $status")
            }
        }

        @Deprecated("Deprecated in Java")
        override fun onCharacteristicChanged(
            gatt:BluetoothGatt,
            characteristic:BluetoothGattCharacteristic,
        ) {
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

                RSSI_UUID -> {
                    val v=ByteBuffer.wrap(characteristic.value).order(ByteOrder.LITTLE_ENDIAN)
                        .getInt()
                    cb.onRSSI(-v/2F)
                }

                BAT_UUID -> {
                    val v=ByteBuffer.wrap(characteristic.value).order(ByteOrder.LITTLE_ENDIAN)
                        .getInt()
                    cb.onBattery(v)
                }

                SERIAL_UUID -> {
                    val v=characteristic.value.toString(Charsets.UTF_8)
                    cb.onSerial(v)
                }

                /*FREQ_UUID -> {
                    //TODO: eliminare?
                    val v=ByteBuffer.wrap(characteristic.value).order(ByteOrder.LITTLE_ENDIAN)
                        .getInt()
                    updateFreq(v/1000.0)
                }

                TYPE_UUID -> {
                    //TODO: eliminare?
                    val v=ByteBuffer.wrap(characteristic.value).order(ByteOrder.LITTLE_ENDIAN)
                        .getInt()
                    updateType(sondeTypes!![v])
                }

                MUTE_UUID -> {
                    val v=ByteBuffer.wrap(characteristic.value).order(ByteOrder.LITTLE_ENDIAN)
                        .getChar()
                    //TODO: eliminare?
                }*/
            }
        }
    }

    @SuppressLint("MissingPermission")
    val bluetoothGatt:BluetoothGatt=device.connectGatt(context,false,bluetoothGattCallback)
    override fun setTypeAndFrequency(type:Int,frequency:Float) {
        freqCharacteristic!!.value=
            ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt((frequency*1000).toInt()).array()
        typeCharacteristic!!.value=
            ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(type-1).array()

        val a:ArrayDeque<BluetoothGattCharacteristic> = ArrayDeque()
        a.addAll(arrayOf(freqCharacteristic!!,typeCharacteristic!!))
        bluetoothGattCallback.writeMultipleCharacteristics(a)
    }

    @SuppressLint("MissingPermission")
    override fun setMute(mute:Boolean) {
        muteCharacteristic!!.value=ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN)
            .putInt(if (mute) 1 else 0).array()
        bluetoothGatt.writeCharacteristic(muteCharacteristic)
    }

    override fun requestSettings() {}

    @SuppressLint("MissingPermission")
    override fun startOTA(otaLength:Int) {
        //TODO: testare
        val bytes=
            ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).putInt(0x4853).putInt(otaLength).array()
        otaCharacteristic!!.value=bytes
        bluetoothGatt.writeCharacteristic(otaCharacteristic)
    }

    @SuppressLint("MissingPermission")
    override fun otaChunk(buf:ByteArray) {
        //TODO: testare
        otaCharacteristic!!.value=buf
        bluetoothGatt.writeCharacteristic(otaCharacteristic)
    }

    companion object {
        private val SERVICE_UUID=UUID.fromString("79ee1705-f663-4674-8774-55042fc215f5")
        private val OTA_SERVICE_UUID=UUID.fromString("0410c8a6-2c9c-4d6a-9f0e-4bc0ff7e0f7e")
        private val OTA_CHR=UUID.fromString("63fa4cbe-3a81-463f-aa84-049dea77a209")
        private val CLIENT_CONFIG_DESCRIPTOR=UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
        private val LAT_UUID=UUID.fromString("fc62efe0-eb5d-4cb0-93d3-01d4fb083e18")
        private val LON_UUID=UUID.fromString("c8666b42-954a-420f-b235-6baaba740840")
        private val ALT_UUID=UUID.fromString("1bfdccfe-80f4-46d0-844f-ad8410001989")
        private val BAT_UUID=UUID.fromString("4578ee77-f50f-4584-b59c-46264c56d949")
        private val RSSI_UUID=UUID.fromString("e482dfeb-774f-4f8b-8eea-87a752326fbd")
        private val TYPE_UUID=UUID.fromString("66bf4d7f-2b21-468d-8dce-b241c7447cc6")
        private val FREQ_UUID=UUID.fromString("b4da41fe-3194-42e7-8bbb-2e11d3ff6f6d")
        private val SERIAL_UUID=UUID.fromString("539fd1f8-f427-4ddc-99d2-80f51616baab")
        private val MUTE_UUID=UUID.fromString("a8b47819-eb1a-4b5c-8873-6258ddfe8055")
    }
}

