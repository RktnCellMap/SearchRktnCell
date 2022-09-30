package jp.tech4u.searchrktncell.util

object EciUtil {

    private const val regEnb = "[0-9]{5,7}"
    private const val regLcid = "[0-9]{1,2}"

    // 標準
    private val regexEnbStd = Regex("($regEnb)-($regLcid)(,$regLcid)+\\b")
    private val regexEnbStdOne = Regex("($regEnb)-($regLcid)\\b")

    // 春日部(@1mai_gear 1Tq3rUIt72m3evbHxYE6J97A6wgwrMBuV)
    // 播磨地域(@s2auxy0t4JYTKXF 1ORPFmLWxiYyXjqDOvD105rOCIGmuerYd)
    // 旧 上尾
    private val regexEnbAgo = Regex("($regEnb) ($regLcid)(-$regLcid)*\\b")

    // 足立
    private val regexEnbAdt = Regex("($regEnb)-($regLcid)(-$regLcid)+\\b")

    // レンジ定義
    private val regexEnbRange = Regex("($regEnb)-($regLcid)~($regLcid)\\b")

    /**
     * eNB-LCIDの文字列を正規表現にかけたものからCellIDを取得する.
     *
     * @args matchResult 正規表現にかけた結果
     * @args d2 LCID部分のデリミタ
     * @return return CellIDのリスト
     */
    private fun convEciArray(regex: Regex, value: String, d2: Char): List<Long> {
        val result = mutableListOf<Long>()
        val matchResultSequence = regex.findAll(value)
        for (matchResult in matchResultSequence) {
            val eNB = matchResult.groupValues[1]
            val neNB256 = eNB.toLong() * 256

            val lcidStr =
                matchResult.groupValues[0].substring(matchResult.groupValues[1].length + 1)
            val lcids = lcidStr.split(d2)

            for (elm in lcids) {
                val lcid = if (elm.startsWith(d2)) {
                    elm.substring(1)
                } else {
                    elm
                }
                val n = lcid.toLong()
                result.add(neNB256 + n)
            }
        }
        return result
    }

    /**
     * eNB-LCIDの文字列を正規表現にかけたものからCellIDを取得する.レンジ表現用.
     *
     * @args matchResult 正規表現にかけた結果
     * @return return CellIDのリスト
     */
    private fun convEciArray(regex: Regex, value: String): List<Long> {
        val result = mutableListOf<Long>()
        val matchResultSequence = regex.findAll(value)
        for (matchResult in matchResultSequence) {
            val eNB = matchResult.groupValues[1]
            val neNB256 = eNB.toLong() * 256

            val lcidFrom = matchResult.groupValues[2].toInt()
            val lcidTo = matchResult.groupValues[3].toInt()

            for (lcid in (lcidFrom..lcidTo)) {
                result.add(neNB256 + lcid)
            }
        }
        return result
    }

    /**
     * eNB-LCIDらしき記述を含むであろう文字列からCellIDを取得する.
     *
     * @args value eNB-LCIDらしき記述を含むであろう文字列
     * @return return CellIDのリスト
     */
    fun getEciFromMemo(value: String): List<Long> {
        var result:List<Long>?

        // レンジ
        result = convEciArray(regexEnbRange,value)
        if (result.isNotEmpty()) return result

        // 標準
        result = convEciArray(regexEnbStd,value, ',')
        if (result.isNotEmpty()) return result

        // 足立
        result = convEciArray(regexEnbAdt,value, '-')
        if (result.isNotEmpty()) return result

        // 春日部・播磨地域 （旧 上尾）　（１つバージョンもココ）
        result = convEciArray(regexEnbAgo,value, '-')
        if (result.isNotEmpty()) return result

        // LCID1つバージョン（複数バージョンに含めると、異形式の1個と誤認されることがあるので、最後に別処理でやる）
        result = convEciArray(regexEnbStdOne,value, ',')
        if (result.isNotEmpty()) return result

        // 見つからなかったので空のまま返す
        return result
    }

    /**
     * ECIのリストから、eNB-LCIDの文字列を作る.
     *
     * @args eciAry ECIのリスト。ソート済みであること
     * @return return eNB-LCIDの文字列
     */
    fun getEnbLcid(eciList: List<Long>): String {
        var enbLcid = ""
        if (eciList.count() == 0) return enbLcid

        val eciFirst = eciList[0]
        val eciLast = eciList[eciList.count() - 1]

        if (3 < eciList.count() && (eciLast - eciFirst).toInt() == eciList.count() - 1) {
            // 3つを超える場合（かつ数があっている場合）は範囲表現
            val enb = eciFirst / 256
            val lcidFirst = eciFirst % 256
            val lcidLast = eciLast % 256
            enbLcid = "$enb-$lcidFirst~$lcidLast"
        } else {
            var lastEnb = -1L
            for (eci in eciList) {
                val enb = eci / 256
                val lcid = eci % 256
                when {
                    enbLcid == "" -> enbLcid = "$enb-$lcid"     // 先頭
                    lastEnb != enb -> enbLcid += " $enb-$lcid"  // データが変だと eNBが複数ある場合があり、その対応
                    else -> enbLcid += ",$lcid" // 2番目以降
                }
                lastEnb = enb
            }
        }
        return enbLcid
    }
}