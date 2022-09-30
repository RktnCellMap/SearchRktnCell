package jp.tech4u.searchrktncell.util

import jp.tech4u.searchrktncell.R
import org.junit.Assert.assertEquals
import org.junit.Test

class UtilsTest {
    @Test
    fun getLuminanceColor() {
        assertEquals(0, Utils.getLuminanceColor(0))
        assertEquals(128, Utils.getLuminanceColor(0x808080))
        assertEquals(255, Utils.getLuminanceColor(0xFFFFFF))
    }
    @Test
    fun getRevMonoColor() {
        assertEquals(R.color.colorThemeIndependentDark, Utils.getRevMonoColor(Utils.calcColor(-70, 0, true)))
        assertEquals(R.color.colorThemeIndependentDark, Utils.getRevMonoColor(Utils.calcColor(-70, 1, true)))
        assertEquals(R.color.colorThemeIndependentLight, Utils.getRevMonoColor(Utils.calcColor(-70, 2, true)))

        assertEquals(R.color.colorThemeIndependentLight, Utils.getRevMonoColor(Utils.calcColor(-60, 0, true)))
        assertEquals(R.color.colorThemeIndependentDark, Utils.getRevMonoColor(Utils.calcColor(-60, 1, true)))
        assertEquals(R.color.colorThemeIndependentLight, Utils.getRevMonoColor(Utils.calcColor(-60, 2, true)))
    }
}