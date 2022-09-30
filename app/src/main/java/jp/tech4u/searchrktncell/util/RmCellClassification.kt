package jp.tech4u.searchrktncell.util

import android.content.Context
import androidx.core.content.ContextCompat
import jp.tech4u.searchrktncell.R

/**
 * 楽天モバイルのセル情報を分類する
 */
class RmCellClassification {
    companion object {

        private val rmTacSpecialMap = mapOf(
            111 to "京浜 内陸",
            112 to "京浜 湾岸",
            113 to "城西",
            114 to "埼京線~西武池袋線",
            211 to "山手線 東京~大塚",
            212 to "山手線 有楽町~恵比寿",
            213 to "山手線 池袋~渋谷",
            214 to "山手線 内側",
            311 to "大宮+埼京線の東",
            312 to "千葉都会",
            313 to "城東の南",
            314 to "城東の北",
            25511 to "大阪 北",
            25512 to "大阪 南",
            25513 to "神戸",
            25514 to "京都",
            25601 to "三重県",
            25602 to "三重県",
            33411 to "名古屋",
            55555 to "杉並世田谷",
            55556 to "夢の島",
            63488 to "移動局",
        )

        private val rmTacPrefAry = listOf(
            "？",
            "北海道",
            "青森県",
            "岩手県",
            "宮城県",
            "秋田県",
            "山形県",
            "福島県",
            "茨城県",
            "栃木県",
            "群馬県",
            "埼玉県",
            "千葉県",
            "東京都下",
            "？",
            "神奈川県",
            "新潟県",
            "富山県",
            "石川県",
            "福井県",
            "山梨県",
            "長野県",
            "岐阜県",
            "静岡県",
            "愛知県",
            "名阪？",     // 割と謎な採番。rmTacSpecialMapにおまかせ
            "滋賀県",
            "京都府",
            "大阪府",
            "兵庫県",
            "奈良県",
            "和歌山県",
            "鳥取県",
            "島根県",   // 33411:名古屋 33793:島根県 これ以外（33600台）で静岡にあるのはミスっぽい
            "岡山県",
            "広島県",
            "山口県",
            "徳島県",   // 37889以外で熊本にあるのはミスっぽい
            "香川県",
            "愛媛県",
            "高知県",
            "福岡県",
            "？",
            "佐賀県",
            "長崎県",
            "熊本県",
            "大分県",
            "宮崎県",
            "鹿児島県",
            "沖縄県",
        )
        private val rmTacFemtoPrefAry = listOf(
            "東京都",
            "千葉県",
            "埼玉県",
            "神奈川県",
            "群馬県",
            "新潟県",
            "山梨県",
            "長野県",
            "茨城県",
            "栃木県",
            "北海道",
            "青森県",
            "岩手県",
            "宮城県",
            "秋田県",
            "山形県",
            "福島県",
            "静岡県",
            "愛知県",
            "三重県",
            "岐阜県",
            "京都府",
            "大阪府",
            "兵庫県",
            "奈良県",
            "和歌山県",
            "滋賀県",
            "富山県",
            "石川県",
            "福井県",
            "鳥取県",
            "島根県",
            "岡山県",
            "広島県",
            "山口県",
            "徳島県",
            "香川県",
            "愛媛県",
            "高知県",
            "福岡県",
            "佐賀県",
            "長崎県",
            "熊本県",
            "大分県",
            "宮崎県",
            "鹿児島県",
            "沖縄県",
        )
        private val rmEnbPrefAry = listOf(
            "？県",
            "北海道",
            "北海道？",
            "北海道？",
            "北海道？",
            "北海道？",
            "青森県",
            "秋田県",
            "岩手県",
            "宮城県",
            "山形県",
            "福島県",
            "新潟県",
            "長野県",
            "群馬県",
            "栃木県",
            "茨城県",
            "東京都",
            "東京都？",
            "千葉県",
            "神奈川県",
            "埼玉県",
            "愛知県",
            "静岡県",
            "山梨県",
            "岐阜県",
            "三重県",
            "大阪府",
            "大阪府？",
            "和歌山県",
            "奈良県",
            "京都府",
            "石川県",
            "富山県",
            "滋賀県",
            "福井県",
            "兵庫県",
            "広島県",
            "山口県",
            "島根県",
            "鳥取県",
            "岡山県",
            "香川県",
            "徳島県",
            "高知県",
            "愛媛県",
            "福岡県",
            "佐賀県",
            "長崎県",
            "熊本県",
            "大分県",
            "宮崎県",
            "沖縄県",
            "鹿児島県",
        )
        private val rmColorAry = listOf(
            -1,  // マクロセル(セクタ番号から色を決めます)
            R.color.colorPicoCellBg,    // ピコセル
            R.color.colorIndoorCellBg,    // 屋内局
            R.color.colorFemtoCellBg,    // Casa
            R.color.colorMobileCellBg,    // 移動局
        )

        fun getTacInfo(tac: Int): Pair<String, Int> {
            var tacPrefType = ""
            var type = 0
            when {
                rmTacSpecialMap.containsKey(tac) -> {
                    // 特殊扱い
                    tacPrefType = rmTacSpecialMap[tac]!!
                    type = if (tac == 63488) 4 else 0
                }
                63968 <= tac -> {
                    // Casa
                    val tacPrefNum = (tac - 63968) / 32
                    tacPrefType = when {
                        tacPrefNum < rmTacFemtoPrefAry.size -> rmTacFemtoPrefAry[tacPrefNum] + ",Casa"
                        else -> "Casa?"
                    }
                    type = 3
                }
                else -> {
                    // 上2桁で判断
                    var rmTacPrefNum = tac / 1000
                    if (rmTacPrefAry.size <= rmTacPrefNum) rmTacPrefNum = 0
                    tacPrefType = rmTacPrefAry[rmTacPrefNum]
                }
            }
            return tacPrefType to type
        }

        val rmEnbTypeNameAry = listOf("マクロセル", "Casa", "屋内局", "ピコセル", )
        val rmEnbTypeNumAry = listOf(0, 3, 2, 1, )

        fun getEnbInfo(eNB: Long): Pair<String, Int> {
            var name = ""
            var type = 0

            var eNBprefNum = (eNB / 0x4000).toInt()
            if (eNBprefNum == 62) {
                name = "移動局"
                type = 4
            } else {
                // 都道府県
                if (rmEnbPrefAry.size <= eNBprefNum) eNBprefNum = 0
                val eNBPref = rmEnbPrefAry[eNBprefNum]

                // セルタイプ
                val typeNum = ((eNB and 0x3fff) / 5000).toInt()
                var eNBType = rmEnbTypeNameAry[typeNum]

                name = "$eNBPref,$eNBType"
                type = rmEnbTypeNumAry[typeNum]
            }
            return name to type
        }

        fun getPciInfo(pci: Int): Pair<String, Int> {
            var pciTypeName = ""
            var pciTypeNum = 0
            when (pci) {
                in 0..389 -> {
                    pciTypeName = "マクロセル"
                    pciTypeNum = 0
                }
                in 390..403 -> {
                    pciTypeName = "ﾏｸﾛorﾋﾟｺ?"
                    pciTypeNum = 0
                }
                in 404..479 -> {
                    pciTypeName = "屋内局"
                    pciTypeNum = 2
                }
                in 480..491 -> {
                    pciTypeName = "ピコセル"   // または移動局なんだけど、その旨書くとごちゃつくので省略
                    pciTypeNum = 1
                }
                in 492..503 -> {
                    pciTypeName = "Casa"
                    pciTypeNum = 3
                }
                else -> {
                    pciTypeName = "LTE規格外"
                    pciTypeNum = 3
                }
            }
            return pciTypeName to pciTypeNum
        }

        fun getColor(type: Int): Int {
            return rmColorAry.getOrElse(type, { -1 })
        }

        fun getFgBgColor(ctx: Context, colorInt: Int, sector3: Int): Pair<Int, Int> {
            val bgColor =  when (colorInt) {
                -1 -> {
                    // マクロセル
                    Utils.calcColor(-60, sector3, true)

                }
                else -> {
                    // ピコ、屋内、Casa、移動
                    ContextCompat.getColor(ctx, colorInt)
                }
            }
            // bgColor の輝度から、fgColorを白か黒か選ぶ
            val fgColor = ContextCompat.getColor(ctx, Utils.getRevMonoColor(bgColor))
            return bgColor to fgColor
        }
    }
}