package com.wh.review.backend.util;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class MathUtils {

    private MathUtils() {
    }

    public static double clamp01(double value) {
        if (Double.isNaN(value) || value < 0D) {
            return 0D;
        }
        if (value > 1D) {
            return 1D;
        }
        return value;
    }

    public static double roundTo4(double value) {
        return round(value, 4);
    }

    public static double roundTo2(double value) {
        return round(value, 2);
    }

    private static double round(double value, int scale) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            return 0D;
        }
        return BigDecimal.valueOf(value).setScale(scale, RoundingMode.HALF_UP).doubleValue();
    }
}
