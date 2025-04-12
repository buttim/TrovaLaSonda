package eu.ydiaeresis.trovalasonda

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
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
import com.harrysoft.androidbluetoothserial.BluetoothManager
import com.harrysoft.androidbluetoothserial.SimpleBluetoothDeviceInterface
import eu.ydiaeresis.trovalasonda.FullscreenActivity.Companion.TAG
import kotlinx.coroutines.sync.Mutex
import java.time.Instant
import java.util.Timer
import java.util.TimerTask
import java.util.UUID

abstract class TTGO(cb:ReceiverCallback,name:String):Receiver(cb,name),
    SimpleBluetoothDeviceInterface.OnMessageReceivedListener,
    SimpleBluetoothDeviceInterface.OnErrorListener,
    SimpleBluetoothDeviceInterface.OnMessageSentListener {
    protected val isRdzTrovaLaSonda=name.startsWith(ReceiverBuilder.TROVALASONDAPREFIX)
    protected val timer=Timer()
    protected var timeLastMessage=Instant.now()
    protected var otaRunning=false
    protected val mutexOta=Mutex()
    override fun getFirmwareName():String=if (isRdzTrovaLaSonda) "rdzTrovaLaSonda" else "MySondyGO"

    init {
        timer.schedule(object:TimerTask() {
            @RequiresApi(Build.VERSION_CODES.O)
            @SuppressLint("MissingPermission")
            override fun run() {
                val dt=Instant.now().epochSecond-timeLastMessage.epochSecond
                if (dt>5) {
                    Log.i(TAG,"Disconnessione dopo 5 secondi")
                    close()
                    cb.onDisconnected()
                    timer.cancel()
                }
            }
        },5000,5000)
    }

    override val sondeTypes:List<String>
        get() {
            return when {
                name.startsWith(ReceiverBuilder.CIAPASONDEPREFIX) -> listOf("RS41",
                    "M20",
                    "M10",
                    "PIL",
                    "DFM",
                    "C50",
                    "IMET4")

                else -> listOf("RS41","M20","M10","PIL","DFM")
            }
        }

    override val hasVerticalSpeed:Boolean
        get()=false

    override fun setTypeAndFrequency(type:Int,frequency:Float) {
        sendCommands(listOf<Pair<String,Any>>(Pair(FREQ,frequency),Pair(TIPO,type+1)))
    }

    override fun setMute(mute:Boolean) {
        sendCommand(MUTE,if (mute) 1 else 0)
    }

    override fun requestSettings():Boolean {
        sendCommand("?")
        return true
    }

    override fun requestVersion() {}

    override fun sendSettings(settings:List<Pair<String,Any>>) {
        sendCommands(settings)
    }

    private fun sendCommands(commands:List<Pair<String,Any>>) {
        if (commands.isEmpty()) return
        val sb=StringBuilder()
        commands.forEach {cmd ->
            if (sb.isNotEmpty()) sb.append("/")
            sb.append("${cmd.first}=${cmd.second}")
        }
        sendCommand(sb.toString())
    }

    protected fun sendCommand(cmd:String,value:Any) {
        sendCommand("$cmd=$value")
    }

    fun sendCommand(cmd:String) {
        try {
            sendBytes("o{$cmd}o\r\n".toByteArray())
        } catch (e:Exception) {
            Log.e(FullscreenActivity.TAG,"Exception in sendCommand: $e")
        }
    }

    abstract fun sendBytes(bytes:ByteArray)

    override suspend fun startOTA(otaLength:Int) {
        sendCommand(OTA,otaLength)
        otaRunning=true
    }

    override suspend fun stopOTA() {
        otaRunning=false
    }

    override fun getOtaChunkSize():Int=4096

    override suspend fun otaChunk(buf:ByteArray) {
        mutexOta.lock()
        sendBytes(buf)
    }

    @Suppress("UNUSED_PARAMETER")
    private fun mySondyGOSondePos(
        type:String,freq:Float,name:String,lat:Double,lon:Double,height:Double,_vel:Float,
        sign:Float,bat:Int,afc:Int,bk:Boolean,bktime:Int,batV:Int,mute:Int,ver:String,hVel:Float=Float.NaN
    ) {
        cb.onTypeAndFreq(sondeTypes.indexOf(type),freq)
        cb.onMute(mute==1)
        cb.onBattery(batV,bat)
        cb.onRSSI(sign)

        if (height==0.0 || height>40000.0 || lat==0.0 || lon==0.0) return
        cb.onSerial(name)
        cb.onLatitude(lat)
        cb.onLongitude(lon)
        cb.onAltitude(height)
        var vel=_vel
        if (!isRdzTrovaLaSonda && ver=="2.30" && (type=="M10" || type=="M20")) vel*=3.6F
        cb.onVelocity(vel)
        cb.onAFC(afc)
        cb.onBurstKill(0,bktime)
        cb.onVersion(ver)
        if (hVel!=Float.NaN) cb.onVerticalSpeed(hVel)
    }

    private fun mySondyGOStatus(
        type:String,freq:Float,sign:Float,bat:Int,batV:Int,mute:Int,ver:String,
    ) {
        cb.onTypeAndFreq(sondeTypes.indexOf(type),freq)
        cb.onMute(mute==1)
        cb.onBattery(batV,bat)
        cb.onRSSI(sign)
        cb.onVersion(ver)
    }

    private fun mySondyGOSonde(
        type:String,freq:Float,name:String,sign:Float,bat:Int,afc:Int,batV:Int,mute:Int,ver:String,
    ) {
        cb.onTypeAndFreq(sondeTypes.indexOf(type),freq)
        cb.onSerial(name)
        cb.onMute(mute==1)
        cb.onBattery(batV,bat)
        cb.onRSSI(sign)
        cb.onAFC(afc)
        cb.onVersion(ver)
    }

    @Suppress("UNUSED_PARAMETER")
    private fun mySondyGOSettings(
        type:String,freq:Float,sda:Int,scl:Int,rst:Int,led:Int,RS41bw:Int,M20bw:Int,M10bw:Int,
        PILOTbw:Int,DFMbw:Int,call:String,offset:Int,bat:Int,batMin:Int,batMax:Int,batType:Int,
        lcd:Int,nam:Int,buz:Int,ver:String,
    ) {
        cb.onSettings(sda,
            scl,
            rst,
            led,
            RS41bw,
            M20bw,
            M10bw,
            PILOTbw,
            DFMbw,
            call,
            offset,
            bat,
            batMin,
            batMax,
            batType,
            lcd,
            nam,
            buz,
            ver)
    }

    protected fun process(msg:String,useHVel:Boolean=false) {
        timeLastMessage=Instant.now()
        val campi=msg.split("/")
        if (campi[campi.size-1]!="o") {
            Log.e(FullscreenActivity.TAG,"manca terminatore messaggio")
            return
        }
        when (campi[0]) {
            "0" -> if (campi.size==9) mySondyGOStatus(campi[1],
                campi[2].toFloat(),
                campi[3].toFloat(),
                campi[4].toInt(),
                campi[5].toInt(),
                campi[6].toInt(),
                campi[7])
            else {
                Log.e(FullscreenActivity.TAG,
                    "numero campi errato in messaggio tipo 0 (${campi.size} invece di 9)")
                return
            }

            "1" -> if (!useHVel && campi.size==20 || useHVel && campi.size==21) {
                val offset=if (useHVel) 1 else 0
                mySondyGOSondePos(campi[1],
                    campi[2].toFloat(),
                    campi[3],
                    campi[4].toDouble(),
                    campi[5].toDouble(),
                    campi[6].toDouble(),
                    campi[7+offset].toFloat(),
                    campi[8+offset].toFloat(),
                    campi[9+offset].toInt(),
                    campi[10+offset].toInt(),
                    campi[11+offset]=="1",
                    campi[12+offset].toInt(),
                    campi[13+offset].toInt(),
                    campi[14+offset].toInt(),
                    campi[18+offset],
                    if (useHVel) campi[7].toFloat() else Float.NaN)
            } else {
                Log.e(FullscreenActivity.TAG,
                    "numero campi errato in messaggio tipo 1 (${campi.size} invece di 20)")
                return
            }

            "2" -> if (campi.size==11) {
                mySondyGOSonde(campi[1],
                    campi[2].toFloat(),
                    campi[3],
                    campi[4].toFloat(),
                    campi[5].toInt(),
                    campi[6].toInt(),
                    campi[7].toInt(),
                    campi[8].toInt(),
                    campi[9])
            } else {
                Log.e(FullscreenActivity.TAG,
                    "numero campi errato in messaggio tipo 2 (${campi.size} invece di 11)")
                return
            }

            "3" -> if (campi.size==23) mySondyGOSettings(campi[1],
                campi[2].toFloat(),
                campi[3].toInt(),
                campi[4].toInt(),
                campi[5].toInt(),
                campi[6].toInt(),
                campi[7].toInt(),
                campi[8].toInt(),
                campi[9].toInt(),
                campi[10].toInt(),
                campi[11].toInt(),
                campi[12],
                campi[13].toInt(),
                campi[14].toInt(),
                campi[15].toInt(),
                campi[16].toInt(),
                campi[17].toInt(),
                campi[18].toInt(),
                campi[19].toInt(),
                campi[20].toInt(),
                campi[21])
            else {
                Log.e(FullscreenActivity.TAG,
                    "numero campi errato in messaggio tipo 3 (${campi.size} invece di 23)")
                return
            }

            else -> Log.e(FullscreenActivity.TAG,"Tipo messaggio sconosciuto")
        }
    }

    override fun onMessageReceived(message:String) {
        Log.i(FullscreenActivity.TAG,"Message: $message")
        try {
            process(message)
        } catch (e:Exception) {
            Log.e(FullscreenActivity.TAG,"Eccezione in process: $e")
        }
    }

    override fun onMessageSent(message:ByteArray) {
        if (otaRunning && mutexOta.isLocked) mutexOta.unlock()
    }

    override fun onError(error:Throwable) {
        Log.i(FullscreenActivity.TAG,"Serial communication error: $error")
        cb.onDisconnected()
    }

    companion object {
        const val LCD="lcd"
        const val OLED_SDA="oled_sda"
        const val OLED_SCL="oled_scl"
        const val OLED_RST="oled_rst"
        const val BUZ_PIN="buz_pin"
        const val LED_POUT="led_pout"
        const val BATTERY="battery"
        const val VBATMIN="vBatMin"
        const val VBATMAX="vBatMax"
        const val VBATTYPE="vBatType"
        const val RS41_RXBW="rs41.rxbw"
        const val M20_RXBW="m20.rxbw"
        const val M10_RXBW="m10.rxbw"
        const val PILOT_RXBW="pilot.rxbw"
        const val DFM_RXBW="dmf.rxbw"
        const val MYCALL="myCall"
        const val APRSNAME="aprsName"
        const val FREQOFS="freqofs"
        private const val TIPO="tipo"
        private const val MUTE="mute"
        private const val FREQ="f"
        private const val OTA="ota"
    }
}

class TTGO2(
    cb:ReceiverCallback,
    name:String,
    private val deviceInterface:SimpleBluetoothDeviceInterface,
):TTGO(cb,name) {
    override val hasVerticalSpeed:Boolean
        get()=false

    override fun close() {
        BluetoothManager.instance?.closeDevice(deviceInterface.device.mac)
    }
    override fun sendBytes(bytes:ByteArray) {
        deviceInterface.sendMessage(bytes)
    }

    override fun onError(error:Throwable) {
        BluetoothManager.instance?.closeDevice(deviceInterface.device.mac)
    }
}

class TTGO3(cb:ReceiverCallback,name:String,val context:Context,private val device:BluetoothDevice):
    TTGO(cb,name) {
    private var connected=false
    private var rxCharacteristic:BluetoothGattCharacteristic?=null
    private var txCharacteristic:BluetoothGattCharacteristic?=null

    private val bluetoothGattCallback=object:BluetoothGattCallback() {
        private fun registerCharacteristic(gatt:BluetoothGatt?) {
            Log.d(TAG,"onRegisterCharacteristic")
        }

        override fun onCharacteristicRead(
            gatt:BluetoothGatt?,
            characteristic:BluetoothGattCharacteristic?,
            status:Int,
        ) {
            Log.d(TAG,"onCharacteristicRead ${characteristic?.uuid} $status")
        }

        override fun onCharacteristicWrite(
            gatt:BluetoothGatt?,
            characteristic:BluetoothGattCharacteristic?,
            status:Int,
        ) {
            Log.d(TAG,"onCharacteristicWrite ${characteristic?.uuid} $status")
        }

        override fun onDescriptorWrite(
            gatt:BluetoothGatt?,
            descriptor:BluetoothGattDescriptor?,
            status:Int,
        ) {
            Log.d(TAG,"onDescriptorWrite ${descriptor?.uuid} $status")
        }

        override fun onConnectionStateChange(gatt:BluetoothGatt?,status:Int,newState:Int) {
            Log.i(TAG,"OnConnectionStateChange status=$status, newState=$newState")
            if (newState==BluetoothProfile.STATE_CONNECTED) {
                connected=true
                bluetoothGatt.discoverServices()
            } else if (newState==BluetoothProfile.STATE_DISCONNECTED) {
                connected=false
                gatt?.disconnect()
                gatt?.close()
                cb.onDisconnected()
                timer.cancel()
            }
        }

        override fun onServicesDiscovered(gatt:BluetoothGatt?,status:Int) {
            if (status==BluetoothGatt.GATT_SUCCESS) {
                if (bluetoothGatt.services!=null) for (svc in bluetoothGatt.services!!) {
                    Log.d(TAG,"SVC: ${svc.uuid} (${svc.characteristics.size})")
                    when (svc.uuid) {
                        UART_SERVICE_UUID -> {
                            Log.d(TAG,"Servizio trovato!")
                            for (ch in svc.characteristics) {
                                when (ch.uuid) {
                                    UART_TX_CHAR_UUID -> {
                                        Log.d(TAG,"caratteristica TX trovata!")
                                        txCharacteristic=ch
                                    }

                                    UART_RX_CHAR_UUID -> {
                                        Log.d(TAG,"caratteristica RX trovata!")
                                        rxCharacteristic=ch
                                    }
                                }
                            }
                        }
                    }
                }
                gatt!!.requestMtu(517)
            }
        }

        override fun onMtuChanged(gatt:BluetoothGatt?,mtu:Int,status:Int) {
            super.onMtuChanged(gatt,mtu,status)
            if (rxCharacteristic!=null) {
                if (!gatt?.setCharacteristicNotification(rxCharacteristic,true)!!)
                    Log.e(TAG,"Error calling setCharacteristicNotification")
                val descriptor=rxCharacteristic!!.getDescriptor(CLIENT_CONFIG_DESCRIPTOR)
                Log.i(TAG,
                    "registrazione notifiche per caratteristica ${rxCharacteristic!!.uuid}, descriptor $descriptor")
                if (descriptor!=null) {
                    if ((BluetoothGattCharacteristic.PROPERTY_INDICATE and rxCharacteristic!!.properties)!=0) descriptor.value=
                        BluetoothGattDescriptor.ENABLE_INDICATION_VALUE
                    else if ((BluetoothGattCharacteristic.PROPERTY_NOTIFY and rxCharacteristic!!.properties)!=0) descriptor.value=
                        BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                    else Log.e(TAG,"Cannot register notification")
                    if (!gatt.writeDescriptor(descriptor)) Log.e(TAG,
                        "registrazione non avvenuta!!! ${rxCharacteristic!!.uuid}")
                }
                else Log.e(TAG,"Impossibile registrare notifiche")
            }
        }

        override fun onCharacteristicChanged(
            gatt:BluetoothGatt,
            characteristic:BluetoothGattCharacteristic,
        ) {
            val v=characteristic.value.toString(Charsets.UTF_8)
            Log.d(TAG,"onCharacteristicChanged ${characteristic.uuid} ${v}")
            if (v!="\r\n")
                process(v,true)
        }
    }

    override val hasVerticalSpeed:Boolean
        get()=true

    override fun close() {
        bluetoothGatt.disconnect()
        bluetoothGatt.close()
    }

    var bluetoothGatt:BluetoothGatt=device.connectGatt(context,
        false,
        bluetoothGattCallback,
        BluetoothDevice.TRANSPORT_AUTO,
        BluetoothDevice.PHY_LE_2M_MASK,
        Handler(Looper.getMainLooper()))

    override fun sendBytes(bytes:ByteArray) {
        txCharacteristic!!.value=bytes
        bluetoothGatt.writeCharacteristic(txCharacteristic)
    }

    override fun onError(error:Throwable) {
        bluetoothGatt.disconnect()
        bluetoothGatt.close()
    }

    companion object {
        private val UART_SERVICE_UUID=UUID.fromString("53797269-614d-6972-6b6f-44616c6d6f6e")
        private val UART_TX_CHAR_UUID=UUID.fromString("53797268-614d-6972-6b6f-44616c6d6f7e")
        private val UART_RX_CHAR_UUID=UUID.fromString("53797267-614d-6972-6b6f-44616c6d6f8e")
    }
}