package com.example.trovalasonda

import android.Manifest
import android.animation.LayoutTransition
import android.annotation.SuppressLint
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
import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import android.os.Debug
import android.os.Handler
import android.os.Looper
import android.util.DisplayMetrics
import android.util.Log
import android.view.*
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.app.ActivityCompat
import androidx.core.os.bundleOf
import androidx.core.view.children
import com.harrysoft.androidbluetoothserial.BluetoothManager
import com.harrysoft.androidbluetoothserial.BluetoothSerialDevice
import com.harrysoft.androidbluetoothserial.SimpleBluetoothDeviceInterface
import eo.view.batterymeter.BatteryMeterView
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import org.osmdroid.config.Configuration
import org.osmdroid.events.*
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
import org.osmdroid.views.overlay.mylocation.IMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.*


@SuppressLint("SetTextI18n")
class FullscreenActivity : AppCompatActivity(), LocationListener {
    private var bluetoothManager = BluetoothManager.getInstance()
    private var sondeTypes: Array<String>? = null
    private val path: Polyline = Polyline()
    private val sondePath: Polyline = Polyline()
    private var mkSonde: Marker? = null
    private val sondeDirection: Polyline = Polyline()
    private var locationOverlay: MyLocationNewOverlay? = null

    private var expandedMenu = false
    private var currentLocation: Location? = null
    private var satelliteView = false
    private var btMacAddress: String? = null
    private var deviceInterface: SimpleBluetoothDeviceInterface? = null
    private var mute: Boolean = false
    private var muteChanged: Boolean = true
    private var sondeId: String? = null
    private var sondeType = -1
    private var heightDelta:Double=0.0
    private var freq: Double = 0.0
    private var height:Double=0.0
    private var bk: Instant? = null
    private var timeLastSeen: Instant? = null
    private var timeLastMessage: Instant? = null

    private var map: MapView? = null
    private var menu: LinearLayout? = null
    private var tvDirection: TextView? = null
    private var tvType: TextView? = null
    private var tvDistance: TextView? = null
    private var tvHeight: TextView? = null
    private var tvHorizontalSpeed: TextView? = null
    private var tvUnits: TextView? = null
    private var ivBuzzer: ImageView? = null
    private var llSonde: LinearLayout? = null
    private var llCoords: LinearLayout? = null
    private var tvLat: TextView? = null
    private var tvLon: TextView? = null
    private var tvId: TextView? = null
    private var tvBk: TextView? = null
    private var batteryMeter:BatteryMeterView?=null
    private var pbRssi: ProgressBar? = null
    private var tvDbm: TextView? = null
    private val sondeLevelListDrawable = LevelListDrawable()
    private val handler = Handler(Looper.getMainLooper())
    private var receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action!!) {
                BluetoothDevice.ACTION_FOUND -> {
                    val device: BluetoothDevice =
                            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                    val deviceName = device.name
                    Log.i(TAG, "BT device found: $deviceName")
                    if (deviceInterface == null && deviceName != null && deviceName.startsWith(
                                    MYSONDYGOPREFIX
                            )
                    ) {
                        BluetoothAdapter.getDefaultAdapter().cancelDiscovery()
                        connectDevice(device.address)
                    }
                }
            }
        }
    }
    private val resultLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode != Activity.RESULT_OK) return@registerForActivityResult
            val data: Intent? = result.data
            var reset = false
            val cmds = mutableListOf<Pair<String, Any>>()
            val resetCmds = listOf(
                    SettingsActivity.LCD, SettingsActivity.OLED_SDA,
                    SettingsActivity.OLED_SCL, SettingsActivity.OLED_RST, SettingsActivity.LED_POUT,
                    SettingsActivity.BUZ_PIN, SettingsActivity.BATTERY
            )
            if (data != null && data.extras != null) {
                for (k in data.extras?.keySet()!!) {
                    if (resetCmds.indexOf(k) >= 0) reset = true
                    val t = data.extras?.get(k)
                    if (k == SettingsActivity.MYCALL)
                        cmds.add(Pair<String, Any>("myCall", t as String))
                    else
                        cmds.add(Pair<String, Any>(k, t as Int))
                }
                if (reset) {
                    val alertDialog = AlertDialog.Builder(this).create()
                    alertDialog.setTitle("Alert")
                    alertDialog.setMessage("New settings require a restart, do you want to apply them anyway?")
                    alertDialog.setButton(AlertDialog.BUTTON_NEGATIVE, "Cancel") { dialog, _ ->
                        dialog.dismiss()
                    }
                    alertDialog.setButton(AlertDialog.BUTTON_POSITIVE, "OK") { dialog, _ ->
                        dialog.dismiss()
                        sendCommands(cmds)
                        showProgress(true)
                    }
                    alertDialog.show()
                } else
                    sendCommands(cmds)
            }
        }

    private fun showProgress(show: Boolean) {
        findViewById<ProgressBar>(R.id.progress).visibility = if (show) View.VISIBLE else View.GONE
    }

    override fun onLocationChanged(location: Location?) {
        val point = GeoPoint(location)
        if (currentLocation == null) {
            map?.controller?.setCenter(point)

        }
        currentLocation = location
        path.addPoint(point)
        path.actualPoints.apply { if (size > 100) removeAt(0) }
        updateSondeDirection()
    }

    override fun onProviderDisabled(provider: String) {}
    override fun onProviderEnabled(provider: String) {}
    override fun onStatusChanged(provider: String, status: Int, extras: Bundle) {}

    private fun connect() {
        with(BluetoothAdapter.getDefaultAdapter()) {
            if (!isEnabled) {
                val intent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)

                registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                    if (result.resultCode != Activity.RESULT_OK) finish()
                }.launch(intent)
            }

            if (!isDiscovering && !startDiscovery())
                Log.e(TAG, "Failed to start BT discovery")
        }

        Handler(Looper.getMainLooper()).postDelayed({
            if (deviceInterface == null) connect()
        }, 2000)
    }

    private fun connectDevice(mac: String) {
        bluetoothManager.openSerialDevice(mac)
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(this::onConnected) { error ->
                Log.e(TAG, error.toString())
                bluetoothManager.closeDevice(mac)
                /*Handler(Looper.getMainLooper()).postDelayed({
                    connect()
                }, 1000)*/
            }
    }

    private fun onConnected(connectedDevice: BluetoothSerialDevice) {
        Log.i(TAG, "------------------------CONNECTED " + connectedDevice.mac)
        if (btMacAddress != null) {
            bluetoothManager.closeDevice(connectedDevice.mac)
            return
        }
        btMacAddress = connectedDevice.mac
        deviceInterface = connectedDevice.toSimpleDeviceInterface()
        deviceInterface?.setListeners(this::onMessageReceived, this::onMessageSent, this::onError)

        val bmp = BitmapFactory.decodeResource(resources, R.drawable.ic_person_yellow)
        locationOverlay?.setDirectionArrow(bmp, bmp)
        muteChanged = false

        val name = BluetoothAdapter.getDefaultAdapter().getRemoteDevice(btMacAddress).name
        Toast.makeText(applicationContext, "Connected to $name", Toast.LENGTH_LONG).apply {
            setGravity(Gravity.CENTER_VERTICAL, 0, 70)
            show()
        }
    }

    private fun onDisconnected() {
        Log.i(TAG, "onDisconnected")
        val bmp = BitmapFactory.decodeResource(resources, R.drawable.ic_person_red)
        locationOverlay?.setDirectionArrow(bmp, bmp)
        sondeLevelListDrawable.level = 0
        muteChanged = true
        bluetoothManager.closeDevice(btMacAddress)
        btMacAddress = null
        deviceInterface = null
        showProgress(false)
        Toast.makeText(applicationContext, "Connection to TTGO lost", Toast.LENGTH_LONG).apply {
            setGravity(Gravity.CENTER_VERTICAL, 0, 0)
            show()
        }
        Handler(Looper.getMainLooper()).postDelayed({
            connect()
        }, 1000)
        batteryMeter?.chargeLevel=null
    }

    private fun onError(error: Throwable) {
        Log.d(TAG, error.toString())
        onDisconnected()
    }

    private fun onMessageSent(message: String) {
        Log.i(TAG, "SENT: $message")
    }

    private fun sendCommand(cmd: String) {
        try {
            deviceInterface?.sendMessage("o{$cmd}o\r\n")
        } catch (e: Exception) {
            Log.e(TAG, e.toString())
        }
    }

    private fun sendCommand(cmd: String, value: Any) {
        sendCommand("$cmd=$value")
    }

    private fun sendCommands(commands: List<Pair<String, Any>>) {
        if (commands.isEmpty()) return
        val sb = StringBuilder()
        commands.forEach { cmd ->
            if (sb.isNotEmpty()) sb.append("/")
            sb.append("${cmd.first}=${cmd.second}")
        }
        sendCommand(sb.toString())
    }

    private fun process(msg: String) {
        timeLastMessage = Instant.now()
        val campi = msg.split("/")
        if (campi[campi.size - 1] != "o") {
            Log.e(TAG, "manca terminatore messaggio")
            return
        }
        when (campi[0]) {
            "0" -> if (campi.size == 9)
                mySondyGOStatus(
                        campi[1], campi[2].toDouble(), campi[3].toDouble(), campi[4].toInt(),
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
                        campi[1], campi[2].toDouble(), campi[3], campi[4].toDouble(),
                        campi[5].toDouble(), campi[6].toDouble(), campi[7].toDouble(),
                        campi[8].toDouble(), campi[9].toInt(), campi[10].toInt(), campi[11] == "1",
                        campi[12].toInt(), campi[13].toInt(), campi[14] == "1", campi[18]
                )
            else {
                Log.e(
                        TAG,
                        "numero campi errato in messaggio tipo 1 (${campi.size} invece di 20)"
                )
                return
            }
            "2" -> if (campi.size == 11)
                mySondyGOSonde(
                        campi[1], campi[2].toDouble(), campi[3], campi[4].toDouble(),
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
                        campi[1], campi[2].toDouble(), campi[3].toInt(), campi[4].toInt(),
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
        Log.i(TAG, "RECEIVED: $message")
        try {
            process(message)
        } catch (e: Exception) {
            Log.e(TAG, e.toString())
        }
    }

    private fun updateMute(mute: Boolean) {
        this.mute = mute
        ivBuzzer?.setImageResource(if (mute) R.drawable.ic_buzzer_off else R.drawable.ic_buzzer_on)
        ivBuzzer?.imageAlpha = 255
        muteChanged = false
    }

    private fun updateSondeLocation(id: String, lat: Double, lon: Double) {
        tvLat?.text = String.format(Locale.US, " %.5f", lat)
        tvLon?.text = String.format(Locale.US, " %.5f", lon)
        if (sondeId != id) {
            sondeId = id
            tvId?.text = id
            bk = null

            mkSonde?.setVisible(true)
            sondePath.actualPoints.clear()

            if (currentLocation != null) {
                map?.zoomToBoundingBox(
                        BoundingBox.fromGeoPointsSafe(
                                mutableListOf(
                                        GeoPoint(
                                                lat,
                                                lon
                                        ), GeoPoint(currentLocation)
                                )
                        ), false, 50
                )
                map?.invalidate()
            } else
                map?.controller?.setCenter(mkSonde?.position)

            playSound()
        }

        mkSonde?.position = GeoPoint(lat, lon)
        sondePath.addPoint(mkSonde?.position)
        sondeLevelListDrawable.level = 1

        if (currentLocation != null) {
            val d = GeoPoint(currentLocation).distanceToAsDouble(mkSonde?.position)
            if (d > 10000F) {
                tvUnits?.text = "km"
                tvDistance?.text = String.format("%.1f", d / 1000)
            } else {
                tvUnits?.text = "m"
                tvDistance?.text = String.format("%.f", d)
            }

            updateSondeDirection()
        }
        timeLastSeen = Instant.now()
    }

    private fun updateSondeDirection() {
        if (currentLocation == null || sondeId == null) return
        sondeDirection.apply {
            actualPoints.clear()
            addPoint(GeoPoint(currentLocation))
            addPoint(mkSonde?.position)
            isVisible = true
        }
    }

    private fun updateTypeAndFreq(type: String, freq: Double) {
        if (this.freq!=freq || sondeType < 1 || type != sondeTypes!![sondeType - 1]) {
            tvType?.text = "$type ${freq}MHz"
            sondeType = sondeTypes?.indexOf(type)!! + 1
        }
        this.freq = freq
    }

    private fun updateBk(bk: Int) {
        tvBk?.visibility = View.VISIBLE
        tvBk?.text = String.format("BK %d:%02d:%02d", bk / 3600, (bk / 60) % 60, bk % 60)
        this.bk = Instant.now().plusSeconds(bk.toLong())
    }

    private fun updateRSSI(rssi: Double) {
        tvDbm?.text = "-${rssi}dBm"
        pbRssi?.progress = ((pbRssi?.max?:157)-rssi).toInt()
    }

    private fun updateBattery(v: Int) {
        batteryMeter?.chargeLevel=v;
    }

    private fun mySondyGOSondePos(
            type: String, freq: Double, name: String, lat: Double, lon: Double,
            height: Double, vel: Double, sign: Double, bat: Int, afc: Int, bk: Boolean,
            bktime: Int, batv: Int, mute: Boolean, ver: String
    ) {
        updateMute(mute)
        updateTypeAndFreq(type, freq)
        if (lat != 0.0 || lon != 0.0)
            updateSondeLocation(name, lat, lon)

        tvHeight?.text = "H: ${height}m"
        tvDirection?.text=if (Math.abs(this.height - height)<2) "=" else if (this.height<height) "▲" else "▼"
        val newHeightDelta=height-this.height
        if (heightDelta>0 && newHeightDelta<0)
            playSound(R.raw._541192__eminyildirim__balloon_explosion_pop)
        heightDelta=newHeightDelta
        this.height=height
        tvHorizontalSpeed?.text = "V: ${vel}km/h"
        if (bk && bktime != 8 * 3600 + 30 * 60) updateBk(bktime)
        updateRSSI(sign)
        updateBattery(bat)
    }

    private fun mySondyGOStatus(
            type: String, freq: Double, sign: Double, bat: Int, batV: Int,
            mute: Boolean, ver: String
    ) {
        updateTypeAndFreq(type, freq)
        updateMute(mute)
        sondeLevelListDrawable.level = 0
        updateRSSI(sign)
        updateBattery(bat)
    }

    private fun mySondyGOSonde(
            type: String, freq: Double, name: String, sign: Double, bat: Int, afc: Int,
            batV: Int, mute: Boolean, ver: String
    ) {
        updateMute(mute)
        updateTypeAndFreq(type, freq)
        tvId?.text = name
        sondeLevelListDrawable.level = 0
        updateRSSI(sign)
        updateBattery(bat)
    }

    private fun mySondyGOSettings(
            type: String, freq: Double, sda: Int, scl: Int, rst: Int, led: Int,
            RS41bw: Int, M20bw: Int, M10bw: Int, PILOTbw: Int, DFMbw: Int, call: String,
            offset: Int, bat: Int, batMin: Int, batMax: Int, batType: Int, lcd: Int,
            nam: Int, buz: Int, ver: String
    ) {
        showProgress(false)
        updateMute(mute)
        val intent = Intent(this, SettingsActivity::class.java)
        val extras = Bundle().apply {
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
        resultLauncher.launch(intent)
    }

    override fun onRequestPermissionsResult(
            requestCode: Int,
            permissions: Array<String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
    }

    private fun closeMenu() {
        llCoords?.visibility = View.GONE
        menu?.layoutParams?.height = menu!!.getChildAt(0).layoutParams.height
        menu?.requestLayout()
        expandedMenu = false
    }

    private fun openMenu() {
        llCoords?.visibility = View.VISIBLE
        expandedMenu = true
        menu?.apply {
            layoutParams?.height = children.fold(0) { sum, el -> sum + el.layoutParams.height }
            requestLayout()
        }
    }

    private fun ttgoNotConnectedWarning() {
        Toast.makeText(applicationContext, "TTGO not connected", Toast.LENGTH_LONG).apply {
            setGravity(Gravity.CENTER_VERTICAL, 0, 0)
            show()
        }
    }

    private fun toggleBuzzer() {
        if (deviceInterface == null)
            ttgoNotConnectedWarning()
        else {
            if (muteChanged) return
            mute = !mute
            sendCommand("mute", if (mute) 1 else 0)
            ivBuzzer?.imageAlpha = 64
            muteChanged = true
        }
    }

    private fun playSound(id:Int=R.raw._573381__ammaro__ding) {
        MediaPlayer().apply {
            setOnPreparedListener { it.start() }
            setOnErrorListener { _, a, b -> Log.e(TAG, "$a $b"); true }
            try {
                setDataSource(applicationContext,Uri.parse("${ContentResolver.SCHEME_ANDROID_RESOURCE}://$packageName/raw/${id}"))
                prepareAsync()
            } catch (e: Exception) {
                Log.e(TAG, e.toString())
            }
        }
    }

    private var n: Int = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        sondeTypes = resources.getStringArray(R.array.sonde_types)
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        window.setFlags(
                WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN
        )

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                        this,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                ) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                    0
            )
            Log.e(TAG, "non abbiamo il permesso")
            return
        }

        val ctx: Context = applicationContext

        setContentView(R.layout.activity_fullscreen)

        tvDirection = findViewById(R.id.direction)
        tvType = findViewById(R.id.type)
        tvHeight = findViewById(R.id.height)
        tvHorizontalSpeed = findViewById(R.id.horizontal_speed)
        tvDistance = findViewById(R.id.distance)
        tvUnits = findViewById(R.id.unit)
        ivBuzzer = findViewById(R.id.buzzer)
        ivBuzzer?.setOnClickListener { toggleBuzzer() }
        tvId = findViewById(R.id.id)
        llCoords = findViewById(R.id.coords)
        tvLat = findViewById(R.id.lat)
        tvLon = findViewById(R.id.lon)
        llSonde = findViewById(R.id.sonde)
        tvBk = findViewById(R.id.bk)
        pbRssi = findViewById(R.id.rssi)
        tvDbm = findViewById(R.id.dbm)
        batteryMeter=findViewById(R.id.battery_meter)
        tvLat?.setOnClickListener {
            val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("sonde latitude", tvLat?.text)
            clipboard.setPrimaryClip(clip)
            Toast.makeText(ctx, "Latitude copied to clipboard", Toast.LENGTH_SHORT).apply {
                setGravity(Gravity.CENTER_VERTICAL, 0, 0)
                show()
            }
        }
        tvLon?.setOnClickListener {
            val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("sonde longitude", tvLat?.text)
            clipboard.setPrimaryClip(clip)
            Toast.makeText(ctx, "Longitude copied to clipboard", Toast.LENGTH_SHORT).apply {
                setGravity(Gravity.CENTER_VERTICAL, 0, 0)
                show()
            }
        }
        llSonde?.setOnClickListener {
            if (deviceInterface == null) {
                ttgoNotConnectedWarning()
                return@setOnClickListener
            }
            val dlg = SondeTypeDialog()
            dlg.freq = freq
            dlg.type = sondeType
            dlg.dialogCloseListener = object : DialogCloseListener {
                override fun handleDialogClose() {
                    freq = dlg.freq
                    sondeType = dlg.type

                    //Log.i(TAG, "SONDE*******$freq $sondeType")
                    sendCommands(
                            listOf<Pair<String, Any>>(
                                    Pair("f", freq),
                                    Pair("tipo", sondeType)
                            )
                    )
                }
            }

            dlg.show(supportFragmentManager, "")
        }

        menu = findViewById(R.id.menu)
        menu?.layoutTransition?.enableTransitionType(LayoutTransition.CHANGING)

        menu?.onFocusChangeListener = View.OnFocusChangeListener { _, _ ->
            Log.i(TAG, "Lost focus")
            closeMenu()
        }

        (findViewById<View>(R.id.menu_center)).setOnClickListener {
            if (currentLocation != null)
                map?.controller?.setCenter(GeoPoint(currentLocation))
            else
                Toast.makeText(ctx, "No current location (yet)", Toast.LENGTH_SHORT).apply {
                    setGravity(Gravity.CENTER_VERTICAL, 0, 0)
                    show()
                }
            //////////////////////////////////////////////////////////////////////////////////
            if(Debug.isDebuggerConnected()) {
                val msgs = arrayOf(
                        "1/RS41/402.800/T1840263/41.20888/5.82557/6060.9/93.1/127.5/53/0/1/28040/3643/0/0/0/0/2.30/o",
                        "1/RS41/402.800/T1840263/45.20888/8.82567/6060.9/93.1/127.5/15/0/1/28039/3643/0/0/0/0/2.30/o",
                        "1/RS41/402.800/T1840263/45.20888/8.82577/6040.9/93.1/127.5/99/0/1/28038/3643/0/0/0/0/2.30/o",
                        "1/RS41/402.800/T1840263/45.20898/8.82567/6030.9/93.1/127.5/1/0/1/28037/3643/0/0/0/0/2.30/o"
                )
                n++
                n %= msgs.size
                process(msgs[n])
            }
            //////////////////////////////////////////////////////////////////////////////////
            closeMenu()
        }
        (findViewById<View>(R.id.menu_settings)).setOnClickListener {
            if (deviceInterface == null)
                ttgoNotConnectedWarning()
            else {
                sendCommand("?")
                showProgress(true)
            }
            closeMenu()
        }
        (findViewById<View>(R.id.menu_layer)).setOnClickListener {
            satelliteView = !satelliteView
            if (satelliteView) {
                val mapbox = MapBoxTileSource()
                mapbox.retrieveAccessToken(this)
                mapbox.retrieveMapBoxMapId(this)
                TileSourceFactory.addTileSource(mapbox)
                map?.setTileSource(mapbox)
            } else
                map?.setTileSource(TileSourceFactory.MAPNIK)
            closeMenu()
        }
        (findViewById<View>(R.id.menu_center_sonde)).setOnClickListener {
            if (mkSonde?.isDisplayed?:false)
                map?.controller?.setCenter(mkSonde?.position)
            else
                Toast.makeText(ctx, "No sonde to show", Toast.LENGTH_SHORT).apply {
                setGravity(Gravity.CENTER_VERTICAL, 0, 0)
                show()
            }
            closeMenu()
        }
        (findViewById<View>(R.id.menu_maps)).setOnClickListener {
            if (sondeId != null) {
                val uri = Uri.parse("google.navigation:q=${mkSonde?.position?.latitude},${mkSonde?.position?.longitude}")
                val intent = Intent(Intent.ACTION_VIEW, uri)
                intent.setPackage("com.google.android.apps.maps")
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
                closeMenu()
        }

        map=findViewById(R.id.map)
        Configuration.getInstance().userAgentValue = applicationContext.packageName
        map?.run {
            overlays?.add(MapEventsOverlay(object : MapEventsReceiver {
                override fun longPressHelper(p: GeoPoint): Boolean {
                    closeMenu()
                    return false
                }

                override fun singleTapConfirmedHelper(p: GeoPoint): Boolean {
                    closeMenu()
                    return false
                }
            }))
            addMapListener(object : MapListener {
                override fun onZoom(event: ZoomEvent?): Boolean {
                    closeMenu()
                    return true
                }

                override fun onScroll(event: ScrollEvent?): Boolean {
                    closeMenu()
                    return true
                }
            })
            addOnFirstLayoutListener { _: View, _: Int, _: Int, _: Int, _: Int ->
                isTilesScaledToDpi=true
                maxZoomLevel=20.0
                controller?.setZoom(15.0)
                zoomController?.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
                controller?.setCenter(GeoPoint(45.5, 7.1))

                setTileSource(TileSourceFactory.MAPNIK)
                setMultiTouchControls(true)

                val dm: DisplayMetrics = ctx.resources.displayMetrics
                val scaleBar = ScaleBarOverlay(map)
                scaleBar.setScaleBarOffset(dm.widthPixels / 2, 10)

                val bmp = BitmapFactory.decodeResource(resources, R.drawable.ic_person_red)
                locationOverlay = object : MyLocationNewOverlay(GpsMyLocationProvider(ctx), map) {
                    override fun onLocationChanged(
                            location: Location?,
                            source: IMyLocationProvider?
                    ) {
                        super.onLocationChanged(location, source)
                        onLocationChanged(location)
                    }
                }.apply {
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
                    position = GeoPoint(45.088144, 7.633692)
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                    setVisible(false)
                }

                path.outlinePaint.color = Color.rgb(0, 0, 255)
                sondePath.outlinePaint.color = Color.rgb(255, 128, 0)
                sondeDirection.outlinePaint.color = Color.rgb(255, 0, 0)
                sondeDirection.isVisible = false

                overlays?.addAll(
                        listOf(path, sondePath, sondeDirection, scaleBar, mkSonde, locationOverlay)
                )
            }
        }
        handler.post(object : Runnable {
            override fun run() {
                if (timeLastSeen != null && timeLastSeen?.until(
                                Instant.now(),
                                ChronoUnit.SECONDS
                        )!! > 3L
                ) {
                    if (bk != null)
                        updateBk(Instant.now().until(bk, ChronoUnit.SECONDS).toInt())
                }
                if (timeLastMessage != null && timeLastMessage?.until(
                                Instant.now(),
                                ChronoUnit.SECONDS
                        )!! > 10L
                ) {
                    if (deviceInterface != null) {
                        bluetoothManager.closeDevice(btMacAddress)
                        onDisconnected()
                    }
                }
                handler.postDelayed(this, 1000)
            }
        })

        registerReceiver(receiver, IntentFilter(BluetoothDevice.ACTION_FOUND))

        if (deviceInterface == null) connect()
    }

    override fun onResume() {
        super.onResume()
        map?.onResume()
    }

    override fun onPause() {
        super.onPause()
        map?.onPause()
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(receiver)
    }

    override fun onBackPressed() {
        if (expandedMenu)
            closeMenu()
        else
            AlertDialog.Builder(this)
                    .setIcon(android.R.drawable.ic_dialog_alert)
                    .setTitle("TrovaLaSonda")
                    .setMessage("Are you sure you want to exit?")
                    .setPositiveButton("Yes") { _, _ -> bluetoothManager.closeDevice(btMacAddress); finish() }
                    .setNegativeButton("No", null)
                    .show()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        Log.i(TAG,"onSaveInstanceState")
        outState.putAll(bundleOf("expandedMenu" to expandedMenu,
            "satelliteView" to satelliteView,
            "currentLocation" to currentLocation,
            //"deviceInterface" to deviceInterface,
            "sondeId" to sondeId,
            "mute" to mute,
            "muteChanged" to muteChanged,
            "btMacAddress" to btMacAddress,
            "sondeType" to sondeType,
            "heightDelta" to heightDelta,
            "height" to height,
            "freq" to freq,
            "bk" to bk,
            "timeLastSeen" to timeLastSeen,
            "timeLastMessage" to timeLastMessage))
    }

    override fun onRestoreInstanceState(savedInstanceState:Bundle) {
        super.onRestoreInstanceState(savedInstanceState)
        Log.i(TAG,"onRestoreInstanceState")
        expandedMenu=savedInstanceState.get("expandedMenu") as Boolean
        satelliteView=savedInstanceState.get("satelliteView") as Boolean
        mute=savedInstanceState.get("mute") as Boolean
        muteChanged=savedInstanceState.get("muteChange") as Boolean
        btMacAddress=savedInstanceState.get("btMacAddress") as String
        sondeType=savedInstanceState.get("sondeType") as Int
        heightDelta=savedInstanceState.get("heightDelta") as Double
        height=savedInstanceState.get("height") as Double
        freq=savedInstanceState.get("freq") as Double
        bk=savedInstanceState.get("bk") as Instant
        timeLastSeen=savedInstanceState.get("timeLastSeen") as Instant
        timeLastMessage=savedInstanceState.get("timeLastMessage") as Instant
        currentLocation=savedInstanceState.get("currentLocation") as Location
        //deviceInterface=savedInstanceState.get("deviceInterface") as SimpleBluetoothDeviceInterface
        sondeId=savedInstanceState.get("deviceInterface") as String
    }

    companion object {
        private const val TAG = "MAURI"
        private const val MYSONDYGOPREFIX = "MySondyGO-"
    }
}