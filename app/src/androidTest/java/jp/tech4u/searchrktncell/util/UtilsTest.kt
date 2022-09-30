package jp.tech4u.searchrktncell.util

import org.junit.Test
import org.junit.Assert.*

class UtilsTest {
    // OKケース１
    @Test
    fun SampleTest001() {

        // 小平
        assertArrayEquals(intArrayOf(71624961,71624962,71624963), EciUtil.getEciFromMemo("end:279785-1,2,3<br>").second)
        assertArrayEquals(intArrayOf(71624961,71624963), EciUtil.getEciFromMemo("end:279785-1,3<br>").second)
        assertArrayEquals(intArrayOf(71624961), EciUtil.getEciFromMemo("end:279785-1<br>").second)
        assertArrayEquals(intArrayOf(71624970,71624971,71624972), EciUtil.getEciFromMemo("279785-10,11,12").second)
        assertArrayEquals(intArrayOf(71624970,71624971), EciUtil.getEciFromMemo("end:279785-10,11<br>").second)
        assertArrayEquals(intArrayOf(71624971), EciUtil.getEciFromMemo("end:279785-11<br>").second)

        // 上尾
        assertArrayEquals(intArrayOf(71624961,71624962,71624963), EciUtil.getEciFromMemo("end:279785 1-2-3<br>").second)
        assertArrayEquals(intArrayOf(71624961,71624963), EciUtil.getEciFromMemo("end:279785 1-3<br>").second)
        assertArrayEquals(intArrayOf(71624961), EciUtil.getEciFromMemo("end:279785 1<br>").second)

        // 足立
        assertArrayEquals(intArrayOf(71624961,71624962,71624963), EciUtil.getEciFromMemo("end:279785-1-2-3<br>").second)
        assertArrayEquals(intArrayOf(71624961,71624963), EciUtil.getEciFromMemo("end:279785-1-3<br>").second)
        assertArrayEquals(intArrayOf(71624961), EciUtil.getEciFromMemo("end:279785-1<br>").second)

        // 奈良
        assertArrayEquals(intArrayOf(113340679,113340680,113340681), EciUtil.getEciFromMemo("442737(CID:7~9)").second)
        assertArrayEquals(intArrayOf(113340679), EciUtil.getEciFromMemo("442737(CID:7)").second)

        // 秋田
        assertArrayEquals(intArrayOf(29363713,29363714,29363715), EciUtil.getEciFromNullpon("<br>114702 1 268<br>114702 2 267→201<br>114702 3 269<br>").second)

    }
}