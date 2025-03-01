package jp.tech4u.searchrktncell

import android.Manifest
import android.app.AlertDialog
import android.content.*
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.content.res.Resources
import android.graphics.Matrix
import android.graphics.Point
import android.graphics.PorterDuff
import android.graphics.Typeface
import android.graphics.drawable.Animatable
import android.hardware.*
import android.location.Location
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.MotionEvent
import android.view.View
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.PopupMenu
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.drawable.toBitmap
import com.github.pengrad.mapscaleview.MapScaleView
import com.google.android.gms.location.*
import com.google.android.gms.maps.*
import com.google.android.gms.maps.model.*
import com.google.maps.android.SphericalUtil
import cz.mroczis.netmonster.core.factory.NetMonsterFactory
import cz.mroczis.netmonster.core.model.cell.CellLte
import cz.mroczis.netmonster.core.model.cell.CellNr
import cz.mroczis.netmonster.core.model.cell.CellWcdma
import cz.mroczis.netmonster.core.model.cell.ICell
import cz.mroczis.netmonster.core.model.connection.PrimaryConnection
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
import java.io.InputStream
import kotlin.math.*

class MapsActivity : AppCompatActivity(),
    OnMapReadyCallback,
    GoogleMap.OnMapClickListener,
    GoogleMap.OnMapLongClickListener,
    PopupMenu.OnMenuItemClickListener,
    GoogleMap.OnCameraMoveStartedListener,
    GoogleMap.OnCameraMoveListener,
    GoogleMap.OnCameraIdleListener,
    //GoogleMap.OnMyLocationButtonClickListener,
    SensorEventListener,
    SharedPreferences.OnSharedPreferenceChangeListener {

    companion object {
        private const val MLS_JSON_FILENAME = "mls44011.json.bz2"

        // 地図表示初期位置
        private const val INITIAL_MAP_LAT = 36.0
        private const val INITIAL_MAP_LNG = 140.0
        private const val INITIAL_MAP_ZOOM = 7f

        private const val ZOOM_PADDING_DP = 40

        public const val UNAVAILABLE = 0x7fffffff

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
            val dummyLocation = Location(LocationManager.PASSIVE_PROVIDER)
            onDataEvent(dummyLocation)
            debugTimerHandler.postDelayed(this, DebugCellInfo.INTERVAL_MS)
        }
    }

    private val timerHandler = Handler(Looper.getMainLooper())
    private val timerRunnable: Runnable = object : Runnable {
        override fun run() {
            if (lastLocation != null) {
                if (lastLocationEpoch + 2000 < System.currentTimeMillis()) {
                    onDataEvent(lastLocation!!)
                }
            }
            timerHandler.postDelayed(this, 1000)
        }
    }

    // 現在地連動予約時刻
    private var enableLocationEpoch = 0L

    // ダミー通信予約時刻
    private var dummyCommunicationEpoch = 0L

    // UI部品
    private lateinit var cellInfoViewFrame: FrameLayout
    private lateinit var cellInfoView: CellInfoView
    private lateinit var lcidView: LcidView
    private lateinit var btnGps: ImageButton
    private lateinit var btnCellFit: ImageButton
    private lateinit var btnLayers: ImageButton
    private lateinit var btnCompass: ImageButton
    private lateinit var btnHeadingUpMode: ImageButton
    private lateinit var imgLocationCenter: ImageView
    private lateinit var txtCellInfoName: TextView
    private lateinit var txtCellInfoEci: TextView
    private lateinit var txtCellInfoTac: TextView
    private lateinit var txtCellInfoPci: TextView
    private lateinit var txtLatLng: TextView
    private lateinit var txtDestination: TextView
    private lateinit var txtDebug: TextView
    private lateinit var scaleView: MapScaleView
    private lateinit var layoutLayers: LinearLayout
    private lateinit var txtMapStandard: TextView
    private lateinit var txtMapSatellite: TextView
    private lateinit var txtMapTerrain: TextView
    private lateinit var txtMapTraffic: TextView

    private val btnMyMapAry = arrayOf(
        R.id.btnMyMap1,
        R.id.btnMyMap2,
        R.id.btnMyMap3,
        R.id.btnMyMap4,
        R.id.btnMyMap5
    )

    private val pBarMyMapAry  = arrayOf(
        R.id.pBarMyMap1,
        R.id.pBarMyMap2,
        R.id.pBarMyMap3,
        R.id.pBarMyMap4,
        R.id.pBarMyMap5,
    )

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var powerConnectionReceiver: PowerConnectionReceiver

    private lateinit var locationCallback: LocationCallback

    private lateinit var sensorManager: SensorManager
    private val accelerometerReading = FloatArray(3)// 加速度
    private val magnetometerReading = FloatArray(3) // 地磁気
    private val accelerometerReadingLpf = FloatArray(3)// 加速度
    private val magnetometerReadingLpf = FloatArray(3) // 地磁気
    private val rotationMatrix = FloatArray(9)      // 加速度と地磁気の合成 または ROTATION_VECTORから変換
    private var magneticSensorExists = false
    private var prefSensorRotateMillis = 0L
    private var prefAccelerometerMillis = 0L
    private var prefMagnetometerMillis = 0L

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
                    // 権限を取得したので、引き続きREAD_PHONE_STATEを
                    showRequestPermissionUI(Manifest.permission.READ_PHONE_STATE)
                }
                permissions.getOrDefault(Manifest.permission.ACCESS_COARSE_LOCATION, false) -> {
                    // （精度低い方だけど）権限を取得したので、引き続きREAD_PHONE_STATEを
                    showRequestPermissionUI(Manifest.permission.READ_PHONE_STATE)
                }
                permissions.getOrDefault(Manifest.permission.READ_PHONE_STATE, false) -> {
                    // 権限を取得したのでもう一度setMyLocationEnabled()を呼び出す
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
    private var lastLocationEpoch = 0L

    private var retryLocation: Boolean = false

    // 磁北補正のアレ
    private var geomagneticField: GeomagneticField? = null

    // 圏外／在圏
    private var isConnected: Boolean = false

    // 現在地追従するかのフラグ
    private var bMoveCurrentLocation = true

    // カメラアニメーションの時間[ms]
    private var cameraMoveDurationMs = 300

    private var destinationMarker: Marker? = null   // 簡易ナビの目的地。nullでなければナビが有効
    private var destinationLine: Polyline? = null

    // デバッグ用
    private var bPress = false

    private var myToast: Toast? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.i(localClassName, "onCreate")
        instance = this
        setContentView(R.layout.activity_maps)

        cellInfoViewFrame = findViewById(R.id.cellInfoViewFrame)
        cellInfoView = findViewById(R.id.cellInfoView)
        lcidView = findViewById(R.id.lcidView)
        btnGps = findViewById(R.id.btnGps)
        btnCellFit = findViewById(R.id.btnCellFit)
        btnLayers = findViewById(R.id.btnLayers)
        btnCompass = findViewById(R.id.btnCompass)
        btnHeadingUpMode = findViewById(R.id.btnHeadingUpMode)
        imgLocationCenter = findViewById(R.id.imgLocationCenter)
        txtCellInfoName = findViewById(R.id.txtCellInfoName)
        txtCellInfoEci = findViewById(R.id.txtCellInfoEci)
        txtCellInfoTac = findViewById(R.id.txtCellInfoTac)
        txtCellInfoPci = findViewById(R.id.txtCellInfoPci)
        txtLatLng = findViewById(R.id.txtLatLng)
        txtDestination = findViewById(R.id.txtDestination)
        txtDebug = findViewById(R.id.txtDebug)
        if (DebugCellInfo.USE_GPS_LOG) {
            txtDebug.visibility = View.VISIBLE   // デバッグ用
        }
        scaleView = findViewById(R.id.scaleView)
        scaleView.metersOnly()
        layoutLayers = findViewById(R.id.layoutLayers)
        txtMapStandard = findViewById(R.id.txtMapStandard)
        txtMapSatellite = findViewById(R.id.txtMapSatellite)
        txtMapTerrain = findViewById(R.id.txtMapTerrain)
        txtMapTraffic = findViewById(R.id.txtMapTraffic)

        initListener()

        cellInfoView.setOnTouchListener(onTouchListener)
        btnGps.setOnClickListener { enableLocation() }

        layoutLayers.visibility = View.INVISIBLE

        // Set up a default TextPaint object
        val typeface = Typeface.createFromAsset(this.assets, "Abel-Regular.ttf")
        txtCellInfoName.typeface = typeface
        txtCellInfoEci.typeface = typeface
        txtCellInfoTac.typeface = typeface
        txtCellInfoPci.typeface = typeface

        //btnGps.setImageResource(R.drawable.gps_not_fixed)
        (btnGps.drawable as Animatable).start()

        changeBtnCellFit()
        changeBtnCompass()
        changeHeadingUpMode()

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
        app.lcidView = lcidView

        if (!DebugCellInfo.DISABLE_RADIO_INFO) {
            app.mlsManager.readRadioInfo()
        }

        scope.launch {
            readMls(false)
        }

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        magneticSensorExists = magneticSensorExistsCheck()

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

    private fun magneticSensorExistsCheck(): Boolean {
        val rv = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        if ( rv==null) {
            val am = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
            val mf = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
            if (am == null || mf == null) {
                return false
            }
        }
        return true
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
                val clipboardManager =
                    applicationContext.getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
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
            Log.i(localClassName, "setTheme() $bDark")
            val success = mMap.setMapStyle(style)
            if (!success) {
                Log.e(localClassName, "Style parsing failed.")
            }

            // 自動でやって欲しいけど、どうやるのかわからんので手動で変更します。
            txtLatLng.setBackgroundColor(ContextCompat.getColor(this, R.color.colorMapObjBg))
            txtLatLng.setTextColor(ContextCompat.getColor(this, R.color.colorMapObjFg))
            btnGps.setBackgroundColor(ContextCompat.getColor(this, R.color.colorMapObjBg))
            btnCellFit.setBackgroundColor(ContextCompat.getColor(this, R.color.colorMapObjBg))
            btnLayers.setBackgroundColor(ContextCompat.getColor(this, R.color.colorMapObjBg))

            // btnCompass と btnHeadingUpMode の再描画のために呼ぶ。
            changeBtnCompass()

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
        // 方位ボタン（見た目の変え方が分からんので独自ボタンを置いた）
        mMap.uiSettings.isCompassEnabled = false
        // ズームボタン
        mMap.uiSettings.isZoomControlsEnabled = true

        changeMapType()
        changeMapLayer()
        changeViewMode(false)

        //mMap.isMyLocationEnabled = true   // ←権限的にそう簡単にいかない・・・
        setMyLocationEnabled()  // 権限考慮版

        val paddingPx = (ZOOM_PADDING_DP * resources.displayMetrics.density).toInt()
        mMap.setPadding(0, paddingPx, 0, paddingPx)
        mMap.setOnMapClickListener(this)
        mMap.setOnMapLongClickListener(this)
        mMap.setOnCameraMoveStartedListener(this)
        mMap.setOnCameraMoveListener(this)
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
                debugTimerHandler.postDelayed(debugTimerRunnable, DebugCellInfo.INTERVAL_MS)
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

        val markerBitmap =
            ResourcesCompat.getDrawable(resources, R.drawable.destination, null)!!.toBitmap()
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
        timerHandler.postDelayed(timerRunnable, 1000)

        if (magneticSensorExists ) {
            sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)?.also { rotationVector ->
                sensorManager.registerListener(
                    this,
                    rotationVector,
                    SensorManager.SENSOR_DELAY_UI,
                    0
                )
            } ?: run {
                // ROTATION_VECTOR が使えなかったら、MAGNETIC_FIELDとACCELEROMETERを使う
                // （そんな端末が存在するかは未確認。）
                sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)?.also { magneticField ->
                    sensorManager.registerListener(
                        this,
                        magneticField,
                        SensorManager.SENSOR_DELAY_NORMAL,
                        SensorManager.SENSOR_DELAY_UI
                    )
                }
                sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)?.also { accelerometer ->
                    sensorManager.registerListener(
                        this,
                        accelerometer,
                        SensorManager.SENSOR_DELAY_NORMAL,
                        SensorManager.SENSOR_DELAY_UI
                    )
                }
            }
        }
    }

    // ここが実行中

    override fun onPause() {
        super.onPause()
        Log.i(localClassName, "onPause")

        cellInfoView.drawCellInfoViewNull()
        val app = MyApplication.getInstance()
        app.myMapManager.removeLocLine()
        app.mlsManager.removeMls()
        isConnected = false
        prevDisplayedLogVO = null
        lastLocation = null

        stopLocationUpdates()
        timerHandler.removeCallbacks(timerRunnable)
        sensorManager.unregisterListener(this)
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
        prevDisplayedLogVO = null

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
        reCalcCellInfoViewLayout2()
    }

    private fun reCalcCellInfoViewLayout2() {
        val density = resources.displayMetrics.density
        cellInfoViewFrame.layoutParams.height = when {
            cellInfoView.bPortrait -> when {
                cellInfoView.bLarge -> (140 * density).toInt()
                else -> (100 * density).toInt()
            }
            else -> (50 * density).toInt()
        }
        cameraMoveLatLng(mMap.cameraPosition.target)    //レイアウト調整のためのダミー更新
    }


    private val onTouchListener = View.OnTouchListener { v, event ->
        // ... Respond to touch events
        v.performClick()
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                Log.i(localClassName, "Action was DOWN")
                bPress = !bPress
                changeViewMode(true)
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

    private fun initListener() {

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
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
        lastLocationEpoch = System.currentTimeMillis()
        geomagneticField = GeomagneticField(
            location.latitude.toFloat(),
            location.longitude.toFloat(),
            location.altitude.toFloat(),
            System.currentTimeMillis()
        )
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED || ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED || ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.READ_PHONE_STATE
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            // onLocation()が呼ばれたということは、LOCATION権限はある。警告避けで上記if文がある。
            // READ_PHONE_STATEが無いということなので、チェックする。
            requestPermissionReadPhoneState()
            return
        }
        onDataEvent(location)
    }

    // ダミー通信
    private fun dummyCommunication() {

        if (!isConnected) {
            Log.d("dummyCommunication", "接続なし")
            return
        }

        scope.launch {
            try {
                val dummyCommunicationUrl = Utils.getPreferenceString(
                    "dummyCommunicationUrl",
                    "https://cellmap.rukihena.com/dummy.html"
                )
                val mobile = Utils.getMobileNetwork(applicationContext)
                if (mobile != null) {
                    val con = Utils.getHttpURLConForMobile(dummyCommunicationUrl!!, mobile)
                    val inputStream: InputStream? = con.inputStream
                    if (inputStream != null) {
                        inputStream.close()
                    }
                } else {
                    Log.d("dummyCommunication", "CELLULARなし")
                }
            } catch (e: java.net.UnknownHostException) {
                Log.d("dummyCommunication", "名前解決できず")
            } catch (e: Exception) {
                Log.d("dummyCommunication", e.toString())
            }
        }
    }

    // onLocation と PhoneStateListenerのイベントから呼ばれる
    private fun onDataEvent(pLocation: Location) {
        val app = MyApplication.getInstance()

        // デバッグ用の表示（リリース時もこのまま残します）
        if (txtDebug.visibility == View.VISIBLE) {
            txtDebug.text = "Acc:%.1f\nDeg:%5.1f Acc:%5.1f\nSpd:%5.1f Acc:%5.1f".format(
                pLocation.accuracy,
                pLocation.bearing,
                pLocation.bearingAccuracyDegrees,
                pLocation.speed,
                pLocation.speedAccuracyMetersPerSecond
            )
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

                if (lvo.debug != null) {
                    pLocation.bearing = lvo.debug!!.bearing
                    pLocation.bearingAccuracyDegrees = lvo.debug!!.bearingAccuracyDegrees
                    pLocation.speed = lvo.debug!!.speed
                }
            } else {
                latlng = LatLng(pLocation.latitude, pLocation.longitude)
            }
        } else {
            // リリース版

            // netMonster.getCells() でエラーになるので権限チェック
            if (ActivityCompat.checkSelfPermission(
                    this,
                    Manifest.permission.ACCESS_FINE_LOCATION
                ) != PackageManager.PERMISSION_GRANTED || ActivityCompat.checkSelfPermission(
                    this,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ) != PackageManager.PERMISSION_GRANTED || ActivityCompat.checkSelfPermission(
                    this,
                    Manifest.permission.READ_PHONE_STATE
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                return
            }

            latlng = LatLng(pLocation.latitude, pLocation.longitude)
            val netMonster = NetMonsterFactory.get(this)
            val cellInfoAry = netMonster.getCells()
            val activeMnc = Utils.getActiveMnc(cellInfoAry)
            for (cellInfo in cellInfoAry) {
                // 処理しない条件
                if (cellInfo.connectionStatus !is PrimaryConnection) continue
                if (cellInfo.network == null) continue
                if (cellInfo.network?.mnc == null) continue
                if (cellInfo.network?.mnc != activeMnc) continue
                if (cellInfo is CellLte) {
                    if (cellInfo.signal.rsrp == null) continue // https://twitter.com/InappyRakuten/status/1383300298953805824
                    if (cellInfo.eci == null) continue
                } else if (cellInfo is CellWcdma) {
                    if (cellInfo.signal.dbm == null) continue
                    if (cellInfo.cid == null) continue
                } else if (cellInfo is CellNr) {
                    if (cellInfo.signal.dbm == null) continue
                    if (cellInfo.nci == null) continue
                } else continue

                // CellInfoView 反映
                cellInfoView.drawCellInfoView(cellInfo, latlng)
                isConnected = true

                // データ準備
                val logVO = when (cellInfo) {
                    is CellLte -> {
                        val cellLte = cellInfo as CellLte
                        LogVO(
                            (System.currentTimeMillis() / 1000).toInt(),
                            latlng.latitude,
                            latlng.longitude,
                            cellLte.network?.mcc,
                            cellLte.network?.mnc,
                            cellLte.tac ?: UNAVAILABLE,
                            cellLte.eci?.toLong() ?: UNAVAILABLE.toLong(),
                            cellLte.signal.rsrp?.toInt() ?: UNAVAILABLE,
                            cellLte.bandwidth ?: UNAVAILABLE,
                            cellLte.signal.timingAdvance ?: UNAVAILABLE,
                            cellLte.pci ?: UNAVAILABLE,
                            4
                        )
                    }
                    is CellNr -> {
                        val cellNr = cellInfo as CellNr
                        LogVO(
                            (System.currentTimeMillis() / 1000).toInt(),
                            latlng.latitude,
                            latlng.longitude,
                            cellNr.network?.mcc,
                            cellNr.network?.mnc,
                            cellNr.tac ?: UNAVAILABLE,
                            cellNr.nci ?: UNAVAILABLE.toLong(),
                            cellNr.signal.dbm ?: UNAVAILABLE,
                            UNAVAILABLE,
                            UNAVAILABLE,
                            cellNr.pci ?: UNAVAILABLE,
                            5
                        )
                    }
                    else -> {
                        val cellWcdma = cellInfo as CellWcdma
                        LogVO(
                            (System.currentTimeMillis() / 1000).toInt(),
                            latlng.latitude,
                            latlng.longitude,
                            cellWcdma.network?.mcc,
                            cellWcdma.network?.mnc,
                            cellWcdma.lac ?: UNAVAILABLE,
                            cellWcdma.cid?.toLong() ?: UNAVAILABLE.toLong(),
                            cellWcdma.signal.dbm ?: UNAVAILABLE,
                            UNAVAILABLE,
                            UNAVAILABLE,
                            cellWcdma.psc ?: UNAVAILABLE,
                            3
                        )
                    }
                }
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

        //// 定期実行モノ
        val epoch = System.currentTimeMillis()
        // 現在地連動
        if (enableLocationEpoch != 0L && enableLocationEpoch < epoch) {
            Log.d("CELLINFO", "enableLocation()")
            enableLocationEpoch = 0L
            enableLocation()
        }
        // ダミー通信(TA取得用)
        val dummyCommunicationEnable = Utils.getPreferenceBoolean("dummyCommunicationEnable", true)
        if (dummyCommunicationEnable) {
            if (dummyCommunicationEpoch < epoch) {
                Log.d("CELLINFO", "dummyCommunication()")
                val dummyCommunicationInterval = Utils.getPreferenceInt("dummyCommunicationInterval", 5)
                dummyCommunicationEpoch = epoch + dummyCommunicationInterval * 1000
                dummyCommunication()
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
            cellInfoView.drawCellInfoViewOutOfService()
            isConnected = false
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
            lcidView.drawLcid(lastLogVO.ci)
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
            val distance = SphericalUtil.computeDistanceBetween(p1, latlng);
            val distanceStr = if (distance < 1000) {
                (floor(distance / 10) * 10).toInt().toString() + "m"
            } else {
                (floor(distance / 100) / 10).toString() + "km"
            }
            txtDestination.text = "目的地まで $distanceStr"
        }

        displayCellName(lastLogVO, latlng)
        //Log.d(localClassName,"onDataEvent(): CI:${lastLogVO?.ci} TA:${lastLogVO?.ta} $cellInfoAry")

        // 地図表示位置反映
        if (bMoveCurrentLocation) {
            moveTo(latlng, pLocation.bearing, pLocation.bearingAccuracyDegrees, pLocation.speed)
        }
    }

    private var prevDisplayedLogVO: LogVO? = null

    private fun displayCellName(logVO: LogVO?, latlng: LatLng) {
        // 前回と同じなら書き換えない
        if ( prevDisplayedLogVO!=null ) {
            if (prevDisplayedLogVO!!.equalsId(logVO)) return
        }
        prevDisplayedLogVO = logVO?.copy()

        txtCellInfoName.text = ""
        txtCellInfoEci.text = ""
        txtCellInfoTac.text = ""
        txtCellInfoPci.text = ""
        txtCellInfoName.visibility = View.INVISIBLE
        txtCellInfoEci.visibility = View.INVISIBLE
        txtCellInfoTac.visibility = View.INVISIBLE
        txtCellInfoPci.visibility = View.INVISIBLE

        val app = MyApplication.getInstance()
        val ctx = this

        if (Utils.getPreferenceBoolean("displayCellName", true)
            && logVO != null && "440" == logVO.mccString && "11" == logVO.mncString && 4 == logVO.gen) {
            val eciChangeDistThreshold = Utils.getPreferenceInt("eciChangeDistThreshold", 3)
            val (name, dist) = app.myMapManager.getName(logVO.ci, latlng)
            if (!name.isNullOrBlank()) {
                // 基地局名表示
                txtCellInfoName.visibility = View.VISIBLE
                if ( eciChangeDistThreshold*1000<dist) {
                    txtCellInfoName.text = "%s - %.1fkm".format(name,  dist/1000)
                }
                else {
                    txtCellInfoName.text = name
                }
                txtCellInfoName.setBackgroundColor(
                    ContextCompat.getColor(
                        ctx,
                        R.color.colorMapObjBg
                    )
                )
                txtCellInfoName.setTextColor(
                    ContextCompat.getColor(
                        ctx,
                        R.color.colorMapObjFg
                    )
                )
            }
            if (name=="" || ( -108 <= logVO.rsrp && eciChangeDistThreshold*1000 < dist)) {
                // マイマップに乗ってない、または電波が強いのに遠すぎる
                val cell = CellId(logVO.ci)

                val enbInfo = RmCellClassification.getEnbInfo(cell.eNB)
                val tacInfo = RmCellClassification.getTacInfo(logVO.tac)
                val pciInfo = RmCellClassification.getPciInfo(logVO.pci)

                //// ECI(eNB)
                val eciColor = RmCellClassification.getColor(enbInfo.second)
                val eciColors =
                    RmCellClassification.getFgBgColor(ctx, eciColor, cell.sector3)
                txtCellInfoEci.setBackgroundColor(eciColors.first)
                txtCellInfoEci.setTextColor(eciColors.second)
                txtCellInfoEci.visibility = View.VISIBLE
                txtCellInfoEci.text = "${cell.eNB}-${cell.sector} (${enbInfo.first})"

                //// TAC
                // TACの色は、マクロセル以外（つまりCasaか移動局）の場合はその色、マクロセルならECIと同じ色
                val tacColor = when {
                    tacInfo.second != 0 -> RmCellClassification.getColor(tacInfo.second)
                    else -> eciColor
                }
                val tacColors =
                    RmCellClassification.getFgBgColor(ctx, tacColor, cell.sector3)
                txtCellInfoTac.setBackgroundColor(tacColors.first)
                txtCellInfoTac.setTextColor(tacColors.second)
                txtCellInfoTac.visibility = View.VISIBLE
                txtCellInfoTac.text = "TAC:${logVO.tac} (${tacInfo.first})"

                //// PCI
                val pciColor = RmCellClassification.getColor(pciInfo.second)
                val pciColors =
                    RmCellClassification.getFgBgColor(ctx, pciColor, cell.sector3)
                txtCellInfoPci.setBackgroundColor(pciColors.first)
                txtCellInfoPci.setTextColor(pciColors.second)
                txtCellInfoPci.visibility = View.VISIBLE
                txtCellInfoPci.text = "PCI:${logVO.pciStr()} (${pciInfo.first})"
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
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

        if (layoutLayers.visibility == View.VISIBLE) {
            layoutLayers.visibility = View.INVISIBLE
        }

        when (item.itemId) {
            R.id.reloadMls -> scope.launch { readMls(true) }
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
    private fun moveTo(
        latlng: LatLng,
        gpsBearing: Float = 0f,
        bearingAccuracyDegrees: Float = 0f,
        speed: Float = 0f
    ) {
        var bearing = gpsBearing

        if (btnGps.tag != "gps_fixed") {
            btnGps.setImageResource(R.drawable.gps_fixed)
            btnGps.tag = "gps_fixed"
        }

        imgLocationCenter.visibility = View.INVISIBLE
        setMyLocation(true)
        setTxtLatLng(true, latlng)

        // ヘディングアップ/ノースアップ
        val headingUp = Utils.getPreferenceBoolean("headingUp", false)
        // 実際に回すべきかどうか(GPS精度が信用できなければ回転しない)
        val needHeadingUp = when {
            headingUp -> {
                val headingUpMode = Utils.getPreferenceString("headingUpMode", "GPS")
                if (headingUpMode == "GEOMAGNETISM") {
                    bearing = geomagnetismBearing()
                    true
                } else {
                    val headingUpSpeed = Utils.getPreferenceInt("headingUpSpeed", 5)
                    val headingUpBearingAccuracyDegrees =
                        Utils.getPreferenceInt("headingUpBearingAccuracyDegrees", 5)
                    (bearingAccuracyDegrees != 0f && bearingAccuracyDegrees < headingUpBearingAccuracyDegrees || headingUpSpeed <= speed * 3.6f)
                }
            }
            else -> false
        }
        cameraMove(headingUp, needHeadingUp, latlng, bearing)
    }

    private fun geomagnetismBearing(): Float {
        val d = this.windowManager.getDefaultDisplay()
        val dRot = d.rotation * 90  // 画面回転 0,90,180,270度
        val declination = geomagneticField?.declination ?: 0f

        // 回転行列から北の向きを取得
        // 参考 https://qiita.com/rukihena/items/88375f56da46a092208b
        val rad = atan2(rotationMatrix[2], rotationMatrix[5]) -
                atan2(rotationMatrix[6], rotationMatrix[7]) + Math.PI
        // Maps SDK for Android の bearing値に変換
        val magAngle = Math.toDegrees(rad).toFloat()
        return magAngle - declination + dRot
    }

    private fun cameraMove(
        headingUp: Boolean,
        needHeadingUp: Boolean,
        latlng: LatLng,
        bearing: Float
    ) {
        val app = MyApplication.getInstance()

        // 現在地追従モードが基地局連動かどうか
        val bMode = Utils.getPreferenceBoolean("locationMode", true)

        var lls = mutableListOf<LatLng>()
        if (bMode && app.myMapManager.myMapLL != null) {
            // 基地局連動モードで、マイマップから基地局発見の場合
            lls.add(app.myMapManager.myMapLL!!)
        } else if (bMode && app.mlsManager.foundMlsLLs != null) {
            // 基地局連動モードで、（マイマップから基地局発見できず、）MLSから基地局発見の場合
            app.mlsManager.foundMlsLLs!!.forEach {
                lls.add(it)
            }
        }

        if (lls.count() != 0) {
            if (headingUp) {
                // 各点が見えるようにヘディングアップしつつズーム
                val newBearing = if (needHeadingUp) bearing else mMap.cameraPosition.bearing
                cameraMoveMultiPointHu(latlng, lls, newBearing)
            } else {
                // 各点が見えるように矩形ズーム
                cameraMoveMultiPointNu(latlng, lls)
            }
        } else {
            if (needHeadingUp) {
                // 移動と回転
                cameraMoveParam(latlng, mMap.cameraPosition.zoom, bearing)
            } else {
                // ただの移動
                cameraMoveLatLng(latlng)
            }
        }
    }

    private fun cameraMoveLatLng(ll: LatLng) {
        myAnimateCamera(CameraUpdateFactory.newLatLng(ll))
    }

    private fun cameraMoveMultiPointNu(ll: LatLng, lls: List<LatLng>) {
        val llArea = LatLngArea(ll)
        lls.forEach {
            llArea.include(it)
        }
        val paddingPx = (ZOOM_PADDING_DP * resources.displayMetrics.density).toInt()
        myAnimateCamera(CameraUpdateFactory.newLatLngBounds(llArea.build(), paddingPx))
    }

    private fun myAnimateCamera(cameraUpdate: CameraUpdate) {
        if (cameraMoveDurationMs == 0) {
            mMap.moveCamera(cameraUpdate)
        } else {
            mMap.animateCamera(cameraUpdate, cameraMoveDurationMs, null)
        }
    }

    private fun addLlToPts(pts: MutableList<Float>, ll: LatLng) {
        val p = mMap.projection.toScreenLocation(ll)
        pts.add(p.x.toFloat())
        pts.add(p.y.toFloat())
    }

    /*
    // 拡縮計算位置可視化用（デバッグ用）
    private var markerT:Marker?=null
    private var markerR:Marker?=null
    private var markerB:Marker?=null
    private var markerL:Marker?=null
     */
    private fun cameraMoveMultiPointHu(ll: LatLng, lls: List<LatLng>, bearing: Float) {
        val mapFragment = supportFragmentManager.findFragmentById(R.id.map) as SupportMapFragment
        val mapW = mapFragment.requireView().width
        val mapH = mapFragment.requireView().height
        val minDistance: Double =
            Utils.getPreferenceString("minDistance", "300")?.toDouble() ?: 300.0
        val maxDistance: Double =
            Utils.getPreferenceString("maxDistance", "2000")?.toDouble() ?: 2000.0

        // 画面上の座標の配列。偶数:x 奇数:y
        val pts = mutableListOf<Float>()
        addLlToPts(pts, ll)

        // 拡大しすぎないよう、現在地から一定距離の点を追加
        val llT = SphericalUtil.computeOffset(ll, minDistance, bearing.toDouble())
        val llR = SphericalUtil.computeOffset(ll, minDistance, bearing + 90.0)
        val llB = SphericalUtil.computeOffset(ll, minDistance, bearing + 180.0)
        val llL = SphericalUtil.computeOffset(ll, minDistance, bearing + 270.0)
        addLlToPts(pts, llT)
        addLlToPts(pts, llR)
        addLlToPts(pts, llB)
        addLlToPts(pts, llL)
        /*
        if (markerT == null) {
            markerT = mMap.addMarker(MarkerOptions().position(llT))
            markerR = mMap.addMarker(MarkerOptions().position(llR))
            markerB = mMap.addMarker(MarkerOptions().position(llB))
            markerL = mMap.addMarker(MarkerOptions().position(llL))
        } else {
            markerT?.position = llT
            markerR?.position = llR
            markerB?.position = llB
            markerL?.position = llL
        }
         */
        lls.forEach {
            val distance = SphericalUtil.computeDistanceBetween(ll, it)
            val p = if (maxDistance < distance) {
                val heading = SphericalUtil.computeHeading(ll, it)
                SphericalUtil.computeOffset(ll, maxDistance, heading)
            } else {
                it
            }
            addLlToPts(pts, p)
        }
        val ptsArray = pts.toFloatArray()

        val matrix = Matrix()
        val dTheta = mMap.cameraPosition.bearing - bearing
        matrix.setRotate(dTheta, (mapW / 2).toFloat(), (mapH / 2).toFloat())
        matrix.mapPoints(ptsArray)

        var l = ptsArray[0]
        var r = ptsArray[0]
        var t = ptsArray[1]
        var b = ptsArray[1]
        for (i in 2 until ptsArray.size step 2) {
            l = min(l, ptsArray[i + 0])
            r = max(r, ptsArray[i + 0])
            t = min(t, ptsArray[i + 1])
            b = max(b, ptsArray[i + 1])
        }

        val paddingPx = (ZOOM_PADDING_DP * resources.displayMetrics.density).toInt()
        val scaleW = (r - l) / (mapW - paddingPx * 2)
        val scaleH = (b - t) / (mapH - paddingPx * 4)
        val scale = max(scaleW, scaleH)
        val zoom = mMap.cameraPosition.zoom - log2(scale)

        // 回転したスクリーン座標での中央
        val centerPointRot = arrayOf(((l + r) / 2), ((t + b) / 2)).toFloatArray()
        // 回転してないスクリーン座標に戻す
        val matrixRev = Matrix()
        matrixRev.setRotate(-dTheta, (mapW / 2).toFloat(), (mapH / 2).toFloat())
        matrixRev.mapPoints(centerPointRot)
        val centerP = Point(centerPointRot[0].toInt(),centerPointRot[1].toInt())
        // 緯度経度に変換
        val centerLL = mMap.projection.fromScreenLocation(centerP)
        //Log.i("CELLINFO", "centerLL:$centerLL, zoom:$zoom, bearing:$bearing, scale:$scale")
        cameraMoveParam(centerLL, zoom, bearing)
    }

    private fun cameraMoveParam(ll: LatLng, zoom: Float, bearing: Float) {
        val cameraPos = CameraPosition.Builder().target(ll).zoom(zoom).bearing(bearing).build()
        myAnimateCamera(CameraUpdateFactory.newCameraPosition(cameraPos))
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
        val shouldShow =
            shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION)
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
                val settingsIntent =
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse(packageUri))
                settingsIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(settingsIntent)
            }
            alertDialogBuilder.setNegativeButton("Cancel", null)
        } else {
            alertDialogBuilder.setPositiveButton(okBtnLabel) { dialog, which ->
                when (permission) {
                    Manifest.permission.ACCESS_FINE_LOCATION -> requestPermissionAccessLocation()
                    Manifest.permission.READ_PHONE_STATE -> requestPermissionReadPhoneState()
                }
            }
        }
        alertDialogBuilder.show()
    }

    private fun requestPermissionAccessLocation() {
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

    private fun requestPermissionReadPhoneState() {
        var permissionArray = arrayOf(
            Manifest.permission.READ_PHONE_STATE
        )
        requestPermissionLauncher.launch(permissionArray)
    }

    // mMap.isMyLocationEnabled の、権限考慮版
    private fun setMyLocationEnabled() {
        if (!checkPermission(true)) {
            return
        }

        mMap.isMyLocationEnabled = true
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
        scope.launch {
            for ((index, key) in app.myMapManager.myMapUrlKeys.withIndex()) {
                if (Utils.checkMyMapMid(key)) {
                    reloadMyMap(index, key, bReload)
                } else {
                    val pBarMyMap: ProgressBar = findViewById(pBarMyMapAry[index])
                    pBarMyMap.visibility = View.INVISIBLE
                    app.myMapManager.clearMyMap(index)
                }
            }
        }
    }

    private suspend fun setVisibilityAsync(view:View, visibility:Int) {
        withContext(Dispatchers.Main) {
            view.visibility = visibility
        }
    }

    private suspend fun reloadMyMap(index: Int, key: String, bReload: Boolean) {
        val pBarMyMap: ProgressBar = findViewById(pBarMyMapAry[index])
        try {
            // doInBackgroundメソッドと同等の処理
            val app = MyApplication.getInstance()
            val result = try {
                setVisibilityAsync(pBarMyMap, View.VISIBLE)
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
                    val app = MyApplication.getInstance()
                    app.lcidView.setMymap(app.myMapManager.kmlParserAry)

                    prevDisplayedLogVO = null
                } else {
                    Toast.makeText(app.applicationContext, result, Toast.LENGTH_SHORT).show()
                }
                Log.i(localClassName, "reloadMyMap $index result $result")
                pBarMyMap.visibility = View.INVISIBLE
            }
        } catch (e: Exception) {
            // onCancelledメソッドと同等の処理
            Log.e(localClassName, "reloadMyMap失敗", e)
            setVisibilityAsync(pBarMyMap, View.INVISIBLE)
        }
    }

    private suspend fun readMls(bReload: Boolean) {
        try {
            // doInBackgroundメソッドと同等の処理
            val app = MyApplication.getInstance()

            val context = app.applicationContext
            val cacheFile = File(context.cacheDir, MLS_JSON_FILENAME)

            val result = try {
                val mls44011Url = Utils.getPreferenceString(
                    "mls44011Url",
                    "https://mls.js2hgw.com/cellmap/mls44011.json.bz2"
                )
                app.mlsManager.readMls(mls44011Url!!, cacheFile, bReload)
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
            if (btnGps.tag != "gps_off") {
                btnGps.setImageResource(R.drawable.gps_off)
                btnGps.tag = "gps_off"
            }

            imgLocationCenter.visibility = View.VISIBLE
            setMyLocation(false)
        }
        resetLocationTimer()
    }

    override fun onCameraIdle() {
        setTxtLatLng(false, mMap.cameraPosition.target)
        scaleView.update(mMap.cameraPosition.zoom, mMap.cameraPosition.target.latitude)

        btnCompass.rotation = -mMap.cameraPosition.bearing
    }

    override fun onCameraMove() {
        btnCompass.rotation = -mMap.cameraPosition.bearing
    }

    private fun resetLocationTimer() {
        if (Utils.getPreferenceBoolean("locationTimer", false)) {
            // 現在地連動タイマースタート
            val secs = Utils.getPreferenceInt("locationTimerSecs", 60)
            enableLocationEpoch = System.currentTimeMillis() + secs * 1000L
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
        btnGps.tag = "gps_not_fixed"
        (btnGps.drawable as Animatable).start()

        Log.d(localClassName, "enableLocation")

        if (!checkPermission(true)) {
            return
        }
        fusedLocationClient.lastLocation.addOnSuccessListener {
            // 前回の現在地
            if (it != null) {
                retryLocation = false
                Log.d(localClassName, "lastLocation" + it.provider)
                val latlng = LatLng(it.latitude, it.longitude)
                moveTo(latlng)
            } else {
                if (!retryLocation) {
                    // 特定できなかったので、もう一度呼ぶ
                    retryLocation = true
                    Log.d(localClassName, "ONE MORE enableLocation()")
                    enableLocation()
                }
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
                    Log.i(
                        localClassName,
                        "onSharedPreferenceChanged(" + sharedPreferences.getInt(key, -1) + ",$key)"
                    )
                    // 一旦止めて再度動かすことで、更新間隔を反映する
                    stopLocationUpdates()
                    startLocationUpdates()
                }
                "keepScreenOn" -> powerConnectionReceiver.setScreenOn()
                "locationTimer", "locationTimerSecs" -> resetLocationTimer()
                "locationMode" -> changeBtnCellFit()
                "headingUp" -> changeBtnCompass()
                "headingUpMode" -> changeHeadingUpMode()
                "mapType" -> changeMapType()
                "mapLayerTraffic" -> changeMapLayer()
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

            // 書き換えが必要かもしれないので、null値を入れとく
            // (必要な場合だけやるべきだけど、
            // 　・漏れがあるとよろしくない。
            // 　・余計な処理が走ったとしても大した処理量でない
            // ということから、任意の設定をいじったら必ずやることにした)
            prevDisplayedLogVO = null
        }
    }

    fun onClickBtnCellFit(view: android.view.View) {
        // 現在地追従モードが基地局連動かどうか
        val bMode = !Utils.getPreferenceBoolean("locationMode", true)
        Utils.setPreferenceBoolean("locationMode", bMode)
        changeBtnCellFit(bMode)
        if (bMode) {
            myToastShow("基地局が見えるよう拡縮", Toast.LENGTH_SHORT)
        } else {
            myToastShow("拡縮しない", Toast.LENGTH_SHORT)
        }
    }

    private fun changeBtnCellFit(bMode: Boolean? = null) {
        val bMode2 = bMode ?: Utils.getPreferenceBoolean("locationMode", false)
        val icon = if (bMode2) R.drawable.focus_on else R.drawable.focus_off
        btnCellFit.setImageResource(icon)
    }

    fun onClickBtnLayers(view: android.view.View) {
        if (layoutLayers.visibility == View.VISIBLE) {
            layoutLayers.visibility = View.INVISIBLE
        } else {
            setMapLayersBtnOnOff()

            val app = MyApplication.getInstance()
            for ((index, keyName) in app.myMapManager.myMapUrlKeys.withIndex()) {
                var btnMyMap: View = findViewById(btnMyMapAry[index])
                var midOrKmz = Utils.getMyMapKmzUrl(keyName)
                if (midOrKmz != "") {
                    btnMyMap.visibility = View.VISIBLE
                    setViewOnOff(Utils.checkMyMapMid(keyName), btnMyMap)
                } else {
                    btnMyMap.visibility = View.INVISIBLE
                }
            }

            layoutLayers.visibility = View.VISIBLE
        }
    }

    private fun setMapLayersBtnOnOff() {
        setViewOnOff(mMap.mapType == GoogleMap.MAP_TYPE_NORMAL, txtMapStandard)
        setViewOnOff(mMap.mapType == GoogleMap.MAP_TYPE_SATELLITE, txtMapSatellite)
        setViewOnOff(mMap.mapType == GoogleMap.MAP_TYPE_TERRAIN, txtMapTerrain)
        setViewOnOff(mMap.isTrafficEnabled, txtMapTraffic)
    }

    private fun setViewOnOff(on: Boolean, view: View) {
        val bg = if (on) R.color.colorMapObjBg else R.color.colorMapObjBgOff
        view.setBackgroundColor(ContextCompat.getColor(this, bg))

        val fg = if (on) R.color.colorMapObjFg else R.color.colorMapObjFgOff
        if (view is ImageButton) {
            view.setColorFilter(ContextCompat.getColor(this, fg), PorterDuff.Mode.SRC_IN)
        }
        if (view is TextView) {
            view.setTextColor(ContextCompat.getColor(this, fg))
        }
    }

    override fun onMapClick(p0: LatLng) {
        if (layoutLayers.visibility == View.VISIBLE) {
            layoutLayers.visibility = View.INVISIBLE
        }
    }

    fun onClickBtnCompass(view: android.view.View) {
        // ヘディングアップすべきかどうか
        val headingUp = !Utils.getPreferenceBoolean("headingUp", false)
        Utils.setPreferenceBoolean("headingUp", headingUp)
        changeBtnCompass(headingUp)

        if (headingUp) {
            myToastShow("ヘディングアップ", Toast.LENGTH_SHORT)
        } else {
            myToastShow("ノースアップ", Toast.LENGTH_SHORT)
            // すぐにノースアップにする
            if (mMap.cameraPosition.bearing != 0f) {
                val ll = mMap.cameraPosition.target
                val zoom = mMap.cameraPosition.zoom
                val cameraPos = CameraPosition.Builder().target(ll).zoom(zoom).bearing(0f).build()
                myAnimateCamera(CameraUpdateFactory.newCameraPosition(cameraPos))
            }
        }
    }

    private fun changeBtnCompass(headingUp: Boolean? = null) {
        val headingUp2 = headingUp ?: Utils.getPreferenceBoolean("headingUp", false)
        val icon = if (headingUp2) R.drawable.compass else R.drawable.northup
        btnCompass.setImageResource(icon)

        if (headingUp2) {
            btnHeadingUpMode.visibility = View.VISIBLE
            // cameraMoveDurationMs の反映目的で呼んでます。（アイコン選択は必要ないと思うけど）
            changeHeadingUpMode()
        } else {
            btnHeadingUpMode.visibility = View.INVISIBLE
            cameraMoveDurationMs = 300
        }
    }

    fun onClickMapStandard(view: android.view.View) {
        val mapType = "MAP_TYPE_NORMAL"
        Utils.setPreferenceString("mapType", mapType)
        changeMapType(mapType)
    }

    fun onClickMapSatellite(view: android.view.View) {
        val mapType = "MAP_TYPE_SATELLITE"
        Utils.setPreferenceString("mapType", mapType)
        changeMapType(mapType)
    }

    fun onClickMapTerrain(view: android.view.View) {
        val mapType = "MAP_TYPE_TERRAIN"
        Utils.setPreferenceString("mapType", mapType)
        changeMapType(mapType)
    }

    private fun changeMapType(mapType: String? = null) {
        val mapType2 = mapType ?: Utils.getPreferenceString("mapType", "MAP_TYPE_NORMAL")
        if (mapType2 == "MAP_TYPE_SATELLITE") {
            mMap.mapType = GoogleMap.MAP_TYPE_SATELLITE
        } else if (mapType2 == "MAP_TYPE_TERRAIN") {
            mMap.mapType = GoogleMap.MAP_TYPE_TERRAIN
        } else /*if (mapType2 == "MAP_TYPE_NORMAL") */ {
            mMap.mapType = GoogleMap.MAP_TYPE_NORMAL
        }
        setMapLayersBtnOnOff()
    }

    fun onClickMapTraffic(view: android.view.View) {
        val bTraffic = !mMap.isTrafficEnabled
        Utils.setPreferenceBoolean("mapLayerTraffic", bTraffic)
        changeMapLayer(bTraffic)
    }

    private fun changeMapLayer(bTraffic: Boolean? = null) {
        val bTraffic2 = bTraffic ?: Utils.getPreferenceBoolean("mapLayerTraffic", false)
        mMap.isTrafficEnabled = bTraffic2
        setMapLayersBtnOnOff()
    }

    private fun changeViewMode(bChange:Boolean) {
        var cellInfoViewLarge = Utils.getPreferenceBoolean("cellInfoViewLarge", false)
        var lcidViewVisible = Utils.getPreferenceBoolean("lcidViewVisible", false)

        if ( bChange){
            if (cellInfoViewLarge) {
                cellInfoViewLarge = false
            } else {
                if ( lcidViewVisible ) {
                    cellInfoViewLarge = true
                    lcidViewVisible = false
                } else {
                    lcidViewVisible = true
                }
            }
            Utils.setPreferenceBoolean("cellInfoViewLarge", cellInfoViewLarge)
            Utils.setPreferenceBoolean("lcidViewVisible", lcidViewVisible)
        }

        cellInfoView.bLarge = cellInfoViewLarge
        lcidView.visibility = when {
            lcidViewVisible -> View.VISIBLE
            else -> View.GONE
        }
        reCalcCellInfoViewLayout2()
    }

    fun onClickMyMap1(view: android.view.View) {
        myMapOnOff(0)
    }

    fun onClickMyMap2(view: android.view.View) {
        myMapOnOff(1)
    }

    fun onClickMyMap3(view: android.view.View) {
        myMapOnOff(2)
    }

    fun onClickMyMap4(view: android.view.View) {
        myMapOnOff(3)
    }

    fun onClickMyMap5(view: android.view.View) {
        myMapOnOff(4)
    }

    private fun myMapOnOff(index: Int) {
        var btnMyMap: View = findViewById(btnMyMapAry[index])
        val app = MyApplication.getInstance()
        val key = app.myMapManager.myMapUrlKeys[index]
        val keyE = key + "Enable"

        val enable = !Utils.getPreferenceBoolean(keyE, false)
        Utils.setPreferenceBoolean(keyE, enable)

        setViewOnOff(enable, btnMyMap)

        if (Utils.checkMyMapMid(key)) {
            scope.launch {
                reloadMyMap(index, key, false)
            }
        } else {
            app.myMapManager.clearMyMap(index)
        }
    }

    private fun myToastShow(text: CharSequence, duration: Int) {
        myToast?.cancel()
        myToast = Toast.makeText(applicationContext, text, duration)
        myToast?.show()
    }

    fun onClickDummy(view: android.view.View) {
        // 裏を押さないようにするためのダミー
    }

    fun onClickBtnHeadingUpMode(view: android.view.View) {
        if ( !magneticSensorExists) {
            myToastShow("地磁気センサー非搭載機種のため切り替えできません",Toast.LENGTH_SHORT)
            return
        }


        val headingUpMode = Utils.getPreferenceString("headingUpMode", "GPS")
        val headingUpModeNow = if (headingUpMode == "GEOMAGNETISM") "GPS" else "GEOMAGNETISM"

        if (headingUpModeNow == "GEOMAGNETISM") {
            myToastShow("ヘディングアップ 地磁気\n（徒歩向け）", Toast.LENGTH_SHORT)
        } else /*if (headingUpMode2 == "GPS") */ {
            myToastShow("ヘディングアップ  GPS\n（車向け）", Toast.LENGTH_SHORT)
        }
        Utils.setPreferenceString("headingUpMode", headingUpModeNow)
        changeHeadingUpMode(headingUpModeNow)
    }

    private fun changeHeadingUpMode(headingUpMode: String? = null) {
        val headingUpModeNow = headingUpMode ?: Utils.getPreferenceString("headingUpMode", "GPS")
        var icon: Int
        if (headingUpModeNow == "GEOMAGNETISM") {
            cameraMoveDurationMs = 50 //0
            icon = R.drawable.walk
        } else /*if (headingUpMode2 == "GPS") */ {
            cameraMoveDurationMs = 300
            icon = R.drawable.car
        }
        btnHeadingUpMode.setImageResource(icon)
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
            System.arraycopy(event.values, 0, accelerometerReading, 0, accelerometerReading.size)
            //Log.d("onSensorChanged", "A")
            val mills = System.currentTimeMillis()
            val dt = mills - prefAccelerometerMillis
            prefAccelerometerMillis = mills
            // （dtの分母が大きいほど緩やか。）
            val alpha = max(1 - dt / 100f, 0f)
            // ローパスフィルタ(Matrixの各値をそれぞれLPF)
            for (i in 0 until magnetometerReading.size) {
                accelerometerReadingLpf[i] =
                    alpha * accelerometerReadingLpf[i] + (1 - alpha) * accelerometerReading[i]
            }
            updateFromAccelerometerAndMagneticField()
        } else if (event.sensor.type == Sensor.TYPE_MAGNETIC_FIELD) {
            System.arraycopy(event.values, 0, magnetometerReading, 0, magnetometerReading.size)
            //Log.d("onSensorChanged", "M")
            val mills = System.currentTimeMillis()
            val dt = mills - prefMagnetometerMillis
            prefMagnetometerMillis = mills
            val alpha = max(1 - dt / 10f, 0f)
            // ローパスフィルタ(Matrixの各値をそれぞれLPF)
            for (i in 0 until magnetometerReading.size) {
                magnetometerReadingLpf[i] =
                    alpha * magnetometerReadingLpf[i] + (1 - alpha) * magnetometerReading[i]
            }
            //updateFromAccelerometerAndMagneticField()
        } else if (event.sensor.type == Sensor.TYPE_ROTATION_VECTOR) {
            SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
            updateFromRotationMatrix()
        }
    }

    override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {
    }

    private fun updateFromAccelerometerAndMagneticField() {
        SensorManager.getRotationMatrix(
            rotationMatrix,
            null,
            accelerometerReadingLpf,
            magnetometerReadingLpf
        )

        updateFromRotationMatrix()
    }

    private fun updateFromRotationMatrix() {
        val mills = System.currentTimeMillis()
        if (bMoveCurrentLocation && lastLocation != null) {
            // 現在地連動モード かつ 位置特定済み
            if (100 < mills - prefSensorRotateMillis) {
                prefSensorRotateMillis = mills
                // 前回の回転から一定時間たっている
                val headingUp = Utils.getPreferenceBoolean("headingUp", false)
                val headingUpMode = Utils.getPreferenceString("headingUpMode", "GPS")
                if (headingUp && headingUpMode == "GEOMAGNETISM") {
                    // ヘディングアップ かつ 地磁気によるヘディングアップ

                    // 地図を回転する
                    val latlng = LatLng(lastLocation!!.latitude, lastLocation!!.longitude)
                    val geoBearing = geomagnetismBearing()
                    val mapBearing = mMap.cameraPosition.bearing
                    val diffDeg = Utils.normalizeDegree(geoBearing - mapBearing)
                    if (1 < diffDeg && diffDeg < 359) {
                        cameraMove(true, true, latlng, geoBearing)
                    }
                }
            }
        }
    }
}