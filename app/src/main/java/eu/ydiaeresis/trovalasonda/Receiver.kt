package eu.ydiaeresis.trovalasonda

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.core.app.ActivityCompat
import com.harrysoft.androidbluetoothserial.BluetoothManager
import com.harrysoft.androidbluetoothserial.BluetoothSerialDevice
import com.harrysoft.androidbluetoothserial.SimpleBluetoothDeviceInterface
import eu.ydiaeresis.trovalasonda.FullscreenActivity.Companion.TAG
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers

enum class SondeType(val value:Int) {
    RS41(0), M20(1), M10(2), PIL(3), DFM(4), C50(5), IMET4(6);
    companion object {
        fun fromInt(value: Int):SondeType = SondeType.entries.first { it.value == value }
    }
}

class BluetoothNotEnabledException(message:String):Exception(message)
class ReceiverException(message:String):Exception(message)

interface ReceiverCallback {
    fun onDisconnected()
    fun onBattery(mv:Int,percentage:Int)
    fun onMute(mute:Boolean)
    fun onTypeAndFreq(type:Int,freq:Float)
    fun onRSSI(rssi:Float)
    fun onSerial(serial:String)
    fun onLatitude(lat:Double)
    fun onLongitude(lon:Double)
    fun onAltitude(alt:Double)
    fun onVelocity(vel:Float)
    fun onAFC(afc:Int)
    fun onBurstKill(status:Byte,time:Int)
    fun onVersion(version:String)
    fun onSettings(
        sda:Int,scl:Int,rst:Int,led:Int,RS41bw:Int,M20bw:Int,M10bw:Int,PILOTbw:Int,DFMbw:Int,
        call:String,offset:Int,bat:Int,batMin:Int,batMax:Int,batType:Int,lcd:Int,nam:Int,
        buz:Int,ver:String,
    )
}

abstract class Receiver(val cb:ReceiverCallback,val name:String) {
    abstract fun getFirmwareName():String
    abstract fun setTypeAndFrequency(type:Int,frequency:Float)
    abstract fun setMute(mute:Boolean)
    abstract fun requestSettings():Boolean
    abstract fun requestVersion()
    abstract fun sendSettings(settings:List<Pair<String,Any>>)
    abstract suspend fun startOTA(otaLength:Int)
    abstract suspend fun stopOTA()
    abstract fun getOtaChunkSize():Int
    abstract suspend fun otaChunk(buf:ByteArray)
}

interface ReceiverBuilderCallback {
    fun onReceiverConnected(receiver:Receiver,builder:ReceiverBuilder)
    fun onTimeout()
}

abstract class ReceiverBuilder(
    val builderCallback:ReceiverBuilderCallback,
    val callback:ReceiverCallback,
    val context:Context,
    val activity:ComponentActivity,
) :Disposable {
    abstract fun connect()

    companion object {
        internal const val SCAN_PERIOD:Long=15000
        internal const val MYSONDYGOPREFIX="MySondyGO-"
        internal const val TROVALASONDAPREFIX="TrovaLaSonda"
        internal const val CIAPASONDEPREFIX="CiapaSonde"
    }
}

class BLEReceiverBuilder(
    rbcb:ReceiverBuilderCallback,
    rcb:ReceiverCallback,
    context:Context,
    activity:ComponentActivity,
):ReceiverBuilder(rbcb,rcb,context,activity) {
    private val bluetoothAdapter=
        (context.getSystemService(Context.BLUETOOTH_SERVICE) as android.bluetooth.BluetoothManager).adapter
    private val bluetoothLeScanner=bluetoothAdapter!!.bluetoothLeScanner
    private var scanning=false
    private val leScanCallback:ScanCallback=object:ScanCallback() {
        @SuppressLint("MissingPermission")
        override fun onScanResult(callbackType:Int,result:ScanResult) {
            super.onScanResult(callbackType,result)

            if (!scanning) return
            if (result.device.name!=null) Log.i(TAG,result.device.name)
            if (result.device.name!=null && result.device.name.startsWith(TROVALASONDAPREFIX)) {
                Log.i(TAG,"TROVATO------------------------")
                stopScanLE()
                doConnectLE(result.device.address)
            }
        }

        override fun onScanFailed(errorCode:Int) {
            super.onScanFailed(errorCode)
            Log.i(TAG,"onScanFailed: $errorCode")
        }
    }

    private fun doConnectLE(address:String):Boolean {
        bluetoothAdapter?.let {adapter ->
            try {
                val device=adapter.getRemoteDevice(address)
                // connect to the GATT server on the device
                if (ActivityCompat.checkSelfPermission(activity,
                        Manifest.permission.BLUETOOTH)!=PackageManager.PERMISSION_GRANTED
                ) {
                    Log.i(TAG,"autorizzazione scansione BLE negata")
                    throw ReceiverException("autorizzazione scansione BLE negata")
                }
                val receiver=HeltecLora32(callback,device.name,context,device)
                builderCallback.onReceiverConnected(receiver,this)
                return true
            } catch (exception:IllegalArgumentException) {
                Log.w(TAG,"Device not found with provided address. Unable to connect.")
            }
        } ?: run {
            Log.w(TAG,"BluetoothAdapter not initialized")
        }
        return false
    }

    @SuppressLint("MissingPermission")
    private fun stopScanLE() {
        if (!scanning) return
        scanning=false
        bluetoothLeScanner!!.stopScan(leScanCallback)
    }

    override fun connect() {
        Log.i(TAG,"Inizio scan")

        if (ActivityCompat.checkSelfPermission(activity,
                Manifest.permission.BLUETOOTH)!=PackageManager.PERMISSION_GRANTED
        ) {
            Log.i(TAG,"autorizzazione scansione BLE negata")
            throw ReceiverException("autorizzazione scansione BLE negata")
        }
        if (!bluetoothAdapter.isEnabled) throw BluetoothNotEnabledException("Bluetooth not enabled")

        if (!scanning) { // Stops scanning after a pre-defined scan period.
            Handler(Looper.getMainLooper()).postDelayed({
                if (scanning) {
                    stopScanLE()
                    builderCallback.onTimeout()
                }
            },SCAN_PERIOD)
            scanning=true
            bluetoothLeScanner!!.startScan(leScanCallback)
        }
    }

    var disposed=false
    override fun isDisposed() = disposed

    @SuppressLint("MissingPermission")
    override fun dispose() {
        if (!disposed && scanning && bluetoothLeScanner!=null)
            bluetoothLeScanner.stopScan(leScanCallback)
        disposed=true
    }
}

class BTReceiverBuilder(
    rbcb:ReceiverBuilderCallback,
    rcb:ReceiverCallback,
    context:Context,
    activity:ComponentActivity,
):ReceiverBuilder(rbcb,rcb,context,activity) {
    private var isRdzTrovaLaSonda=false
    private var isCiapaSonde=false
    private var deviceInterface:SimpleBluetoothDeviceInterface?=null
    private var btMacAddress:String?=null
    private var bluetoothManager=BluetoothManager.instance
    private var broadCastReceiver=object:BroadcastReceiver() {
        override fun onReceive(context:Context,intent:Intent) {
            when (intent.action!!) {
                BluetoothDevice.ACTION_FOUND -> {
                    val device:BluetoothDevice?=
                        if (Build.VERSION.SDK_INT>=33) intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE,
                            BluetoothDevice::class.java)
                        else {
                            @Suppress("DEPRECATION") intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                        }
                    try {
                        val deviceName=device?.name
                        Log.i(TAG,"BT device found: $deviceName")
                        if (deviceInterface==null && deviceName!=null && (deviceName.startsWith(
                                MYSONDYGOPREFIX) || deviceName.startsWith(TROVALASONDAPREFIX) || deviceName.startsWith(
                                CIAPASONDEPREFIX))
                        ) {
                            val btAdapter=
                                (context.getSystemService(Context.BLUETOOTH_SERVICE) as android.bluetooth.BluetoothManager).adapter
                            btAdapter.cancelDiscovery()
                            isRdzTrovaLaSonda=deviceName.startsWith(TROVALASONDAPREFIX)
                            isCiapaSonde=deviceName.startsWith(CIAPASONDEPREFIX)
                            connectDevice(device.address)
                        }
                    } catch (ex:SecurityException) {
                        throw ReceiverException("Failed Bluetooth discovery")
                    }
                }
            }
        }
    }

    init {
        context.registerReceiver(broadCastReceiver,IntentFilter(BluetoothDevice.ACTION_FOUND))
    }

    @SuppressLint("MissingPermission")
    private fun onConnected(connectedDevice:BluetoothSerialDevice) {
        Log.i(TAG,"------------------------CONNECTED "+connectedDevice.mac)
        if (btMacAddress!=null) {
            bluetoothManager?.closeDevice(connectedDevice.mac)
            return
        }
        btMacAddress=connectedDevice.mac
        deviceInterface=connectedDevice.toSimpleDeviceInterface()
        val btAdapter=
            (context.getSystemService(Context.BLUETOOTH_SERVICE) as android.bluetooth.BluetoothManager).adapter
        val name=btAdapter.getRemoteDevice(btMacAddress).name
        val receiver=TTGO(callback,name,deviceInterface!!)
        deviceInterface?.setListeners(receiver,receiver,receiver)

        builderCallback.onReceiverConnected(receiver,this)
    }

    @SuppressLint("CheckResult")
    private fun connectDevice(mac:String) {
        context.unregisterReceiver(broadCastReceiver)
        bluetoothManager?.openSerialDevice(mac)!!.subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread()).subscribe(this::onConnected) {error ->
                Log.e(TAG,"Uh, oh: $error")
                bluetoothManager?.closeDevice(mac)
                builderCallback.onTimeout()//better than nothing
            }
    }

    @SuppressLint("MissingPermission")
    override fun connect() {
        val btAdapter=
            (context.getSystemService(Context.BLUETOOTH_SERVICE) as android.bluetooth.BluetoothManager).adapter

        if (btAdapter==null) {
            context.unregisterReceiver(broadCastReceiver)
            throw ReceiverException("Cannot access Bluetooth service")
        }

        with(btAdapter) {
            if (isEnabled) {
                try {
                    if (!isDiscovering)
                        if (startDiscovery()) {
                            Handler(Looper.getMainLooper()).postDelayed({
                                if (isDiscovering) {
                                    cancelDiscovery()
                                    builderCallback.onTimeout()
                                }
                            },SCAN_PERIOD)
                        } else {
                            Log.e(TAG,"Failed to start BT discovery")
                        }

                } catch (ex:SecurityException) {
                    context.unregisterReceiver(broadCastReceiver)
                    throw ReceiverException("Cannot start Bluetooth discovery")
                }
            } else {
                context.unregisterReceiver(broadCastReceiver)
                throw BluetoothNotEnabledException("Bluetooth not enabled")
            }
        }
    }

    private var disposed=false

    override fun dispose() {
        if (!disposed)
            try {
                context.unregisterReceiver(broadCastReceiver)
            }
            catch (_:IllegalArgumentException) {}
        disposed=true
    }

    override fun isDisposed()=disposed
}
