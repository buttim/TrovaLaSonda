package eu.ydiaeresis.trovalasonda

import android.Manifest
import android.animation.LayoutTransition
import android.annotation.SuppressLint
import android.app.Activity
import android.app.ActivityManager
import android.bluetooth.BluetoothAdapter
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
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
import androidx.core.animation.doOnEnd
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.core.graphics.plus
import androidx.core.os.bundleOf
import androidx.core.view.children
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import eu.ydiaeresis.trovalasonda.databinding.ActivityFullscreenBinding
import io.nacular.measured.units.Length.Companion.feet
import io.nacular.measured.units.Length.Companion.kilometers
import io.nacular.measured.units.Length.Companion.meters
import io.nacular.measured.units.Length.Companion.miles
import io.nacular.measured.units.times
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
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
import java.util.Timer
import java.util.TimerTask
import java.util.UUID
import kotlin.math.abs
import kotlin.math.max
import kotlin.system.exitProcess
import kotlin.time.DurationUnit
import kotlin.time.toDuration
import org.osmdroid.views.overlay.Polygon as Polygon1

fun MaterialShowcaseSequence.addSequenceItem(
    ctx:Context,
    targetView:View,
    title:Int,
    content:Int,
    dismissText:Int,
) {
    addSequenceItem(targetView,
        ctx.getString(title),
        ctx.getString(content),
        ctx.getString(dismissText))
}

class FullscreenActivity:AppCompatActivity(),LocationListener,MapEventsReceiver,
    ReceiverBuilderCallback,ReceiverCallback {
    private var version=""
    private var reportAlreadyShown=false
    private var distance=999999.9
    private lateinit var binding:ActivityFullscreenBinding
    private var receiver:Receiver?=null
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
    private var sondeLat:Double?=null
    private var sondeLon:Double?=null
    private var sondeAlt:Double?=null
    private var mapStyle=0
    private var mute=-1
    private var muteChanged=true
    private var sondeId:String?=null
    private var sondeType=-1
    private var heightDelta=0.0
    private var freq=0F
    private var bk:Instant?=null
    private var timeLastSeen:Instant?=null
    private var timeLastSondehub:Instant?=null
    private var timeLastMessage:Instant?=null
    private val sondeLevelListDrawable=LevelListDrawable()
    private val handler=Handler(Looper.getMainLooper())
    private var burst=false
    private var batteryLevel:Int?=null
    private val mapbox=MapBoxTileSource()
    private var versionDB:VersionDB?=null
    private var versionChecked=false
    private var isRdzTrovaLaSonda=false
    private var isCiapaSonde=false
    private var otaRunning=false
    private var roadManager:RoadManager=OSRMRoadManager(this,BuildConfig.APPLICATION_ID)
    private var roadOverlay:Polyline?=null
    private var lastConnectionChoice=0
    private var huntingMode=false
    private val cyclOSM=XYTileSource("CyclOSM",
        0,
        18,
        256,
        ".png",
        arrayOf("https://a.tile-cyclosm.openstreetmap.fr/cyclosm/"))

    private val resultLauncher=
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {result ->
            if (result.resultCode!=Activity.RESULT_OK) return@registerForActivityResult
            val data:Intent?=result.data
            var reset=false
            val settings=mutableListOf<Pair<String,Any>>()
            val resetCmds=listOf(TTGO.LCD,TTGO.OLED_SCL,TTGO.OLED_RST,TTGO.LED_POUT,TTGO.OLED_SDA,
                TTGO.BUZ_PIN,TTGO.BATTERY)
            if (data!=null && data.extras!=null) {
                for (k in data.extras?.keySet()!!) {
                    reset=resetCmds.indexOf(k)>=0
                    if (k==TTGO.MYCALL) settings.add(Pair<String,Any>("myCall",
                        data.extras?.getString(k)!!))
                    else settings.add(Pair<String,Any>(k,data.extras?.getInt(k)!!))
                }
                if (reset) MaterialAlertDialogBuilder(this,
                    R.style.MaterialAlertDialog_rounded).setTitle(R.string.ALERT)
                    .setMessage(R.string.NEW_SETTINGS_REQUIRE_A_RESTART)
                    .setNegativeButton(R.string.CANCEL) {dialog,_ -> dialog.dismiss()}
                    .setPositiveButton("OK") {dialog,_ ->
                        dialog.dismiss()
                        receiver?.sendSettings(settings)
                        showProgress(true)
                    }.show()
                else receiver?.sendSettings(settings)
            }
        }

    private fun addShowcaseItem(seq:MaterialShowcaseSequence,view:View,title:Int,content:Int) : MaterialShowcaseView{
        val mcsv=MaterialShowcaseView.Builder(this@FullscreenActivity)
            .setTarget(view)
            .setTitleText(applicationContext.getString(title))
            .setSkipText(getString(R.string.skip_tutorial))
            .setContentText(applicationContext.getString(content))
            .setDismissText(R.string.GOT_IT).build()
        seq.addSequenceItem(mcsv)
        return mcsv
    }

    @Suppress("SameParameterValue")
    private fun showcase(id:String) : Boolean {
        var res=false
        MaterialShowcaseSequence(this,id).apply {
            var timer:Timer?=null
            setConfig(ShowcaseConfig().apply {
                delay=500
                dismissTextColor=Color.GREEN
            })
            res=hasFired()

            addShowcaseItem(this,binding.type,R.string.SONDE_DATA,R.string.TAP_HERE_TO_CHANGE_SONDE_TYPE_FREQUENCY)
                .setTarget(MultipleViewsTarget(listOf(binding.verticalSpeed,
                    binding.horizontalSpeed,
                    binding.height,
                    binding.type)))
            addShowcaseItem(this,binding.id,R.string.SERIAL_NUMBER_FOR_THE_SONDE_YOU_ARE_RECEIVING,R.string.YOU_CAN_TAP_IT_TO_OPEN_SONDEHUB_OR_RADIOSONDY)
            addShowcaseItem(this,binding.distance,R.string.DISTANCE,R.string.BETWEEN_YOU_AND_THE_SONDE_YOU_ARE_RECEIVING)
            addShowcaseItem(this,binding.buzzer,R.string.BUZZER,R.string.MUTE_YOUR_RECEIVER_TAPPING_THIS)
            addShowcaseItem(this,binding.batteryMeter,R.string.BATTERY,R.string.KEEP_AN_EYE_ON_YOUR_RECEIVER_BATTERY_LEVEL)
            addShowcaseItem(this,binding.menuLayer,R.string.MAP_LAYERS,R.string.CHOOSE_BETWEEN_THREE_DIFFERENT_MAP_LAYERS)
            addShowcaseItem(this,binding.menuMaps,R.string.NAVIGATION,R.string.LAUNCH_GOOGLE_MAPS)
            addShowcaseItem(this,binding.menuSettings,R.string.RECEIVER_PARAMETERS,R.string.SET_PINS_BANDWIDTH_AND_CALIBRATION_FOR_YOUR_RECEIVER)
            setOnItemShownListener {_,i ->
                timer?.cancel()
                if (i==5) openMenu()
            }
            setOnItemDismissedListener {_,i ->
                timer=Timer().apply {
                    schedule(object:TimerTask() {
                        override fun run() {
                            handler.post {askForScanning(true)}
                        }

                    },2000)
                }
                Log.i(TAG,"showcase OnItemDismissed $i")
                if (i==7) closeMenu()
            }
            start()
        }
        return res
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
        if (sondeLat!=null && sondeLon!=null && sondeLat!=0.0 && sondeLon!=0.0) {
            distance=GeoPoint(currentLocation).distanceToAsDouble(GeoPoint(sondeLat!!,sondeLon!!))
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

    private val turnOnBTContract=registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {result ->
        if (result.resultCode!=Activity.RESULT_OK) finish()
        else handler.postDelayed({
            askForScanning()
        },2000)
    }

    private fun createReceiver(builder:ReceiverBuilder) {
        try {
            builder.connect()
        }
        catch (ex:BluetoothNotEnabledException) {
            val intent=Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            turnOnBTContract.launch(intent)
        }
        catch (ex1:ReceiverException) {
            Log.e(TAG,"Impossibile connettersi al ricevitore: $ex1")
        }
    }

    private fun onConnectedCommon() {
        Snackbar.make(binding.root,
            applicationContext.getString(R.string.CONNECTED_TO)+" "+receiver?.name,
            Snackbar.LENGTH_LONG).show()
        isCiapaSonde=receiver?.name?.startsWith(CIAPASONDEPREFIX)?:false
        isRdzTrovaLaSonda=receiver?.name?.startsWith(TROVALASONDAPREFIX)?:false
        timeLastMessage=null
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

        maybeShowDonation()
    }

    private fun onDisconnectedCommon() {
        Log.i(TAG,"----------- disconnected")
        receiver=null
        playSound(R.raw._541506__se2001__cartoon_quick_zip_reverse)
        val bmp=BitmapFactory.decodeResource(resources,R.drawable.ic_person_red)
        locationOverlay?.setPersonIcon(bmp)
        locationOverlay?.setDirectionIcon(bmp)
        locationOverlay?.setDirectionAnchor(.5f,.5f)
        sondeLevelListDrawable.level=0
        muteChanged=true
        showProgress(false)
        Snackbar.make(binding.root,R.string.CONNECTION_LOST,Snackbar.LENGTH_LONG).show()
        handler.postDelayed({
            askForScanning()//connect()
        },1000)
        binding.batteryMeter.chargeLevel=null
        versionChecked=false
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
        nPositionsReceived=0
        mkSondehub?.setVisible(false)
        sondehubPath.actualPoints.clear()
        sondehubPath.isVisible=false
        sondePath.actualPoints.clear()
        mkBurst?.setVisible(false)
        trajectory.actualPoints.clear()
        trajectory.isVisible=false
        mkTarget?.setVisible(false)
        timeLastSeen=Instant.now()
        reportAlreadyShown=false
    }

    private fun updateSondeLocation(id:String?,lat:Double?,lon:Double?,alt:Double?) {
        if (id!=null && sondeId!=id) {
            newSonde(id)
            if (currentLocation!=null && lat!=null && lon!=null && lat!=0.0 && lon!=0.0) {
                binding.map.zoomToBoundingBox(BoundingBox.fromGeoPointsSafe(mutableListOf(
                    GeoPoint(lat,lon),
                    GeoPoint(currentLocation))).increaseByScale(1.9F),false,50)
                binding.map.invalidate()
            } else
                if (lat!=null && lon!=null && lat!=0.0 && lon!=0.0) {
                    mkSonde?.position=GeoPoint(lat,lon,alt?:0.0)
                    binding.map.controller?.setCenter(mkSonde?.position)
                }

            playSound()
        }

        val deltaAlt = if (alt!=null && sondeAlt!=null) alt-sondeAlt!! else null
        if (lat!=null) sondeLat=lat
        if (lon!=null) sondeLon=lon
        if (alt!=null) sondeAlt=alt

        if (lat==null || lon==null || sondeLat==null || sondeLon==null || sondeLat!! == 0.0 || sondeLon!! == 0.0)
            return

        mkSonde?.setVisible(true)
        mkSonde?.position=GeoPoint(lat,lon,alt?:0.0)
        sondePath.addPoint(mkSonde?.position)
        sondeLevelListDrawable.level=1

        val d=
            if (currentLocation!=null) GeoPoint(currentLocation).distanceToAsDouble(GeoPoint(lat,lon)) else 0.0
        if (d>1000000.0) return

        binding.lat.text=String.format(Locale.US," %.5f",lat)
        binding.lon.text=String.format(Locale.US," %.5f",lon)

        if (currentLocation!=null) {
            setDistance(d)
            updateSondeDirection()
        }
        if (alt==null) return
        if (timeLastSeen!=null) {
            val delta=Instant.now().epochSecond-timeLastSeen!!.epochSecond
            if (delta!=0L && deltaAlt!=null && deltaAlt!=0.0) {
                val verticalSpeed=deltaAlt/delta
                val vs=verticalSpeed*meters
                binding.verticalSpeed.text=
                    if (useImperialUnits())
                        String.format(Locale.US,"Vs: %.1fft/s",vs `in` feet)
                    else
                        String.format(Locale.US,"Vs: %.1fm/s",verticalSpeed)
            }
        }
        if (deltaAlt!=0.0)
            timeLastSeen=Instant.now()
        if (nPositionsReceived>10 && (lastPrediction==null || lastPrediction?.until(Instant.now(),
                ChronoUnit.SECONDS)!!>60)
        ) {
            lastPrediction=Instant.now()
            predict(lat,lon,alt)
        }
        if (useImperialUnits()) {
            val h=String.format(Locale.US,"%.1f",alt*meters `in` feet)
            binding.height.text="H: ${h}ft"
        } else binding.height.text="H: ${alt.toInt()}m"
        if (sondeAlt!=null && deltaAlt!=null && deltaAlt!=0.0) {
            binding.direction.text=
                if (abs(deltaAlt)<2) "=" else if (deltaAlt>0.0) "▲" else "▼"
            if (!burst && heightDelta>0 && deltaAlt<0) {
                burst=true
                mkBurst?.apply {
                    position=GeoPoint(lat,lon)
                    setVisible(true)
                    val dtf=DateTimeFormatter.ofPattern("HH:mm")
                    title=LocalTime.from(Instant.now().atZone(ZoneId.systemDefault())).format(dtf)
                }
                playSound(R.raw._541192__eminyildirim__balloon_explosion_pop)
            }
            heightDelta=deltaAlt
        }
}

    private fun useImperialUnits():Boolean {
        if (Build.VERSION.SDK_INT<Build.VERSION_CODES.P) return false
        val ms=LocaleData.getMeasurementSystem(ULocale.getDefault())
        return ms.equals(LocaleData.MeasurementSystem.US)
    }

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
            panel.requestLayout()
        }
    }

    private fun updateSondeDirection() {
        if (currentLocation==null || sondeId==null) return
        sondeDirection.apply {
            actualPoints.clear()
            addPoint(GeoPoint(currentLocation))
            addPoint(mkSonde?.position)
            isVisible=sondeLat!=null && sondeLon!=null && sondeLat!=0.0 && sondeLon!=0.0
        }
    }

    private fun updateTypeAndFreq(type:Int,freq:Float) {
        sondeType=type
        this.freq=freq
        binding.type.text="%s %.3f".format(Locale.US,SondeType.fromInt(sondeType).name,freq)
    }

    private fun updateBk(bk:Int) {
        binding.bk.apply {
            visibility=View.VISIBLE
            text=String.format("BK %d:%02d:%02d",bk/3600,(bk/60)%60,bk%60)
        }
        this.bk=Instant.now().plusSeconds(bk.toLong())
    }

    private fun updateRSSI(rssi:Float) {
        binding.dbm.text="-%.1fdBm".format(Locale.US,rssi)
        binding.rssi.progress=(binding.rssi.max-rssi).toInt()
        if (timeLastSeen!=null && Instant.now().toEpochMilli()-timeLastSeen!!.toEpochMilli()>8000 &&
                sondeLat!=null && sondeLon!=null && !reportAlreadyShown && distance<30)
            showReport()
    }

    private fun updateBattery(percent:Int,mV:Int) {
        batteryLevel=mV
        binding.batteryMeter.chargeLevel=percent
    }

    private fun showReport() {
        if (sondeLat==null || sondeLon==null) return
        reportAlreadyShown=true
        WebPageChooserDialog().showForRecovery(supportFragmentManager,sondeId!!,sondeLat!!, sondeLon!!,sondeAlt!!)
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

    override fun onRequestPermissionsResult(
        requestCode:Int,
        permissions:Array<String>,
        grantResults:IntArray,
    ) {
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

    private fun receiverNotConnectedWarning() {
        Snackbar.make(binding.root,R.string.RECEIVER_NOT_CONNECTED,Snackbar.LENGTH_LONG)
            .setAction("connect") { askForScanning(true) }
            .show()
    }

    private fun toggleBuzzer() {
        receiver?.setMute(mute==0)
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
                Log.e(TAG,"Eccezione playsound: $e")
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

        lastConnectionChoice=getSharedPreferences(BuildConfig.APPLICATION_ID,MODE_PRIVATE).getInt(LAST_CONNECTION_CHOICE,0)
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
            versionDB=FirmwareUpdater().getVersion()
            if (versionDB!=null)
                receiver?.requestVersion()
            else runOnUiThread {
                Snackbar.make(binding.root,"Cannot access remote firmware updates database",Snackbar.LENGTH_SHORT).show()
            }
        }

        mapbox.retrieveAccessToken(this)
        mapbox.retrieveMapBoxMapId(this)
        TileSourceFactory.addTileSource(mapbox)

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

        if (useImperialUnits()) with(binding) {
            unit.text="mi"
            height.text="H: -ft"
            horizontalSpeed.text="V: -mph"
            verticalSpeed.text="Vs: -ft/s"
        }

        binding.buzzer.setOnClickListener {toggleBuzzer()}
        binding.batteryMeter.setOnClickListener {
            if (receiver==null) receiverNotConnectedWarning()
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
            if (sondeId!=null) {
                if (sondeLat==null || sondeLon==null)
                    WebPageChooserDialog().showForInfo(supportFragmentManager,sondeId!!,currentLocation!!.latitude,currentLocation!!.longitude)
                else
                    WebPageChooserDialog().showForInfo(supportFragmentManager,sondeId!!,sondeLat!!,sondeLon!!)
            } else Snackbar.make(binding.root,
                R.string.NO_SONDE_TO_OPEN_A_WEBPAGE_FOR,
                Snackbar.LENGTH_SHORT).show()
        }
        binding.id.setOnLongClickListener {
            Snackbar.make(binding.root,R.string.SHOW_A_WEB_PAGE,Snackbar.LENGTH_SHORT).show()
            true
        }
        binding.subpanel.setOnClickListener {
            if (receiver==null) {
                receiverNotConnectedWarning()
                return@setOnClickListener
            }
            SondeTypeDialog().apply {
                freq=this@FullscreenActivity.freq
                type=sondeType
                isCiapaSonde=this@FullscreenActivity.isCiapaSonde
                dialogCloseListener=object:DialogCloseListener {
                    override fun handleDialogClose() {
                        receiver?.setTypeAndFrequency(type,freq)
                    }
                }
                show(supportFragmentManager,"")
            }
        }
        binding.apply {
            distance.setOnClickListener {
                val lp=subpanel.layoutParams
                var idMsg=R.string.hunting_mode_off
                if (huntingMode) {
                    huntingMode=false
                    lp.width=0
                } else {
                    huntingMode=true
                    lp.width=1
                    idMsg=R.string.hunting_mode_on
                }
                panel.layoutTransition?.apply {
                    enableTransitionType(LayoutTransition.CHANGING)
                    setDuration(1000)
                    getAnimator(LayoutTransition.CHANGING).doOnEnd {
                        Snackbar.make(root,getString(idMsg),Snackbar.LENGTH_SHORT).show()
                    }
                }
                subpanel.layoutParams=lp
                panel.requestLayout()
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
//                val msgs=
//                    arrayOf("1/RS41/402.800/T1840263/41.20888/5.82557/6060.9/93.1/127.5/53/0/1/28040/3643/0/0/0/0/2.30/o",
//                        "1/RS41/402.800/T1840263/45.20888/8.82567/6060.9/93.1/127.5/15/0/1/28039/3643/0/0/0/0/2.30/o",
//                        "1/RS41/402.800/T1840263/45.20888/8.82577/6040.9/93.1/127.5/99/0/1/28038/3643/0/0/0/0/2.30/o",
//                        "1/RS41/402.800/T1840263/45.20898/8.82567/6030.9/93.1/127.5/1/0/1/28037/3643/0/0/0/0/2.30/o",
//                        "1/RS41/402.800/T1840263/45.20898/8.82567/6030.9/93.1/127.5/1/0/1/28037/3643/0/0/0/0/2.30/o",
//                        "1/RS41/402.800/T1840263/45.20898/8.82567/6030.9/93.1/127.5/1/0/1/28037/3643/0/0/0/0/2.30/o",
//                        "1/RS41/402.800/T1840263/45.20898/8.82567/6030.9/93.1/127.5/1/0/1/28037/3643/0/0/0/0/2.30/o",
//                        "1/RS41/402.800/T1840263/45.20898/8.82567/6030.9/93.1/127.5/1/0/1/28037/3643/0/0/0/0/2.30/o",
//                        "1/RS41/402.800/T1840263/45.20898/8.82567/6030.9/93.1/127.5/1/0/1/28037/3643/0/0/0/0/2.30/o",
//                        "1/RS41/402.800/T1840263/45.20898/8.82567/6030.9/93.1/127.5/1/0/1/28037/3643/0/0/0/0/2.30/o",
//                        "1/RS41/402.800/T1840263/45.20898/8.82567/6030.9/93.1/127.5/1/0/1/28037/3643/0/0/0/0/2.30/o",
//                        "1/RS41/402.800/T1840263/45.20898/8.82567/6030.9/93.1/127.5/1/0/1/28037/3643/0/0/0/0/2.30/o",
//                        "1/RS41/402.800/T1840263/45.20898/8.82567/6030.9/93.1/127.5/1/0/1/28037/3643/0/0/0/0/2.30/o",
//                        "1/RS41/402.800/T1840263/45.20898/8.82567/6030.9/93.1/127.5/1/0/1/28037/3643/0/0/0/0/2.30/o")
//                n++
//                n%=msgs.size
//                process(msgs[n])
            }
            //////////////////////////////////////////////////////////////////////////////////
            closeMenu()
        }
        binding.menuCenter.setOnLongClickListener {
            Snackbar.make(binding.root,R.string.center_user_in_map,Snackbar.LENGTH_SHORT).show()
            true
        }
        binding.menuSettings.setOnClickListener {
            if (receiver==null)
                receiverNotConnectedWarning()
            else {
                if (receiver?.requestSettings()==true)
                    showProgress(true)
                else
                    Snackbar.make(binding.root,
                        getString(R.string.settings_not_available_for_this_receiver),Snackbar.LENGTH_LONG).show()
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
                //val res=showcase(Instant.now().toString())////////////////////////////////
                getSharedPreferences(BuildConfig.APPLICATION_ID,MODE_PRIVATE).edit {
                    putLong(LAST_TIME_DONATION_SHOWN,Instant.now().epochSecond-8*3600*24)
                    commit()
                }
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
                    if (sondeLat!=null && sondeLon!=null) {
                        position=GeoPoint(sondeLat!!,sondeLon!!)
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
                    getFromSondeHub(SondeType.fromInt(sondeType).name,sondeId!!,timeLastSeen!!)
                    timeLastSondehub=Instant.now()
                }

                handler.postDelayed(this,1000)
            }
        })

        handler.postDelayed({
            whatsnew(this) {
                val hasAlreadyFired=showcase("info")
                if (hasAlreadyFired)
                    askForScanning(true)
            }
        },2000)

        ////////////////////////////////////////////////
//        binding.apply {
//            height.text="H: 34715m"
//            verticalSpeed.text="V: 415.8km/h"
//            horizontalSpeed.text="Vs: 4.8m/s"
//            direction.text="▲"
//            distance.text="97.2"
//            id.text="W1234567"
//            type.text="RS41 403.700MHz"
//        }
        ////////////////////////////////////////////////
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
                                Log.i(TAG,"cache download completed")
                            }

                            override fun updateProgress(progress:Int,
                                                        currentZoomLevel:Int,
                                                        zoomMin:Int,
                                                        zoomMax:Int) {
                                Log.i(TAG,"downloading cache ($currentZoomLevel): $progress")
                            }

                            override fun downloadStarted() {
                                Log.i(TAG,"cache download started")
                            }

                            override fun setPossibleTilesInArea(total:Int) {
                                Log.i(TAG,"$total tiles to download")
                            }

                            override fun onTaskFailed(errors:Int) {
                                Log.e(TAG,"Cache download error $errors")
                            }

                        })
                    }
                    catch (ex:Exception) {
                        Log.e(TAG,ex.toString())
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
                    Log.e(TAG,"getRoad fallita")
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
                Log.e(TAG,"Eccezione predict: $e")
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

    @SuppressLint("MissingSuperCall")
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (expandedMenu) closeMenu()
        else MaterialAlertDialogBuilder(this,R.style.MaterialAlertDialog_rounded).setIconAttribute(
            android.R.attr.alertDialogIcon).setTitle("TrovaLaSonda")
            .setMessage(R.string.ARE_YOU_SURE_YOU_WANT_TO_EXIT)
            .setPositiveButton(R.string.YES) {_,_ ->
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
            SONDE_TYPE to sondeType,
            HEIGHT_DELTA to heightDelta,
            HEIGHT to sondeAlt,
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
            SONDE_LAT to sondeLat,
            SONDE_LON to sondeLon))
    }

    @Suppress("DEPRECATION")
    private fun Bundle.getInstant(key:String)=get(key) as Instant?

    @Suppress("DEPRECATION")
    private fun Bundle.getLocation(key:String)=get(key) as Location?

    private fun normalizeSondeId():String=
        sondeId?.trim()?.replace("-","")?.ifEmpty {"????????"} ?: "[NO SONDE]"

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
                sondeAlt=getDouble(HEIGHT)
                freq=getFloat(FREQ)
                bk=getInstant(BK)
                timeLastSeen=getInstant(TIME_LAST_SEEN)
                timeLastMessage=getInstant(TIME_LAST_MESSAGE)
                currentLocation=getLocation(CURRENT_LOCATION)
                sondeId=getString(SONDE_ID)
                binding.lat.text=getString(LAT)
                binding.lon.text=getString(LON)
                distance=getDouble(DISTANCE)
                setDistance(distance)
                binding.unit.text=getString(UNITS)
                binding.horizontalSpeed.text=getString(HORIZONTAL_SPEED)
                binding.direction.text=getString(DIRECTION)
                reportAlreadyShown=getBoolean(REPORT_ALREADY_SHOWN)
                sondeLat=getDouble(SONDE_LAT)
                sondeLon=getDouble(SONDE_LON)
            } catch (ex:Exception) {
                Log.e(TAG,"eccezione in onRestoreInstanceState $ex")
            }
        }
        binding.id.text=normalizeSondeId()
        if (sondeAlt!=null)
            if (useImperialUnits()) {
                val h=sondeAlt!!*meters
                binding.height.text=(h `in` feet).toString()
            } else binding.height.text=sondeAlt.toString()
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

    private var rec:Receiver?=null
    private fun askForScanning(firstTime:Boolean=false) {
        val prefs=getSharedPreferences(BuildConfig.APPLICATION_ID,MODE_PRIVATE)
        var choice:Int=lastConnectionChoice

        MaterialAlertDialogBuilder(this,R.style.MaterialAlertDialog_rounded)
            .setCancelable(false)
            .setTitle(if (firstTime) getString(R.string.chose_connection) else getString(R.string.no_receiver_found))
            .setSingleChoiceItems(arrayOf<CharSequence>(getString(R.string.bluetooth_classic_ttgo),
                getString(R.string.bluetooth_low_energy_heltec),getString(R.string.no_receiver)),lastConnectionChoice) {_,x -> choice=x}
            .setPositiveButton("OK") {_,_ ->
                if (choice<2) lastConnectionChoice=choice
                prefs.edit {
                    putInt(LAST_CONNECTION_CHOICE,lastConnectionChoice)
                    commit()
                }
                when (choice) {
                    0 -> createReceiver(BTReceiverBuilder(this,this,applicationContext,this@FullscreenActivity))
                    1 -> createReceiver(BLEReceiverBuilder(this,this,applicationContext,this@FullscreenActivity))
                }
            }.setNegativeButton(getString(R.string.exit)) {_,_ ->
                exitProcess(-1)
            }.show()
    }

    override fun onReceiverConnected(receiver:Receiver,builder:ReceiverBuilder) {
        this.receiver=receiver
        runOnUiThread {
            onConnectedCommon()
        }
        builder.dispose()
    }

    override fun onTimeout() {
        runOnUiThread {askForScanning(true)}
    }

    override fun onDisconnected() {
        runOnUiThread {onDisconnectedCommon()}
    }

    override fun onBattery(mv:Int,percentage:Int) {
        runOnUiThread {updateBattery(percentage,mv)}
    }

    override fun onMute(mute:Boolean) {
        this.mute=if (mute) 1 else 0
        runOnUiThread {updateMute(this.mute)}
    }

    override fun onTypeAndFreq(type:Int,freq:Float) {
        runOnUiThread {updateTypeAndFreq(type,freq)}
    }

    override fun onRSSI(rssi:Float) {
        runOnUiThread {updateRSSI(rssi)}
    }

    override fun onSerial(serial:String) {
        runOnUiThread {updateSondeLocation(serial,sondeLat,sondeLon,sondeAlt)}
    }

    override fun onLatitude(lat:Double) {
        sondeLat=lat
    }

    override fun onLongitude(lon:Double) {
        nPositionsReceived++
        runOnUiThread {
            updateSondeLocation(sondeId,sondeLat,lon,sondeAlt)
            mkSondehub?.setVisible(false)
            sondehubPath.isVisible=false
        }
    }

    override fun onAltitude(alt:Double) {
        runOnUiThread {updateSondeLocation(sondeId,sondeLat,sondeLon,alt)}
    }

    override fun onVelocity(vel:Float) {
        val v=vel*kilometers
        runOnUiThread {
            binding.horizontalSpeed.text=
                if (useImperialUnits())
                    String.format(Locale.US,"V: %.1fmph",v `in` miles)
                else
                    String.format(Locale.US,"V: %.1fkm/h",vel)
        }
    }

    override fun onAFC(afc:Int) {
        freqOffsetReceiver?.freqOffset(afc)
    }

    override fun onBurstKill(status:Byte,time:Int) {
        runOnUiThread {
            if (time>0 && time!=8*3600+30*60) updateBk(time)
        }
    }

    override fun onVersion(version:String) {
        this.version=version
        if (!versionChecked && versionDB!=null && receiver!=null) {
            versionChecked=true
            var versionInfo:VersionInfo?=versionDB?.db?.getOrDefault(receiver!!.getFirmwareName(),null)
            if (versionInfo!=null && versionInfo.version!=version)
                runOnUiThread {
                    UpdateDialog(receiver!!,versionInfo).show(supportFragmentManager,"")
                }
        }
    }

    override fun onSettings(
        sda:Int,scl:Int,rst:Int,led:Int,RS41bw:Int,M20bw:Int,M10bw:Int,PILOTbw:Int,DFMbw:Int,
        call:String,offset:Int,bat:Int,batMin:Int,batMax:Int,batType:Int,lcd:Int,nam:Int,buz:Int,
        ver:String,
    ) {
        runOnUiThread {
            showProgress(false)
            updateMute(mute)
            val intent=Intent(this,SettingsActivity::class.java)
            val extras=Bundle().apply {
                putInt(TTGO.OLED_SDA,sda)
                putInt(TTGO.OLED_SCL,scl)
                putInt(TTGO.OLED_RST,rst)
                putInt(TTGO.LED_POUT,led)
                putInt(TTGO.RS41_RXBW,RS41bw)
                putInt(TTGO.M20_RXBW,M20bw)
                putInt(TTGO.M10_RXBW,M10bw)
                putInt(TTGO.PILOT_RXBW,PILOTbw)
                putInt(TTGO.DFM_RXBW,DFMbw)
                putString(TTGO.MYCALL,call)
                putInt(TTGO.FREQOFS,offset)
                putInt(TTGO.VBATMIN,batMin)
                putInt(TTGO.VBATMAX,batMax)
                putInt(TTGO.VBATTYPE,batType)
                putInt(TTGO.LCD,lcd)
                putInt(TTGO.APRSNAME,nam)
                putInt(TTGO.BUZ_PIN,buz)
                putString("ver",version)
            }
            intent.putExtras(extras)
            resultLauncher.launch(intent)
        }
      }

    companion object {
        const val TAG="TrovaLaSonda"
        private const val SCAN_PERIOD:Long=15000
        private val SERVICE_UUID=UUID.fromString("79ee1705-f663-4674-8774-55042fc215f5")
        private val CLIENT_CONFIG_DESCRIPTOR=UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
        private val LAT_UUID=UUID.fromString("fc62efe0-eb5d-4cb0-93d3-01d4fb083e18")
        private val LON_UUID=UUID.fromString("c8666b42-954a-420f-b235-6baaba740840")
        private val ALT_UUID=UUID.fromString("1bfdccfe-80f4-46d0-844f-ad8410001989")
        private val FRAME_UUID=UUID.fromString("343b7b66-8208-4e48-949f-e62739147f92")
        private val BAT_UUID=UUID.fromString("4578ee77-f50f-4584-b59c-46264c56d949")
        private val RSSI_UUID=UUID.fromString("e482dfeb-774f-4f8b-8eea-87a752326fbd")
        private val TYPE_UUID=UUID.fromString("66bf4d7f-2b21-468d-8dce-b241c7447cc6")
        private val FREQ_UUID=UUID.fromString("b4da41fe-3194-42e7-8bbb-2e11d3ff6f6d")
        private val SERIAL_UUID=UUID.fromString("539fd1f8-f427-4ddc-99d2-80f51616baab")
        private val MUTE_UUID=UUID.fromString("a8b47819-eb1a-4b5c-8873-6258ddfe8055")
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
        private const val SONDE_LAT="sondeLat"
        private const val SONDE_LON="sondeLon"
        private const val REQUEST_PERMISSIONS_REQUEST_CODE=1
        private const val MYSONDYGOPREFIX="MySondyGO-"
        private const val TROVALASONDAPREFIX="TrovaLaSonda"
        private const val CIAPASONDEPREFIX="CiapaSonde"
        private const val LAST_TIME_DONATION_SHOWN="lastTimeDonationShown"
        private const val LAST_CONNECTION_CHOICE="LAST_CONNECTION_CHOICE"
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
