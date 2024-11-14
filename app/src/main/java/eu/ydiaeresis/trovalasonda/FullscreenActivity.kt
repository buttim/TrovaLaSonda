package eu.ydiaeresis.trovalasonda

import android.Manifest
import android.animation.LayoutTransition
import android.annotation.SuppressLint
import android.app.Activity
import android.app.ActivityManager
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Point
import android.graphics.Rect
import android.graphics.drawable.LevelListDrawable
import android.icu.util.LocaleData
import android.icu.util.ULocale
import android.location.Location
import android.location.LocationListener
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Debug
import android.os.Handler
import android.os.Looper
import android.util.DisplayMetrics
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.view.Window
import android.view.WindowManager
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.core.graphics.plus
import androidx.core.os.bundleOf
import androidx.core.view.children
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.harrysoft.androidbluetoothserial.BluetoothManager
import com.harrysoft.androidbluetoothserial.BluetoothSerialDevice
import com.harrysoft.androidbluetoothserial.SimpleBluetoothDeviceInterface
import eu.ydiaeresis.trovalasonda.databinding.ActivityFullscreenBinding
import io.nacular.measured.units.Length.Companion.feet
import io.nacular.measured.units.Length.Companion.kilometers
import io.nacular.measured.units.Length.Companion.meters
import io.nacular.measured.units.Length.Companion.miles
import io.nacular.measured.units.times
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import org.osmdroid.bonuspack.routing.OSRMRoadManager
import org.osmdroid.bonuspack.routing.Road
import org.osmdroid.bonuspack.routing.RoadManager
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.tileprovider.tilesource.MapBoxTileSource
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.tileprovider.tilesource.XYTileSource
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.CopyrightOverlay
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import org.osmdroid.views.overlay.ScaleBarOverlay
import org.osmdroid.views.overlay.infowindow.BasicInfoWindow
import org.osmdroid.views.overlay.infowindow.InfoWindow
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.IMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay
import uk.co.deanwild.materialshowcaseview.MaterialShowcaseSequence
import uk.co.deanwild.materialshowcaseview.MaterialShowcaseView
import uk.co.deanwild.materialshowcaseview.ShowcaseConfig
import uk.co.deanwild.materialshowcaseview.target.ViewTarget
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.time.DurationUnit
import kotlin.time.toDuration
import org.osmdroid.views.overlay.Polygon as Polygon1


fun MaterialShowcaseSequence.addSequenceItem(ctx:Context,
                                             targetView:View,
                                             title:Int,
                                             content:Int,
                                             dismissText:Int) {
    addSequenceItem(targetView,
        ctx.getString(title),
        ctx.getString(content),
        ctx.getString(dismissText))
}

class FullscreenActivity:AppCompatActivity(),LocationListener,MapEventsReceiver,
    SimpleBluetoothDeviceInterface.OnMessageReceivedListener,
    SimpleBluetoothDeviceInterface.OnErrorListener,
    SimpleBluetoothDeviceInterface.OnMessageSentListener {
    private var reportAlreadyShown=false
    private var distance=999999.9
    private lateinit var binding:ActivityFullscreenBinding
    private var bluetoothManager=BluetoothManager.instance
    private var btSerialDevice:Disposable?=null
    private var sondeTypes:Array<String>?=null
    private val path=Polyline()
    private val sondePath=Polyline()
    private val sondehubPath=Polyline()
    private val trajectory=Polyline()
    private var mkSonde:MyMarker?=null
    private var mkSondehub:MyMarker?=null
    private var mkTarget:MyMarker?=null
    private var mkBurst:Marker?=null
    private var lastPrediction:Instant?=null
    private var nPositionsReceived=0
    private val sondeDirection=Polyline()
    private var locationOverlay:MyLocationNewOverlay?=null
    private var accuracyOverlay=Polygon1()
    private var expandedMenu=false
    private var currentLocation:Location?=null
    private var sondePosition:GeoPoint?=null
    private var mapStyle=0
    private var btMacAddress:String?=null
    private var deviceInterface:SimpleBluetoothDeviceInterface?=null
    private var mute=-1
    private var muteChanged=true
    private var sondeId:String?=null
    private var sondeType=-1
    private var heightDelta=0.0
    private var freq=0.0
    private var height=0.0
    private var bk:Instant?=null
    private var timeLastSeen:Instant?=null
    private var timeLastSondehub:Instant?=null
    private var timeLastMessage:Instant?=null
    private val sondeLevelListDrawable=LevelListDrawable()
    private val handler=Handler(Looper.getMainLooper())
    private var burst=false
    private var batteryLevel:Int?=null
    private val mapbox=MapBoxTileSource()
    private var versionInfo:VersionInfo?=null
    private var versionChecked=false
    private var isRdzTrovaLaSonda=false
    private var isCiapaSonde=false
    private var otaRunning=false
    private val mutexOta=Mutex()
    private var roadManager:RoadManager=OSRMRoadManager(this,BuildConfig.APPLICATION_ID)
    private var roadOverlay:Polyline?=null
    private val cyclOSM=XYTileSource("CyclOSM",
        0,
        18,
        256,
        ".png",
        arrayOf("https://a.tile-cyclosm.openstreetmap.fr/cyclosm/"))

    private var receiver=object:BroadcastReceiver() {
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
                                (applicationContext.getSystemService(Context.BLUETOOTH_SERVICE) as android.bluetooth.BluetoothManager).adapter
                            btAdapter.cancelDiscovery()
                            isRdzTrovaLaSonda=deviceName.startsWith(TROVALASONDAPREFIX)
                            isCiapaSonde=deviceName.startsWith(CIAPASONDEPREFIX)
                            connectDevice(device.address)
                        }
                    } catch (ex:SecurityException) {
                        Snackbar.make(binding.root,
                            "Failed Bluetooth discovery",
                            Snackbar.LENGTH_LONG).show()
                    }
                }
            }
        }
    }
    private val resultLauncher=
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {result ->
            if (result.resultCode!=Activity.RESULT_OK) return@registerForActivityResult
            val data:Intent?=result.data
            var reset=false
            val cmds=mutableListOf<Pair<String,Any>>()
            val resetCmds=listOf(SettingsActivity.LCD,
                SettingsActivity.OLED_SDA,
                SettingsActivity.OLED_SCL,
                SettingsActivity.OLED_RST,
                SettingsActivity.LED_POUT,
                SettingsActivity.BUZ_PIN,
                SettingsActivity.BATTERY)
            if (data!=null && data.extras!=null) {
                for (k in data.extras?.keySet()!!) {
                    reset=resetCmds.indexOf(k)>=0
                    if (k==SettingsActivity.MYCALL) cmds.add(Pair<String,Any>("myCall",
                        data.extras?.getString(k)!!))
                    else cmds.add(Pair<String,Any>(k,data.extras?.getInt(k)!!))
                }
                if (reset) MaterialAlertDialogBuilder(this,
                    R.style.MaterialAlertDialog_rounded).setTitle(R.string.ALERT)
                    .setMessage(R.string.NEW_SETTINGS_REQUIRE_A_RESTART)
                    .setNegativeButton(R.string.CANCEL) {dialog,_ -> dialog.dismiss()}
                    .setPositiveButton("OK") {dialog,_ ->
                        dialog.dismiss()
                        sendCommands(cmds)
                        showProgress(true)
                    }.show()
                else sendCommands(cmds)
            }
        }

    @Suppress("SameParameterValue")
    private fun showcase(id:String) {
        MaterialShowcaseSequence(this,id).apply {
            setConfig(ShowcaseConfig().apply {
                delay=500
                dismissTextColor=Color.GREEN
            })

            /*val mcsv1=MaterialShowcaseView.Builder(this@FullscreenActivity)
                .setTarget(binding.type)
                .setTitleText("Your location")
                .setContentText("Red when no TTGO connected, yellow when connected")
                .setDismissText(R.string.GOT_IT)
                .build()
            mcsv1.setTarget(GeoPointTarget(binding.map,GeoPoint(currentLocation!!)))
            addSequenceItem(mcsv1)*/
            val mcsv=MaterialShowcaseView.Builder(this@FullscreenActivity).setTarget(binding.type)
                .setTitleText(applicationContext.getString(R.string.SONDE_DATA))
                .setContentText(applicationContext.getString(R.string.TAP_HERE_TO_CHANGE_SONDE_TYPE_FREQUENCY))
                .setDismissText(R.string.GOT_IT).build()
            mcsv.setTarget(MultipleViewsTarget(listOf(binding.verticalSpeed,
                binding.horizontalSpeed,
                binding.height,
                binding.type)))
            addSequenceItem(mcsv)
            addSequenceItem(applicationContext,
                binding.id,
                R.string.SERIAL_NUMBER_FOR_THE_SONDE_YOU_ARE_RECEIVING,
                R.string.YOU_CAN_TAP_IT_TO_OPEN_SONDEHUB_OR_RADIOSONDY,
                R.string.GOT_IT)
            addSequenceItem(applicationContext,
                binding.distance,
                R.string.DISTANCE,
                R.string.BETWEEN_YOU_AND_THE_SONDE_YOU_ARE_RECEIVING,
                R.string.GOT_IT)
            addSequenceItem(applicationContext,
                binding.buzzer,
                R.string.BUZZER,
                R.string.MUTE_YOUR_TTGO_TAPPING_THIS,
                R.string.GOT_IT)
            addSequenceItem(applicationContext,
                binding.batteryMeter,
                R.string.BATTERY,
                R.string.KEEP_AN_EYE_ON_YOUR_TTGOS_BATTERY_LEVEL,
                R.string.GOT_IT)
            //addSequenceItem(applicationContext,binding.rssi,R.string.RSSI,R.string.SIGNAL_STRENGTH_IS_SHOWN_HERE,R.string.GOT_IT)
            addSequenceItem(applicationContext,
                binding.menuLayer,
                R.string.MAP_LAYERS,
                R.string.CHOOSE_BETWEEN_THREE_DIFFERENT_MAP_LAYERS,
                R.string.GOT_IT)
            addSequenceItem(applicationContext,
                binding.menuMaps,
                R.string.NAVIGATION,
                R.string.LAUNCH_GOOGLE_MAPS,
                R.string.GOT_IT)
            addSequenceItem(applicationContext,
                binding.menuSettings,
                R.string.TTGOS_PARAMETERS,
                R.string.SET_PINS_BANDWIDTH_AND_CALIBRATION_FOR_YOUR_TTGO,
                R.string.GOT_IT)
            setOnItemShownListener {_,i ->
                if (i==5) openMenu()
            }
            setOnItemDismissedListener {_,i ->
                if (i==7) closeMenu()
            }
            start()
        }
    }

    fun startOta() {
        otaRunning=true
    }

    fun stopOta() {
        otaRunning=false
    }

    private fun showProgress(show:Boolean) {
        binding.progress.visibility=if (show) View.VISIBLE else View.GONE
    }

    override fun onLocationChanged(location:Location) {
        //discard points with accuracy less than 100m
        if (location.hasAccuracy() && location.accuracy>100) return
        val point=GeoPoint(location)
        if (currentLocation==null) binding.map.controller?.setCenter(point)
        currentLocation=location
        path.addPoint(point)
        path.actualPoints.apply {if (size>400) removeAt(0)}
        if (sondePosition!=null) {
            distance=GeoPoint(currentLocation).distanceToAsDouble(sondePosition)
            setDistance(distance)
        }
        updateSondeDirection()

        accuracyOverlay.points=
            Polygon1.pointsAsCircle(GeoPoint(location.latitude,location.longitude),
                location.accuracy.toDouble())
        binding.map.invalidate()
    }

    override fun onProviderDisabled(provider:String)=Unit
    override fun onProviderEnabled(provider:String)=Unit

    @Deprecated("deprecated")
    override fun onStatusChanged(provider:String,status:Int,extras:Bundle) {
    }

    private val activityResultContract=
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {result ->
            if (result.resultCode!=Activity.RESULT_OK) finish()
            else Handler(Looper.getMainLooper()).postDelayed({
                if (deviceInterface==null) connect()
            },2000)
        }

    private fun connect() {
        val btAdapter=
            (applicationContext.getSystemService(Context.BLUETOOTH_SERVICE) as android.bluetooth.BluetoothManager).adapter
                ?: return
        with(btAdapter) {
            if (isEnabled) Handler(Looper.getMainLooper()).postDelayed({
                if (deviceInterface==null) connect()
            },2000)
            else {
                val intent=Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)

                //TODO: al termine può causare IllegalStateException
                activityResultContract.launch(intent)
            }

            try {
                if (!isDiscovering && !startDiscovery()) Log.e(TAG,"Failed to start BT discovery")
                Unit
            } catch (ex:SecurityException) {
                Snackbar.make(binding.root,"Cannot start Bluetooth discovery",Snackbar.LENGTH_LONG)
                    .show()
            }
        }
    }

    @SuppressLint("CheckResult")
    private fun connectDevice(mac:String) {
        btSerialDevice=bluetoothManager?.openSerialDevice(mac)!!.subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread()).subscribe(this::onConnected) {error ->
                Log.e(TAG,error.toString())
                bluetoothManager?.closeDevice(mac)/*Handler(Looper.getMainLooper()).postDelayed({
                    connect()
                }, 1000)*/
            }
    }

    private fun onConnected(connectedDevice:BluetoothSerialDevice) {
        Log.i(TAG,"------------------------CONNECTED "+connectedDevice.mac)
        if (btMacAddress!=null) {
            bluetoothManager?.closeDevice(connectedDevice.mac)
            return
        }
        timeLastMessage=null
        btMacAddress=connectedDevice.mac
        deviceInterface=connectedDevice.toSimpleDeviceInterface()
        deviceInterface?.setListeners(this,
            this,
            this)//this::onMessageReceived, this::onMessageSent, this::onError)

        val bmp=BitmapFactory.decodeResource(resources,R.drawable.ic_person_yellow)
        locationOverlay?.apply {
            setPersonIcon(bmp)
            setDirectionIcon(bmp)
            setPersonAnchor(.5f,.5f)
            setDirectionAnchor(.5f,.5f)
        }
        binding.map.invalidate()
        muteChanged=false
        playSound(R.raw._541506__se2001__cartoon_quick_zip)

        try {
            val btAdapter=
                (applicationContext.getSystemService(Context.BLUETOOTH_SERVICE) as android.bluetooth.BluetoothManager).adapter
            val name=btAdapter.getRemoteDevice(btMacAddress).name
            Snackbar.make(binding.root,
                applicationContext.getString(R.string.CONNECTED_TO)+" "+name,
                Snackbar.LENGTH_LONG).show()
        } catch (ex:SecurityException) {
            Snackbar.make(binding.root,R.string.CANNOT_CONNECT,Snackbar.LENGTH_LONG).show()
        }
        maybeShowDonation()
    }

    private fun onDisconnected() {
        Log.i(TAG,"onDisconnected")
        playSound(R.raw._541506__se2001__cartoon_quick_zip_reverse)
        val bmp=BitmapFactory.decodeResource(resources,R.drawable.ic_person_red)
        locationOverlay?.setPersonIcon(bmp)
        locationOverlay?.setDirectionIcon(bmp)
        locationOverlay?.setDirectionAnchor(.5f,.5f)
        sondeLevelListDrawable.level=0
        muteChanged=true
        bluetoothManager?.closeDevice(btMacAddress!!)
        btMacAddress=null
        deviceInterface=null
        showProgress(false)
        Snackbar.make(binding.root,R.string.CONNECTION_LOST,Snackbar.LENGTH_LONG).show()
        Handler(Looper.getMainLooper()).postDelayed({
            connect()
        },1000)
        binding.batteryMeter.chargeLevel=null
        versionChecked=false
    }

    override fun onError(error:Throwable) {
        Log.d(TAG,error.toString())
        onDisconnected()
    }

    override fun onMessageSent(message:ByteArray) {
        //Log.i(TAG, "SENT: $message")
        if (otaRunning && mutexOta.isLocked) mutexOta.unlock()
    }

    fun sendOTA(length:Int) {
        sendCommand("ota",length)
    }

    fun sendBytes(bytes:ByteArray) {
        deviceInterface?.sendMessage(bytes)
    }

    private fun sendCommand(cmd:String) {
        try {
            deviceInterface?.sendMessage("o{$cmd}o\r\n".toByteArray())
        } catch (e:Exception) {
            Log.e(TAG,e.toString())
            btSerialDevice?.dispose()
            btSerialDevice=null
            //connect()
        }
    }

    private fun sendCommand(cmd:String,value:Any) {
        sendCommand("$cmd=$value")
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

    private fun process(msg:String) {
        timeLastMessage=Instant.now()
        val campi=msg.split("/")
        if (campi[campi.size-1]!="o") {
            Log.e(TAG,"manca terminatore messaggio")
            return
        }
        when (campi[0]) {
            "0" -> if (campi.size==9) mySondyGOStatus(campi[1],
                campi[2].toDouble(),
                campi[3].toDouble(),
                campi[4].toInt(),
                campi[5].toInt(),
                campi[6].toInt(),
                campi[7])
            else {
                Log.e(TAG,"numero campi errato in messaggio tipo 0 (${campi.size} invece di 9)")
                return
            }

            "1" -> if (campi.size==20) {
                mySondyGOSondePos(campi[1],
                    campi[2].toDouble(),
                    campi[3],
                    campi[4].toDouble(),
                    campi[5].toDouble(),
                    campi[6].toDouble(),
                    campi[7].toDouble(),
                    campi[8].toDouble(),
                    campi[9].toInt(),
                    campi[10].toInt(),
                    campi[11]=="1",
                    campi[12].toInt(),
                    campi[13].toInt(),
                    campi[14].toInt(),
                    campi[18])
                freqOffsetReceiver?.freqOffset(campi[10].toInt())
            } else {
                Log.e(TAG,"numero campi errato in messaggio tipo 1 (${campi.size} invece di 20)")
                return
            }

            "2" -> if (campi.size==11) {
                mySondyGOSonde(campi[1],
                    campi[2].toDouble(),
                    campi[3],
                    campi[4].toDouble(),
                    campi[5].toInt(),
                    campi[6].toInt(),
                    campi[7].toInt(),
                    campi[8].toInt(),
                    campi[9])
                freqOffsetReceiver?.freqOffset(campi[6].toInt())
            } else {
                Log.e(TAG,"numero campi errato in messaggio tipo 2 (${campi.size} invece di 11)")
                return
            }

            "3" -> if (campi.size==23) mySondyGOSettings(campi[1],
                campi[2].toDouble(),
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
                Log.e(TAG,"numero campi errato in messaggio tipo 3 (${campi.size} invece di 23)")
                return
            }

            else -> Log.e(TAG,"Tipo messaggio sconosciuto")
        }
    }

    override fun onMessageReceived(message:String) {
        Log.i(TAG,"RECEIVED: $message")
        try {
            process(message)
        } catch (e:Exception) {
            Log.e(TAG,e.toString())
            btSerialDevice?.dispose()
            btSerialDevice=null
        }
    }

    private fun updateMute(mute:Int) {
        this.mute=mute
        binding.buzzer.apply {
            if (mute==-1) {
                isEnabled=false
                imageAlpha=128
            } else {
                setImageResource(if (mute==1) R.drawable.ic_buzzer_off else R.drawable.ic_buzzer_on)
                isEnabled=true
                imageAlpha=255
            }
        }
        muteChanged=false
    }

    private fun newSonde(id:String) {
        sondeId=id
        binding.id.text=normalizeSondeId()
        bk=null
        burst=false
        binding.bk.visibility=View.GONE
        //sondePosition=null
        nPositionsReceived=0
        mkSondehub?.setVisible(false)
        sondehubPath.actualPoints.clear()
        sondehubPath.isVisible=false
        //mkSonde?.setVisible(true)
        sondePath.actualPoints.clear()
        mkBurst?.setVisible(false)
        trajectory.actualPoints.clear()
        trajectory.isVisible=false
        mkTarget?.setVisible(false)
        timeLastSeen=Instant.now()
        reportAlreadyShown=false
    }

    private fun updateSondeLocation(id:String,lat:Double,lon:Double,alt:Double) {
        sondePosition=GeoPoint(lat,lon)
        val d=
            if (currentLocation!=null) GeoPoint(currentLocation).distanceToAsDouble(sondePosition) else 0.0
        if (d>1000000.0) return

        binding.lat.text=String.format(Locale.US," %.5f",lat)
        binding.lon.text=String.format(Locale.US," %.5f",lon)
        if (sondeId!=id) {
            newSonde(id)
            if (currentLocation!=null) {
                binding.map.zoomToBoundingBox(BoundingBox.fromGeoPointsSafe(mutableListOf(GeoPoint(
                    lat,
                    lon),GeoPoint(currentLocation))).increaseByScale(1.9F),false,50)
                binding.map.invalidate()
            } else binding.map.controller?.setCenter(mkSonde?.position)

            playSound()
        }

        mkSonde?.setVisible(true)
        mkSonde?.position=GeoPoint(lat,lon,alt)
        sondePath.addPoint(mkSonde?.position)
        sondeLevelListDrawable.level=1

        if (currentLocation!=null) {
            setDistance(d)
            updateSondeDirection()
        }
        timeLastSeen=Instant.now()
        if (nPositionsReceived>10 && (lastPrediction==null || lastPrediction?.until(Instant.now(),
                ChronoUnit.SECONDS)!!>60)
        ) {
            lastPrediction=Instant.now()
            predict(lat,lon,alt)
        }
    }

    private fun useImperialUnits():Boolean {
        if (Build.VERSION.SDK_INT<Build.VERSION_CODES.P) return false
        val ms=LocaleData.getMeasurementSystem(ULocale.getDefault())
        return ms.equals(LocaleData.MeasurementSystem.US)
    }

    @SuppressLint("SetTextI18n")
    private fun setDistance(dist:Double) {
        with(binding) {
            if (useImperialUnits()) {
                val d=dist*meters
                if (d>.25*miles) {
                    unit.text="mi"
                    distance.text=String.format(Locale.US,"%.1f",d `in` miles)
                } else {
                    unit.text="ft"
                    distance.text=String.format(Locale.US,"%.1f",d `in` feet)
                }
            } else if (dist>=10000F) {
                unit.text="km"
                distance.text=String.format(Locale.US,"%.1f",dist/1000)
            } else {
                unit.text="m"
                distance.text=String.format(Locale.US,"%.1f",dist)
            }
        }
    }

    private fun updateSondeDirection() {
        if (currentLocation==null || sondeId==null) return
        sondeDirection.apply {
            actualPoints.clear()
            addPoint(GeoPoint(currentLocation))
            addPoint(mkSonde?.position)
            isVisible=sondePosition!=null && sondePosition?.latitude!=.0
        }
    }

    private fun updateTypeAndFreq(type:String,freq:Double) {
        if (this.freq!=freq || sondeType<1 || type!=sondeTypes!![sondeType-1]) {
            @Suppress("SetTextI18n")
            binding.type.text="$type ${freq}MHz"
            sondeType=sondeTypes?.indexOf(type)!!+1
            this.freq=freq
        }
    }

    @SuppressLint("DefaultLocale")
    private fun updateBk(bk:Int) {
        binding.bk.apply {
            visibility=View.VISIBLE
            text=String.format("BK %d:%02d:%02d",bk/3600,(bk/60)%60,bk%60)
        }
        this.bk=Instant.now().plusSeconds(bk.toLong())
    }

    private fun updateRSSI(rssi:Double) {
        @Suppress("SetTextI18n")
        binding.dbm.text="-${rssi}dBm"
        binding.rssi.progress=(binding.rssi.max-rssi).toInt()
    }

    private fun updateBattery(percent:Int,mV:Int) {
        batteryLevel=mV
        binding.batteryMeter.chargeLevel=percent
    }

    @Suppress("UNUSED_PARAMETER")
    private fun mySondyGOSondePos(type:String,
                                  freq:Double,
                                  name:String,
                                  lat:Double,
                                  lon:Double,
                                  height:Double,
                                  _vel:Double,
                                  sign:Double,
                                  bat:Int,
                                  afc:Int,
                                  bk:Boolean,
                                  bktime:Int,
                                  batV:Int,
                                  mute:Int,
                                  ver:String) {
        updateMute(mute)
        updateTypeAndFreq(type,freq)

        if (height==0.0 || height>40000.0 || lat==0.0 || lon==0.0) return

        mkSondehub?.setVisible(false)
        sondehubPath.isVisible=false

        //HACKHACK: MySondyGO 2.30 incorrectly reports horizontal speed in m/s for meteomodem sondes
        var vel=_vel
        if (!isRdzTrovaLaSonda && ver=="2.30" && (type=="M10" || type=="M20")) vel*=3.6

        nPositionsReceived++

        if (timeLastSeen!=null) {
            val delta=Instant.now().epochSecond-timeLastSeen!!.epochSecond
            if (delta!=0L) {
                val verticalSpeed=(height-this.height)/delta
                val vs=verticalSpeed*meters
                @Suppress("SetTextI18n") if (useImperialUnits()) binding.verticalSpeed.text=
                    String.format(Locale.US,"Vs: %.1fft/s",vs `in` feet)
                else binding.verticalSpeed.text=String.format(Locale.US,"Vs: %.1fm/s",verticalSpeed)
            }
        }

        updateSondeLocation(name,lat,lon,height)

        @Suppress("SetTextI18n") if (useImperialUnits()) {
            val h=String.format(Locale.US,"%.1f",height*meters `in` feet)
            binding.height.text="H: ${h}ft"
        } else binding.height.text="H: ${height}m"
        binding.direction.text=
            if (abs(this.height-height)<2) "=" else if (this.height<height) "▲" else "▼"
        val newHeightDelta=height-this.height
        if (!burst && heightDelta>0 && newHeightDelta<0) {
            burst=true
            mkBurst?.apply {
                position=GeoPoint(lat,lon)
                setVisible(true)
                val dtf=DateTimeFormatter.ofPattern("HH:mm")
                title=LocalTime.from(Instant.now().atZone(ZoneId.systemDefault())).format(dtf)
            }
            playSound(R.raw._541192__eminyildirim__balloon_explosion_pop)
        }
        @Suppress("SetTextI18n") if (useImperialUnits()) {
            val v=vel*kilometers
            binding.horizontalSpeed.text=String.format(Locale.US,"V: %.1fmph",v `in` miles)
        } else binding.horizontalSpeed.text=String.format(Locale.US,"V: %.1fkm/h",vel)
        heightDelta=newHeightDelta
        this.height=height

        if (bk && bktime>0 && bktime!=8*3600+30*60) updateBk(bktime)
        updateRSSI(sign)
        updateBattery(bat,batV)
    }

    private fun mySondyGOStatus(type:String,
                                freq:Double,
                                sign:Double,
                                bat:Int,
                                batV:Int,
                                mute:Int,
                                ver:String) {
        if (isRdzTrovaLaSonda && !versionChecked && versionInfo!=null) {
            versionChecked=true
            if (versionInfo?.version!=ver) UpdateDialog(this,mutexOta,versionInfo!!).show(
                supportFragmentManager,
                "")
        }
        updateTypeAndFreq(type,freq)
        updateMute(mute)
        sondeLevelListDrawable.level=0
        updateRSSI(sign)
        updateBattery(bat,batV)

        if (sondePosition!=null && !reportAlreadyShown && distance<30) showReport()
    }

    private fun showReport() {
        if (sondePosition==null) return
        reportAlreadyShown=true
        WebPageChooserDialog().showForRecovery(supportFragmentManager,sondeId!!,sondePosition!!.latitude, sondePosition!!.longitude,sondePosition!!.altitude)
    }

    private fun getFromSondeHub(type:String,id:String,lastSeen:Instant) {
        val sh=Sondehub(type,id,lastSeen)

        CoroutineScope(Dispatchers.IO).launch {
            val points=sh.getTrack()
            if (points.isNotEmpty()) {
                sondehubPath.actualPoints.clear()
                points.forEach {pt ->
                    sondehubPath.addPoint(pt)
                }
                mkSondehub?.position=points.last()
                mkSondehub?.setVisible(true)
                sondehubPath.isVisible=true

                if ((lastPrediction==null || lastPrediction?.until(Instant.now(),
                        ChronoUnit.SECONDS)!!>60)
                ) {
                    lastPrediction=Instant.now()
                    points.last().apply {
                        predict(latitude,longitude,altitude)
                    }
                }
            }
        }
    }

    @Suppress("UNUSED_PARAMETER")
    private fun mySondyGOSonde(type:String,
                               freq:Double,
                               name:String,
                               sign:Double,
                               bat:Int,
                               afc:Int,
                               batV:Int,
                               mute:Int,
                               ver:String) {
        updateMute(mute)
        updateTypeAndFreq(type,freq)
        sondeLevelListDrawable.level=0
        updateRSSI(sign)
        updateBattery(bat,batV)
        if (sondeId!=name) newSonde(name)
    }

    @Suppress("UNUSED_PARAMETER")
    private fun mySondyGOSettings(type:String,
                                  freq:Double,
                                  sda:Int,
                                  scl:Int,
                                  rst:Int,
                                  led:Int,
                                  RS41bw:Int,
                                  M20bw:Int,
                                  M10bw:Int,
                                  PILOTbw:Int,
                                  DFMbw:Int,
                                  call:String,
                                  offset:Int,
                                  bat:Int,
                                  batMin:Int,
                                  batMax:Int,
                                  batType:Int,
                                  lcd:Int,
                                  nam:Int,
                                  buz:Int,
                                  ver:String) {
        showProgress(false)
        updateMute(mute)
        val intent=Intent(this,SettingsActivity::class.java)
        val extras=Bundle().apply {
            putInt("oled_sda",sda)
            putInt("oled_scl",scl)
            putInt("oled_rst",rst)
            putInt("led_pout",led)
            putInt("rs41.rxbw",RS41bw)
            putInt("m20.rxbw",M20bw)
            putInt("m10.rxbw",M10bw)
            putInt("pilot.rxbw",PILOTbw)
            putInt("dfm.rxbw",DFMbw)
            putString("myCall",call)
            putInt("freqofs",offset)
            putInt("battery",bat)
            putInt("vBatMin",batMin)
            putInt("vBatMax",batMax)
            putInt("vBatType",batType)
            putInt("lcd",lcd)
            putInt("aprsName",nam)
            putInt("buz_pin",buz)
            putString("ver",ver)
        }
        intent.putExtras(extras)
        resultLauncher.launch(intent)
    }

    override fun onRequestPermissionsResult(requestCode:Int,
                                            permissions:Array<String>,
                                            grantResults:IntArray) {
        super.onRequestPermissionsResult(requestCode,permissions,grantResults)
    }

    private fun closeMenu() {
        binding.coords.visibility=View.GONE
        binding.menu.layoutParams?.height=binding.menu.getChildAt(0).layoutParams.height
        binding.menu.requestLayout()
        expandedMenu=false
    }

    private fun openMenu() {
        if (binding.lat.text!="") binding.coords.visibility=View.VISIBLE
        expandedMenu=true
        binding.menu.apply {
            layoutParams?.height=
                children.fold(0) {sum,el -> sum+if (el.visibility==View.VISIBLE) el.layoutParams.height else 0}
            requestLayout()
        }
    }

    private fun ttgoNotConnectedWarning() {
        Snackbar.make(binding.root,R.string.TTGO_NOT_CONNECTED,Snackbar.LENGTH_LONG).show()
    }

    private fun toggleBuzzer() {
        if (deviceInterface==null) ttgoNotConnectedWarning()
        else {
            if (muteChanged) return
            mute=if (mute==1) 0 else 1
            sendCommand("mute",mute)
            binding.buzzer.imageAlpha=64
            muteChanged=true
        }
    }

    private fun playSound(id:Int=R.raw._573381__ammaro__ding) {
        MediaPlayer().apply {
            setOnPreparedListener {it.start()}
            setOnErrorListener {_,a,b -> Log.e(TAG,"$a $b"); true}
            try {
                setDataSource(applicationContext,
                    Uri.parse("${ContentResolver.SCHEME_ANDROID_RESOURCE}://$packageName/raw/${id}"))
                prepareAsync()
            } catch (e:Exception) {
                Log.e(TAG,e.toString())
            }
        }
    }

    private fun requestPermissionsIfNecessary(permissions:Array<String>) {
        val permissionsToRequest:ArrayList<String> =ArrayList()
        for (permission in permissions) {
            if (ContextCompat.checkSelfPermission(this,
                    permission)!=PackageManager.PERMISSION_GRANTED
            ) {
                // Permission is not granted
                permissionsToRequest.add(permission)
            }
        }
        if (permissionsToRequest.size>0) {
            ActivityCompat.requestPermissions(this,
                permissionsToRequest.toArray(arrayOfNulls(0)),
                REQUEST_PERMISSIONS_REQUEST_CODE)
        }
    }

    private var n:Int=0 //solo per debug

    private fun maybeShowDonation() {
        val prefs=getSharedPreferences(BuildConfig.APPLICATION_ID,MODE_PRIVATE)
        val last:Long=prefs.getLong(LAST_TIME_DONATION_SHOWN,0)
        if (Instant.now().epochSecond-last>3600*24*8) {
            startActivity(Intent(this,DonationActivity::class.java))
            prefs.edit {
                putLong(LAST_TIME_DONATION_SHOWN,Instant.now().epochSecond)
                commit()
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    override fun onCreate(savedInstanceState:Bundle?) {
        super.onCreate(savedInstanceState)

        @Suppress("DEPRECATION") setTaskDescription(if (Build.VERSION.SDK_INT>=Build.VERSION_CODES.TIRAMISU) {
            ActivityManager.TaskDescription.Builder().setIcon(R.drawable.ic_launcher_foreground)
                .build()
        } else ActivityManager.TaskDescription(null,
            R.drawable.ic_launcher_foreground,
            Color.YELLOW))
        Configuration.getInstance().apply {
            load(applicationContext,this@FullscreenActivity.getPreferences(Context.MODE_PRIVATE))
            userAgentValue=BuildConfig.APPLICATION_ID
        }

        CoroutineScope(Dispatchers.IO).launch {
            versionInfo=FirmwareUpdater().getVersion()
        }

        mapbox.retrieveAccessToken(this)
        mapbox.retrieveMapBoxMapId(this)
        TileSourceFactory.addTileSource(mapbox)

        sondeTypes=resources.getStringArray(R.array.sonde_types)
        supportRequestWindowFeature(Window.FEATURE_NO_TITLE)
        //TODO: screen on solo se connesso ?
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        /*WindowInsetsControllerCompat(window, window.decorView).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.systemBars())
        }*/

        requestPermissionsIfNecessary(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.BLUETOOTH_ADMIN,
            Manifest.permission.INTERNET))

        binding=ActivityFullscreenBinding.inflate(layoutInflater)
        setContentView(binding.root)

        /*//////////////////////////////////////
        CoroutineScope(Dispatchers.IO).launch {
            with (binding) {
                recovered(applicationContext,
                    "test",
                    "string",
                    45.0,
                    7.0,
                    1.0,
                    "this is a test")
            }
        }
        *///////////////////////////////////////
        /*SondehubReport().apply {
            sondeId="string"
            lat=45.069122825913084
            lon=7.693258448936513
            alt=500.0
            show(supportFragmentManager,"")
        }
        *///////////////////////////////////////

        @SuppressLint("SetTextI18n") if (useImperialUnits()) with(binding) {
            unit.text="mi"
            height.text="H: -ft"
            horizontalSpeed.text="V: -mph"
            verticalSpeed.text="Vs: -ft/s"
        }

        binding.buzzer.setOnClickListener {toggleBuzzer()}
        binding.batteryMeter.setOnClickListener {
            if (deviceInterface==null) ttgoNotConnectedWarning()
            else if (batteryLevel!=null) Snackbar.make(binding.root,
                applicationContext.getString(R.string.BATTERY_)+" %.1fV".format(batteryLevel!!/1000f),
                Snackbar.LENGTH_SHORT).show()
        }
        binding.lat.setOnClickListener {
            val clipboard=getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
            val clip=ClipData.newPlainText("sonde latitude",binding.lat.text)
            clipboard.setPrimaryClip(clip)
            Snackbar.make(binding.root,R.string.LATITUDE_COPIED,Snackbar.LENGTH_SHORT).show()
        }
        binding.lon.setOnClickListener {
            val clipboard=getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
            val clip=ClipData.newPlainText("sonde longitude",binding.lon.text)
            clipboard.setPrimaryClip(clip)
            Snackbar.make(binding.root,R.string.LONGITUDE_COPIED,Snackbar.LENGTH_SHORT).show()
        }
        val copyCoordinates={_:View ->
            val clipboard=getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
            val clip=
                ClipData.newPlainText("sonde coordinates","${binding.lat.text} ${binding.lon.text}")
            clipboard.setPrimaryClip(clip)
            Snackbar.make(binding.root,R.string.COORDINATES_COPIED,Snackbar.LENGTH_SHORT).show()
            true
        }
        binding.lat.setOnLongClickListener(copyCoordinates)
        binding.lon.setOnLongClickListener(copyCoordinates)
        binding.id.setOnClickListener {
            /*//////////////////////////////////////////
            if (sondeId==null) {
                sondeType=1
                sondeId="string"
            }
            WebPageChooserDialog().showForRecovery(supportFragmentManager,sondeId!!,currentLocation!!.latitude,currentLocation!!.longitude,1000.0)
            *///////////////////////////////////////////
            if (sondeId!=null) {
                if (sondePosition==null)
                    WebPageChooserDialog().showForInfo(supportFragmentManager,sondeId!!,currentLocation!!.latitude,currentLocation!!.longitude)
                else
                    WebPageChooserDialog().showForInfo(supportFragmentManager,sondeId!!,sondePosition!!.latitude,sondePosition!!.longitude)
            } else Snackbar.make(binding.root,
                R.string.NO_SONDE_TO_OPEN_A_WEBPAGE_FOR,
                Snackbar.LENGTH_SHORT).show()
        }
        binding.id.setOnLongClickListener {
            Snackbar.make(binding.root,R.string.SHOW_A_WEB_PAGE,Snackbar.LENGTH_SHORT).show()
            true
        }
        binding.panel.setOnClickListener {
            if (deviceInterface==null) {
                ttgoNotConnectedWarning()
                return@setOnClickListener
            }
            SondeTypeDialog().apply {
                freq=this@FullscreenActivity.freq
                type=sondeType
                isCiapaSonde=this@FullscreenActivity.isCiapaSonde
                dialogCloseListener=object:DialogCloseListener {
                    override fun handleDialogClose() {
                        sendCommands(listOf<Pair<String,Any>>(Pair("f",freq),Pair("tipo",type)))
                    }
                }
                show(supportFragmentManager,"")
            }
        }

        binding.menu.apply {
            layoutTransition?.enableTransitionType(LayoutTransition.CHANGING)

            onFocusChangeListener=View.OnFocusChangeListener {_,_ ->
                closeMenu()
            }
        }
        closeMenu()
        binding.menuCenter.setOnClickListener {
            if (currentLocation!=null) binding.map.controller?.setCenter(GeoPoint(currentLocation))
            else Snackbar.make(binding.root,R.string.NO_CURRENT_LOCATION,Snackbar.LENGTH_SHORT)
                .show()
            //////////////////////////////////////////////////////////////////////////////////
            if (Debug.isDebuggerConnected()) {
                //predict(45.0,7.0,1000.0)
                val msgs=
                    arrayOf("1/RS41/402.800/T1840263/41.20888/5.82557/6060.9/93.1/127.5/53/0/1/28040/3643/0/0/0/0/2.30/o",
                        "1/RS41/402.800/T1840263/45.20888/8.82567/6060.9/93.1/127.5/15/0/1/28039/3643/0/0/0/0/2.30/o",
                        "1/RS41/402.800/T1840263/45.20888/8.82577/6040.9/93.1/127.5/99/0/1/28038/3643/0/0/0/0/2.30/o",
                        "1/RS41/402.800/T1840263/45.20898/8.82567/6030.9/93.1/127.5/1/0/1/28037/3643/0/0/0/0/2.30/o",
                        "1/RS41/402.800/T1840263/45.20898/8.82567/6030.9/93.1/127.5/1/0/1/28037/3643/0/0/0/0/2.30/o",
                        "1/RS41/402.800/T1840263/45.20898/8.82567/6030.9/93.1/127.5/1/0/1/28037/3643/0/0/0/0/2.30/o",
                        "1/RS41/402.800/T1840263/45.20898/8.82567/6030.9/93.1/127.5/1/0/1/28037/3643/0/0/0/0/2.30/o",
                        "1/RS41/402.800/T1840263/45.20898/8.82567/6030.9/93.1/127.5/1/0/1/28037/3643/0/0/0/0/2.30/o",
                        "1/RS41/402.800/T1840263/45.20898/8.82567/6030.9/93.1/127.5/1/0/1/28037/3643/0/0/0/0/2.30/o",
                        "1/RS41/402.800/T1840263/45.20898/8.82567/6030.9/93.1/127.5/1/0/1/28037/3643/0/0/0/0/2.30/o",
                        "1/RS41/402.800/T1840263/45.20898/8.82567/6030.9/93.1/127.5/1/0/1/28037/3643/0/0/0/0/2.30/o",
                        "1/RS41/402.800/T1840263/45.20898/8.82567/6030.9/93.1/127.5/1/0/1/28037/3643/0/0/0/0/2.30/o",
                        "1/RS41/402.800/T1840263/45.20898/8.82567/6030.9/93.1/127.5/1/0/1/28037/3643/0/0/0/0/2.30/o",
                        "1/RS41/402.800/T1840263/45.20898/8.82567/6030.9/93.1/127.5/1/0/1/28037/3643/0/0/0/0/2.30/o")
                n++
                n%=msgs.size
                process(msgs[n])
            }
            //////////////////////////////////////////////////////////////////////////////////
            closeMenu()
        }
        binding.menuCenter.setOnLongClickListener {
            Snackbar.make(binding.root,R.string.center_user_in_map,Snackbar.LENGTH_SHORT).show()
            true
        }
        binding.menuSettings.setOnClickListener {
            if (deviceInterface==null) ttgoNotConnectedWarning()
            else {
                sendCommand("?")
                showProgress(true)
            }
            closeMenu()
        }
        binding.menuSettings.setOnLongClickListener {
            Snackbar.make(binding.root,R.string.RADIO_SETTINGS,Snackbar.LENGTH_SHORT).show()
            true
        }
        binding.menuLayer.setOnClickListener {
            mapStyle=(mapStyle+1)%3
            Snackbar.make(binding.root,
                arrayOf("Mapnik",
                    "CyclOSM",
                    "Mapbox satellite")[mapStyle]+" "+applicationContext.getString(R.string.MAP_STYLE_SELECTED),
                Snackbar.LENGTH_SHORT).show()
            with(binding.map) {
                setTileSource(when (mapStyle) {
                    0 -> TileSourceFactory.MAPNIK
                    1 -> cyclOSM
                    else -> mapbox
                })
                closeMenu()
            }
        }
        binding.menuLayer.setOnLongClickListener {
            Snackbar.make(binding.root,R.string.CHOOSE_LAYER,Snackbar.LENGTH_SHORT).show()
            true
        }
        binding.menuCenterSonde.setOnClickListener {
            if (sondeId!=null) binding.map.controller?.setCenter(mkSonde?.position)
            else Snackbar.make(binding.root,R.string.NO_SONDE_TO_SHOW,Snackbar.LENGTH_SHORT).show()
            closeMenu()
        }
        binding.menuCenterSonde.setOnLongClickListener {
            Snackbar.make(binding.root,R.string.CENTER_SONDE_ON_MAP,Snackbar.LENGTH_SHORT).show()
            true
        }
        binding.menuMaps.setOnClickListener {
            if (sondeId!=null) navigate(mkSonde?.position!!)
            else Snackbar.make(binding.root,R.string.NO_SONDE_TO_NAVIGATE_TO,Snackbar.LENGTH_SHORT)
                .show()

            closeMenu()
        }
        binding.menuMaps.setOnLongClickListener {
            navigateGeneric(mkSonde?.position!!)
            true
        }
        binding.menuOpen.setOnClickListener {
            if (!expandedMenu) openMenu()
            else closeMenu()
        }
        binding.menuOpen.setOnLongClickListener {
            Snackbar.make(binding.root,
                "Trova la sonda v. ${BuildConfig.VERSION_NAME}",
                Snackbar.LENGTH_LONG).show()
            //////////////////////////
            if (Debug.isDebuggerConnected()) {
                //showcase(Instant.now().toString())
                getSharedPreferences(BuildConfig.APPLICATION_ID,MODE_PRIVATE).edit {
                    putLong(LAST_TIME_DONATION_SHOWN,Instant.now().epochSecond-8*3600*24)
                    commit()
                }
                //startActivity(Intent(this,DonationActivity::class.java))
            }
            true
        }
        binding.menuHelp.setOnClickListener {
            startActivity(Intent(this,ScrollingActivity::class.java))
            closeMenu()
        }

        Configuration.getInstance().userAgentValue=applicationContext.packageName
        binding.map.run {
            overlays?.add(MapEventsOverlay(object:MapEventsReceiver {
                override fun longPressHelper(p:GeoPoint):Boolean {
                    closeMenu()
                    return false
                }

                override fun singleTapConfirmedHelper(p:GeoPoint):Boolean {
                    closeMenu()
                    return false
                }
            }))

            addOnFirstLayoutListener {_:View,_:Int,_:Int,_:Int,_:Int ->
                isTilesScaledToDpi=true
                maxZoomLevel=20.0
                controller?.setZoom(15.0)
                zoomController?.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
                controller?.setCenter(GeoPoint(45.5,7.1))

                setTileSource(TileSourceFactory.MAPNIK)
                setMultiTouchControls(true)

                val dm:DisplayMetrics=applicationContext.resources.displayMetrics
                val scaleBar=ScaleBarOverlay(binding.map)
                scaleBar.setScaleBarOffset(dm.widthPixels/2,10)

                val bmp=BitmapFactory.decodeResource(resources,R.drawable.ic_person_red)
                locationOverlay=object:
                    MyLocationNewOverlay(GpsMyLocationProvider(applicationContext),binding.map) {
                    override fun onLocationChanged(location:Location,source:IMyLocationProvider?) {
                        super.onLocationChanged(location,source)
                        onLocationChanged(location)
                    }
                }.apply {
                    setPersonIcon(bmp)
                    setDirectionIcon(bmp)
                    setDirectionAnchor(.5f,.5f)
                    isDrawAccuracyEnabled=true
                    enableMyLocation()
                    runOnFirstFix {
                        runOnUiThread {
                            binding.map.controller?.animateTo(GeoPoint(lastFix))
                        }
                    }
                }
                sondeLevelListDrawable.apply {
                    addLevel(0,
                        0,
                        AppCompatResources.getDrawable(applicationContext,R.drawable.ic_sonde_red))
                    addLevel(1,
                        1,
                        AppCompatResources.getDrawable(applicationContext,
                            R.drawable.ic_sonde_green))
                    level=0
                }
                mkSonde=MyMarker(binding.map).apply {
                    icon=sondeLevelListDrawable
                    setAnchor(Marker.ANCHOR_CENTER,Marker.ANCHOR_CENTER)
                    setVisible(false)
                    setOnMarkerClickListener {marker,_ -> if (sondeId!=null) navigate(marker.position); true}
                    setOnLongPressListener {_,_ -> if (sondeId!=null) navigateGeneric(mkSonde!!.position); true}
                    if (sondePosition!=null) {
                        position=sondePosition
                        setVisible(true)
                    }
                }
                mkSondehub=MyMarker(binding.map).apply {
                    icon=AppCompatResources.getDrawable(applicationContext,R.drawable.ic_sonde_blue)
                    setAnchor(Marker.ANCHOR_CENTER,Marker.ANCHOR_CENTER)
                    setVisible(false)
                    setOnMarkerClickListener {marker,_ -> if (sondeId!=null) navigate(marker.position); true}
                    setOnLongPressListener {_,_ -> if (sondeId!=null) navigateGeneric(mkSondehub!!.position); true}
                }
                mkTarget=MyMarker(binding.map).apply {
                    icon=AppCompatResources.getDrawable(applicationContext,R.drawable.target)
                    setAnchor(Marker.ANCHOR_CENTER,Marker.ANCHOR_CENTER)
                    setVisible(false)
                    setOnMarkerClickListener {marker,_ -> navigate(marker.position); true}
                    setOnLongPressListener {_,_ -> navigateGeneric(mkTarget!!.position); true}
                }
                mkBurst=Marker(binding.map).apply {
                    title="Burst"
                    icon=AppCompatResources.getDrawable(applicationContext,R.drawable.ic_burst)
                    setAnchor(Marker.ANCHOR_CENTER,Marker.ANCHOR_CENTER)
                    setVisible(false)
                }

                path.outlinePaint.apply {
                    color=Color.rgb(0,0,255)
                    strokeCap=Paint.Cap.ROUND
                }
                sondePath.outlinePaint.apply {
                    color=Color.rgb(255,128,0)
                    strokeCap=Paint.Cap.ROUND
                }
                sondehubPath.outlinePaint.apply {
                    color=Color.rgb(0,255,255)
                    strokeCap=Paint.Cap.ROUND
                }
                sondeDirection.outlinePaint.color=Color.rgb(255,0,0)
                sondeDirection.isVisible=false
                trajectory.outlinePaint.color=Color.argb(128,255,128,0)
                trajectory.isVisible=false
                accuracyOverlay=Polygon1(binding.map).apply {
                    fillPaint.color=Color.argb(32,0,0,255)
                    outlinePaint.strokeWidth=2F
                    outlinePaint.color=Color.argb(128,0,0,255)
                }

                val copyrightOverlay=CopyrightOverlay(context).apply {
                    setAlignBottom(false)
                    setAlignRight(false)
                }

                overlays?.addAll(listOf(accuracyOverlay,
                    path,
                    sondePath,
                    sondehubPath,
                    sondeDirection,
                    scaleBar,
                    locationOverlay,
                    trajectory,
                    mkBurst,
                    mkTarget,
                    copyrightOverlay,
                    mkSondehub,
                    mkSonde,
                    MapEventsOverlay(this@FullscreenActivity)))
            }
        }
        handler.post(object:Runnable {
            override fun run() {
                if (timeLastSeen!=null && timeLastSeen?.until(Instant.now(),
                        ChronoUnit.SECONDS)!!>3L
                ) {
                    if (bk!=null) updateBk(Instant.now().until(bk,ChronoUnit.SECONDS).toInt())
                }

                if ((sondeId?.length
                        ?: 0)>0 && timeLastSeen!=null && timeLastSeen?.until(Instant.now(),
                        ChronoUnit.MINUTES)!!>=2 && (timeLastSondehub==null || timeLastSondehub?.until(
                        Instant.now(),
                        ChronoUnit.SECONDS)!!>=30)
                ) {
                    getFromSondeHub(sondeTypes!![sondeType-1],sondeId!!,timeLastSeen!!)
                    timeLastSondehub=Instant.now()
                }

                if (!otaRunning && timeLastMessage!=null && timeLastMessage?.until(Instant.now(),
                        ChronoUnit.SECONDS)!!>10L
                ) {
                    if (deviceInterface!=null) {
                        bluetoothManager?.closeDevice(btMacAddress!!)
                        onDisconnected()
                    }
                }
                handler.postDelayed(this,1000)
            }
        })
        registerReceiver(receiver,IntentFilter(BluetoothDevice.ACTION_FOUND))
        if (deviceInterface==null) connect()
        Handler(Looper.getMainLooper()).postDelayed({
            showcase("info")
        },2000)
    }

    private fun predict(lat:Double,lng:Double,alt:Double) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                //TODO: usare velocità verticale corrente in discesa sotto a una certa quota
                val tawhiri=Tawhiri(timeLastSeen ?: Instant.now(),
                    lat,
                    lng,
                    alt,
                    if (burst) alt+1 else max(alt+100,33000.0))
                mkTarget?.setVisible(false)
                mkBurst?.setVisible(false)
                trajectory.actualPoints.clear()
                trajectory.isVisible=false

                var lastPoint:GeoPoint?=null
                var lastTrajectoryPoint:TrajectoryPoint?=null
                tawhiri.getPrediction().onEach {
                    if (it.stage=="descent" && !burst) mkBurst?.apply {
                        position=lastPoint
                        val t=Instant.parse(lastTrajectoryPoint?.datetime)
                        val dtf=DateTimeFormatter.ofPattern("HH:mm")
                        title=LocalTime.from(t.atZone(ZoneId.systemDefault())).format(dtf)
                        setVisible(true)
                    }
                    it.trajectory.forEach {point ->
                        lastTrajectoryPoint=point
                        lastPoint=GeoPoint(point.latitude,point.longitude)
                        trajectory.addPoint(lastPoint)
                    }
                }
                trajectory.isVisible=true
                mkTarget?.position=trajectory.actualPoints[trajectory.actualPoints.size-1]

                //preload map cache
                /*val cacheManager=CacheManager(cyclOSM, binding.map.tileProvider.tileWriter,0,12)//binding.map)
                val start=trajectory.actualPoints[trajectory.actualPoints.size-1]
                //Go 10km NW and 10km SE
                val nw=start.destinationPoint(10000.0,-45.0)
                val se=start.destinationPoint(10000.0,135.0)
                val bb=BoundingBox(nw.latitude,nw.longitude,se.latitude,se.longitude)
                withContext(Dispatchers.Main) {
                    try {
                        cacheManager.downloadAreaAsyncNoUI(this@FullscreenActivity,bb,0,9, object: CacheManager.CacheManagerCallback {
                            override fun onTaskComplete() {
                                Log.i("MAURI","cache download completed")
                            }

                            override fun updateProgress(progress:Int,
                                                        currentZoomLevel:Int,
                                                        zoomMin:Int,
                                                        zoomMax:Int) {
                                Log.i("MAURI","downloading cache ($currentZoomLevel): $progress")
                            }

                            override fun downloadStarted() {
                                Log.i("MAURI","cache download started")
                            }

                            override fun setPossibleTilesInArea(total:Int) {
                                Log.i("MAURI","$total tiles to download")
                            }

                            override fun onTaskFailed(errors:Int) {
                                Log.e("MAURI","Cache download error $errors")
                            }

                        })
                    }
                    catch (ex:Exception) {
                        Log.e("MAURI",ex.toString())
                    }
                }*/

                mkTarget?.setVisible(true)

                val waypoints=ArrayList<GeoPoint>()
                waypoints.add(GeoPoint(currentLocation))
                val endPoint=trajectory.actualPoints[trajectory.actualPoints.size-1]
                waypoints.add(endPoint)

                (roadManager as OSRMRoadManager).setMean(OSRMRoadManager.MEAN_BY_CAR)
                val road=roadManager.getRoad(waypoints)
                if (road.mStatus!=Road.STATUS_OK) {
                    Log.e("MAURI","getRoad fallita")
                } else {
                    if (roadOverlay!=null) binding.map.overlays.remove(roadOverlay)
                    roadOverlay=RoadManager.buildRoadOverlay(road).apply {
                        outlinePaint.apply {
                            color=Color.rgb(0,0,192)
                            strokeWidth=10F
                            strokeCap=Paint.Cap.ROUND
                            pathEffect=DashPathEffect(floatArrayOf(10f,20f),0f)
                        }
                        val duration=road.mDuration.toDuration(DurationUnit.SECONDS)
                            .toComponents {hours,minutes,_,_ ->
                                hours.toDuration(DurationUnit.HOURS)+minutes.toDuration(DurationUnit.MINUTES)
                            }
                        title=applicationContext.getString(R.string.ROUTE_TO_PREDICTED_LANDING_SITE)
                        snippet=applicationContext.getString(R.string.BY_CAR)+duration.toString()
                        infoWindow=BasicInfoWindow(R.layout.bonuspack_bubble,binding.map)
                    }
                    binding.map.overlays.add(roadOverlay)
                    binding.map.invalidate()
                }
            } catch (e:Exception) {
                Log.e(TAG,e.toString())
                trajectory.isVisible=false
                mkTarget?.setVisible(false)
            }
        }
    }

    private fun navigate(position:GeoPoint) {
        val uri=Uri.parse(String.format(Locale.US,
            "google.navigation:q=%f,%f",
            position.latitude,
            position.longitude))
        val intent=Intent(Intent.ACTION_VIEW,uri)
        intent.setPackage("com.google.android.apps.maps")
        startActivity(intent)
    }

    private fun navigateGeneric(position:GeoPoint) {
        val uri=Uri.parse(String.format(Locale.US,"geo:%f,%f",position.latitude,position.longitude))
        val intent=Intent(Intent.ACTION_VIEW,uri)
        startActivity(intent)
    }

    override fun onResume() {
        super.onResume()
        binding.map.onResume()
    }

    override fun onPause() {
        super.onPause()
        binding.map.onPause()
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(receiver)
    }

    @SuppressLint("MissingSuperCall")
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (expandedMenu) closeMenu()
        else MaterialAlertDialogBuilder(this,R.style.MaterialAlertDialog_rounded).setIconAttribute(
            android.R.attr.alertDialogIcon).setTitle("TrovaLaSonda")
            .setMessage(R.string.ARE_YOU_SURE_YOU_WANT_TO_EXIT)
            .setPositiveButton(R.string.YES) {_,_ ->
                if (bluetoothManager!=null && btMacAddress!=null) bluetoothManager?.closeDevice(
                    btMacAddress!!)
                finish()
            }.setNegativeButton("No",null).show()
    }

    override fun onSaveInstanceState(outState:Bundle) {
        super.onSaveInstanceState(outState)
        Log.i(TAG,"onSaveInstanceState")
        outState.putAll(bundleOf(EXPANDED_MENU to expandedMenu,
            MAP_STYLE to mapStyle,
            CURRENT_LOCATION to currentLocation,
            SONDE_ID to sondeId,
            MUTE to mute,
            MUTE_CHANGE to muteChanged,
            BT_MAC_ADDRESS to btMacAddress,
            SONDE_TYPE to sondeType,
            HEIGHT_DELTA to heightDelta,
            HEIGHT to height,
            FREQ to freq,
            BK to bk,
            TIME_LAST_SEEN to timeLastSeen,
            TIME_LAST_MESSAGE to timeLastMessage,
            LAT to binding.lat.text,
            LON to binding.lon.text,
            DISTANCE to distance,
            UNITS to binding.unit.text,
            HORIZONTAL_SPEED to binding.horizontalSpeed.text,
            DIRECTION to binding.direction.text,
            REPORT_ALREADY_SHOWN to reportAlreadyShown,
            SONDE_POSITION to sondePosition))
    }

    @Suppress("DEPRECATION")
    private fun Bundle.getInstant(key:String)=get(key) as Instant?

    @Suppress("DEPRECATION")
    private fun Bundle.getLocation(key:String)=get(key) as Location?

    @Suppress("DEPRECATION")
    private fun Bundle.getPosition(key:String)=get(key) as GeoPoint?

    private fun normalizeSondeId():String=
        sondeId?.trim()?.replace("-","")?.ifEmpty {"????????"} ?: "[NO SONDE]"

    @SuppressLint("SetTextI18n")
    override fun onRestoreInstanceState(savedInstanceState:Bundle) {
        super.onRestoreInstanceState(savedInstanceState)
        Log.i(TAG,"onRestoreInstanceState")
        with(savedInstanceState) {
            try {
                //TODO: ristrutturare memorizzando modello, non UI
                expandedMenu=getBoolean(EXPANDED_MENU)
                mapStyle=getInt(MAP_STYLE)
                mute=getInt(MUTE)
                muteChanged=getBoolean(MUTE_CHANGE)
                sondeType=getInt(SONDE_TYPE)
                heightDelta=getDouble(HEIGHT_DELTA)
                height=getDouble(HEIGHT)
                freq=getDouble(FREQ)
                bk=getInstant(BK)
                timeLastSeen=getInstant(TIME_LAST_SEEN)
                timeLastMessage=getInstant(TIME_LAST_MESSAGE)
                currentLocation=getLocation(CURRENT_LOCATION)
                btMacAddress=getString(BT_MAC_ADDRESS)
                sondeId=getString(SONDE_ID)
                binding.lat.text=getString(LAT)
                binding.lon.text=getString(LON)
                distance=getDouble(DISTANCE)
                setDistance(distance)
                binding.unit.text=getString(UNITS)
                binding.horizontalSpeed.text=getString(HORIZONTAL_SPEED)
                binding.direction.text=getString(DIRECTION)
                reportAlreadyShown=getBoolean(REPORT_ALREADY_SHOWN)
                sondePosition=getPosition(SONDE_POSITION)
            } catch (ex:Exception) {
                Log.e(TAG,"eccezione in onRestoreInstanceState $ex")
            }
        }
        binding.id.text=normalizeSondeId()
        if (useImperialUnits()) {
            val h=height*meters
            binding.height.text=(h `in` feet).toString()
        } else binding.height.text=height.toString()
    }

    override fun singleTapConfirmedHelper(p:GeoPoint?):Boolean {
        InfoWindow.closeAllInfoWindowsOn(binding.map)
        closeMenu()
        return false
    }

    override fun longPressHelper(p:GeoPoint?):Boolean {
        InfoWindow.closeAllInfoWindowsOn(binding.map)
        closeMenu()
        return false
    }

    companion object {
        const val TAG="MAURI"
        private const val EXPANDED_MENU="expandedMenu"
        private const val MAP_STYLE="mapStyle"
        private const val MUTE="mute"
        private const val MUTE_CHANGE="muteChange"
        private const val SONDE_TYPE="sondeType"
        private const val HEIGHT_DELTA="heightDelta"
        private const val HEIGHT="height"
        private const val FREQ="freq"
        private const val BK="bk"
        private const val TIME_LAST_SEEN="timeLastSeen"
        private const val TIME_LAST_MESSAGE="timeLastMessage"
        private const val CURRENT_LOCATION="currentLocation"
        private const val BT_MAC_ADDRESS="btMacAddress"
        private const val SONDE_ID="sondeId"
        private const val LAT="lat"
        private const val LON="lon"
        private const val DISTANCE="distance"
        private const val UNITS="units"
        private const val HORIZONTAL_SPEED="horizontalSpeed"
        private const val DIRECTION="direction"
        private const val REPORT_ALREADY_SHOWN="reportAlreadyShown"
        private const val SONDE_POSITION="sondePosition"
        private const val REQUEST_PERMISSIONS_REQUEST_CODE=1
        private const val MYSONDYGOPREFIX="MySondyGO-"
        private const val TROVALASONDAPREFIX="TrovaLaSonda"
        private const val CIAPASONDEPREFIX="CiapaSonde"
        private const val LAST_TIME_DONATION_SHOWN="lastTimeDonationShown"
        private var freqOffsetReceiver:FreqOffsetReceiver?=null
        fun registerFreqOffsetReceiver(r:FreqOffsetReceiver) {
            freqOffsetReceiver=r
        }

        fun unregisterFreqOffsetReceiver() {
            freqOffsetReceiver=null
        }
    }
}

class MyMarker(mapView:MapView?):Marker(mapView) {
    fun interface OnLongPressListener {
        fun invoke(event:MotionEvent?,mapView:MapView?):Boolean
    }

    private var longPress:OnLongPressListener?=null
    fun setOnLongPressListener(longPress:OnLongPressListener) {
        this.longPress=longPress
    }

    override fun onLongPress(event:MotionEvent?,mapView:MapView?):Boolean {
        if (hitTest(event,mapView)) longPress?.invoke(event,mapView)
        return super.onLongPress(event,mapView)
    }
}

class MultipleViewsTarget(views:List<View>):ViewTarget(views.first()) {
    private var boundingBox=views.fold(Rect()) {result,element ->
        val l=IntArray(2)
        element.getLocationInWindow(l)
        val r=Rect(l[0],l[1],l[0]+element.measuredWidth,l[1]+element.measuredHeight)
        result.plus(r)
    }

    override fun getPoint():Point=Point(boundingBox.centerX(),boundingBox.centerY())

    override fun getBounds():Rect=boundingBox
}

/*class GeoPointTarget(private val map:MapView,private val geoPoint:GeoPoint) : uk.co.deanwild.materialshowcaseview.target.Target {
    override fun getPoint():Point {
        val pt=Point()
        val origin=IntArray(2)
        map.projection.toPixels(geoPoint,pt)
        map.getLocationInWindow(origin)
        return Point(pt.x+origin[0],pt.y+origin[1])
    }

    override fun getBounds():Rect {
        val size=200
        return Rect(point.x-size/2,point.y-size/2,point.x+size/2,point.y+size/2)
    }
}*/

