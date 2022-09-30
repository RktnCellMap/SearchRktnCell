package jp.tech4u.searchrktncell.util

object EciUtil {

    private const val regEnb = "[0-9]{5,6}"
    private const val regLcid = "[0-9]{1,2}"

    // 小平
    private val regexEnb0c3 = Regex("($regEnb)-($regLcid),($regLcid),($regLcid)")
    private val regexEnb0c2 = Regex("($regEnb)-($regLcid),($regLcid)")
    private val regexEnb0c1 = Regex("($regEnb)-($regLcid)")

    // 上尾
    private val regexEnb1c3 = Regex("($regEnb) ($regLcid)-($regLcid)-($regLcid)")
    private val regexEnb1c2 = Regex("($regEnb) ($regLcid)-($regLcid)")
    private val regexEnb1c1 = Regex("($regEnb) ($regLcid)")

    // 奈良
    private val regexEnb2c2 = Regex("($regEnb)\\(CID:($regLcid)~($regLcid)\\)")
    private val regexEnb2c1 = Regex("($regEnb)\\(CID:($regLcid)\\)")

    // 足立
    private val regexEnb3c3 = Regex("($regEnb)-($regLcid)-($regLcid)-($regLcid)")
    private val regexEnb3c2 = Regex("($regEnb)-($regLcid)-($regLcid)")
    private val regexEnb3c1 = Regex("($regEnb)-($regLcid)")

    // 秋田
    //private val regexEnbNullpon = Regex("($regEnb) ($regLcid) ($regPci)")

    private fun conv3EciArray(destructured: MatchResult.Destructured): LongArray {
        val (eNB, c1, c2, c3) = destructured
        val neNB256 = eNB.toLong() * 256
        val n1 = c1.toInt()
        val n2 = c2.toInt()
        val n3 = c3.toInt()
        return longArrayOf(neNB256 + n1, neNB256 + n2, neNB256 + n3)
    }

    private fun conv2EciArray(destructured: MatchResult.Destructured): LongArray {
        val (eNB, c1, c2) = destructured
        val neNB256 = eNB.toLong() * 256
        val n1 = c1.toInt()
        val n2 = c2.toInt()
        return longArrayOf(neNB256 + n1, neNB256 + n2)
    }

    private fun conv1EciArray(destructured: MatchResult.Destructured): LongArray {
        val (eNB, c1) = destructured
        val neNB256 = eNB.toLong() * 256
        val n1 = c1.toInt()
        return longArrayOf(neNB256 + n1)
    }

    private fun convRangeEciArray(destructured: MatchResult.Destructured): LongArray {
        val (eNB, c1, c2) = destructured
        val neNB256 = eNB.toLong() * 256
        val n1 = neNB256 + c1.toInt()
        val n2 = neNB256 + c2.toInt()
        return (n1..n2).toList().toLongArray()
    }

    fun getEciFromMemo(value: String): LongArray {
        var matchResult: MatchResult?

        // LCID3つ
        matchResult = regexEnb0c3.find(value)
        if (matchResult != null) return conv3EciArray(matchResult.destructured)
        matchResult = regexEnb1c3.find(value)
        if (matchResult != null) return conv3EciArray(matchResult.destructured)
        matchResult = regexEnb2c2.find(value)
        if (matchResult != null) return convRangeEciArray(matchResult.destructured)
        matchResult = regexEnb3c3.find(value)
        if (matchResult != null) return conv3EciArray(matchResult.destructured)

        // LCID2つ
        matchResult = regexEnb0c2.find(value)
        if (matchResult != null) return conv2EciArray(matchResult.destructured)
        matchResult = regexEnb1c2.find(value)
        if (matchResult != null) return conv2EciArray(matchResult.destructured)
        matchResult = regexEnb3c2.find(value)
        if (matchResult != null) return conv2EciArray(matchResult.destructured)

        // LCID1つ
        matchResult = regexEnb0c1.find(value)
        if (matchResult != null) return conv1EciArray(matchResult.destructured)
        matchResult = regexEnb1c1.find(value)
        if (matchResult != null) return conv1EciArray(matchResult.destructured)
        matchResult = regexEnb2c1.find(value)
        if (matchResult != null) return conv1EciArray(matchResult.destructured)
        matchResult = regexEnb3c1.find(value)
        if (matchResult != null) return conv1EciArray(matchResult.destructured)

        // 見つからなかったので空を返す
        return longArrayOf()
    }

    // ECIのリストから、eNB-LCIDの文字列を作る。
    // eciAryはソート済みであること
    fun getEnbLcid(eciList: List<Long>): String {
        var enbLcid = ""
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
        return enbLcid
    }
}