package eu.ydiaeresis.trovalasonda

import android.util.Log
import com.harrysoft.androidbluetoothserial.SimpleBluetoothDeviceInterface
import java.time.Instant

class TTGO(cb:ReceiverCallback,name:String, private val deviceInterface:SimpleBluetoothDeviceInterface):Receiver(cb,name),
    SimpleBluetoothDeviceInterface.OnMessageReceivedListener,
    SimpleBluetoothDeviceInterface.OnErrorListener,
    SimpleBluetoothDeviceInterface.OnMessageSentListener {
    private var timeLastMessage:Instant?=null
    val isRdzTrovaLaSonda=name.startsWith("TrovaLaSonda")
    var isCiapaSonde=name.startsWith("CiapaSonde")

    override fun setTypeAndFrequency(type:Int,frequency:Float) {
        sendCommands(listOf<Pair<String,Any>>(Pair("f",frequency),Pair("tipo",type)))
    }

    override fun setMute(mute:Boolean) {
        sendCommand("mute",mute)
    }

    override fun requestSettings() {
        sendCommand("?")
    }

    override fun startOTA(otaLength:Int) {
        sendCommand("ota",otaLength)
    }

    override fun otaChunk(buf:ByteArray) {
        sendBytes(buf)
    }

    fun sendOTA(length:Int) {
        sendCommand("ota",length)
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
            Log.e(FullscreenActivity.TAG,e.toString())
        }
    }

    private fun sendCommand(cmd:String,value:Any) {
        sendCommand("$cmd=$value")
    }

    private fun mySondyGOSondePos(
        type:String,freq:Float,name:String,lat:Double,lon:Double,height:Double,_vel:Float,
        sign:Float,bat:Int,afc:Int,bk:Boolean,bktime:Int,batV:Int,mute:Int,ver:String,
    ) {
        cb.onType(SondeType.valueOf(type))
        cb.onFrequency(freq)
        cb.onMute(mute==1)
        cb.onBattery(batV)
        cb.onRSSI(sign)
        cb.onSerial(name)
        cb.onLatitude(lat)
        cb.onLongitude(lon)
        cb.onAltitude(height)
        cb.onVelocity(_vel)
        cb.onAFC(afc)
        cb.onBkTime(bktime)
        cb.onVersion(ver)
    }

    private fun mySondyGOStatus(
        type:String,freq:Float,sign:Float,bat:Int,batV:Int,mute:Int,ver:String,
    ) {
        cb.onType(SondeType.valueOf(type))
        cb.onFrequency(freq)
        cb.onMute(mute==1)
        cb.onBattery(batV)
        cb.onRSSI(sign)
        cb.onVersion(ver)
    }

    private fun mySondyGOSonde(
        type:String,freq:Float,name:String,sign:Float,bat:Int,afc:Int,batV:Int,mute:Int,ver:String,
    ) {
        cb.onType(SondeType.valueOf(type))
        cb.onFrequency(freq)
        cb.onSerial(name)
        cb.onMute(mute==1)
        cb.onBattery(batV)
        cb.onRSSI(sign)
        cb.onAFC(afc)
        cb.onVersion(ver)
    }

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

    private fun process(msg:String) {
        timeLastMessage=Instant.now()
        val campi=msg.split("/")
        if (campi[campi.size-1]!="o") {
            Log.e(TAG,"manca terminatore messaggio")
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
                Log.e(TAG,
                    "numero campi errato in messaggio tipo 0 (${campi.size} invece di 9)")
                return
            }

            "1" -> if (campi.size==20) {
                mySondyGOSondePos(campi[1],
                    campi[2].toFloat(),
                    campi[3],
                    campi[4].toDouble(),
                    campi[5].toDouble(),
                    campi[6].toDouble(),
                    campi[7].toFloat(),
                    campi[8].toFloat(),
                    campi[9].toInt(),
                    campi[10].toInt(),
                    campi[11]=="1",
                    campi[12].toInt(),
                    campi[13].toInt(),
                    campi[14].toInt(),
                    campi[18])
                //TODO: callback - freqOffsetReceiver?.freqOffset(campi[10].toInt())
            } else {
                Log.e(TAG,
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
                //TODO: callback - freqOffsetReceiver?.freqOffset(campi[6].toInt())
            } else {
                Log.e(TAG,
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
                Log.e(TAG,
                    "numero campi errato in messaggio tipo 3 (${campi.size} invece di 23)")
                return
            }

            else -> Log.e(TAG,"Tipo messaggio sconosciuto")
        }
    }

    override fun onMessageReceived(message:String) {
        Log.i(TAG,"Message: $message")
        try {
            process(message)
        } catch (e:Exception) {
            Log.e(TAG,e.toString())
        }
    }

    override fun onError(error:Throwable) {
        Log.i(TAG,"Error: $error")
    }

    override fun onMessageSent(message:ByteArray) {}

    companion object {
        const val TAG="TrovaLaSonda"
    }
}