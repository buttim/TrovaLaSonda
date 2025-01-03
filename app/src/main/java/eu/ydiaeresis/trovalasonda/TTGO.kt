package eu.ydiaeresis.trovalasonda

import android.util.Log
import com.harrysoft.androidbluetoothserial.BluetoothManager
import com.harrysoft.androidbluetoothserial.SimpleBluetoothDeviceInterface
import kotlinx.coroutines.sync.Mutex
import java.time.Instant

class TTGO(cb:ReceiverCallback,name:String, private val deviceInterface:SimpleBluetoothDeviceInterface):Receiver(cb,name),
    SimpleBluetoothDeviceInterface.OnMessageReceivedListener,
    SimpleBluetoothDeviceInterface.OnErrorListener,
    SimpleBluetoothDeviceInterface.OnMessageSentListener {
    private var timeLastMessage:Instant?=null
    private val isRdzTrovaLaSonda=name.startsWith(ReceiverBuilder.TROVALASONDAPREFIX)
    private var otaRunning=false
    private val mutexOta=Mutex()

    override fun getFirmwareName():String =if (isRdzTrovaLaSonda) "rdzTrovaLaSonda" else "MySondyGO"
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

    override suspend fun startOTA(otaLength:Int) {
        sendCommand(OTA,otaLength)
        otaRunning=true
    }

    override suspend fun stopOTA() {
        otaRunning=false
    }

    override fun getOtaChunkSize():Int = 4096

    override suspend fun otaChunk(buf:ByteArray) {
        mutexOta.lock()
        sendBytes(buf)
    }

    private fun sendBytes(bytes:ByteArray) {
        deviceInterface.sendMessage(bytes)
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

    private fun sendCommand(cmd:String) {
        try {
            deviceInterface.sendMessage("o{$cmd}o\r\n".toByteArray())
        } catch (e:Exception) {
            Log.e(FullscreenActivity.TAG,"Eccezione in sendCommand: $e")
        }
    }

    private fun sendCommand(cmd:String,value:Any) {
        sendCommand("$cmd=$value")
    }

    private fun mySondyGOSondePos(
        type:String,freq:Float,name:String,lat:Double,lon:Double,height:Double,_vel:Float,
        sign:Float,bat:Int,afc:Int,bk:Boolean,bktime:Int,batV:Int,mute:Int,ver:String,
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

    private fun mySondyGOSettings(
        type:String,freq:Float,sda:Int,scl:Int,rst:Int,led:Int,RS41bw:Int,M20bw:Int,M10bw:Int,
        PILOTbw:Int,DFMbw:Int,call:String,offset:Int,bat:Int,batMin:Int,batMax:Int,batType:Int,
        lcd:Int,nam:Int,buz:Int,ver:String,
    ) {
        cb.onSettings(sda,scl,rst,led,RS41bw,M20bw,M10bw,PILOTbw,DFMbw,call,offset,bat,batMin,
            batMax,batType,lcd,nam,buz,ver)
    }

    private fun process(msg:String) {
        timeLastMessage=Instant.now()
        val campi=msg.split("/")
        if (campi[campi.size-1]!="o") {
            Log.e(FullscreenActivity.TAG,"manca terminatore messaggio")
            return
        }
        when (campi[0]) {
            "0" -> if (campi.size==9)
                mySondyGOStatus(campi[1],campi[2].toFloat(),campi[3].toFloat(),campi[4].toInt(),
                campi[5].toInt(),campi[6].toInt(),campi[7])
            else {
                Log.e(FullscreenActivity.TAG,
                    "numero campi errato in messaggio tipo 0 (${campi.size} invece di 9)")
                return
            }

            "1" -> if (campi.size==20) {
                mySondyGOSondePos(campi[1],campi[2].toFloat(),campi[3],campi[4].toDouble(),
                    campi[5].toDouble(),campi[6].toDouble(),campi[7].toFloat(),campi[8].toFloat(),
                    campi[9].toInt(),campi[10].toInt(),campi[11]=="1",campi[12].toInt(),
                    campi[13].toInt(),campi[14].toInt(),campi[18])
            } else {
                Log.e(FullscreenActivity.TAG,
                    "numero campi errato in messaggio tipo 1 (${campi.size} invece di 20)")
                return
            }

            "2" -> if (campi.size==11) {
                mySondyGOSonde(campi[1],campi[2].toFloat(),campi[3],campi[4].toFloat(),
                    campi[5].toInt(),campi[6].toInt(),campi[7].toInt(),campi[8].toInt(),campi[9])
            } else {
                Log.e(FullscreenActivity.TAG,
                    "numero campi errato in messaggio tipo 2 (${campi.size} invece di 11)")
                return
            }

            "3" -> if (campi.size==23)
                mySondyGOSettings(campi[1],campi[2].toFloat(),campi[3].toInt(),campi[4].toInt(),
                campi[5].toInt(),campi[6].toInt(),campi[7].toInt(),campi[8].toInt(),
                campi[9].toInt(),campi[10].toInt(),campi[11].toInt(),campi[12],campi[13].toInt(),
                campi[14].toInt(),campi[15].toInt(),campi[16].toInt(),campi[17].toInt(),
                campi[18].toInt(),campi[19].toInt(),campi[20].toInt(),campi[21])
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

    override fun onError(error:Throwable) {
        Log.i(FullscreenActivity.TAG,"Serial communication error: $error")
        BluetoothManager.instance?.closeDevice(deviceInterface.device.mac)
        cb.onDisconnected()
    }

    override fun onMessageSent(message:ByteArray) {
        if (otaRunning && mutexOta.isLocked) mutexOta.unlock()
    }

    companion object{
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