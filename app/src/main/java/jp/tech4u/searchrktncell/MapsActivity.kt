package jp.tech4u.searchrktncell

import android.Manifest
import android.app.AlertDialog
import android.content.*
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.content.res.Resources
import android.graphics.Typeface
import android.graphics.drawable.Animatable
import android.location.Location
import android.location.LocationManager.PASSIVE_PROVIDER
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.telephony.CellInfo
import android.telephony.PhoneStateListener
import android.telephony.SignalStrength
import android.telephony.TelephonyManager
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.MotionEvent
import android.view.View
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.PopupMenu
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.drawable.toBitmap
import com.github.pengrad.mapscaleview.MapScaleView
import com.google.android.gms.location.*
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.*
import jp.tech4u.searchrktncell.data.CellId
import jp.tech4u.searchrktncell.data.CellInfoViewVO
import jp.tech4u.searchrktncell.data.DebugVO
import jp.tech4u.searchrktncell.data.LogVO
import jp.tech4u.searchrktncell.setting.SettingsActivity
import jp.tech4u.searchrktncell.util.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParserException
import java.io.File
import java.io.IOException
import kotlin.math.floor


class MapsActivity : AppCompatActivity(),
    OnMapReadyCallback,
    GoogleMap.OnMapLongClickListener,
    PopupMenu.OnMenuItemClickListener,
    GoogleMap.OnCameraMoveStartedListener,
    GoogleMap.OnCameraIdleListener,
    //GoogleMap.OnMyLocationButtonClickListener,
    SharedPreferences.OnSharedPreferenceChangeListener {

    companion object {
        private const val MLS_JSON_FILENAME = "mls44011.json"
        private const val MLS_JSON_URL = "https://cellmap.rukihena.com/$MLS_JSON_FILENAME"

        // 地図表示初期位置
        private const val INITIAL_MAP_LAT = 36.0
        private const val INITIAL_MAP_LNG = 140.0
        private const val INITIAL_MAP_ZOOM = 7f

        private lateinit var instance: MapsActivity

        // あんまりやりたくないけど、みんなコレどうしてんだろう？
        fun getInstance(): MapsActivity {
            return instance
        }
    }

    private val scope = CoroutineScope(Dispatchers.Default)

    private lateinit var mMap: GoogleMap

    private var debugCellInfo: DebugCellInfo? = null
    private val debugTimerHandler = Handler(Looper.getMainLooper())
    private val debugTimerRunnable: Runnable = object : Runnable {
        override fun run() {
            val dummyLocation = Location(PASSIVE_PROVIDER)
            onDataEvent(listOf(), dummyLocation)
            debugTimerHandler.postDelayed(this, DebugCellInfo.INTERVAL_MS)
        }
    }

    // 現在地連動タイマー用
    private val enableLocationHandler = Handler(Looper.getMainLooper())

    // UI部品
    private lateinit var cellInfoView: CellInfoView
    private lateinit var btnGps: ImageButton
    private lateinit var imgLocationCenter: ImageView
    private lateinit var txtCellInfoEci: TextView
    private lateinit var txtCellInfoTac: TextView
    private lateinit var txtCellInfoPci: TextView
    private lateinit var txtLatLng: TextView
    private lateinit var txtDestination: TextView
    private lateinit var txtDebug: TextView
    private lateinit var scaleView: MapScaleView

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var powerConnectionReceiver: PowerConnectionReceiver

    // Register the permissions callback, which handles the user's response to the
    // system permissions dialog. Save the return value, an instance of
    // ActivityResultLauncher. You can use either a val, as shown in this snippet,
    // or a lateinit var in your onAttach() or onCreate() method.
    private val requestPermissionLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { permissions ->
            when {
                permissions.getOrDefault(Manifest.permission.ACCESS_FINE_LOCATION, false) -> {
                    // 権限を取得したのでもう一度setMyLocationEnabled()を呼び出す
                    setMyLocationEnabled()
                }
                permissions.getOrDefault(Manifest.permission.ACCESS_COARSE_LOCATION, false) -> {
                    // （精度低い方だけど）権限を取得したのでもう一度setMyLocationEnabled()を呼び出す
                    setMyLocationEnabled()
                }
                else -> {
                    // 権限を取得できなかったので諦める
                    Toast.makeText(this, "位置情報の権限がありません", Toast.LENGTH_LONG).show()
                }
            }
        }

    private lateinit var darkMapStyle: MapStyleOptions

    // 最後に得た現在地（PhoneStateListenerで参照）
    private var lastLocation: Location? = null

    // 現在地追従するかのフラグ
    private var bMoveCurrentLocation = true

    // カメラアニメーションの時間[ms]
    private var cameraMoveDurationMs = 300

    private var destinationMarker: Marker? = null   // 簡易ナビの目的地。nullでなければナビが有効
    private var destinationLine: Polyline? = null
    private val distanceResult = FloatArray(3)

    // デバッグ用
    private var bPress = false


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.i(localClassName, "onCreate")
        instance = this
        setContentView(R.layout.activity_maps)

        cellInfoView = findViewById(R.id.cellInfoView)
        btnGps = findViewById(R.id.btnGps)
        imgLocationCenter = findViewById(R.id.imgLocationCenter)
        txtCellInfoEci = findViewById(R.id.txtCellInfoEci)
        txtCellInfoTac = findViewById(R.id.txtCellInfoTac)
        txtCellInfoPci = findViewById(R.id.txtCellInfoPci)
        txtLatLng = findViewById(R.id.txtLatLng)
        txtDestination = findViewById(R.id.txtDestination)
        txtDebug = findViewById(R.id.txtDebug)
        if ( DebugCellInfo.USE_GPS_LOG) {
            txtDebug.visibility = View.VISIBLE   // デバッグ用
        }
        scaleView = findViewById(R.id.scaleView)
        scaleView.metersOnly()

        cellInfoView.setOnTouchListener(onTouchListener)
        btnGps.setOnClickListener { enableLocation() }


        // Set up a default TextPaint object
        val typeface = Typeface.createFromAsset(this.assets, "Abel-Regular.ttf")
        txtCellInfoEci.typeface = typeface
        txtCellInfoTac.typeface = typeface
        txtCellInfoPci.typeface = typeface

        //btnGps.setImageResource(R.drawable.gps_not_fixed)
        (btnGps.drawable as Animatable).start()

        // ダークテーマのマップスタイルを読み込む（反映はまだ）
        darkMapStyle = MapStyleOptions.loadRawResourceStyle(this, R.raw.dark_map)

        powerConnectionReceiver = PowerConnectionReceiver(applicationContext, window)
        powerConnectionReceiver.setScreenOn()
        val filter = IntentFilter()
        //filter.addAction(Intent.ACTION_POWER_CONNECTED)
        //filter.addAction(Intent.ACTION_POWER_DISCONNECTED)
        filter.addAction(Intent.ACTION_BATTERY_CHANGED)
        registerReceiver(powerConnectionReceiver, filter)

        //checkPermission()

        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        // 訳：SupportMapFragmentを取得し、マップを使用する準備ができたら通知（訳註：onMapReady()）を受け取ります。
        val mapFragment = supportFragmentManager.findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)

        val app = MyApplication.getInstance()
        if (!DebugCellInfo.DISABLE_RADIO_INFO) {
            app.mlsManager.readRadioInfo()
        }

        scope.launch {
            readMls(false)
        }

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        // この辺でやっておかないとぬるぽ
        if (DebugCellInfo.DEBUG_CELL_INFO_ENABLE) {
            debugCellInfo = DebugCellInfo()
            debugCellInfo!!.init()

            // 本来のログはデバッグの邪魔なので消しておく
            val file = Utils.createLogFileObj(applicationContext)
            file.delete()

            cameraMoveDurationMs = (DebugCellInfo.INTERVAL_MS / 3).toInt()
        }
    }

    fun onClickTxtLatLng(view: View) {
        Log.d(localClassName, "onClickTxtLatLng")
        PopupMenu(this, view).apply {
            // MainActivity implements OnMenuItemClickListener
            setOnMenuItemClickListener(this@MapsActivity)
            inflate(R.menu.location_actions)
            show()
        }
    }

    override fun onMenuItemClick(item: MenuItem): Boolean {
        val txt = txtLatLng.text
        return when (item.itemId) {
            R.id.menu_copy -> {
                Log.d(localClassName, "copy:$txt")
                val clipboardManager = applicationContext.getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
                clipboardManager.setPrimaryClip(ClipData.newPlainText("", txt))
                Toast.makeText(applicationContext, "クリップボードにコピーしました", Toast.LENGTH_SHORT).show()
                true
            }
            R.id.menu_open_map_app -> {
                Log.d(localClassName, "map:$txt")
                val gmmIntentUri = Uri.parse("geo:$txt")
                val mapIntent = Intent(Intent.ACTION_VIEW, gmmIntentUri)
                mapIntent.setPackage("com.google.android.apps.maps")
                mapIntent.resolveActivity(packageManager)?.let {
                    startActivity(mapIntent)
                }
                true
            }
            else -> false
        }
    }

    // 現在のテーマを調べ、それを反映する
    private fun setTheme() {
        try {
            // アプリ設定を見て判断。アプリ設定がFOLLOW_SYSTEMなら、システム設定を見る。
            val bDark = when (Utils.getPreferenceString("theme", "MODE_NIGHT_FOLLOW_SYSTEM")) {
                "MODE_NIGHT_YES" -> true
                "MODE_NIGHT_NO" -> false
                else -> {
                    // システム設定を見る
                    val configuration = applicationContext.resources.configuration
                    val currentNightMode = configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
                    currentNightMode == Configuration.UI_MODE_NIGHT_YES
                }
            }

            val style = if (bDark) darkMapStyle else null
            Log.e(localClassName, "setTheme() $bDark")
            val success = mMap.setMapStyle(style)
            if (!success) {
                Log.e(localClassName, "Style parsing failed.")
            }

            // 自動でやって欲しいけど、どうやるのかわからんので手動で変更します。
            txtLatLng.setBackgroundColor(ContextCompat.getColor(this, R.color.colorMapObjBg))
            txtLatLng.setTextColor(ContextCompat.getColor(this, R.color.colorMapObjFg))
            btnGps.setBackgroundColor(ContextCompat.getColor(this, R.color.colorMapObjBg))


        } catch (e: UninitializedPropertyAccessException) {
            // 設定でテーマが指定されていると、起動時にmMapがコレ。
            // 問題ないので握りつぶす。
        } catch (e: Resources.NotFoundException) {
            Log.e(localClassName, "Can't find style. Error: ", e)
        }
    }

    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap
        //myMapManager.init(mMap)
        MyApplication.getInstance().mMap = mMap

        setTheme()

        // マーカータップ時にズームボタンの左に表示されるツールバー（いらない）
        mMap.uiSettings.isMapToolbarEnabled = false
        // 現在地ボタン（見た目の変え方が分からんので独自ボタンを置いた）
        mMap.uiSettings.isMyLocationButtonEnabled = false
        // ズームボタン
        mMap.uiSettings.isZoomControlsEnabled = true

        //mMap.isMyLocationEnabled = true   // ←権限的にそう簡単にいかない・・・
        setMyLocationEnabled()  // 権限考慮版

        val density = resources.displayMetrics.density
        mMap.setPadding(0, (34 * density).toInt(), 0, (34 * density).toInt())
        mMap.setOnMapLongClickListener(this)
        mMap.setOnCameraMoveStartedListener(this)
        mMap.setOnCameraIdleListener(this)
        //mMap.setOnMyLocationButtonClickListener(this)

        mMap.setInfoWindowAdapter(CustomInfoWindowAdapter(this))

        // 簡易ナビ開始
        val destination = Utils.getPreferenceString("destination", "")
        if (!destination.isNullOrEmpty()) {
            try {
                val ary = destination.split(",")
                if (ary.size == 2) {
                    val lat = ary[0].toDouble()
                    val lng = ary[1].toDouble()
                    val latLng = LatLng(lat, lng)
                    startTinyNavigation(latLng, false)
                }
            } catch (ex: Exception) {
                // ここには来ないはず
            }
        }

        val initLatLng = LatLng(INITIAL_MAP_LAT, INITIAL_MAP_LNG)
        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(initLatLng, INITIAL_MAP_ZOOM))

        initRecordManager()

        readLogAndPutMarker()
        reloadMyMap(false)

        // 早めにやるとぬるぽ
        if (DebugCellInfo.DEBUG_CELL_INFO_ENABLE) {
            if (debugCellInfo != null && debugCellInfo!!.isAvailable()) {
                debugTimerHandler.postDelayed(debugTimerRunnable, 5000L)
            }
        }
    }

    override fun onMapLongClick(latLng: LatLng) {
        AlertDialog.Builder(this)
            .setTitle("簡易ナビ機能")
            .setMessage("この場所を目的地に設定します。")
            .setNegativeButton("キャンセル", null)
            .setPositiveButton("ここへ行く") { dialog, which ->
                startTinyNavigation(latLng)
            }
            .show()
    }

    private fun startTinyNavigation(latLng: LatLng, persistence: Boolean = true) {
        stopTinyNavigation()

        val markerBitmap = ResourcesCompat.getDrawable(resources, R.drawable.destination, null)!!.toBitmap()
        val markerIcon = BitmapDescriptorFactory.fromBitmap(markerBitmap)
        val title = "簡易ナビ目的地"
        //val snippet = "%.6f,%.6f".format(latLng.latitude, latLng.longitude)
        val markerOptions = MarkerOptions()
            .position(latLng)
            .anchor(0.5f, 0.5f)
            .title(title)
            //.snippet(snippet)
            .icon(markerIcon)
            .alpha(0.8f)
            .zIndex(5000f)
        destinationMarker = mMap.addMarker(markerOptions)

        txtDestination.visibility = View.VISIBLE

        if (persistence) {
            val latLngStr = "${latLng.latitude},${latLng.longitude}"
            Utils.setPreferenceString("destination", latLngStr)
        }
    }

    private fun stopTinyNavigation() {
        destinationLine?.remove()
        destinationMarker?.remove()
        destinationLine = null
        destinationMarker = null
        txtDestination.visibility = View.INVISIBLE
        Utils.setPreferenceString("destination", "")
    }

    // フラグの類を初期化する（設定から読み込む）
    fun initRecordManager() {
        val app = MyApplication.getInstance()

        app.recordManager.bPlotPartner = Utils.getPreferenceBoolean("plotPartner", true)
        app.recordManager.bPlotMyMapCell = Utils.getPreferenceBoolean("plotMyMapCell", true)
        Utils.getPreferenceString("excludeTac", "")?.let { app.recordManager.setExcludeTac(it) }
    }

    override fun onStart() {
        super.onStart()
        Log.i(localClassName, "onStart")
    }

    override fun onResume() {
        super.onResume()
        Log.i(localClassName, "onResume")
        startLocationUpdates()
    }

    // ここが実行中

    override fun onPause() {
        super.onPause()
        Log.i(localClassName, "onPause")
        stopLocationUpdates()
    }

    override fun onStop() {
        super.onStop()
        Log.i(localClassName, "onStop")
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.i(localClassName, "onDestroy")
    }

    // stop状態から復活時に、ココを経由して、onCreateは呼ばれず、onStart()に行く
    override fun onRestart() {
        super.onRestart()
        Log.i(localClassName, "onRestart")
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        Log.i(localClassName, "onConfigurationChanged")

        try {
            when (newConfig.orientation) {
                Configuration.ORIENTATION_LANDSCAPE -> supportActionBar!!.hide()
                else -> supportActionBar!!.show()
            }
        } catch (e: Exception) {
            e.message?.let { Log.e(localClassName, it) }
        }

        // 書き換えが必要かもしれないので、適当な値を入れとく
        prevDisplayedCell = -1L

        setTheme()
    }

    private var prevWidth = 0
    private fun reCalcCellInfoViewLayout() {
        // onConfigurationChanged() だとサイズが確定してないので、描画時にやる。

        if (prevWidth == cellInfoView.width) {
            // 変更されてなければ何もしない
            return
        }
        prevWidth = cellInfoView.width

        val density = resources.displayMetrics.density
        val widthDp = cellInfoView.width / density
        cellInfoView.bPortrait = widthDp < 500  // 横画面判定（縦横比ではなく、絶対的な横幅があるかどうかで判断）
        cellInfoView.layoutParams.height = when {
            cellInfoView.bPortrait -> (100 * density).toInt()
            else -> (50 * density).toInt()
        }
    }

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(locationResult: LocationResult?) {
            locationResult ?: return
            val location = locationResult.lastLocation
            location ?: return

            // 30秒以上前のデータなら無視
            if (location.time < System.currentTimeMillis() - 30 * 1000) return

            //// 精度15m未満なら無視
            //if (location.accuracy < 15) return

            /*
            var pros = ""
            for (loc in locationResult.locations) {
                pros += "-" + loc.provider
            }
            Log.d(localClassName, "onLocationResult$pros")
             */

            onLocation(location)
        }
    }

    private val onTouchListener = View.OnTouchListener { v, event ->
        // ... Respond to touch events
        v.performClick()
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                Log.i(localClassName, "Action was DOWN")
                bPress = !bPress
                true
            }
            MotionEvent.ACTION_MOVE -> {
                Log.i(localClassName, "Action was MOVE")
                true
            }
            MotionEvent.ACTION_UP -> {
                Log.i(localClassName, "Action was UP")
                true
            }
            MotionEvent.ACTION_CANCEL -> {
                Log.i(localClassName, "Action was CANCEL")
                true
            }
            MotionEvent.ACTION_OUTSIDE -> {
                Log.i(localClassName, "Movement occurred outside bounds of current screen element")
                true
            }
            else -> onTouchEvent(event)
        }
    }

    private val phoneStateListener = object : PhoneStateListener() {
        override fun onCellInfoChanged(cellInfo: MutableList<CellInfo>?) {
            super.onCellInfoChanged(cellInfo)
            Log.i(localClassName, "onCellInfoChanged")
            cellInfo ?: return  // SIMを抜いたときはnullなので

            val activeMnc = Utils.getActiveMnc(cellInfo)
            if (activeMnc != "11") {
                // 楽天以外なら無視（DualSIM機で、他社をチラチラ拾ってしまうことがあるため）
                return
            }
            if (lastLocation != null) {
                onDataEvent(cellInfo, lastLocation!!)
            }
        }

        override fun onSignalStrengthsChanged(signalStrength: SignalStrength?) {
            super.onSignalStrengthsChanged(signalStrength)
            Log.d(localClassName, "onSignalStrengthsChanged")
            if (ActivityCompat.checkSelfPermission(
                    MyApplication.getInstance(),
                    Manifest.permission.ACCESS_FINE_LOCATION
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                return
            }
            if (lastLocation != null) {
                val tm = getSystemService(TELEPHONY_SERVICE) as TelephonyManager
                onDataEvent(tm.allCellInfo, lastLocation!!)
            }
        }
    }

    private fun onLocation(location: Location) {
        val latlng = LatLng(location.latitude, location.longitude)
        Log.v(
            localClassName,
            "onLocation ${latlng.latitude} , ${latlng.longitude} (${location.provider}, ${location.accuracy})"
        )

        if (mMap.cameraPosition.zoom == INITIAL_MAP_ZOOM) {
            // 現在地 初回
            mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(latlng, 15f))
        }
        lastLocation = location
        val tm = getSystemService(TELEPHONY_SERVICE) as TelephonyManager
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val cellInfoAry = tm.allCellInfo
        onDataEvent(cellInfoAry, location)
    }

    // onLocation と PhoneStateListenerのイベントから呼ばれる
    private fun onDataEvent(cellInfoAry: List<CellInfo>, pLocation: Location) {
        val app = MyApplication.getInstance()

        // デバッグ用の表示（リリース時もこのまま残します）
        if (txtDebug.visibility == View.VISIBLE) {
            txtDebug.text = "Acc:%.1f\nDeg:%5.1f Acc:%5.1f\nSpd:%5.1f Acc:%5.1f".format(
                pLocation.accuracy,
                pLocation.bearing,
                pLocation.bearingAccuracyDegrees,
                pLocation.speed,
                pLocation.speedAccuracyMetersPerSecond)
        }

        val listLogVoNow = mutableListOf<LogVO>()
        val latlng: LatLng

        // サイズ調整
        reCalcCellInfoViewLayout()

        // デバッグならそれから、そうでなければ引数から採用
        if (debugCellInfo != null && debugCellInfo!!.isAvailable()) {
            if (bPress) return
            // デバッグ版
            debugCellInfo!!.getLog(listLogVoNow)
            if (listLogVoNow.size != 0) {
                val lvo = listLogVoNow[0]
                val bgColor = cellInfoView.getColorByMnc(lvo.mccString, lvo.mncString)
                val cvo = CellInfoViewVO(
                    if (lvo.rsrp < 0) lvo.rsrp else null,
                    "eNB-LCID: ${lvo.ci / 256}-${lvo.ci % 256}",
                    "PCI: ${lvo.pci}  BW: ${lvo.bwStr()}",
                    "TA: ${lvo.taStr()}",
                    bgColor
                )
                cellInfoView.drawCellInfoView(cvo)
                app.recordManager.plotAndLog(mMap, lvo)

                latlng = LatLng(lvo.lat, lvo.lng)
            } else {
                latlng = LatLng(pLocation.latitude, pLocation.longitude)
            }
        } else {
            // リリース版
            latlng = LatLng(pLocation.latitude, pLocation.longitude)
            val activeMnc = Utils.getActiveMnc(cellInfoAry)
            for (cellInfo in cellInfoAry) {
                // 処理しない条件
                if (!cellInfo.isRegistered) continue
                if (cellInfo.mncString != activeMnc) continue
                if (cellInfo.rsrp == CellInfo.UNAVAILABLE) continue // https://twitter.com/InappyRakuten/status/1383300298953805824

                // CellInfoView 反映
                cellInfoView.drawCellInfoView(cellInfo)

                // データ準備
                val logVO = LogVO(
                    (System.currentTimeMillis() / 1000).toInt(),
                    latlng.latitude, latlng.longitude,
                    cellInfo.mccString, cellInfo.mncString,
                    cellInfo.tac, cellInfo.ci,
                    cellInfo.rsrp, cellInfo.bandwidth, cellInfo.timingAdvance, cellInfo.pci
                )
                if (DebugCellInfo.USE_GPS_LOG) {
                    val debugVO = DebugVO(
                        pLocation.accuracy,
                        pLocation.bearing, pLocation.bearingAccuracyDegrees,
                        pLocation.speed, pLocation.speedAccuracyMetersPerSecond
                    )
                    logVO.debug = debugVO
                }
                listLogVoNow.add(logVO)
            }
        }

        var lastLogVO: LogVO? = null
        for (logVO in listLogVoNow) {
            lastLogVO = logVO

            // 記録
            app.recordManager.plotAndLog(mMap, logVO)

            val logFile = Utils.createLogFileObj(applicationContext)
            app.recordManager.writeCsvLog(logVO, logFile)
        }

        // 圏外の場合は、上のビューに圏外表示
        if (lastLogVO == null) {
            cellInfoView.drawCellInfoView(null)
            app.myMapManager.removeLocLine()
            app.mlsManager.removeMls()
        } else {
            //// 基地局線表示
            // マイマップにある場合はソレ
            val bExist = app.myMapManager.drawLine(this, latlng, lastLogVO, mMap)
            if (!bExist) {
                // マイマップにない場合はMLSから
                app.mlsManager.drawMlsCells(this, latlng, lastLogVO, mMap)
            } else {
                app.mlsManager.removeMls()
            }
        }

        // 簡易ナビ（線を引くだけ）
        if (destinationLine != null) {
            // 既に引かれていたらとりあえず消す
            destinationLine?.remove()
            destinationLine = null
        }
        if (destinationMarker != null) {
            val pattern = listOf(
                Gap(20F), Dash(30F)
            )
            val p1 = destinationMarker!!.position
            destinationLine = mMap.addPolyline(
                PolylineOptions()
                    .add(p1, latlng)
                    .color(ContextCompat.getColor(this, R.color.colorDestinationLine))
                    .pattern(pattern)
            )
            Location.distanceBetween(p1.latitude, p1.longitude, latlng.latitude, latlng.longitude, distanceResult)
            val distanceStr = if (distanceResult[0] < 1000) {
                (floor(distanceResult[0] / 10) * 10).toInt().toString() + "m"
            } else {
                (floor(distanceResult[0] / 100) / 10).toString() + "km"
            }
            txtDestination.text = "目的地まで $distanceStr"
        }

        displayCellName(lastLogVO)

        // 地図表示位置反映
        if (bMoveCurrentLocation) {
            moveTo(latlng, pLocation.bearing, pLocation.bearingAccuracyDegrees, pLocation.speed)
        }
    }

    private var prevDisplayedCell: Long? = -1

    private fun displayCellName(logVO: LogVO?) {
        // 前回と同じなら書き換えない
        if (prevDisplayedCell == logVO?.ci) return
        prevDisplayedCell = logVO?.ci

        val app = MyApplication.getInstance()

        if (!Utils.getPreferenceBoolean("displayCellName", true)) {
            // 表示しない設定
            txtCellInfoEci.text = ""
            txtCellInfoTac.text = ""
            txtCellInfoPci.text = ""
            txtCellInfoTac.visibility = View.INVISIBLE
            txtCellInfoPci.visibility = View.INVISIBLE
        } else {
            if (logVO == null || "440" != logVO.mccString || "11" != logVO.mncString) {
                // 他キャリア
                txtCellInfoEci.text = ""
                txtCellInfoTac.text = ""
                txtCellInfoPci.text = ""
                txtCellInfoTac.visibility = View.INVISIBLE
                txtCellInfoPci.visibility = View.INVISIBLE
            } else {
                val ctx = this
                when (val name = app.myMapManager.getName(logVO.ci)) {
                    null -> txtCellInfoEci.text = ""   // そもそもマイマップにeNB-LCIDの定義がない
                    "" -> {
                        // マイマップに乗ってない
                        val cell = CellId(logVO.ci)

                        val enbInfo = RmCellClassification.getEnbInfo(cell.eNB)
                        val tacInfo = RmCellClassification.getTacInfo(logVO.tac)
                        val pciInfo = RmCellClassification.getPciInfo(logVO.pci)

                        //// ECI(eNB)
                        val eciColor = RmCellClassification.getColor(enbInfo.second)
                        val eciColors = RmCellClassification.getFgBgColor(ctx, eciColor, cell.sector3)
                        txtCellInfoEci.setBackgroundColor(eciColors.first)
                        txtCellInfoEci.setTextColor(eciColors.second)
                        txtCellInfoEci.text = "${cell.eNB}-${cell.sector} (${enbInfo.first})"

                        //// TAC
                        // TACの色は、マクロセル以外（つまりCasaか移動局）の場合はその色、マクロセルならECIと同じ色
                        val tacColor = when {
                            tacInfo.second != 0 -> RmCellClassification.getColor(tacInfo.second)
                            else -> eciColor
                        }
                        val tacColors = RmCellClassification.getFgBgColor(ctx, tacColor, cell.sector3)
                        txtCellInfoTac.setBackgroundColor(tacColors.first)
                        txtCellInfoTac.setTextColor(tacColors.second)
                        txtCellInfoTac.visibility = View.VISIBLE
                        txtCellInfoTac.text = "TAC:${logVO.tac} (${tacInfo.first})"

                        //// PCI
                        val pciColor = RmCellClassification.getColor(pciInfo.second)
                        val pciColors = RmCellClassification.getFgBgColor(ctx, pciColor, cell.sector3)
                        txtCellInfoPci.setBackgroundColor(pciColors.first)
                        txtCellInfoPci.setTextColor(pciColors.second)
                        txtCellInfoPci.visibility = View.VISIBLE
                        txtCellInfoPci.text = "PCI:${logVO.pci} (${pciInfo.first})"
                    }
                    else -> {
                        // 基地局名表示
                        txtCellInfoEci.text = name
                        txtCellInfoEci.setBackgroundColor(ContextCompat.getColor(ctx, R.color.colorMapObjBg))
                        txtCellInfoEci.setTextColor(ContextCompat.getColor(ctx, R.color.colorMapObjFg))

                        txtCellInfoTac.visibility = View.INVISIBLE
                        txtCellInfoPci.visibility = View.INVISIBLE
                    }
                }
            }
        }

        // 名前が""なら非表示にする
        txtCellInfoEci.visibility = if (txtCellInfoEci.text == "") View.INVISIBLE else View.VISIBLE
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        Log.i(localClassName, "onCreateOptionsMenu")
        val inflater = menuInflater
        inflater.inflate(R.menu.main, menu)
        return true
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        // マイマップURLが1つでも設定されていたら、マイマップリロードのメニューを活性にする
        val app = MyApplication.getInstance()
        var bReloadMyMapEnable = false
        for (key in app.myMapManager.myMapUrlKeys) {
            if (Utils.checkMyMapMid(key)) {
                bReloadMyMapEnable = true
                break
            }
        }
        menu.findItem(R.id.reloadMyMap).isEnabled = bReloadMyMapEnable

        // 簡易ナビ終了 のメニュー活性非活性
        menu.findItem(R.id.removeDestination).isEnabled = this.destinationMarker != null

        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        Log.i(localClassName, "onOptionsItemSelected")
        Log.i(localClassName, item.toString())

        when (item.itemId) {
            R.id.reloadMls -> scope.launch { readMls(false) }
            R.id.reloadMyMap -> reloadMyMap(true)
            R.id.removeDestination -> stopTinyNavigation()
            R.id.settings -> startActivity(Intent(this@MapsActivity, SettingsActivity::class.java))
            R.id.myMapList -> openUrl("https://cellmap.rukihena.com/mymaplist.html")
            R.id.appPage -> openUrl("https://cellmap.rukihena.com/SearchRktnCell/")
        }
        return true
    }

    private fun openUrl(url: String) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        startActivity(intent)
    }

    // 地図の表示位置を現在地に移動する
    private fun moveTo(latlng: LatLng, bearing: Float = 0f, bearingAccuracyDegrees: Float = 0f, speed: Float = 0f) {
        val app = MyApplication.getInstance()

        btnGps.setImageResource(R.drawable.gps_fixed)
        imgLocationCenter.visibility = View.INVISIBLE
        setMyLocation(true)
        setTxtLatLng(true, latlng)

        // 現在地追従モードが基地局連動かどうか
        val bMode = Utils.getPreferenceBoolean("locationMode", true)

        var llArea: LatLngArea? = null
        if (bMode && app.myMapManager.myMapLL != null) {
            // 基地局連動モードで、マイマップから基地局発見の場合
            llArea = LatLngArea(latlng)
            llArea.include(app.myMapManager.myMapLL!!)
        } else if (bMode && app.mlsManager.foundMlsBounds != null) {
            // 基地局連動モードで、（マイマップから基地局発見できず、）MLSから基地局発見の場合
            llArea = LatLngArea(latlng)
            llArea.include(app.mlsManager.foundMlsBounds!!.northeast)
            llArea.include(app.mlsManager.foundMlsBounds!!.southwest)
        }

        if (llArea != null) {
            mMap.animateCamera(CameraUpdateFactory.newLatLngBounds(llArea.build(), 100), cameraMoveDurationMs, null)
        } else {
            val headingUp = Utils.getPreferenceBoolean("headingUp", false)
            val headingUpSpeed = Utils.getPreferenceInt("headingUpSpeed", 5)
            val headingUpBearingAccuracyDegrees = Utils.getPreferenceInt("headingUpBearingAccuracyDegrees", 5)

            if (headingUp && (bearingAccuracyDegrees != 0f && bearingAccuracyDegrees < headingUpBearingAccuracyDegrees || headingUpSpeed <= speed * 3.6f)) {
                val cameraPos = CameraPosition.Builder()
                    .target(latlng)
                    .zoom(mMap.cameraPosition.zoom)
                    .bearing(bearing)
                    .build()
                mMap.animateCamera(CameraUpdateFactory.newCameraPosition(cameraPos), cameraMoveDurationMs, null)
            } else {
                mMap.animateCamera(CameraUpdateFactory.newLatLng(latlng), cameraMoveDurationMs, null)
            }
        }
    }

    private fun readLogAndPutMarker() {
        Log.i(localClassName, "＊＊＊＊＊＊＊＊＊＊＊＊＊＊＊＊＊＊＊＊＊readLogAndPutMarker")
        val app = MyApplication.getInstance()
        app.recordManager.readLog(Utils.createLogFileObj(applicationContext))
        app.recordManager.plotAll(mMap, CellId(0))
    }


    // 権限有無をチェックする
    // 権限がない場合、かつ、bRequestがtrue なら、権限要求をする
    private fun checkPermission(bRequest: Boolean): Boolean {
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        )
            return true
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        )
            return true

        if (bRequest) {
            showRequestPermissionUI(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        return false
    }

    private fun showRequestPermissionUI(permission: String) {
        val shouldShow = shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION)
        var msg = when (permission) {
            Manifest.permission.ACCESS_FINE_LOCATION -> "現在地を取得するため、位置情報の権限が必要です。"
            Manifest.permission.READ_PHONE_STATE -> "基地局情報を取得するため、電話の権限が必要です。本アプリは電話の権限を基地局情報の取得のみに用い、電話をかけるなどの操作は行いません。"
            else -> ""
        }
        // READ_PHONE_STATE は今は未使用。

        var okBtnLabel = "OK"
        if (shouldShow) {
            msg += "\n本アプリの設定画面から権限を許可してください"
            okBtnLabel = "設定画面を開く"
        }

        val alertDialogBuilder = AlertDialog.Builder(this)
            //.setTitle("権限")
            .setMessage(msg)
        if (shouldShow) {
            alertDialogBuilder.setPositiveButton(okBtnLabel) { dialog, which ->
                // 本アプリの設定画面を開く
                val packageUri = "package:jp.tech4u.searchrktncell"
                val settingsIntent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse(packageUri))
                settingsIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(settingsIntent)
            }
            alertDialogBuilder.setNegativeButton("Cancel", null)
        } else {
            alertDialogBuilder.setPositiveButton(okBtnLabel) { dialog, which ->
                requestPermission()
            }
        }
        alertDialogBuilder.show()
    }

    private fun requestPermission() {
        var permissionArray = arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        if (Build.VERSION.SDK_INT == Build.VERSION_CODES.Q) {
            // 参考 https://spirits.appirits.com/type/technology/11780/
            permissionArray += Manifest.permission.ACCESS_BACKGROUND_LOCATION
        }
        // 今のところ、実は ACCESS_BACKGROUND_LOCATION は必要ないが、将来のバックグラウンド処理対応のために取得している。

        requestPermissionLauncher.launch(permissionArray)
    }

    // mMap.isMyLocationEnabled の、権限考慮版
    private fun setMyLocationEnabled() {
        if (!checkPermission(true)) {
            return
        }

        mMap.isMyLocationEnabled = true

        // 権限が必要なので、ここでやる。
        val tm = getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
        tm.listen(phoneStateListener, PhoneStateListener.LISTEN_CELL_INFO or PhoneStateListener.LISTEN_SIGNAL_STRENGTHS)
    }

    // 青ポチを表示する/しない
    private fun setMyLocation(enable: Boolean) {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
            && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) {
            // 権限が無ければ何もしない
            return
        }

        mMap.isMyLocationEnabled = enable
    }

    private fun reloadMyMap(bReload: Boolean) {
        val app = MyApplication.getInstance()
        for ((index, key) in app.myMapManager.myMapUrlKeys.withIndex()) {
            if (Utils.checkMyMapMid(key)) {
                scope.launch {
                    reloadMyMap(index, key, bReload)
                }
            } else {
                app.myMapManager.clearMyMap(index)
            }
        }
    }

    private suspend fun reloadMyMap(index: Int, key: String, bReload: Boolean) {
        try {
            // doInBackgroundメソッドと同等の処理
            val app = MyApplication.getInstance()
            val result = try {
                app.myMapManager.readMyMap(index, key, bReload)
                "OK"
            } catch (e: MyException) {
                e.message ?: ""
            } catch (e: IOException) {
                "マイマップダウンロード失敗"
            } catch (e: XmlPullParserException) {
                "マイマップ解析失敗"
            } catch (e: Exception) {
                "マイマップ読み込み失敗"
            }

            // onPostExecuteメソッドと同等の処理
            withContext(Dispatchers.Main) {
                if (result == "OK") {
                    app.myMapManager.plotAllPlacemarks(index, app.mMap)
                    val bPlotMyMapCell = Utils.getPreferenceBoolean("plotMyMapCell", true)
                    if (!bPlotMyMapCell) {
                        // マイマップが判明したら軌跡を消さなきゃいけない場合があるので、軌跡をリプロットする
                        initRecordManager()
                        app.recordManager.rePlot(app.mMap)
                    }
                    prevDisplayedCell = -1
                } else {
                    Toast.makeText(app.applicationContext, result, Toast.LENGTH_SHORT).show()
                }
                Log.i(localClassName, "reloadMyMap $index result $result")
            }
        } catch (e: Exception) {
            // onCancelledメソッドと同等の処理
            Log.e(localClassName, "reloadMyMap失敗", e)
        }
    }

    private suspend fun readMls(bReload: Boolean) {
        try {
            // doInBackgroundメソッドと同等の処理
            val app = MyApplication.getInstance()

            val context = app.applicationContext
            val cacheFile = File(context.cacheDir, MLS_JSON_FILENAME)

            val result = try {
                app.mlsManager.readMls(MLS_JSON_URL, cacheFile, bReload)
                "OK"
            } catch (e: MyException) {
                e.message ?: ""
            } catch (e: IOException) {
                "MLSダウンロード失敗"
            } catch (e: Exception) {
                "MLS読み込み失敗"
            }

            // onPostExecuteメソッドと同等の処理
            withContext(Dispatchers.Main) {
                if (result != "OK") {
                    val myApp = MyApplication.getInstance()
                    Toast.makeText(myApp.applicationContext, result, Toast.LENGTH_SHORT).show()
                }
                Log.i(localClassName, result)
            }
        } catch (e: Exception) {
            // onCancelledメソッドと同等の処理
            Log.e(localClassName, "readMls失敗", e)
        }
    }

    private fun startLocationUpdates() {
        val locationRequest = createLocationRequest() ?: return
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            return
        }
        fusedLocationClient.requestLocationUpdates(
            locationRequest,
            locationCallback,
            null
        )
    }

    private fun stopLocationUpdates() {
        fusedLocationClient.removeLocationUpdates(locationCallback)
    }

    private fun createLocationRequest(): LocationRequest? {
        return LocationRequest.create()?.apply {

            val intervalSecs = Utils.getPreferenceInt("refreshInterval", 5)

            interval = intervalSecs * 1000L
            //fastestInterval = intervalSecs * 1000L
            priority = LocationRequest.PRIORITY_HIGH_ACCURACY
        }
    }

    override fun onCameraMoveStarted(reason: Int) {
        Log.v(localClassName, "onCameraMoveStarted:$reason")
        if (reason != GoogleMap.OnCameraMoveStartedListener.REASON_DEVELOPER_ANIMATION) {
            bMoveCurrentLocation = false
            btnGps.setImageResource(R.drawable.gps_off)
            imgLocationCenter.visibility = View.VISIBLE
            setMyLocation(false)
        }
        resetLocationTimer()
    }

    override fun onCameraIdle() {
        setTxtLatLng(false, mMap.cameraPosition.target)
        scaleView.update(mMap.cameraPosition.zoom, mMap.cameraPosition.target.latitude)
    }

    private fun resetLocationTimer() {
        enableLocationHandler.removeCallbacksAndMessages(null)    // もしCallBackがあれば削除
        if (Utils.getPreferenceBoolean("locationTimer", false)) {
            // 現在地連動タイマースタート
            val secs = Utils.getPreferenceInt("locationTimerSecs", 60)
            enableLocationHandler.postDelayed({ enableLocation() }, secs * 1000L)
        }
    }

    // ボタンを自力にしたので廃止
    /*
    override fun onMyLocationButtonClick(): Boolean {
        Log.i(localClassName, "onMyLocationButtonClick")
        enableLocation()
        return false
    }
    */

    private fun setTxtLatLng(gps: Boolean, latlng: LatLng) {
        // TODO 現在地かどうかでスタイルを変える？
        txtLatLng.text = "%.6f,%.6f".format(latlng.latitude, latlng.longitude)
    }

    // 現在地追従を有効にする
    private fun enableLocation() {
        bMoveCurrentLocation = true
        btnGps.setImageResource(R.drawable.gps_not_fixed)
        (btnGps.drawable as Animatable).start()

        Log.d(localClassName, "enableLocation")

        if (!checkPermission(true)) {
            return
        }
        fusedLocationClient.lastLocation.addOnSuccessListener {
            // 前回の現在地
            if (it != null) {
                Log.d(localClassName, "lastLocation" + it.provider)
                val latlng = LatLng(it.latitude, it.longitude)
                moveTo(latlng)
            } else {
                // 特定できなかったので、もう一度呼ぶ
                Log.d(localClassName, "ONE MORE enableLocation()")
                enableLocation()
            }
        }

        // 現在地連動タイマーがONなら解除・・・は、いらないかな。（無駄に呼ばれても副作用はなさそう）
    }

    // 設定変更ハンドラ
    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        if (sharedPreferences != null) {
            val app = MyApplication.getInstance()

            when (key) {
                "refreshInterval" -> {
                    Log.i(localClassName, "onSharedPreferenceChanged(" + sharedPreferences.getInt(key, -1) + ",$key)")
                    // 一旦止めて再度動かすことで、更新間隔を反映する
                    stopLocationUpdates()
                    startLocationUpdates()
                }
                "keepScreenOn" -> powerConnectionReceiver.setScreenOn()
                "locationTimer", "locationTimerSecs" -> resetLocationTimer()
                "plotPartner", "plotMyMapCell", "excludeTac" -> {
                    initRecordManager()
                    app.recordManager.rePlot(mMap)
                }
                "theme" -> {
                    app.setDefaultNightModeByPreference()
                    setTheme()
                }
                else -> {
                    var bMyMapUrlChanged = false
                    for ((index, keyName) in app.myMapManager.myMapUrlKeys.withIndex()) {
                        // 該当マイマップだけリロード
                        if (key != null && key == keyName || key == keyName + "Enable") {
                            if (Utils.checkMyMapMid(keyName)) {
                                scope.launch {
                                    reloadMyMap(index, keyName, true)
                                }
                            } else {
                                app.myMapManager.clearMyMap(index)
                            }
                            bMyMapUrlChanged = true
                            break
                        }
                    }
                    if (!bMyMapUrlChanged) {
                        // マイマップURL以外の設定が変更されました。
                        Log.i(localClassName, "onSharedPreferenceChanged($key)")
                    }
                }
            }

            // 書き換えが必要かもしれないので、適当な値を入れとく
            // (必要な場合だけやるべきだけど、
            // 　・漏れがあるとよろしくない。
            // 　・余計な処理が走ったとしても大した処理量でない
            // ということから、任意の設定をいじったら必ずやることにした)
            prevDisplayedCell = -1
        }
    }
}