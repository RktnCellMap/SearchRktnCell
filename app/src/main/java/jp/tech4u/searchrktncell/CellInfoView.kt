package jp.tech4u.searchrktncell

import android.content.Context
import android.graphics.*
import android.text.TextPaint
import android.util.AttributeSet
import android.util.Log
import android.view.View
import androidx.core.content.ContextCompat
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.SphericalUtil
import cz.mroczis.netmonster.core.model.cell.CellLte
import cz.mroczis.netmonster.core.model.cell.CellNr
import cz.mroczis.netmonster.core.model.cell.CellWcdma
import cz.mroczis.netmonster.core.model.cell.ICell
import jp.tech4u.searchrktncell.data.CellInfoViewVO
import jp.tech4u.searchrktncell.util.*
import kotlin.math.max
import kotlin.math.min

class CellInfoView : View {

    companion object {
        private const val TAG = "CellInfoView"
        private const val RSRP_MAX = -50
        private const val RSRP_MIN = -140
    }

    private var _stringL1: String? = null
    private var _stringL2: String? = null
    private var _stringR: String? = null
    private var _cellRsrp: Int? = null

    private lateinit var barRect: RectF     // バーの内枠
    private lateinit var barPadRect: RectF  // バーの外枠
    private lateinit var textRect: RectF    // テキストエリアの矩形

    private lateinit var textBarPaint: TextPaint
    private lateinit var textPaintL: TextPaint
    private lateinit var textPaintR: TextPaint

    var bPortrait = true
    var bLarge = false

    /**
     * The text to draw
     */
    var stringL1: String?
        get() = _stringL1
        set(value) {
            _stringL1 = value
        }

    var stringL2: String?
        get() = _stringL2
        set(value) {
            _stringL2 = value
        }

    var stringR: String?
        get() = _stringR
        set(value) {
            _stringR = value
        }

    var cellRsrp: Int?
        get() = _cellRsrp
        set(value) {
            _cellRsrp = value
        }

    constructor(context: Context) : super(context) {
        init(null, 0)
    }

    constructor(context: Context, attrs: AttributeSet) : super(context, attrs) {
        init(attrs, 0)
    }

    constructor(context: Context, attrs: AttributeSet, defStyle: Int) : super(context, attrs, defStyle) {
        init(attrs, defStyle)
    }

    private fun init(attrs: AttributeSet?, defStyle: Int) {
        // Load attributes
        val a = context.obtainStyledAttributes(
            attrs, R.styleable.CellInfoView, defStyle, 0
        )

        _stringL1 = a.getString(R.styleable.CellInfoView_stringL1)
        _stringL2 = a.getString(R.styleable.CellInfoView_stringL2)
        _stringR = a.getString(R.styleable.CellInfoView_stringR)

        _cellRsrp = a.getInteger(R.styleable.CellInfoView_cellRsrp, Int.MAX_VALUE)
        if (_cellRsrp == Int.MAX_VALUE) {
            _cellRsrp = null
        }

        a.recycle()

        // Set up a default TextPaint object
        val typeface = Typeface.createFromAsset(context.assets, "Abel-Regular.ttf")
        textBarPaint = TextPaint().apply {
            flags = Paint.ANTI_ALIAS_FLAG
            textAlign = Paint.Align.CENTER
            textSize = 24f
            setTypeface(typeface)
        }

        textPaintL = TextPaint().apply {
            flags = Paint.ANTI_ALIAS_FLAG
            textAlign = Paint.Align.LEFT
            textSize = 24f
            setTypeface(typeface)
        }
        textPaintR = TextPaint().apply {
            flags = Paint.ANTI_ALIAS_FLAG
            textAlign = Paint.Align.RIGHT
            textSize = 24f
            setTypeface(typeface)
        }
    }

    private fun calcCoordinates() {
        val barArea: RectF
        val padding: Float
        if (bPortrait) {
            // 通常
            if (bLarge) {
                barArea = RectF(0f, 0f, width.toFloat() * 0.6f, height / 2.8f)
                padding = barArea.height() * 0.2f
                textRect = RectF(0f + padding, height / 2.8f, width - padding, height - padding)
            } else {
                barArea = RectF(0f, 0f, width.toFloat(), height / 2f)
                padding = barArea.height() * 0.2f
                textRect = RectF(0f + padding, height / 2f, width - padding, height - padding)
            }
        } else {
            // 横長 要修正
            val textWidth = height * 6f
            barArea = RectF(textWidth, 0f, width.toFloat(), height.toFloat())
            padding = barArea.height() * 0.2f
            textRect = RectF(padding, padding / 2, textWidth, height - padding / 2)
        }

        barRect = RectF(
            barArea.left + padding,
            barArea.top + padding,
            barArea.right - padding,
            barArea.bottom - padding,
        )

        val barPadding = barArea.height() * 0.04f
        barPadRect = RectF(
            barRect.left - barPadding,
            barRect.top - barPadding,
            barRect.right + barPadding,
            barRect.bottom + barPadding
        )
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        Log.v(TAG, "CellInfoView.onDraw()")

        textBarPaint.color = ContextCompat.getColor(context, R.color.colorCellInfoBarText)
        textPaintL.color = ContextCompat.getColor(context, R.color.colorCellInfoText)
        textPaintR.color = ContextCompat.getColor(context, R.color.colorCellInfoText)

        calcCoordinates()
        drawBar(canvas)
        drawText(canvas)
    }

    private fun darkColor(color: Int): Int {
        val hsv = floatArrayOf(0f, 0f, 0f)
        Color.colorToHSV(color, hsv)
        hsv[1] = hsv[1] / 2 // 彩度を落とす
        hsv[2] = hsv[2] / 2 // 暗くする
        return Color.HSVToColor(hsv)
    }

    private fun drawBar(canvas: Canvas) {
        val barPaint = Paint()

        if (_cellRsrp != null) {
            val rsrp = _cellRsrp!!
            val barN = (rsrp - RSRP_MIN).toFloat() / (RSRP_MAX - RSRP_MIN)  // 0-1 正規化
            val bar1 = max(0f, min(1f, barN))   // 最大最小クリップ
            val barX = barRect.left + bar1 * (barRect.right - barRect.left) // X座標に変換

            // バー枠
            barPaint.color = ContextCompat.getColor(context, R.color.colorCellInfoBarFrame)
            canvas.drawRect(barPadRect, barPaint)

            when (Utils.getPreferenceString("dbmBarDesign", "default")) {
                "default" -> {
                    // バー背景
                    barPaint.color = ContextCompat.getColor(context, R.color.colorCellInfoBarBg)

                    canvas.drawRect(barRect, barPaint)

                    // バー
                    barPaint.color = when {
                        (-98 <= rsrp) -> ContextCompat.getColor(context, R.color.colorSignal1) // 強：緑
                        (-108 <= rsrp) -> ContextCompat.getColor(context, R.color.colorSignal2)    // 中：黄
                        (-118 <= rsrp) -> ContextCompat.getColor(context, R.color.colorSignal3)    // 弱：オレンジ
                        else -> ContextCompat.getColor(context, R.color.colorSignal4)  // ぴえん：赤
                    }
                    canvas.drawRect(barRect.left, barRect.top, barX, barRect.bottom, barPaint)
                }
                else -> {
                    val barX43 =
                        barRect.left + (-118 - RSRP_MIN).toFloat() / (RSRP_MAX - RSRP_MIN) * (barRect.right - barRect.left)
                    val barX32 =
                        barRect.left + (-108 - RSRP_MIN).toFloat() / (RSRP_MAX - RSRP_MIN) * (barRect.right - barRect.left)
                    val barX21 =
                        barRect.left + (-98 - RSRP_MIN).toFloat() / (RSRP_MAX - RSRP_MIN) * (barRect.right - barRect.left)

                    when {
                        (-98 <= rsrp) -> {
                            barPaint.color = (ContextCompat.getColor(context, R.color.colorSignal4))
                            canvas.drawRect(barRect.left, barRect.top, barX43, barRect.bottom, barPaint)
                            barPaint.color = (ContextCompat.getColor(context, R.color.colorSignal3))
                            canvas.drawRect(barX43, barRect.top, barX32, barRect.bottom, barPaint)
                            barPaint.color = (ContextCompat.getColor(context, R.color.colorSignal2))
                            canvas.drawRect(barX32, barRect.top, barX21, barRect.bottom, barPaint)
                            barPaint.color = (ContextCompat.getColor(context, R.color.colorSignal1))
                            canvas.drawRect(barX21, barRect.top, barX, barRect.bottom, barPaint)
                            barPaint.color = darkColor(ContextCompat.getColor(context, R.color.colorSignal1))
                            canvas.drawRect(barX, barRect.top, barRect.right, barRect.bottom, barPaint)
                        }
                        (-108 <= rsrp) -> {
                            barPaint.color = (ContextCompat.getColor(context, R.color.colorSignal4))
                            canvas.drawRect(barRect.left, barRect.top, barX43, barRect.bottom, barPaint)
                            barPaint.color = (ContextCompat.getColor(context, R.color.colorSignal3))
                            canvas.drawRect(barX43, barRect.top, barX32, barRect.bottom, barPaint)
                            barPaint.color = (ContextCompat.getColor(context, R.color.colorSignal2))
                            canvas.drawRect(barX32, barRect.top, barX, barRect.bottom, barPaint)
                            barPaint.color = darkColor(ContextCompat.getColor(context, R.color.colorSignal2))
                            canvas.drawRect(barX, barRect.top, barX21, barRect.bottom, barPaint)
                            barPaint.color = darkColor(ContextCompat.getColor(context, R.color.colorSignal1))
                            canvas.drawRect(barX21, barRect.top, barRect.right, barRect.bottom, barPaint)
                        }
                        (-118 <= rsrp) -> {
                            barPaint.color = (ContextCompat.getColor(context, R.color.colorSignal4))
                            canvas.drawRect(barRect.left, barRect.top, barX43, barRect.bottom, barPaint)
                            barPaint.color = (ContextCompat.getColor(context, R.color.colorSignal3))
                            canvas.drawRect(barX43, barRect.top, barX, barRect.bottom, barPaint)
                            barPaint.color = darkColor(ContextCompat.getColor(context, R.color.colorSignal3))
                            canvas.drawRect(barX, barRect.top, barX32, barRect.bottom, barPaint)
                            barPaint.color = darkColor(ContextCompat.getColor(context, R.color.colorSignal2))
                            canvas.drawRect(barX32, barRect.top, barX21, barRect.bottom, barPaint)
                            barPaint.color = darkColor(ContextCompat.getColor(context, R.color.colorSignal1))
                            canvas.drawRect(barX21, barRect.top, barRect.right, barRect.bottom, barPaint)
                        }
                        else -> {
                            barPaint.color = (ContextCompat.getColor(context, R.color.colorSignal4))
                            canvas.drawRect(barRect.left, barRect.top, barX, barRect.bottom, barPaint)
                            barPaint.color = darkColor(ContextCompat.getColor(context, R.color.colorSignal4))
                            canvas.drawRect(barX, barRect.top, barX43, barRect.bottom, barPaint)
                            barPaint.color = darkColor(ContextCompat.getColor(context, R.color.colorSignal3))
                            canvas.drawRect(barX43, barRect.top, barX32, barRect.bottom, barPaint)
                            barPaint.color = darkColor(ContextCompat.getColor(context, R.color.colorSignal2))
                            canvas.drawRect(barX32, barRect.top, barX21, barRect.bottom, barPaint)
                            barPaint.color = darkColor(ContextCompat.getColor(context, R.color.colorSignal1))
                            canvas.drawRect(barX21, barRect.top, barRect.right, barRect.bottom, barPaint)
                        }
                    }
                }
            }

            // バーの中の RSRP値テキスト
            textBarPaint.textSize = barRect.bottom - barRect.top
            // 初期化済み textBarPaint.color = ContextCompat.getColor(context, R.color.colorCellInfoBarText)
            canvas.drawText(
                "RSRP: $rsrp",
                barRect.centerX(),
                barRect.bottom - textBarPaint.fontMetrics.bottom / 2,
                textBarPaint
            )
        } else {
            // 圏外
            // 機能してない風にするため、枠と同色
            barPaint.color = ContextCompat.getColor(context, R.color.colorCellInfoBarFrame)
            canvas.drawRect(barRect, barPaint)

            // バーの中の 圏外テキスト
            /*
            textBarPaint.textSize = barRect.bottom - barRect.top
            textBarPaint.color = Color.WHITE
            canvas.drawText(
                "Out of service",
                barRect.centerX(),
                barRect.bottom - textBarPaint.fontMetrics.bottom / 2,
                textBarPaint
            )
             */
        }
    }

    private fun drawText(canvas: Canvas) {
        if ( bLarge && bPortrait ) {
            val textLeft = textRect.left
            val text1Bottom = textRect.top + textRect.height() / 4
            val text2Bottom = textRect.top + textRect.height() / 2
            val text3Bottom = textRect.bottom
            textPaintL.textSize = textRect.height() / 4
            textPaintR.textSize = textRect.height() / 2
            textPaintR.textAlign = Paint.Align.LEFT

            _stringL1?.let {
                canvas.drawText(
                    it,
                    textLeft,
                    text1Bottom - textPaintL.fontMetrics.bottom / 2,
                    textPaintL
                )
            }
            _stringL2?.let {
                canvas.drawText(
                    it,
                    textLeft,
                    text2Bottom - textPaintL.fontMetrics.bottom / 2,
                    textPaintL
                )
            }
            _stringR?.let {
                canvas.drawText(
                    it,
                    textLeft,
                    text3Bottom - textPaintR.fontMetrics.bottom / 2,
                    textPaintR
                )
            }
        }
        else {
            val textLeft = textRect.left
            val textRight = textRect.right
            val text1Bottom = textRect.top + textRect.height() / 2
            val text2Bottom = textRect.bottom
            textPaintL.textSize = textRect.height() / 2
            textPaintR.textSize = textRect.height()
            textPaintR.textAlign = Paint.Align.RIGHT

            _stringL1?.let {
                canvas.drawText(
                    it,
                    textLeft,
                    text1Bottom - textPaintL.fontMetrics.bottom / 2,
                    textPaintL
                )
            }
            _stringL2?.let {
                canvas.drawText(
                    it,
                    textLeft,
                    text2Bottom - textPaintL.fontMetrics.bottom / 2,
                    textPaintL
                )
            }
            _stringR?.let {
                canvas.drawText(
                    it,
                    textRight,
                    text2Bottom - textPaintR.fontMetrics.bottom / 2,
                    textPaintR
                )
            }
        }
    }

    //// 外部から扱いやすいようにするメソッド群 ////

    // 通常のデータ反映
    fun drawCellInfoView(cellInfo: ICell, latLng: LatLng) {
        if (cellInfo is CellLte) {
            // 4G
            drawCellInfoView(cellInfo, latLng)
        } else if (cellInfo is CellNr) {
            // 5G
            drawCellInfoView(cellInfo)
        } else if (cellInfo is CellWcdma) {
            // 3G
            drawCellInfoView(cellInfo)
        }
    }

    // 3G
    private fun drawCellInfoView(cellInfo: CellWcdma) {
        val bgColor = getColorByMnc(cellInfo.network?.mcc, cellInfo.network?.mnc)
        val vo =  CellInfoViewVO(
            cellInfo.signal.dbm,
            "LAC-UCID: ${cellInfo.lac}-${cellInfo.cid}",
            "RNC-CID: ${cellInfo.rnc}-${cellInfo.cid}",
            "",
            bgColor
        )
        drawCellInfoView(vo)
    }

    fun getColorByMnc(mccString: String?, mncString: String?): Int {
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

    private var prevTaLL = LatLng(0.0,0.0)
    private var prevEci:Int? = null
    private var prevTa:Int? = null

    // 4G
    private fun drawCellInfoView(cellInfo: CellLte, latLng: LatLng) {

        var bandwidth:Int? = null
        val bwFromEarfcn = Utils.getPreferenceBoolean("bwFromEarfcn", false)
        if (!bwFromEarfcn) {
            bandwidth = cellInfo.bandwidth
        }
        bandwidth = when (bandwidth) {
            null -> when (cellInfo.band?.downlinkEarfcn) {
                1500 -> 20 * 1000
                1575 -> 5 * 1000
                else -> null
            }
            else -> bandwidth
        }

        val bwStr = if ( bandwidth!=null ){
            (bandwidth / 1000).toString() + "MHz"
        } else {
            "-"
        }
        val ta = if (Utils.getPreferenceBoolean("countermeasuresForTaNotBeingUpdated", false)) {
            // サムスンTA更新されない対策
            if (prevEci != cellInfo.eci || prevTa != cellInfo.signal.timingAdvance) {
                // 変化があったら正しいと判断する
                prevEci = cellInfo.eci
                prevTa = cellInfo.signal.timingAdvance
                prevTaLL = LatLng(latLng.latitude, latLng.longitude)
                cellInfo.signal.timingAdvance
            } else {
                if (SphericalUtil.computeDistanceBetween(prevTaLL, latLng) < 78) {
                    // 変化がない場合、移動距離が一定以内なら信用してやる
                    cellInfo.signal.timingAdvance
                } else {
                    // 一定以上移動したのに変化が無いので信用しない！
                    null
                }
            }
        } else {
            // 通常
            cellInfo.signal.timingAdvance
        }
        val taStr = ta?.toString() ?: "-"
        val pciStr = when (cellInfo.pci) {
            MapsActivity.UNAVAILABLE -> "-"
            else -> cellInfo.pci?.toString() ?: "-"
        }

        val bgColor = getColorByMnc(cellInfo.network?.mcc, cellInfo.network?.mnc)
        val vo = CellInfoViewVO(
            cellInfo.signal.rsrp?.toInt(),
            "eNB-LCID: ${cellInfo.enb}-${cellInfo.cid}",
            "PCI: $pciStr  BW: $bwStr",
            "TA: $taStr",
            bgColor
        )
        drawCellInfoView(vo)
    }

    // 5G
    private fun drawCellInfoView(cellInfo: CellNr) {
        val bgColor = getColorByMnc(cellInfo.network?.mcc, cellInfo.network?.mnc)
        val vo = CellInfoViewVO(
            cellInfo.signal.dbm,
            "NCI: ${cellInfo.nci}",
            "PCI: ${cellInfo.pci}",
            "",
            bgColor
        )
        drawCellInfoView(vo)
    }

    // 圏外
    fun drawCellInfoViewOutOfService() {
        val vo = CellInfoViewVO(
            null,
            "eNB-LCID: -",
            "PCI: -  BW: -",
            "TA: -",
            ContextCompat.getColor(context, R.color.colorForeign)
        )
        drawCellInfoView(vo)
    }

    // データなし
    fun drawCellInfoViewNull() {
        val vo = CellInfoViewVO(
            null,
            "",
            "",
            "",
            ContextCompat.getColor(context, R.color.colorThemeIndependentDark)
        )
        drawCellInfoView(vo)
    }

    // 用意されたデータをそのまま反映
    fun drawCellInfoView(vo: CellInfoViewVO) {
        vo.bgColor?.let { setBackgroundColor(it) }
        cellRsrp = vo.rsrp
        stringL1 = vo.l1
        stringL2 = vo.l2
        stringR = vo.r
        invalidate()
    }
}