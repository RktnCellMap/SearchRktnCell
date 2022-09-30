package jp.tech4u.searchrktncell

import android.Manifest
import android.content.*
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.*
import android.graphics.drawable.Animatable
import android.location.Location
import android.os.*
import android.telephony.*
import android.util.Log
import android.view.*
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.google.android.gms.location.*
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.*
import jp.tech4u.searchrktncell.data.CellId
import jp.tech4u.searchrktncell.data.CellInfoViewVO
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
import java.text.SimpleDateFormat
import java.util.*
import android.content.Intent
import android.net.Uri


class MapsActivity : AppCompatActivity(),
    OnMapReadyCallback,
    GoogleMap.OnCameraMoveStartedListener,
    GoogleMap.OnMyLocationButtonClickListener,
    SharedPreferences.OnSharedPreferenceChangeListener {

    companion object {
        private const val PERMISSION_REQUEST_CODE = 44011   // なんでもいい
        private const val MLS_JSON_FILENAME = "mls44011.json"
        private const val MLS_JSON_URL = "https://cellmap.rukihena.com/$MLS_JSON_FILENAME"

        private lateinit var instance: MapsActivity

        // 地図表示初期位置
        private val initialMapPos = LatLng(36.0, 140.0)
        private const val initialMapZoom = 7f

        // root_preferences.xml のマイマップ定義の個数と合わせてください
        private val myMapUrlKeys = arrayOf("myMapUrl", "myMapUrl2", "myMapUrl3", "myMapUrl4", "myMapUrl5")

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
            if (!bPress) {
                onDataEvent(listOf(), LatLng(0.0, 0.0))
            }
            debugTimerHandler.postDelayed(this, DebugCellInfo.INTERVAL_MS)
        }
    }

    // 現在地連動タイマー用
    private val enableLocationHandler = Handler(Looper.getMainLooper())

    // UI部品
    private lateinit var cellInfoView: CellInfoView
    private lateinit var txtCellName: TextView
    private lateinit var txtLatLng: TextView
    private lateinit var btnGps: ImageButton

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var powerConnectionReceiver: PowerConnectionReceiver

    // 最後に得た現在地（PhoneStateListenerで参照）
    private var lastLatLng: LatLng? = null

    // 現在地追従するかのフラグ
    private var bMoveCurrentLocation = true

    // カメラアニメーションの時間[ms]
    private var cameraMoveDurationMs = 300

    // デバッグ用
    private var bPress = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.i(localClassName, "onCreate")
        instance = this
        setContentView(R.layout.activity_maps)

        cellInfoView = findViewById(R.id.cellInfoView)
        txtCellName = findViewById(R.id.txtCellName)
        txtLatLng = findViewById(R.id.txtLatLng)
        btnGps = findViewById(R.id.btnGps)

        cellInfoView.setOnTouchListener(onTouchListener)
        btnGps.setOnClickListener { enableLocation() }
        txtLatLng.setOnClickListener { onClickTxtLatLng() }

        //btnGps.setImageResource(R.drawable.gps_not_fixed)
        (btnGps.drawable as Animatable).start()

        powerConnectionReceiver = PowerConnectionReceiver(applicationContext, window)
        powerConnectionReceiver.setScreenOn()
        val filter = IntentFilter()
        //filter.addAction(Intent.ACTION_POWER_CONNECTED)
        //filter.addAction(Intent.ACTION_POWER_DISCONNECTED)
        filter.addAction(Intent.ACTION_BATTERY_CHANGED)
        registerReceiver(powerConnectionReceiver, filter)

        requestPermission()

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
            val file = createLogFileObj()
            file.delete()

            cameraMoveDurationMs = (DebugCellInfo.INTERVAL_MS / 3).toInt()
        }
    }

    private fun onClickTxtLatLng() {
        Log.d(localClassName, "onClickTxtLatLng")
    }

    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap
        //myMapManager.init(mMap)
        MyApplication.getInstance().mMap = mMap

        // マーカータップ時にズームボタンの左に表示されるツールバー（いらない）
        mMap.uiSettings.isMapToolbarEnabled = false
        // 現在地ボタン（見た目の変え方が分からんので独自ボタンを置いた）
        mMap.uiSettings.isMyLocationButtonEnabled = false

        //mMap.isMyLocationEnabled = true   // ←権限的にそう簡単にいかない・・・
        setMyLocationEnabled()  // 権限考慮版

        val density = resources.displayMetrics.density
        mMap.setPadding(0, (34 * density).toInt(), 0, (34 * density).toInt())
        mMap.setOnCameraMoveStartedListener(this)
        mMap.setOnMyLocationButtonClickListener(this)

        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(initialMapPos, initialMapZoom))

        readLogAndPutMarker()
        reloadMyMap(false)

        // 早めにやるとぬるぽ
        if (DebugCellInfo.DEBUG_CELL_INFO_ENABLE) {
            if (debugCellInfo != null && debugCellInfo!!.isAvailable()) {
                debugTimerHandler.postDelayed(debugTimerRunnable, 5000L)
            }
        }
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
        Log.i(localClassName, "onRestart")
        when (newConfig.orientation) {
            Configuration.ORIENTATION_LANDSCAPE -> supportActionBar!!.hide()
            else -> supportActionBar!!.show()
        }
    }

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(locationResult: LocationResult?) {
            locationResult ?: return
            val location = locationResult.lastLocation
            location ?: return

            onLocation(location)
        }
    }

    private val onTouchListener = View.OnTouchListener { v, event ->
        // ... Respond to touch events
        v.performClick()
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                Log.i(localClassName, "Action was DOWN")
                bPress = true
                true
            }
            MotionEvent.ACTION_MOVE -> {
                Log.i(localClassName, "Action was MOVE")
                true
            }
            MotionEvent.ACTION_UP -> {
                Log.i(localClassName, "Action was UP")
                bPress = false
                true
            }
            MotionEvent.ACTION_CANCEL -> {
                Log.i(localClassName, "Action was CANCEL")
                bPress = false
                true
            }
            MotionEvent.ACTION_OUTSIDE -> {
                Log.i(localClassName, "Movement occurred outside bounds of current screen element")
                bPress = false
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
            if (lastLatLng != null) {
                val latlng = LatLng(lastLatLng!!.latitude, lastLatLng!!.longitude)
                onDataEvent(cellInfo, latlng)
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
            if (lastLatLng != null) {
                val latlng = LatLng(lastLatLng!!.latitude, lastLatLng!!.longitude)
                val tm = getSystemService(TELEPHONY_SERVICE) as TelephonyManager
                onDataEvent(tm.allCellInfo, latlng)
            }
        }
    }

    private fun onLocation(location: Location) {
        val latlng = LatLng(location.latitude, location.longitude)
        Log.v(localClassName, "onLocation ${latlng.latitude} , ${latlng.longitude}")

        if (mMap.cameraPosition.zoom == initialMapZoom) {
            // 現在地 初回
            mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(latlng, 15f))
        }
        lastLatLng = LatLng(location.latitude, location.longitude)
        val tm = getSystemService(TELEPHONY_SERVICE) as TelephonyManager
        val cellInfoAry = tm.allCellInfo
        onDataEvent(cellInfoAry, latlng)
    }

    // onLocation と PhoneStateListenerのイベントから呼ばれる
    private fun onDataEvent(cellInfoAry: List<CellInfo>, pLatlng: LatLng) {
        val app = MyApplication.getInstance()

        val listLogVoNow = mutableListOf<LogVO>()
        val latlng: LatLng
        // デバッグならそれから、そうでなければ引数から採用
        if (debugCellInfo != null && debugCellInfo!!.isAvailable()) {
            // デバッグ版
            debugCellInfo!!.getLog(listLogVoNow)
            if (listLogVoNow.size != 0) {
                val lvo = listLogVoNow[0]
                val bw = if (lvo.bw == CellInfo.UNAVAILABLE) "-" else "${lvo.bw / 1000}MHz"
                val ta = if (lvo.ta == CellInfo.UNAVAILABLE) "-" else lvo.ta.toString()
                val bgColor = Utils.getColorByMnc(lvo.mccString, lvo.mncString)
                val cvo = CellInfoViewVO(
                    if (lvo.rsrp < 0) lvo.rsrp else null,
                    "eNB-LCID: ${lvo.ci / 256}-${lvo.ci % 256}",
                    "PCI: 999  BW: $bw",
                    "TA: $ta",
                    bgColor
                )
                cellInfoView.drawCellInfoView(cvo)
                val bPlotPartner = Utils.getPreferenceBoolean("plotPartner", false)
                val bPlotMyMapCell = Utils.getPreferenceBoolean("plotMyMapCell", true)
                app.recordManager.plotAndLog(mMap, lvo, bPlotPartner, bPlotMyMapCell)

                latlng = LatLng(lvo.lat, lvo.lng)
            } else {
                latlng = pLatlng
            }
        } else {
            // リリース版
            latlng = pLatlng
            val activeMnc = Utils.getActiveMnc(cellInfoAry)
            for (cellInfo in cellInfoAry) {
                // 処理しない条件
                if (!cellInfo.isRegistered) continue
                if (cellInfo.mncString != activeMnc) continue

                // CellInfoView 反映
                cellInfoView.drawCellInfoView(cellInfo)

                // データ準備
                val logVO = LogVO(
                    (System.currentTimeMillis() / 1000).toInt(),
                    latlng.latitude, latlng.longitude,
                    cellInfo.mccString, cellInfo.mncString,
                    cellInfo.tac, cellInfo.ci,
                    cellInfo.rsrp, cellInfo.bandwidth, cellInfo.timingAdvance
                )
                listLogVoNow.add(logVO)
            }
        }

        var lastLogVO: LogVO? = null
        for (logVO in listLogVoNow) {
            lastLogVO = logVO

            // 記録
            val bPlotPartner = Utils.getPreferenceBoolean("plotPartner", false)
            val bPlotMyMapCell = Utils.getPreferenceBoolean("plotMyMapCell", true)

            app.recordManager.plotAndLog(mMap, logVO, bPlotPartner, bPlotMyMapCell)

            val logFile = createLogFileObj()
            app.recordManager.writeCsvLog(logVO, logFile)
        }

        // 圏外の場合は、上のビューに圏外表示
        if (lastLogVO == null) {
            cellInfoView.drawCellInfoView(null)
            app.myMapManager.removeLocLine()
            app.mlsManager.removeMls()
        } else {
            //// 基地局表示
            // マイマップにある場合はソレ
            val bExist = app.myMapManager.drawLine(latlng, lastLogVO, mMap)
            if (!bExist) {
                // マイマップにない場合はMLSから
                app.mlsManager.drawMlsCells(latlng, lastLogVO, mMap)
            } else {
                app.mlsManager.removeMls()
            }
        }

        if (Utils.getPreferenceBoolean("displayCellName", true)) {
            if (lastLogVO != null) {
                txtCellName.text = app.myMapManager.getName(lastLogVO.ci)
            } else {
                txtCellName.text = ""
            }
        } else {
            txtCellName.text = ""
        }

        txtCellName.setBackgroundColor(
            if (txtCellName.text == "") Color.TRANSPARENT else Color.argb(0xa0, 255, 255, 255)
        )

        // 地図表示位置反映
        if (bMoveCurrentLocation) {
            moveTo(latlng)
        } else {
            txtLatLng.visibility = View.INVISIBLE
        }
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        Log.i(localClassName, "onCreateOptionsMenu")
        val inflater = menuInflater
        inflater.inflate(R.menu.main, menu)
        return true
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        // マイマップURLが1つでも設定されていたら、マイマップリロードのメニューを活性にする
        var bEnable = false
        for (key in myMapUrlKeys) {
            val mid = Utils.getMyMapMid(key)
            if (mid != "") {
                bEnable = true
                break
            }
        }
        menu.findItem(R.id.reloadMyMap).isEnabled = bEnable

        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        Log.i(localClassName, "onOptionsItemSelected")
        Log.i(localClassName, item.toString())

        when (item.itemId) {
            R.id.reloadMls -> scope.launch { readMls(false) }
            R.id.reloadMyMap -> reloadMyMap(true)
            R.id.settings -> startActivity(Intent(this@MapsActivity, SettingsActivity::class.java))
            R.id.myMapList -> openUrl("https://cellmap.rukihena.com/mymaplist.html")
            R.id.appPage -> openUrl("https://cellmap.rukihena.com/SearchRktnCell/")
        }
        return true
    }

    private fun openUrl(url:String) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        startActivity(intent)
    }

    private fun createLogFileObj(): File {
        val df = SimpleDateFormat("yyyyMMdd")
        val yyyyMMdd = df.format(Date())
        return File(applicationContext.externalCacheDir, "log_$yyyyMMdd.csv")
    }

    // 地図の表示位置を現在地に移動する
    private fun moveTo(latlng: LatLng) {
        val app = MyApplication.getInstance()

        btnGps.setImageResource(R.drawable.gps_fixed)
        txtLatLng.text = "${latlng.latitude},${latlng.longitude}"
        txtLatLng.visibility = View.VISIBLE

        // 現在地追従モードが基地局連動かどうか
        val bMode = Utils.getPreferenceBoolean("locationMode", true)

        var llBuilder: LatLngBounds.Builder? = null
        if (bMode && app.myMapManager.myMapLL != null) {
            // 基地局連動モードで、マイマップから基地局発見の場合
            llBuilder = LatLngBounds.Builder()
            llBuilder.include(app.myMapManager.myMapLL!!)
        } else if (bMode && app.mlsManager.foundMlsBounds != null) {
            // 基地局連動モードで、（マイマップから基地局発見できず、）MLSから基地局発見の場合
            llBuilder = LatLngBounds.Builder()
            llBuilder.include(app.mlsManager.foundMlsBounds!!.northeast)
            llBuilder.include(app.mlsManager.foundMlsBounds!!.southwest)
        }

        if (llBuilder != null) {
            llBuilder.include(latlng)
            // 拡大しすぎないように最低限のエリアを含める(MLSの場合はより拡大傾向)
            llBuilder.include(LatLng(latlng.latitude - 0.003, latlng.longitude - 0.003 * 0.8))
            llBuilder.include(LatLng(latlng.latitude + 0.003, latlng.longitude + 0.003 * 0.8))
            // 参考：0.00001[度]≒1.11[m]  cos(36)≒0.8

            mMap.animateCamera(CameraUpdateFactory.newLatLngBounds(llBuilder.build(), 100), cameraMoveDurationMs, null)
        } else {
            mMap.animateCamera(CameraUpdateFactory.newLatLng(latlng), cameraMoveDurationMs, null)
        }
    }

    private fun readLogAndPutMarker() {
        Log.i(localClassName, "＊＊＊＊＊＊＊＊＊＊＊＊＊＊＊＊＊＊＊＊＊readLogAndPutMarker")
        val app = MyApplication.getInstance()
        app.recordManager.readLog(createLogFileObj())
        val bPlotPartner = Utils.getPreferenceBoolean("plotPartner", false)
        val bPlotMyMapCell = Utils.getPreferenceBoolean("plotMyMapCell", true)
        app.recordManager.plotAll(mMap, CellId(0), bPlotPartner, bPlotMyMapCell)
    }

    private fun requestPermission() {
        val permissionAccessCoarseLocationApproved =
            ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) ==
                    PackageManager.PERMISSION_GRANTED &&
                    ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) ==
                    PackageManager.PERMISSION_GRANTED

        if (permissionAccessCoarseLocationApproved) {
            val backgroundLocationPermissionApproved = ActivityCompat
                .checkSelfPermission(this, Manifest.permission.ACCESS_BACKGROUND_LOCATION) ==
                    PackageManager.PERMISSION_GRANTED

            if (backgroundLocationPermissionApproved) {
                // フォアグラウンドとバックグランドのバーミッションがある
            } else {
                // フォアグラウンドのみOKなので、バックグラウンドの許可を求める
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.ACCESS_BACKGROUND_LOCATION),
                    PERMISSION_REQUEST_CODE
                )
            }
        } else {
            // 位置情報の権限が無いため、許可を求める
            ActivityCompat.requestPermissions(
                this,
                arrayOf(
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_BACKGROUND_LOCATION
                ),
                PERMISSION_REQUEST_CODE
            )
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_CODE) {
            onRequestLocationPermissionsResult(permissions, grantResults)
        }
    }

    private fun onRequestLocationPermissionsResult(permissions: Array<String>, grantResults: IntArray) {
        if (permissions.size != grantResults.size) {
            // ココには来ないはず
            return
        }
        var allow = false
        var deny = false
        val size = permissions.size
        for (i in 0 until size) {
            if (permissions[i] == Manifest.permission.ACCESS_COARSE_LOCATION || permissions[i] == Manifest.permission.ACCESS_FINE_LOCATION) {
                if (grantResults[i] == PackageManager.PERMISSION_GRANTED) {
                    allow = true
                } else {
                    deny = true
                }
            }
        }
        if (allow) {
            // 権限を取得したのでもう一度setMyLocationEnabled()を呼び出す
            setMyLocationEnabled()
        }
        // 注：else if じゃないです
        if (deny) {
            // 権限を取得できなかったので諦める
            Toast.makeText(this, "No location permissions granted", Toast.LENGTH_LONG).show()
        }
    }

    // mMap.isMyLocationEnabled の、権限考慮版
    private fun setMyLocationEnabled() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
            && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this, arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ), PERMISSION_REQUEST_CODE
            )
            return
        }
        mMap.isMyLocationEnabled = true

        // 権限が必要なので、ここでやる。
        val tm = getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
        tm.listen(phoneStateListener, PhoneStateListener.LISTEN_CELL_INFO or PhoneStateListener.LISTEN_SIGNAL_STRENGTHS)
    }

    private fun reloadMyMap(bReload: Boolean) {
        for ((index, key) in myMapUrlKeys.withIndex()) {

            val mid = Utils.getMyMapMid(key)
            if (mid != "") {
                scope.launch {
                    reloadMyMap(index, mid, bReload)
                }
            }
        }
    }

    private suspend fun reloadMyMap(index: Int, mid: String, bReload: Boolean) {
        try {
            // doInBackgroundメソッドと同等の処理
            val app = MyApplication.getInstance()
            val result = try {
                val cacheFile = File(app.applicationContext.cacheDir, "$mid.kmz")
                app.myMapManager.readMyMap(index, mid, cacheFile, bReload)
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
                    app.myMapManager.plotPlacemark(index, app.mMap)
                    val bPlotMyMapCell = Utils.getPreferenceBoolean("plotMyMapCell", true)
                    if (!bPlotMyMapCell) {
                        // マイマップが判明したので、消さなきゃいけない場合があるので、リプロットする
                        app.recordManager.rePlot(app.mMap)
                    }
                } else {
                    Toast.makeText(app.applicationContext, result, Toast.LENGTH_SHORT).show()
                }
                Log.i(localClassName, result)
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
        }
        resetLocationTimer()
    }

    private fun resetLocationTimer() {
        enableLocationHandler.removeCallbacksAndMessages(null)    // もしCallBackがあれば削除
        if (Utils.getPreferenceBoolean("locationTimer", false)) {
            // 現在地連動タイマースタート
            val secs = Utils.getPreferenceInt("locationTimerSecs", 60)
            enableLocationHandler.postDelayed({ enableLocation() }, secs * 1000L)
        }
    }

    override fun onMyLocationButtonClick(): Boolean {
        Log.i(localClassName, "onMyLocationButtonClick")
        enableLocation()
        return false
    }

    // 現在地追従を有効にする
    private fun enableLocation() {
        bMoveCurrentLocation = true
        btnGps.setImageResource(R.drawable.gps_not_fixed)
        (btnGps.drawable as Animatable).start()

        fusedLocationClient.lastLocation
            .addOnSuccessListener {
                if (it != null) {
                    val latlng = LatLng(it.latitude, it.longitude)
                    moveTo(latlng)
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
                "plotPartner", "plotMyMapCell" -> app.recordManager.rePlot(mMap)
                else -> {
                    var bMyMapUrl = false
                    for ((index, keyName) in myMapUrlKeys.withIndex()) {
                        // 該当マイマップだけリロード
                        if (key == keyName) {
                            val mid = Utils.getMyMapMid(key)
                            if (mid != "") {
                                scope.launch {
                                    reloadMyMap(index, mid, true)
                                }
                            }
                            bMyMapUrl = true
                            break
                        }
                    }
                    if (!bMyMapUrl) {
                        Log.i(localClassName, "onSharedPreferenceChanged($key)")
                    }
                }
            }
        }
    }
}