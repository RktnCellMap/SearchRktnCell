package jp.tech4u.searchrktncell.util

import org.junit.Test
import org.junit.Assert.*

class UtilsTest {

    // OKケース１
    @Test
    fun sampleTest001() {

        var eciAry: LongArray

        // 小平
        eciAry = EciUtil.getEciFromMemo("end:279785-1,2,3<br>")
        assertEquals("279785-1,2,3", EciUtil.getEnbLcid(eciAry.toList()))
        assertArrayEquals(longArrayOf(71624961, 71624962, 71624963), eciAry)
        eciAry = EciUtil.getEciFromMemo("end:279785-1,3<br>")
        assertEquals("279785-1,3", EciUtil.getEnbLcid(eciAry.toList()))
        assertArrayEquals(longArrayOf(71624961, 71624963), eciAry)
        eciAry = EciUtil.getEciFromMemo("end:279785-1<br>")
        assertEquals("279785-1", EciUtil.getEnbLcid(eciAry.toList()))
        assertArrayEquals(longArrayOf(71624961), eciAry)
        eciAry = EciUtil.getEciFromMemo("279785-10,11,12")
        assertEquals("279785-10,11,12", EciUtil.getEnbLcid(eciAry.toList()))
        assertArrayEquals(longArrayOf(71624970, 71624971, 71624972), eciAry)
        eciAry = EciUtil.getEciFromMemo("end:279785-10,11<br>")
        assertEquals("279785-10,11", EciUtil.getEnbLcid(eciAry.toList()))
        assertArrayEquals(longArrayOf(71624970, 71624971), eciAry)
        eciAry = EciUtil.getEciFromMemo("end:279785-11<br>")
        assertEquals("279785-11", EciUtil.getEnbLcid(eciAry.toList()))
        assertArrayEquals(longArrayOf(71624971), eciAry)

        // 5桁
        eciAry = EciUtil.getEciFromMemo("end:12345-1,2,3<br>")
        assertEquals("12345-1,2,3", EciUtil.getEnbLcid(eciAry.toList()))
        assertArrayEquals(longArrayOf(12345*256+1, 12345*256+2, 12345*256+3), eciAry)
        // 7桁
        eciAry = EciUtil.getEciFromMemo("end:1234567-1,2,3<br>")
        assertEquals("1234567-1,2,3", EciUtil.getEnbLcid(eciAry.toList()))
        assertArrayEquals(longArrayOf(1234567*256+1, 1234567*256+2, 1234567*256+3), eciAry)

        // 千葉 車載基地局
        eciAry = EciUtil.getEciFromMemo("2020.11.24 1015829-1,1015827-1\n車載基地局\n21.1 三里塚開局に伴い撤退？")
        assertArrayEquals(longArrayOf(1015829*256+1), eciAry)
        assertEquals("1015829-1", EciUtil.getEnbLcid(eciAry.toList()))

        // 上尾
        eciAry = EciUtil.getEciFromMemo("end:279785 1-2-3<br>")
        assertEquals("279785-1,2,3", EciUtil.getEnbLcid(eciAry.toList()))
        assertArrayEquals(longArrayOf(71624961, 71624962, 71624963), eciAry)
        eciAry = EciUtil.getEciFromMemo("end:279785 1-3<br>")
        assertEquals("279785-1,3", EciUtil.getEnbLcid(eciAry.toList()))
        assertArrayEquals(longArrayOf(71624961, 71624963), eciAry)
        eciAry = EciUtil.getEciFromMemo("end:279785 1<br>")
        assertEquals("279785-1", EciUtil.getEnbLcid(eciAry.toList()))
        assertArrayEquals(longArrayOf(71624961), eciAry)

        // 足立
        eciAry = EciUtil.getEciFromMemo("end:279785-1-2-3<br>")
        assertEquals("279785-1,2,3", EciUtil.getEnbLcid(eciAry.toList()))
        assertArrayEquals(longArrayOf(71624961, 71624962, 71624963), eciAry)
        eciAry = EciUtil.getEciFromMemo("end:279785-1-3<br>")
        assertEquals("279785-1,3", EciUtil.getEnbLcid(eciAry.toList()))
        assertArrayEquals(longArrayOf(71624961, 71624963), eciAry)
        eciAry = EciUtil.getEciFromMemo("end:279785-1<br>")
        assertEquals("279785-1", EciUtil.getEnbLcid(eciAry.toList()))
        assertArrayEquals(longArrayOf(71624961), eciAry)

        // 奈良
        eciAry = EciUtil.getEciFromMemo("442737(CID:7~9)")
        assertEquals("442737-7,8,9", EciUtil.getEnbLcid(eciAry.toList()))
        assertArrayEquals(longArrayOf(113340679, 113340680, 113340681), eciAry)
        eciAry = EciUtil.getEciFromMemo("442737(CID:7)")
        assertEquals("442737-7", EciUtil.getEnbLcid(eciAry.toList()))
        assertArrayEquals(longArrayOf(113340679), eciAry)
    }
}