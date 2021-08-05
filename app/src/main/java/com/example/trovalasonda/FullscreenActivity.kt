package com.example.trovalasonda

import android.Manifest
import android.animation.LayoutTransition
import android.app.Activity
import android.app.AlertDialog
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.*
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.drawable.LevelListDrawable
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.DisplayMetrics
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.Window
import android.view.WindowManager
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.app.ActivityCompat
import androidx.core.view.children
import com.harrysoft.androidbluetoothserial.BluetoothManager
import com.harrysoft.androidbluetoothserial.BluetoothSerialDevice
import com.harrysoft.androidbluetoothserial.SimpleBluetoothDeviceInterface
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.tileprovider.tilesource.MapBoxTileSource
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import org.osmdroid.views.overlay.ScaleBarOverlay
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay
import java.time.Instant
import java.util.*


class FullscreenActivity : AppCompatActivity(), LocationListener {
    private val path : Polyline=Polyline()
    private val sondePath : Polyline=Polyline()
    private val sondeDirection : Polyline=Polyline()
    private var locationOverlay:MyLocationNewOverlay?=null
    private var currentLocation:Location?=null
    private var satelliteView=false
    private var btMacAddress:String?=null
    private var bluetoothManager=BluetoothManager.getInstance()
    private var deviceInterface: SimpleBluetoothDeviceInterface?=null
    private var expandedMenu=false
    private var map : MapView? = null
    private var menu : LinearLayout? = null
    private var nConnections=0
    private var tvFreq:TextView?=null
    private var tvType:TextView?=null
    private var tvDistance:TextView?=null
    private var tvHeight:TextView?=null
    private var tvHorizontalSpeed:TextView?=null
    private var tvUnits:TextView?=null
    private var ivBuzzer:ImageView?=null
    private var llSonde:LinearLayout?=null
    private var tvCoords:TextView?=null
    private var tvId:TextView?=null
    private var tvBk:TextView?=null
    private var tvBattPercent:TextView?=null
    private var pbRssi:ProgressBar?=null
    private var tvDbm:TextView?=null
    private var mute:Boolean=false
    private var muteChanged:Boolean=true
    private var sondeId:String?=null
    private var sondeType=-1
    private var freq=0F
    private var bk: Instant?=null
    private var timeLastSeen:Instant?=null
    private var sondeTypes:Array<String>?=null
    private var mkSonde: Marker?=null
    private val sondeLevelListDrawable=LevelListDrawable()
    private val handler=Handler(Looper.getMainLooper())

    private val resultLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode != Activity.RESULT_OK) return@registerForActivityResult
        val data: Intent? = result.data
        var reset=false
        val cmds= mutableListOf<Pair<String, Any>>()
        val resetCmds=listOf(SettingsActivity.LCD, SettingsActivity.OLED_SDA,
                SettingsActivity.OLED_SCL, SettingsActivity.OLED_RST, SettingsActivity.LED_POUT,
                SettingsActivity.BUZ_PIN, SettingsActivity.BATTERY)
        if (data!=null && data?.extras!=null) {
            for (k in data.extras?.keySet()!!) {
                if (resetCmds.indexOf(k)>=0) reset=true;
                val t=data.extras?.get(k)
                if (k==SettingsActivity.MYCALL)
                    cmds.add(Pair<String, Any>("myCall", t as String))
                else
                    cmds.add(Pair<String, Any>(k, t as Int))
            }
            if (reset) {
                val alertDialog = AlertDialog.Builder(this).create()
                alertDialog.setTitle("Alert")
                alertDialog.setMessage("New settings require a restart, do you want to apply them anyway?")
                alertDialog.setButton(AlertDialog.BUTTON_NEGATIVE, "Cancel") {dialog, which ->
                    dialog.dismiss()
                }
                alertDialog.setButton(AlertDialog.BUTTON_POSITIVE, "OK") { dialog, which ->
                    dialog.dismiss()
                    sendCommands(cmds)
                    showProgress(true)
                }
                alertDialog.show()
            }
            else
                sendCommands(cmds)
        }
    }

    private fun showProgress(show: Boolean) {
        findViewById<ProgressBar>(R.id.progress).visibility = if (show) View.VISIBLE else View.GONE
    }

    override fun onLocationChanged(location: Location?) {
        val point = GeoPoint(location)
        if (currentLocation==null) {
            map?.controller?.setCenter(point)

        }
        currentLocation=location
        path.addPoint(point)
        path.actualPoints.apply { if (size>100) removeAt(0) }
        updateSondeDirection()
    }

    override fun onProviderDisabled(provider: String) {}
    override fun onProviderEnabled(provider: String) {}
    override fun onStatusChanged(provider: String, status: Int, extras: Bundle) {}

    private fun onConnected(connectedDevice: BluetoothSerialDevice) {
        Log.i(TAG, "------------------------CONNECTED " + connectedDevice.mac)
        if (btMacAddress!=null) {
            bluetoothManager.closeDevice(connectedDevice.mac)
            return
        }
        btMacAddress=connectedDevice.mac
        deviceInterface = connectedDevice.toSimpleDeviceInterface()
        deviceInterface?.setListeners(this::onMessageReceived, this::onMessageSent, this::onError)

        val bmp=BitmapFactory.decodeResource(resources, R.drawable.ic_person_yellow)
        locationOverlay?.setDirectionArrow(bmp, bmp)
        muteChanged=false

        val name=bluetoothManager.pairedDevicesList.first() { it-> it.address==btMacAddress }.name
        Toast.makeText(applicationContext, "Connected to ${name}", Toast.LENGTH_LONG).apply {
            setGravity(Gravity.CENTER_VERTICAL, 0, 0)
            show()
        }
    }

    private fun onError(error: Throwable) {
        Log.d(TAG, error.toString())
        val bmp = BitmapFactory.decodeResource(resources, R.drawable.ic_person_red)
        locationOverlay?.setDirectionArrow(bmp, bmp)
        muteChanged=true
        bluetoothManager.closeDevice(btMacAddress)
        btMacAddress=null
        deviceInterface=null
        showProgress(false)
        Handler(Looper.getMainLooper()).postDelayed({
            connect()
        }, 1000)
    }

    private fun onMessageSent(message: String) {
        Log.i(TAG, "SENT: $message")
    }

    private fun sendCommand(cmd: String) {
        deviceInterface?.sendMessage("o{$cmd}o\r\n")
    }

    private fun sendCommand(cmd: String, value: Any) {
        sendCommand("$cmd=$value")
    }

    private fun sendCommands(cmds: List<Pair<String, Any>>) {
        if (cmds.isEmpty()) return
        val sb=StringBuilder()
        cmds.forEach { cmd ->
            if (sb.isNotEmpty()) sb.append("/")
            sb.append("${cmd.first}=${cmd.second}")
        }
        sendCommand(sb.toString())
    }

    private fun updateMute(mute: Boolean) {
        this.mute=mute
        ivBuzzer?.setImageResource(if (mute) R.drawable.ic_buzzer_off else R.drawable.ic_buzzer_on)
        ivBuzzer?.imageAlpha=255
    }

    var player:MediaPlayer?=null

    private fun updateSondeLocation(id: String, lat: Float, lon: Float) {
        tvCoords?.setText(String.format(Locale.US, " %.6f %.6f ", lat, lon))
        if (sondeId!=id) {
            sondeId=id
            tvId?.text=id
            bk=null

            mkSonde?.setVisible(true)
            sondePath.actualPoints.clear()

            if (currentLocation!=null) {
                map?.zoomToBoundingBox(BoundingBox(lat.toDouble(), lon.toDouble(), currentLocation?.latitude!!, currentLocation?.longitude!!), false, 50)
                map?.invalidate()
            }
            else
                map?.controller?.setCenter(mkSonde?.position)

            /*player=MediaPlayer()
            player?.setDataSource(applicationContext,Uri.parse("${ContentResolver.SCHEME_ANDROID_RESOURCE}://com.example.trovalasonda/raw/_573381__ammaro__ding.mp3"))
            player?.prepare()*/
            player=MediaPlayer.create(applicationContext, RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION))
            player?.start()
        }

        mkSonde?.position = GeoPoint(lat.toDouble(), lon.toDouble())
        sondePath.addPoint(mkSonde?.position)
        sondeLevelListDrawable.level = 1

        if (currentLocation!=null) {
            var d = GeoPoint(currentLocation).distanceToAsDouble(mkSonde?.position)
            if (d > 10000F) {
                tvUnits?.text = "km"
                tvDistance?.text = String.format("%.1f", d / 1000)
            } else {
                tvUnits?.text = "m"
                tvDistance?.text = String.format("%.f", d)
            }

            updateSondeDirection()
        }
        timeLastSeen=Instant.now()
    }

    private fun updateSondeDirection() {
        if (currentLocation==null || sondeId==null) return
        sondeDirection.apply {
            actualPoints.clear()
            addPoint(GeoPoint(currentLocation))
            addPoint(mkSonde?.position)
            isVisible = true
        }
    }

    private fun updateTypeAndFreq(type: String, freq: Float) {
        if (freq != this.freq) {
            tvFreq?.text = "${freq}MHz"
            this.freq=freq
        }
        if (sondeType<1 || type!=sondeTypes!![sondeType - 1]) {
            tvType?.text = type
            sondeType=sondeTypes?.indexOf(type)!!+1
        }
    }

    private fun updateBk(bk: Int) {
        tvBk?.visibility=View.VISIBLE
        tvBk?.text = String.format("BK %d:%02d:%02d", bk / 3600, (bk / 60) % 60, bk % 60)
        this.bk=Instant.now().plusSeconds(bk.toLong())
    }

    private fun updateRSSI(rssi: Float) {
        tvDbm?.text="${rssi}dBm"
        pbRssi?.progress=rssi.toInt()
    }

    private fun updateBattery(v:Int) {
        tvBattPercent?.apply {
            text = "$v%"
            if (v < 20)
                setBackgroundColor(Color.rgb(255, 0, 0))
        }
    }

    private fun mySondyGOSondePos(type: String, freq: Float, name: String, lat: Float, lon: Float,
                                  alt: Float, vel: Float, sign: Float, bat: Int, afc: Int, bk: Boolean,
                                  bktime: Int, batv: Int, mute: Boolean, ver: String) {
        updateMute(mute)
        updateTypeAndFreq(type, freq)
        if (lat!=0F || lon!=0F)
            updateSondeLocation(name, lat, lon)

        tvHeight?.text="H: ${alt}m"
        tvHorizontalSpeed?.text="V: ${vel}m/s"
        if (bk) updateBk(bktime)
        updateRSSI(sign)
        updateBattery(bat)
    }

    private fun mySondyGOStatus(type: String, freq: Float, sign: Float, bat: Int, batV: Int,
                                mute: Boolean, ver: String) {
        updateTypeAndFreq(type, freq)
        updateMute(mute)
        muteChanged=false
        sondeLevelListDrawable.level = 0
        updateRSSI(sign)
        updateBattery(bat)
    }

    private fun mySondyGOSonde(type: String, freq: Float, name: String, sign: Float, bat: Int, afc: Int,
                               batV: Int, mute: Boolean, ver: String) {
        updateMute(mute)
        updateTypeAndFreq(type, freq)
        tvId?.text=name;
        sondeLevelListDrawable.level = 0
        updateRSSI(sign)
        updateBattery(bat)
    }

    private fun mySondyGOSettings(type: String, freq: Float, sda: Int, scl: Int, rst: Int, led: Int,
                                  RS41bw: Int, M20bw: Int, M10bw: Int, PILOTbw: Int, DFMbw: Int, call: String,
                                  offset: Int, bat: Int, batMin: Int, batMax: Int, batType: Int, lcd: Int,
                                  nam: Int, buz: Int, ver: String) {
        showProgress(false)
        updateMute(mute)
        val intent=Intent(this, SettingsActivity::class.java)
        val extras=Bundle().apply {
            putInt("oled_sda", sda)
            putInt("oled_scl", scl)
            putInt("oled_rst", rst)
            putInt("led_pout", led)
            putInt("rs41.rxbw", RS41bw)
            putInt("m20.rxbw", M20bw)
            putInt("m10.rxbw", M10bw)
            putInt("pilot.rxbw", PILOTbw)
            putInt("dfm.rxbw", DFMbw)
            putString("myCall", call)
            putInt("freqofs", offset)
            putInt("battery", bat)
            putInt("vBatMin", batMin)
            putInt("vBatMax", batMax)
            putInt("vBatType", batType)
            putInt("lcd", lcd)
            putInt("aprsName", nam)
            putInt("buz_pin", buz)
        }
        intent.putExtras(extras)
        resultLauncher.launch(intent);
    }

    private fun process(msg: String) {
        var campi=msg.split("/")
        if (campi[campi.size - 1]!="o") {
            Log.e(TAG, "manca terminatore messaggio")
            return
        }
        when (campi[0]) {
            "0" -> if (campi.size == 9)
                mySondyGOStatus(
                        campi[1], campi[2].toFloat(), campi[3].toFloat(), campi[4].toInt(),
                        campi[5].toInt(), campi[6] == "1", campi[7]
                )
            else {
                Log.e(
                        TAG,
                        "numero campi errato in messaggio tipo 0 (${campi.size} invece di 9)"
                )
                return
            }
            "1" -> if (campi.size == 20)
                mySondyGOSondePos(
                        campi[1], campi[2].toFloat(), campi[3], campi[4].toFloat(),
                        campi[5].toFloat(), campi[6].toFloat(), campi[7].toFloat(),
                        campi[8].toFloat(), campi[9].toInt(), campi[10].toInt(), campi[11] == "1",
                        campi[12].toInt(), campi[13].toInt(), campi[14] == "1", campi[18])
            else {
                Log.e(
                        TAG,
                        "numero campi errato in messaggio tipo 1 (${campi.size} invece di 20)"
                )
                return
            }
            "2" -> if (campi.size == 11)
                mySondyGOSonde(
                        campi[1], campi[2].toFloat(), campi[3], campi[4].toFloat(),
                        campi[5].toInt(), campi[6].toInt(), campi[7].toInt(), campi[8] == "1",
                        campi[9]
                )
            else {
                Log.e(
                        TAG,
                        "numero campi errato in messaggio tipo 2 (${campi.size} invece di 11)"
                )
                return
            }
            "3" -> if (campi.size == 23)
                mySondyGOSettings(
                        campi[1], campi[2].toFloat(), campi[3].toInt(), campi[4].toInt(),
                        campi[5].toInt(), campi[6].toInt(), campi[7].toInt(), campi[8].toInt(),
                        campi[9].toInt(), campi[10].toInt(), campi[11].toInt(), campi[12],
                        campi[13].toInt(), campi[14].toInt(), campi[15].toInt(), campi[16].toInt(),
                        campi[17].toInt(), campi[18].toInt(), campi[19].toInt(), campi[20].toInt(),
                        campi[21]
                )
            else {
                Log.e(
                        TAG,
                        "numero campi errato in messaggio tipo 3 (${campi.size} invece di 23)"
                )
                return
            }
            else -> Log.e(TAG, "Tipo messaggio sconosciuto")
        }
    }

    private fun onMessageReceived(message: String) {
        Log.i(TAG, "RECEIVED: " + message)
        try {
            process(message)
        }
        catch (e: Exception) {
            Log.e(TAG, e.toString())
        }
    }

    //@SuppressLint("CheckResult")
    private fun connectDevice(mac: String) {
        bluetoothManager.openSerialDevice(mac)
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(this::onConnected) { error ->
                Log.e(TAG, error.toString() + " (" + nConnections + ")")
                bluetoothManager.closeDevice(mac)
                if (--nConnections==0)
                    Handler(Looper.getMainLooper()).postDelayed({
                        connect()
                    }, 1000)
            }
    }

    override fun onRequestPermissionsResult(requestCode: Int,
                                            permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
    }

    private fun closeMenu() {
        tvCoords?.visibility=View.GONE
        menu?.layoutParams?.height = menu!!.getChildAt(0).layoutParams.height
        menu?.requestLayout();
        expandedMenu=false
    }
    private fun openMenu() {
        tvCoords?.visibility=View.VISIBLE
        expandedMenu=true
        menu?.apply {
            layoutParams?.height = children.fold(0) { sum, el->sum+el.layoutParams.height}
            requestLayout();
        }
    }

    private fun connect() {
        val btAdapter = BluetoothAdapter.getDefaultAdapter()
        if (!btAdapter.isEnabled) {
            val intent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)

            registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result->
                if (result.resultCode != Activity.RESULT_OK) finish()
            }.launch(intent)
        }
        nConnections=0
        bluetoothManager.close();
        val pairedDevices: Collection<BluetoothDevice> = bluetoothManager.pairedDevicesList
        for (device in pairedDevices) {
            Log.d(TAG, "Device name: ${device.name}")
            if (device.name.startsWith("MySondyGO-")) {
                connectDevice(device.address)
                nConnections++
            }
        }
        if (nConnections==0)
            Handler(Looper.getMainLooper()).postDelayed({
                connect()
            }, 1000)
    }

    private fun ttgoNotConnectedWarning() {
        Toast.makeText(applicationContext, "TTGO not connected", Toast.LENGTH_LONG).apply {
            setGravity(Gravity.CENTER_VERTICAL, 0, 0)
            show()
        }
    }

    private fun toggleBuzzer() {
        if (deviceInterface==null)
            ttgoNotConnectedWarning()
        else {
            if (muteChanged) return
            mute = !mute
            sendCommand("mute", if (mute) 1 else 0)
            ivBuzzer?.imageAlpha = 64
            muteChanged = true
        }
    }
    var n:Int=0
    //@SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sondeTypes=resources.getStringArray(R.array.sonde_types)
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        window.setFlags(
                WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN
        )
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                        this,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                ) != PackageManager.PERMISSION_GRANTED) {
            val requestCode = 0
            ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                    requestCode
            )
            Log.e(TAG, "non abbiamo il permesso")
            return
        }

        val locationManager: LocationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0L, 0.0f, this)

        val ctx: Context = applicationContext
        //Configuration.getInstance().load(ctx, PreferenceManager.getDefaultSharedPreferences(ctx))

        connect()

        setContentView(R.layout.activity_fullscreen)

        tvFreq=findViewById(R.id.freq)
        tvType=findViewById(R.id.type)
        tvHeight=findViewById(R.id.height)
        tvHorizontalSpeed=findViewById(R.id.horizontal_speed)
        tvDistance=findViewById(R.id.distance)
        tvUnits=findViewById(R.id.unit)
        ivBuzzer=findViewById(R.id.buzzer)
        ivBuzzer?.setOnClickListener { toggleBuzzer() }
        tvId=findViewById(R.id.id)
        tvCoords=findViewById(R.id.coords)
        llSonde=findViewById(R.id.sonde)
        tvBk=findViewById(R.id.bk)
        pbRssi=findViewById(R.id.rssi)
        tvDbm=findViewById(R.id.dbm)
        tvBattPercent=findViewById(R.id.batt_percent)
        tvCoords?.setOnClickListener {
            val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("sonde coordinates", tvCoords?.text)
            clipboard.setPrimaryClip(clip)
            Toast.makeText(ctx, "Coordinates copied to clipboard", Toast.LENGTH_SHORT).apply {
                setGravity(Gravity.CENTER_VERTICAL, 0, 0)
                show()
            }
        }
        llSonde?.setOnClickListener {
            val dlg = SondeTypeDialog()
            dlg.freq = freq
            dlg.type = sondeType
            dlg.dialogCloseListener = object : DialogCloseListener {
                override fun handleDialogClose() {
                    freq=dlg.freq
                    sondeType=dlg.type

                    Log.i(TAG, "SONDE*******$freq $sondeType")
                    sendCommands(listOf<Pair<String, Any>>(Pair("f", freq), Pair("tipo", sondeType)))
                }
            }

            dlg.show(supportFragmentManager, "")
        }

        menu = findViewById(R.id.menu)
        menu?.layoutTransition?.enableTransitionType(LayoutTransition.CHANGING)

        (findViewById<View>(R.id.menu_center)).setOnClickListener {
            if (currentLocation!=null)
                map?.controller?.setCenter(GeoPoint(currentLocation))
            //////////////////////////////////////////////////////////////////////////////////
            val msgs=arrayOf<String>(
                    "1/RS41/402.800/T1840263/45.20888/8.82557/6060.9/93.1/127.5/53/0/1/28040/3643/0/0/0/0/2.30/o",
                    "1/RS41/402.800/T1840263/45.20888/8.82567/6060.9/93.1/127.5/53/0/1/28039/3643/0/0/0/0/2.30/o",
                    "1/RS41/402.800/T1840263/45.20888/8.82577/6060.9/93.1/127.5/53/0/1/28038/3643/0/0/0/0/2.30/o",
                    "1/RS41/402.800/T1840263/45.20898/8.82567/6060.9/93.1/127.5/53/0/1/28037/3643/0/0/0/0/2.30/o")
            n++
            n%=msgs.size
            process(msgs[n])
            //////////////////////////////////////////////////////////////////////////////////
            closeMenu();
        }
        (findViewById<View>(R.id.menu_settings)).setOnClickListener {
            if (deviceInterface==null)
                ttgoNotConnectedWarning()
            else {
                sendCommand("?")
                showProgress(true)
            }
            closeMenu()
        }
        (findViewById<View>(R.id.menu_layer)).setOnClickListener {
            satelliteView=!satelliteView
            if (satelliteView) {
                val mapbox = MapBoxTileSource()
                mapbox.retrieveAccessToken(this)
                mapbox.retrieveMapBoxMapId(this)
                TileSourceFactory.addTileSource(mapbox)
                map?.setTileSource(mapbox)
            }
            else
                map?.setTileSource(TileSourceFactory.OpenTopo)
            closeMenu();
        }
        (findViewById<View>(R.id.menu_maps)).setOnClickListener {
            if (sondeId!=null) {
                val uri=Uri.parse("google.navigation:q=${mkSonde?.position?.latitude},${mkSonde?.position?.longitude}")
                val intent = Intent(Intent.ACTION_VIEW, uri)
                intent.setPackage("com.google.android.apps.maps");
                startActivity(intent)
            }
            else
                Toast.makeText(ctx, "No sonde to navigate to", Toast.LENGTH_SHORT).apply {
                    setGravity(Gravity.CENTER_VERTICAL, 0, 0)
                    show()
                }
            closeMenu()
        }
        (findViewById<View>(R.id.menu_open)).setOnClickListener {
            if (!expandedMenu)
                openMenu()
            else
                closeMenu();
        }

        map = findViewById(R.id.map)
        map?.overlays?.add(MapEventsOverlay(object : MapEventsReceiver {
            override fun longPressHelper(p: GeoPoint): Boolean {
                closeMenu()
                return false
            }

            override fun singleTapConfirmedHelper(p: GeoPoint): Boolean {
                closeMenu()
                return false
            }
        }))
        map?.addOnFirstLayoutListener { _: View, _: Int, _: Int, _: Int, _: Int ->
            val mapController = map!!.controller
            mapController.setZoom(15.0)
            val zoomController = map?.zoomController
            zoomController?.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
            val startPoint = GeoPoint(45.5, 7.1)
            mapController.setCenter(startPoint)

            val dm: DisplayMetrics = ctx.resources.displayMetrics;
            val scaleBar = ScaleBarOverlay(map);
            scaleBar.setScaleBarOffset(dm.widthPixels / 2, 10);

            val bmp = BitmapFactory.decodeResource(resources, R.drawable.ic_person_red)
            locationOverlay = MyLocationNewOverlay(GpsMyLocationProvider(ctx), map).apply {
                setDirectionArrow(bmp, bmp)
                enableMyLocation()
                runOnFirstFix {
                    runOnUiThread {
                        map?.controller?.animateTo(GeoPoint(lastFix))
                    }
                }
            }
            sondeLevelListDrawable.apply {
                addLevel(0, 0, AppCompatResources.getDrawable(ctx, R.drawable.ic_sonde_red))
                addLevel(1, 1, AppCompatResources.getDrawable(ctx, R.drawable.ic_sonde_green))
                level = 0
            }
            mkSonde = Marker(map).apply {
                icon = sondeLevelListDrawable
                position=GeoPoint(45.088144, 7.633692)
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER);
                setVisible(false)
            }

            path.outlinePaint.color = Color.rgb(0, 0, 255)
            sondePath.outlinePaint.color = Color.rgb(255, 128, 0)
            sondeDirection.outlinePaint.color=Color.rgb(255, 0, 0)
            sondeDirection.isVisible=false;

            map?.overlays?.addAll(listOf(path, sondePath, sondeDirection, scaleBar, mkSonde, locationOverlay))
        }

        map?.setTileSource(TileSourceFactory.MAPNIK)
        map?.setMultiTouchControls(true);

        handler.post(object : Runnable {
            override fun run() {
                if (timeLastSeen != null && timeLastSeen?.until(Instant.now(), java.time.temporal.ChronoUnit.SECONDS)!! > 3L) {
                    if (bk!=null)
                        updateBk(Instant.now().until(bk, java.time.temporal.ChronoUnit.SECONDS).toInt())
                }
                handler.postDelayed(this, 1000)
            }
        })
    }

    override fun onResume() {
        super.onResume();
        map?.onResume();
    }

    override fun onPause() {
        super.onPause();
        map?.onPause();
    }

    override fun onBackPressed() {
        AlertDialog.Builder(this)
                .setIcon(android.R.drawable.ic_dialog_alert)
                .setTitle("TrovaLaSonda")
                .setMessage("Are you sure you want to exit?")
                .setPositiveButton("Yes", DialogInterface.OnClickListener { _, _ -> finish() })
                .setNegativeButton("No", null)
                .show()
    }
    companion object {
        private const val TAG = "MAURI"
    }
}