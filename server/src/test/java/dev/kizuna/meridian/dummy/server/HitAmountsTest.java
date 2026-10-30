package dev.kizuna.meridian.dummy.server;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class HitAmountsTest {
    @Test
    public void amountsKeepOneDecimalFromATenthAndTwoBelow() {
        assertEquals("1.0", HitAmounts.format(1.0));
        assertEquals("0.1", HitAmounts.format(0.1));
        assertEquals("0.09", HitAmounts.format(0.09));
        assertEquals("0.01", HitAmounts.format(0.01));
        assertEquals("0.01", HitAmounts.format(HitAmounts.LISTED_MIN));
    }

    @Test
    public void amountsBelowHalfAHundredthAreNotListed() {
        // 门槛本身列出：两位小数下它显示为 0.01，不会出现 0.00。
        assertTrue(HitAmounts.listed(HitAmounts.LISTED_MIN));
        assertFalse(HitAmounts.listed(0.0049));
        assertFalse(HitAmounts.listed(0));
    }
}
