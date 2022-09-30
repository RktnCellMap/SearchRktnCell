package jp.tech4u.searchrktncell.util

object EciUtil {

    private const val regEnb = "[0-9]{5,6}"
    private const val regPci = "[0-9]{1,3}"
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
    private val regexEnbNullpon = Regex("($regEnb) ($regLcid) ($regPci)")

    private fun conv3EciArray(destructured: MatchResult.Destructured): Pair<String, IntArray> {
        val (eNB, c1, c2, c3) = destructured
        val neNB256 = eNB.toInt() * 256
        val n1 = c1.toInt()
        val n2 = c2.toInt()
        val n3 = c3.toInt()
        val enbLcid = "$eNB-$n1,$n2,$n3"
        return Pair(enbLcid, intArrayOf(neNB256 + n1, neNB256 + n2, neNB256 + n3))
    }

    private fun conv2EciArray(destructured: MatchResult.Destructured): Pair<String, IntArray> {
        val (eNB, c1, c2) = destructured
        val neNB256 = eNB.toInt() * 256
        val n1 = c1.toInt()
        val n2 = c2.toInt()
        val enbLcid = "$eNB-$n1,$n2"
        return Pair(enbLcid, intArrayOf(neNB256 + n1, neNB256 + n2))
    }

    private fun conv1EciArray(destructured: MatchResult.Destructured): Pair<String, IntArray> {
        val (eNB, c1) = destructured
        val neNB256 = eNB.toInt() * 256
        val n1 = c1.toInt()
        val enbLcid = "$eNB-$n1"
        return Pair(enbLcid, intArrayOf(neNB256 + n1))
    }

    private fun convRangeEciArray(destructured: MatchResult.Destructured): Pair<String, IntArray> {
        val (eNB, c1, c2) = destructured
        val neNB256 = eNB.toInt() * 256
        val n1 = neNB256 + c1.toInt()
        val n2 = neNB256 + c2.toInt()
        val enbLcid = "$eNB-$n1~$n2"
        return Pair(enbLcid, (n1..n2).toList().toIntArray())
    }

    fun getEciFromMemo(value: String): Pair<String, IntArray> {
        var matchResult: MatchResult? = null

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

        return Pair("", intArrayOf())
    }

    fun getEciFromNullpon(value: String): Pair<String, IntArray> {
        var matchResult: MatchResult? = null

        val resultList = mutableListOf<Int>()
        matchResult = regexEnbNullpon.find(value)
        while (matchResult != null) {
            val (eNB, c1, pci) = matchResult.destructured
            resultList.add(eNB.toInt() * 256 + c1.toInt())
            matchResult = matchResult.next()
        }

        return Pair("", resultList.toIntArray())
    }
}