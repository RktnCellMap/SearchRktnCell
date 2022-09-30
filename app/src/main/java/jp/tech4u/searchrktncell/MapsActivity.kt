package jp.tech4u.searchrktncell

import android.Manifest
import android.content.*
import android.content.pm.PackageManager
import android.graphics.*
import android.graphics.drawable.Animatable
import android.location.Location
import android.os.*
import android.telephony.*
import android.view.*
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.*
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.*
import jp.tech4u.searchrktncell.data.CellId
import jp.tech4u.searchrktncell.data.LogVO
import jp.tech4u.searchrktncell.setting.SettingsActivity
import jp.tech4u.searchrktncell.util.*
import jp.tech4u.searchrktncell.util.Utils
import org.xmlpull.v1.XmlPullParserException
import java.io.File
import java.io.IOException
import java.net.URI
import java.text.SimpleDateFormat
import java.util.*

class MapsActivity : AppCompatActivity(),
    OnMapReadyCallback,
    GoogleMap.OnCameraMoveStartedListener,
    GoogleMap.OnMyLocationButtonClickListener,
    SharedPreferences.OnSharedPreferenceChangeListener {

    companion object {
        // デバッグ用ここから
        //private const val DEBUG_LOAD_LOG_FILENAME = "log_20210105_higashiyamato_yamada_with_au.csv"
        //private const val DEBUG_LOAD_LOG_FILENAME = "log_20210105_higashiyamato_yamada.csv"
        //private const val DEBUG_LOAD_LOG_FILENAME = "log_20210201_sayama_iruma.csv"
        private const val DEBUG_LOAD_LOG_FILENAME = ""    // リリースはコレ

        //const val DEBUG_TRACE_LOG = true    // CellInfoViewクリックでログをトレース
        const val DEBUG_TRACE_LOG = false    // リリースはコレ

        private const val DEBUG_TRACE_LOG_START_INDEX = 1 // ↑がfalseなら関係ない
        // デバッグ用ここまで

        private const val PERMISSION_REQUEST_CODE = 44011   // なんでもいい
        private const val MLS_JSON_FILENAME = "mls44011.json"
        private const val MLS_JSON_URL = "https://cellmap.rukihena.com/$MLS_JSON_FILENAME"
        private lateinit var instance: MapsActivity

        // 地図表示初期位置
        private val initialMapPos = LatLng(36.0, 140.0)
        private const val initialMapZoom = 7f

        // あんまりやりたくないけど、みんなコレどうしてんだろう？
        fun getInstance(): MapsActivity {
            return instance
        }
    }

    private var debugCellIndex = if (DEBUG_TRACE_LOG) DEBUG_TRACE_LOG_START_INDEX else -1

    private val handler = Handler(Looper.getMainLooper())

    private lateinit var cellInfoView: CellInfoView
    private lateinit var txtCellName: TextView
    private lateinit var btnGps:ImageButton

    private lateinit var mMap: GoogleMap
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback

    private var lastLocation: Location? = null
    private lateinit var recordManager: RecordManager
    private lateinit var mlsManager: MlsManager
    private var myMapManager = MyMapManager()

    private var bMoveCurrentLocation = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        instance = this
        setContentView(R.layout.activity_maps)

        setScreenOn()

        cellInfoView = findViewById(R.id.cellInfoView)
        txtCellName = findViewById(R.id.txtCellName)
        btnGps = findViewById(R.id.btnGps)

        cellInfoView.setOnTouchListener { v, event ->
            // ... Respond to touch events
            v.performClick()
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    println("Action was DOWN")
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    println("Action was MOVE")
                    true
                }
                MotionEvent.ACTION_UP -> {
                    println("Action was UP")

                    if (DEBUG_TRACE_LOG) {
                        // 現在地、電測地をログからアレする
                        debug_nextLoc()
                    }
                    true
                }
                MotionEvent.ACTION_CANCEL -> {
                    println("Action was CANCEL")
                    true
                }
                MotionEvent.ACTION_OUTSIDE -> {
                    println("Movement occurred outside bounds of current screen element")
                    true
                }
                else -> super.onTouchEvent(event)
            }
        }

        btnGps.setOnClickListener { v ->
            enableLocation()
        }
        //btnGps.setImageResource(R.drawable.gps_not_fixed)
        (btnGps.drawable as Animatable).start()

        val br: BroadcastReceiver = PowerConnectionReceiver()
        val filter = IntentFilter()
        //filter.addAction(Intent.ACTION_POWER_CONNECTED)
        //filter.addAction(Intent.ACTION_POWER_DISCONNECTED)
        filter.addAction(Intent.ACTION_BATTERY_CHANGED)
        registerReceiver(br, filter)

        requestPermission()


        val tm = getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
        tm.listen(phoneStateListener, PhoneStateListener.LISTEN_CELL_INFO or PhoneStateListener.LISTEN_SIGNAL_STRENGTHS)

        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        // 訳：SupportMapFragmentを取得し、マップを使用する準備ができたら通知（訳註：onMapReady()）を受け取ります。
        val mapFragment = supportFragmentManager
            .findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)

        DownloadMlsTask().execute(false)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        createLocationCallback()
    }

    private val phoneStateListener = object : PhoneStateListener() {
        override fun onCellInfoChanged(cellInfo: MutableList<CellInfo>?) {
            super.onCellInfoChanged(cellInfo)
            println("onCellInfoChanged")
            cellInfo ?: return  // SIMを抜いたときはnullなので
            if (lastLocation != null) {
                var latlng = LatLng(lastLocation!!.latitude, lastLocation!!.longitude)
                onDataEvent(cellInfo, latlng)
            }
        }

        override fun onSignalStrengthsChanged(signalStrength: SignalStrength?) {
            super.onSignalStrengthsChanged(signalStrength)
            println("onSignalStrengthsChanged")
            if (ActivityCompat.checkSelfPermission(
                    getInstance(),
                    Manifest.permission.ACCESS_FINE_LOCATION
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                return
            }
            if (lastLocation != null) {
                var latlng = LatLng(lastLocation!!.latitude, lastLocation!!.longitude)
                val tm = getSystemService(TELEPHONY_SERVICE) as TelephonyManager
                onDataEvent(tm.allCellInfo, latlng)
            }
        }
    }

    // デバッグ用。
    private fun debug_nextLoc() {
        val lci = if (debugCellIndex < 0) {
            debugCellIndex++
            0
        } else recordManager.logList[debugCellIndex].ci

        while (debugCellIndex < recordManager.logList.size) {
            if (recordManager.logList[debugCellIndex].ci != lci) break
            debugCellIndex++
        }

        if (recordManager.logList.size <= debugCellIndex) {
            debugCellIndex = 0
        }
        println("debugCellIndex:$debugCellIndex")
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        println("onCreateOptionsMenu")
        val inflater = menuInflater
        inflater.inflate(R.menu.main, menu)
        return true
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        val mid = getMyMapMid()
        menu.findItem(R.id.reloadMyMap).isEnabled = mid != ""
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        println("onOptionsItemSelected")
        println(item)

        when (item.itemId) {
            R.id.reloadMls -> {
                DownloadMlsTask().execute(true)
            }
            R.id.reloadMyMap -> {
                val mid = getMyMapMid()
                if (mid != "") {
                    DownloadMyMapTask().execute(mid as Object, true as Object)
                } else {
                    // 未設定の場合。メニューを非活性にしているので、ここには来ないと思う。
                }
            }
            R.id.settings -> {
                startActivity(Intent(this@MapsActivity, SettingsActivity::class.java))
            }
        }

        return true
    }

    private fun onLocation(location: Location) {
        var latlng = LatLng(location.latitude, location.longitude)
        println("${latlng.latitude} , ${latlng.longitude}")

        if (mMap.cameraPosition.zoom == initialMapZoom) {
            // 現在地 初回
            mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(latlng, 15f))
        }
        lastLocation = location
        val tm = getSystemService(TELEPHONY_SERVICE) as TelephonyManager
        val cellInfoAry = tm.allCellInfo
        onDataEvent(cellInfoAry, latlng)
    }

    private fun onDataEvent(cellInfoAry: List<CellInfo>, latlng: LatLng) {
        val activeMnc = getActiveMnc(cellInfoAry)

        var cellid = 0L
        for (cellInfo in cellInfoAry) {
            if (!cellInfo.isRegistered) continue
            if (cellInfo.mncString != activeMnc) continue

            if (cellInfo is CellInfoLte) {
                // 4G
                drawCellInfoView(cellInfo)
            } else if (29 <= Build.VERSION.SDK_INT && cellInfo is CellInfoNr) {
                // 5G
                drawCellInfoView(cellInfo as CellInfoNr)
            } else if (cellInfo is CellInfoWcdma) {
                // 3G
                drawCellInfoView(cellInfo)
            }

            val logVO = LogVO(
                (System.currentTimeMillis() / 1000).toInt(),
                latlng.latitude, latlng.longitude,
                cellInfo.mccString, cellInfo.mncString,
                cellInfo.tac, cellInfo.ci,
                cellInfo.rsrp, cellInfo.bandwidth, cellInfo.timingAdvance
            )
            cellid = cellInfo.ci

            if (logVO != null) {
                val bPlotPartner = Utils.getPreferenceBoolean("plotPartner", false)
                val bPlotMyMapCell = Utils.getPreferenceBoolean("plotMyMapCell", true)

                recordManager.plotAndLog(logVO, bPlotPartner, bPlotMyMapCell)

                val logFile = createLogFileObj()
                recordManager.writeCsvLog(logVO, logFile)
            }
        }

        //cellid = 184026*256+21 // docomo  dummy cell
        //cellid = 279785*256+12 // rakuten dummy cell
        var latlngForDisp = latlng
        if (0 <= debugCellIndex && debugCellIndex < recordManager.logList.size) {
            val lvo = recordManager.logList[debugCellIndex]
            cellid = lvo.ci
            latlngForDisp = LatLng(lvo.lat, lvo.lng)
            val bw = if (lvo.bw == CellInfo.UNAVAILABLE) "-" else "${lvo.bw / 1000}MHz"
            val ta = if (lvo.ta == CellInfo.UNAVAILABLE) "-" else lvo.ta.toString()
            val bgColor = getColorByMnc(lvo.mccString, lvo.mncString)
            val cvo = CellInfoViewVO(
                if (lvo.rsrp < 0) lvo.rsrp else null,
                "eNB-LCID: ${cellid / 256}-${cellid % 256}",
                "PCI: 999  BW: $bw",
                "TA: $ta",
                bgColor
            )
            drawCellInfoView(cvo)
            val bPlotPartner = Utils.getPreferenceBoolean("plotPartner", false)
            val bPlotMyMapCell = Utils.getPreferenceBoolean("plotMyMapCell", true)
            recordManager.plotAndLog(lvo, bPlotPartner, bPlotMyMapCell)
            cvo.rsrp
        }

        // 圏外の場合は、上のビューに圏外表示
        if (cellid == 0L) drawCellInfoViewOutOfService()

        //if (myMapManager == null) return

        //// 基地局表示
        // マイマップにある場合はソレ
        val bExist = myMapManager.drawLine(latlngForDisp, cellid, mMap)
        if (!bExist) {
            // マイマップにない場合はMLSから
            mlsManager.drawMlsCells(latlngForDisp, cellid, mMap)
        } else {
            mlsManager.removeMls()
        }

        if (Utils.getPreferenceBoolean("displayCellName", true)) {
            txtCellName.text = myMapManager.getName(cellid)
        } else {
            txtCellName.text = ""
        }

        txtCellName.setBackgroundColor(
            if (txtCellName.text == "") Color.TRANSPARENT else Color.argb(0xa0, 255, 255, 255)
        )


        // 地図表示位置反映
        if (bMoveCurrentLocation) moveTo(latlngForDisp)
    }

    private fun getActiveMnc(cellInfoAry: List<CellInfo>): String? {
        val listMnc = mutableListOf<String?>()
        for (cellInfo in cellInfoAry) {
            if (cellInfo.isRegistered) {
                val mncString = cellInfo.mncString
                if (mncString != null && mncString != "") {
                    listMnc.add(mncString)
                }
            }
        }

        // 採用するMNCを決める
        val activeMnc = when (listMnc.size) {
            0 -> null // 圏外
            1 -> listMnc[0] // 1個の場合はそれ
            else -> {
                // 複数ある場合は、下記優先順位順で
                when {
                    listMnc.contains("11") -> "11"
                    listMnc.contains("53") -> "53"
                    listMnc.contains("50") -> "50"
                    else -> listMnc[0]  // 決められない場合は最初に見つかったモノ
                }
            }
        }
        return activeMnc
    }

    private fun createLogFileObj(): File {
        val df = SimpleDateFormat("yyyyMMdd")
        val yyyyMMdd = df.format(Date())
        return File(applicationContext.externalCacheDir, "log_$yyyyMMdd.csv")
    }

    data class CellInfoViewVO(
        val rsrp: Int?,
        val l1: String,
        val l2: String,
        val r: String,
        val bgColor: Int?
    )

    // 4G
    private fun drawCellInfoView(cellInfo: CellInfoLte?) {
        if (cellInfo != null) {
            val cid = cellInfo.cellIdentity
            val css = cellInfo.cellSignalStrength
            val enb = cid.ci ushr 8
            val lcid = cid.ci and 255
            val bw = cellInfo.bandwidth

            val bwStr = if (bw != CellInfo.UNAVAILABLE) {
                (bw / 1000).toString() + "MHz"
            } else {
                "-"
            }
            val ta =
                if (css.timingAdvance != CellInfo.UNAVAILABLE) css.timingAdvance.toString() else "-"

            val rsrp = if (css.rsrp != CellInfo.UNAVAILABLE) css.rsrp else null
            val bgColor = getColorByMnc(cellInfo.mccString, cellInfo.mncString)
            val vo = CellInfoViewVO(
                rsrp,
                "eNB-LCID: ${enb}-${lcid}",
                "PCI: ${cid.pci}  BW: $bwStr",
                "TA: $ta",
                bgColor
            )
            drawCellInfoView(vo)
        } else {
            drawCellInfoViewOutOfService()
        }
    }

    private fun drawCellInfoViewOutOfService() {
        val vo = CellInfoViewVO(
            null,
            "eNB-LCID: -",
            "PCI: -  BW: -",
            "TA: -",
            ContextCompat.getColor(applicationContext, R.color.colorForeign)
        )
        drawCellInfoView(vo)
    }

    // 5G
    private fun drawCellInfoView(cellInfo: CellInfoNr?) {
        val vo = if (cellInfo != null) {
            val cid = cellInfo.cellIdentity as CellIdentityNr
            val css = cellInfo.cellSignalStrength as CellSignalStrengthNr
            val bw = cellInfo.bandwidth

            val bwStr = if (bw != CellInfo.UNAVAILABLE) {
                (bw / 1000).toString() + "MHz"
            } else {
                "-"
            }
            val ta = "-"

            val rsrp = if (css.csiRsrp != CellInfo.UNAVAILABLE) css.csiRsrp else null
            val bgColor = getColorByMnc(cellInfo.mccString, cellInfo.mncString)
            CellInfoViewVO(
                rsrp,
                "NCI: ${cid.nci}",
                "PCI: ${cid.pci}  BW: $bwStr",
                "TA: $ta",
                bgColor
            )
        } else {
            CellInfoViewVO(
                null,
                "NCI: -",
                "PCI: -  BW: -",
                "TA: -",
                null
            )
        }
        drawCellInfoView(vo)
    }

    // 3G
    private fun drawCellInfoView(cellInfo: CellInfoWcdma?) {
        val vo = if (cellInfo != null) {
            val ci = cellInfo.cellIdentity
            val css = cellInfo.cellSignalStrength
            val rnc = ci.cid ushr 16
            val cid = ci.cid and 65535

            var rsrp = if (css.dbm != CellInfo.UNAVAILABLE) css.dbm else null
            val bgColor = getColorByMnc(cellInfo.mccString, cellInfo.mncString)

            CellInfoViewVO(
                rsrp,
                "LAC-UCID: ${ci.lac}-${ci.cid}",
                "RNC-CID: ${rnc}-${cid}",
                "",
                bgColor
            )
        } else {
            CellInfoViewVO(
                null,
                "LAC-UCID: -",
                "RNC-CID:  -",
                "",
                null
            )
        }
        drawCellInfoView(vo)
    }

    private fun drawCellInfoView(vo: CellInfoViewVO) {
        vo.bgColor?.let { cellInfoView.setBackgroundColor(it) }
        cellInfoView.cellRsrp = vo.rsrp
        cellInfoView.stringL1 = vo.l1
        cellInfoView.stringL2 = vo.l2
        cellInfoView.stringR = vo.r
        cellInfoView.invalidate()
    }

    private fun getColorByMnc(mccString: String?, mncString: String?): Int {
        val context = applicationContext
        return when (mccString) {
            "440" -> {
                when (mncString) {
                    "11" -> ContextCompat.getColor(context, R.color.colorRakuten)
                    "50", "51", "52", "53" -> ContextCompat.getColor(context, R.color.colorAu)
                    "10" -> ContextCompat.getColor(context, R.color.colorDocomo)
                    else -> ContextCompat.getColor(context, R.color.colorSoftbank)
                }
            }
            "441" -> {
                when (mncString) {
                    "0" -> ContextCompat.getColor(context, R.color.colorSoftbank)
                    "10" -> ContextCompat.getColor(context, R.color.colorAu)
                    else -> ContextCompat.getColor(context, R.color.colorForeign)
                }
            }
            else -> ContextCompat.getColor(context, R.color.colorForeign)
        }
    }

    // 地図の表示位置を現在地に移動する
    private fun moveTo(latlng: LatLng) {
        btnGps.setImageResource(R.drawable.gps_fixed)

        // 現在地追従モードが基地局連動かどうか
        val bMode = Utils.getPreferenceBoolean("locationMode", true)

        if (bMode && myMapManager.myMapLL != null) {
            // 基地局連動モードで、マイマップから基地局発見の場合
            val llBuilder = LatLngBounds.Builder()
            llBuilder.include(myMapManager.myMapLL!!)
            llBuilder.include(latlng)
            // 拡大しすぎないように最低限のエリアを含める
            llBuilder.include(LatLng(latlng.latitude - 0.005, latlng.longitude - 0.005))
            llBuilder.include(LatLng(latlng.latitude + 0.005, latlng.longitude + 0.005))

            mMap.animateCamera(CameraUpdateFactory.newLatLngBounds(llBuilder.build(), 100), 300, null)
        } else if (bMode && mlsManager.foundMlsBounds != null) {
            // 基地局連動モードで、（マイマップから基地局発見できず、）MLSから基地局発見の場合
            val llBuilder = LatLngBounds.Builder()
            llBuilder.include(mlsManager.foundMlsBounds!!.northeast)
            llBuilder.include(mlsManager.foundMlsBounds!!.southwest)
            llBuilder.include(latlng)
            // 拡大しすぎないように最低限のエリアを含める
            llBuilder.include(LatLng(latlng.latitude - 0.005, latlng.longitude - 0.005))
            llBuilder.include(LatLng(latlng.latitude + 0.005, latlng.longitude + 0.005))

            mMap.animateCamera(CameraUpdateFactory.newLatLngBounds(llBuilder.build(), 100), 300, null)
        } else {
            mMap.animateCamera(CameraUpdateFactory.newLatLng(latlng), 300, null)
        }
    }

    private fun createLocationCallback() {

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult?) {
                locationResult ?: return
                val location = locationResult.lastLocation
                location ?: return

                onLocation(location)
            }
        }
    }

    private fun readLogAndPutMarker() {
        println("＊＊＊＊＊＊＊＊＊＊＊＊＊＊＊＊＊＊＊＊＊readLogAndPutMarker")
        val context = applicationContext

        val logFile = if (DEBUG_LOAD_LOG_FILENAME == "") createLogFileObj()
        else File(context.externalCacheDir, DEBUG_LOAD_LOG_FILENAME)

        recordManager.readLog(logFile)

        val bPlotPartner = Utils.getPreferenceBoolean("plotPartner", false)
        val bPlotMyMapCell = Utils.getPreferenceBoolean("plotMyMapCell", true)
        recordManager.plotAll(CellId(0), bPlotPartner, bPlotMyMapCell)
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
    }

    override fun onResume() {
        super.onResume()
        startLocationUpdates()
    }

    override fun onPause() {
        super.onPause()
        stopLocationUpdates()
    }

    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap
        myMapManager.init(mMap)

        // マーカータップ時にズームボタンの左に表示されるツールバー（いらない）
        mMap.uiSettings.isMapToolbarEnabled = false
        // 現在地ボタン（見た目の変え方が分からんので独自ボタンを置いた）
        mMap.uiSettings.isMyLocationButtonEnabled = false

        //mMap.isMyLocationEnabled = true   // ←権限的にそう簡単にいかない・・・
        setMyLocationEnabled()  // 権限考慮版

        mMap.setOnCameraMoveStartedListener(this)
        mMap.setOnMyLocationButtonClickListener(this)

        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(initialMapPos, initialMapZoom))

        recordManager = RecordManager(mMap, myMapManager)
        readLogAndPutMarker()

        val mid = getMyMapMid()
        if (mid != "") {
            DownloadMyMapTask().execute(mid as Object, false as Object)
        }
    }

    // 設定からマイマップのMIDを取得する
    private fun getMyMapMid(): String {
        var mid = ""
        try {
            val myMapUrl = Utils.getPreferenceString("myMapUrl", null)
            if (myMapUrl != null) {
                val myMapUrlTrimmed = myMapUrl.trim()
                if (myMapUrlTrimmed.length == 33) {
                    // 33文字の場合はそのまま
                    mid = myMapUrlTrimmed
                } else {
                    // それ以外の場合は、URIを解析して引数midを取得
                    val uri = URI(myMapUrlTrimmed)
                    val query = uri.query
                    val queryList = query.split("&")
                    for (q in queryList) {
                        val kv = q.split("=")
                        if (kv[0] == "mid") {
                            mid = kv[1]
                        }
                    }
                }
            }
        } catch (e: Exception) {
            // マイマップURI解析失敗
            // ユーザーへの通知は、、、まあいいや・・・
        }
        return mid
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
            //fastestInterval = 1000
            priority = LocationRequest.PRIORITY_HIGH_ACCURACY
        }
    }

    override fun onCameraMoveStarted(reason: Int) {
        println("onCameraMoveStarted:$reason")
        if (reason != GoogleMap.OnCameraMoveStartedListener.REASON_DEVELOPER_ANIMATION) {
            bMoveCurrentLocation = false
            btnGps.setImageResource(R.drawable.gps_off)
        }
        resetLocationTimer()
    }

    private fun resetLocationTimer() {
        handler.removeCallbacksAndMessages(null)    // もしCallBackがあれば全削除
        if (Utils.getPreferenceBoolean("locationTimer", false)) {
            // 現在地連動タイマースタート
            val secs = Utils.getPreferenceInt("locationTimerSecs", 60)
            handler.postDelayed({ enableLocation() }, secs * 1000L)
        }
    }

    override fun onMyLocationButtonClick(): Boolean {
        println("onMyLocationButtonClick")
        enableLocation()
        return false
    }

    // 現在地追従を有効にする
    private fun enableLocation()
    {
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

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        if (sharedPreferences != null) {
            when (key) {
                "myMapUrl" -> {
                    val mid = getMyMapMid()
                    if (mid != "") {
                        DownloadMyMapTask().execute(mid as Object, true as Object)
                    } else {
                        Toast.makeText(applicationContext, "マイマップURL未設定（またはエラー）", Toast.LENGTH_LONG).show()
                    }
                }
                "refreshInterval" -> {
                    println("onSharedPreferenceChanged(" + sharedPreferences.getInt(key, -1) + ",$key)")
                    // 一旦止めて再度動かすことで、更新間隔を反映する
                    stopLocationUpdates()
                    startLocationUpdates()
                }
                "keepScreenOn" -> {
                    setScreenOn()
                }
                "locationTimer", "locationTimerSecs" -> {
                    resetLocationTimer()
                }
                "plotPartner", "plotMyMapCell" -> {
                    rePlot()
                }
                else -> {
                    println("onSharedPreferenceChanged($key)")
                }
            }
        }
    }

    private fun rePlot() {
        if (recordManager.logList.count() == 0) {
            return
        }
        recordManager.clearMarkers()
        val bPlotPartner = Utils.getPreferenceBoolean("plotPartner", false)
        val bPlotMyMapCell = Utils.getPreferenceBoolean("plotMyMapCell", true)
        recordManager.plotAll(CellId(recordManager.logList.last().ci), bPlotPartner, bPlotMyMapCell)
    }

    private inner class DownloadMlsTask : AsyncTask<Boolean, Void, String>() {
        override fun doInBackground(vararg args: Boolean?): String {
            val bReload = args[0]!!
            val context = applicationContext
            val cacheFile = File(context.cacheDir, MLS_JSON_FILENAME)
            mlsManager = MlsManager()
            return try {
                mlsManager.readMls(MLS_JSON_URL, cacheFile, bReload)
                "OK"
            } catch (e: MyException) {
                e.message ?: ""
            } catch (e: IOException) {
                "MLSダウンロード失敗"
            } catch (e: Exception) {
                "MLS読み込み失敗"
            }
        }

        override fun onPostExecute(result: String) {
            if (result != "OK") {
                Toast.makeText(applicationContext, result, Toast.LENGTH_SHORT).show()
            }
            println(result)
        }
    }

    private inner class DownloadMyMapTask : AsyncTask<Object, Void, String>() {
        override fun doInBackground(vararg args: Object?): String? {
            return try {
                val key = args[0].toString()
                val bReload = args[1] as Boolean
                val context = applicationContext
                val cacheFile = File(context.cacheDir, key + ".kmz")
                myMapManager.readMyMap(key, cacheFile, bReload)
                "OK"
            } catch (e: MyException) {
                e.message ?: ""
            } catch (e: IOException) {
                "マイマップダウンロード失敗"//resources.getString(R.string.connection_error)
            } catch (e: XmlPullParserException) {
                "マイマップ解析失敗"//resources.getString(R.string.xml_error)
            } catch (e: Exception) {
                "マイマップ読み込み失敗"//resources.getString(R.string.xml_error)
            }
        }

        override fun onPostExecute(result: String) {

            if (result == "OK") {
                myMapManager.plotPlacemark()
                val bPlotMyMapCell = Utils.getPreferenceBoolean("plotMyMapCell", true)
                if (!bPlotMyMapCell) {
                    // マイマップが判明したので、消さなきゃいけない場合があるので、リプロットする
                    rePlot()
                }
            } else {
                Toast.makeText(applicationContext, result, Toast.LENGTH_SHORT).show()
            }
            println(result)
        }
    }

    // 電源状態の変更ハンドラ
    inner class PowerConnectionReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            // 設定読み込み
            val keepScreenOn = Utils.getPreferenceString("keepScreenOn", "OFF")

            if (keepScreenOn == "POWER") {
                // 設定が電源連動だった場合のみ、画面OK設定へ反映
                setScreenOn(intent)
            }
        }
    }

    // 常時画面ONの設定をする（設定とAC電源の状態を見て適切に設定する）
    private fun setScreenOn() {
        val keepScreenOn = Utils.getPreferenceString("keepScreenOn", "OFF")
        when (keepScreenOn) {
            "ON" -> {
                window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
            "OFF" -> {
                window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
            "POWER" -> {
                val batteryStatus: Intent? = IntentFilter(Intent.ACTION_BATTERY_CHANGED).let { iFilter ->
                    applicationContext.registerReceiver(null, iFilter)
                }
                setScreenOn(batteryStatus)
            }
        }
    }

    // 常時画面ONの設定をする（AC電源の状態を見て適切に設定する）
    private fun setScreenOn(batteryStatus: Intent?) {
        if (batteryStatus == null) return

        when (batteryStatus.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0)) {
            0 -> {
                window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
            else -> {
                window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
        }
    }
}