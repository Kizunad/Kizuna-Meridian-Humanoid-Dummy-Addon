package dev.kizuna.meridian.dummy.server;

import java.util.Locale;

/**
 * 击打报告中扣减与剂量的列出门槛和显示精度：不小于 0.1 保留一位小数，更小的保留两位；
 * 不足 0.005 的扣减在两位小数下也是 0.00，不进受伤清单。
 */
public final class HitAmounts {
    public static final double LISTED_MIN = 0.005;
    private static final double ONE_DECIMAL_MIN = 0.1;

    private HitAmounts() {
        // 纯函数集合。
    }

    public static boolean listed(double amount) {
        return amount >= LISTED_MIN;
    }

    public static String format(double amount) {
        // 报告里的扣减与剂量都已校验为非负，这里只分两档精度。
        if (amount >= ONE_DECIMAL_MIN) {
            return String.format(Locale.ROOT, "%.1f", amount);
        }
        return String.format(Locale.ROOT, "%.2f", amount);
    }
}
