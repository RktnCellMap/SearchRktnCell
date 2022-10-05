package jp.tech4u.searchrktncell

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.util.AttributeSet
import android.util.Log
import android.view.Gravity
import android.widget.TableLayout
import android.widget.TableRow
import android.widget.TextView
import androidx.core.content.ContextCompat
import jp.tech4u.searchrktncell.data.MlsCellInfo

// https://twitter.com/SFlistener/status/1550853273460310018 から
// LCID電測済み表示

class LcidView : TableLayout {

    // eNB-ID毎のマップ。中身はlcid有無を表すSet。
    private val mlsMap = mutableMapOf<Int,MutableSet<Int>>()
    private val mymapMap = mutableMapOf<Int,MutableSet<Int>>()
    private val radioInfoMap = mutableMapOf<Int,MutableSet<Int>>()

    private var prevWidth = 0
    private var over12 = false

    constructor(context: Context) : super(context) {
        init(null)
    }

    constructor(context: Context, attrs: AttributeSet) : super(context, attrs) {
        init(attrs)
    }

    private fun init(attrs: AttributeSet?) {
        val typeface = Typeface.createFromAsset(context.assets, "Abel-Regular.ttf")

        for (iy in 0..10) {
            val tableRow = TableRow(this.context)
            tableRow.visibility = GONE

            this.addView(tableRow)
            val lp = tableRow.layoutParams as LayoutParams
            lp.weight = 1f
            lp.gravity = Gravity.CENTER

            for (ix in 0..23) {
                val txt = TextView(this.context)
                val num = (iy * 24 + ix + 1)
                if (num in 1..255) {
                    txt.text = "" + num
                } else if (num == 264) {
                    txt.text = "0"
                }
                txt.maxLines = 1
                txt.gravity = Gravity.CENTER
                txt.typeface = typeface
                tableRow.addView(txt)

                val lpt = TableRow.LayoutParams(
                    LayoutParams.MATCH_PARENT,
                    LayoutParams.MATCH_PARENT
                )
                lpt.weight = 1f
                txt.layoutParams = lpt
            }
        }

        this.viewTreeObserver.addOnGlobalLayoutListener {

            if (prevWidth != width) {
                prevWidth = width
                setTextSize()
            }
        }
    }

    private fun setTextSize(over12:Boolean?=null) {
        if ( over12!=null) {
            this.over12 = over12
        }
        val metrics = context.resources.displayMetrics
        val size2 = when {
            this.over12 -> width / metrics.density / 2 / 24 * 1.5f
            else -> width / metrics.density / 2 / 12 * 1.5f
        }
        val size3 = width / metrics.density / 3 / 24 * 1.5f
        val sizeAry = arrayOf(size2, size2, size2, size3)

        for (iy in 0..10) {
            val tableRow = this.getChildAt(iy) as TableRow
            for (ix in 0..23) {
                val txt = tableRow.getChildAt(ix) as TextView
                txt.width = width / 24
                txt.textSize = sizeAry[txt.text.length]
            }
        }
    }

    fun setMls(cidMap: MutableMap<Long, MlsCellInfo>) {
        mlsMap.clear()
        for (eci in cidMap.keys) {
            val eNB = (eci / 256).toInt()
            val lcid = (eci % 256).toInt()
            if (!mlsMap.containsKey(eNB)) {
                mlsMap[eNB] = mutableSetOf()
            }
            mlsMap[eNB]?.add(lcid)
        }
    }

    fun setMymap(kmlParserAry:Array<KmlParser?>) {
        mymapMap.clear()
        for (i in 0 until kmlParserAry.size) {
            val kmlParser = kmlParserAry[i] ?: continue
            for (eci in kmlParser.eciMap.keys) {
                val eNB = (eci / 256).toInt()
                val lcid = (eci % 256).toInt()
                if (!mymapMap.containsKey(eNB)) {
                    mymapMap[eNB] = mutableSetOf()
                }
                mymapMap[eNB]?.add(lcid)
            }
        }
        Log.d("SETMYMAP", ""+mymapMap.size)
    }

    private var debugCount = 0

    fun drawLcid(eci:Long){
        val eNB = (eci / 256).toInt()
        val lcid = (eci % 256).toInt()
        if (!radioInfoMap.containsKey(eNB)) {
            radioInfoMap[eNB] = mutableSetOf()
        }
        radioInfoMap[eNB]?.add(lcid)

        val mlsSet = mlsMap[eNB]
        val mymapSet = mymapMap[eNB]
        val radioInfoSet = radioInfoMap[eNB]

        // LCIDが12を超えるものが有るか？（有ったらレイアウトがそれ用になる）
        var over12 = false
        for (lcid in 13..255) {
            if ( mlsSet!=null &&  mlsSet.contains(lcid)) {
                over12 = true
                break
            }
            if ( mymapSet!=null &&  mymapSet.contains(lcid)) {
                over12 = true
                break
            }
            if ( radioInfoSet!=null &&  radioInfoSet.contains(lcid)) {
                over12 = true
                break
            }
        }
        if ( mlsSet!=null &&  mlsSet.contains(0))
            over12 = true
        if ( mymapSet!=null &&  mymapSet.contains(0))
            over12 = true
        if ( radioInfoSet!=null &&  radioInfoSet.contains(0))
            over12 = true

        setTextSize(over12)
        for (iy in 0..10) {
            // iyは行に相当
            val tableRow = this.getChildAt(iy) as TableRow
            var existRow = false    // 最初は行が無いことにする
            for (ix in 0..23) {
                // ixは列に相当
                val num = (iy * 24 + ix + 1)
                val lcid = when (num) {
                    in 1..255 -> num    // 通常
                    264 -> 0    // 0を最後に持っていくための。
                    else -> -1  // 値域外（処理対象外）
                }

                if ( lcid!=-1) {
                    val txt = tableRow.getChildAt(ix) as TextView

                    // 背景色（MLSデータ有無）
                    val bgColor = if ( mlsSet!=null &&  mlsSet.contains(lcid)) {
                        existRow = true
                        ContextCompat.getColor(this.context, R.color.colorRakuten)
                    }
                    else {
                        Color.GRAY
                    }

                    // 文字色(電測したら白、未電測は黒、ただし、マイマップに無ければ背景色（文字ナシ））
                    val fgColor = if ( radioInfoSet!=null &&  radioInfoSet.contains(lcid)) {
                        existRow = true
                        Color.WHITE
                    }
                    else {
                        if ( mymapSet!=null &&  mymapSet.contains(lcid)) {
                            existRow = true
                            Color.BLACK
                        }
                        else {
                            bgColor
                        }
                    }
                    txt.setBackgroundColor(bgColor)
                    txt.setTextColor(fgColor)
                    txt.visibility = if (over12 || ix < 12) VISIBLE else GONE
                }
            }
            tableRow.visibility = if (existRow) VISIBLE else GONE
        }
    }
}