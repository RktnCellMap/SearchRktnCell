package jp.tech4u.searchrktncell.data

class CellId(val ci: Long) {
    val eNB = ci / 256
    val sector = (ci % 256).toInt()
    val sector3 = (sector - 1) % 3
    private val grp = (sector - 1) / 3
    private val eNB1 = "" + eNB + '-' + (grp * 3 + 1)

    fun equalBs(a: CellId): Boolean {
        return a.eNB1 == this.eNB1
    }
}