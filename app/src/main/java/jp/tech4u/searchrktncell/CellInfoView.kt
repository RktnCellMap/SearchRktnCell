package jp.tech4u.searchrktncell

import android.content.Context
import android.graphics.*
import android.text.TextPaint
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import kotlin.math.max
import kotlin.math.min

class CellInfoView : View {

    companion object {
        private const val RSRP_MAX = -50
        private const val RSRP_MIN = -140
    }

    private var _stringL1: String? = null
    private var _stringL2: String? = null
    private var _stringR: String? = null
    private var _cellRsrp: Int? = null

    private lateinit var barRect: RectF
    private lateinit var barPadRect: RectF

    private lateinit var textBarPaint: TextPaint
    private lateinit var textPaintL: TextPaint
    private lateinit var textPaintR: TextPaint

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
            color = Color.BLACK
        }

        textPaintL = TextPaint().apply {
            flags = Paint.ANTI_ALIAS_FLAG
            textAlign = Paint.Align.LEFT
            textSize = 24f
            setTypeface(typeface)
            color = Color.WHITE
        }
        textPaintR = TextPaint().apply {
            flags = Paint.ANTI_ALIAS_FLAG
            textAlign = Paint.Align.RIGHT
            textSize = 24f
            setTypeface(typeface)
            color = Color.WHITE
        }
    }

    private fun calcCoordinates() {
        val contentWidth = width - paddingLeft - paddingRight
        val contentHeight = height - paddingTop - paddingBottom

        barRect = RectF(
            contentHeight * 0.1f,
            contentHeight * 0.1f,
            contentWidth - contentHeight * 0.1f,
            contentHeight * 0.4f
        )

        val barPadding = contentHeight * 0.02f
        barPadRect = RectF(
            barRect.left - barPadding,
            barRect.top - barPadding,
            barRect.right + barPadding,
            barRect.bottom + barPadding
        )
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        println("CellInfoView.onDraw()")

        calcCoordinates()
        drawBar(canvas)
        drawText(canvas)
    }

    private fun drawBar(canvas: Canvas) {
        val barPaint = Paint()

        if (_cellRsrp != null) {
            val rsrp = _cellRsrp!!
            val barN = (rsrp - RSRP_MIN).toFloat() / (RSRP_MAX - RSRP_MIN)  // 0-1 正規化
            val bar1 = max(0f, min(1f, barN))   // 最大最小クリップ
            val barX = barRect.left + bar1 * (barRect.right - barRect.left) // X座標に変換

            // バー枠
            barPaint.color = Color.DKGRAY
            canvas.drawRect(barPadRect, barPaint)

            // バー背景
            barPaint.color = Color.LTGRAY
            canvas.drawRect(barRect, barPaint)

            // バー
            barPaint.color = when {
                (-98 <= rsrp) -> ContextCompat.getColor(context, R.color.colorSignal1) // 強：緑
                (-108 <= rsrp) -> ContextCompat.getColor(context, R.color.colorSignal2)    // 中：黄
                (-118 <= rsrp) -> ContextCompat.getColor(context, R.color.colorSignal3)    // 弱：オレンジ
                else -> ContextCompat.getColor(context, R.color.colorSignal4)  // ぴえん：赤
            }
            canvas.drawRect(barRect.left, barRect.top, barX, barRect.bottom, barPaint)

            // バーの中の RSRP値テキスト
            textBarPaint.textSize = barRect.bottom - barRect.top
            textBarPaint.color = Color.BLACK
            canvas.drawText(
                "RSRP: $rsrp",
                barRect.centerX(),
                barRect.bottom - textBarPaint.fontMetrics.bottom / 2,
                textBarPaint
            )
        } else {
            // 圏外
            barPaint.color = Color.DKGRAY
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
        val contentHeight = height - paddingTop - paddingBottom

        val textLeft = barRect.left
        val textRight = barRect.right
        val text1Bottom = contentHeight * 0.7f
        val text2Bottom = contentHeight * 0.9f
        textPaintL.textSize = text2Bottom - text1Bottom
        textPaintR.textSize = textPaintL.textSize * 2

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