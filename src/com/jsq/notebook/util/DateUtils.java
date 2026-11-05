package com.jsq.notebook.util;

import java.util.Calendar;
import java.util.Date;

/**
 * Lightweight date formatting utility for CLDC 1.1.
 * Avoids dependencies on locale formatting packages and allocations.
 */
public final class DateUtils {

    private DateUtils() {
    }

    /**
     * Formats timestamp into "YYYY-MM-DD HH:MM".
     */
    public static String formatDateTime(long timestamp) {
        Calendar cal = Calendar.getInstance();
        cal.setTime(new Date(timestamp));

        StringBuffer sb = new StringBuffer(16);
        append4Digits(sb, cal.get(Calendar.YEAR));
        sb.append('-');
        append2Digits(sb, cal.get(Calendar.MONTH) + 1);
        sb.append('-');
        append2Digits(sb, cal.get(Calendar.DAY_OF_MONTH));
        sb.append(' ');
        append2Digits(sb, cal.get(Calendar.HOUR_OF_DAY));
        sb.append(':');
        append2Digits(sb, cal.get(Calendar.MINUTE));

        return sb.toString();
    }

    /**
     * Formats timestamp into date only "YYYY-MM-DD".
     */
    public static String formatDate(long timestamp) {
        Calendar cal = Calendar.getInstance();
        cal.setTime(new Date(timestamp));

        StringBuffer sb = new StringBuffer(10);
        append4Digits(sb, cal.get(Calendar.YEAR));
        sb.append('-');
        append2Digits(sb, cal.get(Calendar.MONTH) + 1);
        sb.append('-');
        append2Digits(sb, cal.get(Calendar.DAY_OF_MONTH));

        return sb.toString();
    }

    /**
     * Formats timestamp into ISO 8601 "YYYY-MM-DDTHH:MM:SS".
     */
    public static String formatIso8601(long timestamp) {
        Calendar cal = Calendar.getInstance();
        cal.setTime(new Date(timestamp));

        StringBuffer sb = new StringBuffer(20);
        append4Digits(sb, cal.get(Calendar.YEAR));
        sb.append('-');
        append2Digits(sb, cal.get(Calendar.MONTH) + 1);
        sb.append('-');
        append2Digits(sb, cal.get(Calendar.DAY_OF_MONTH));
        sb.append('T');
        append2Digits(sb, cal.get(Calendar.HOUR_OF_DAY));
        sb.append(':');
        append2Digits(sb, cal.get(Calendar.MINUTE));
        sb.append(':');
        append2Digits(sb, cal.get(Calendar.SECOND));

        return sb.toString();
    }

    private static void append2Digits(StringBuffer sb, int val) {
        if (val < 10) {
            sb.append('0');
        }
        sb.append(val);
    }

    private static void append4Digits(StringBuffer sb, int val) {
        if (val < 10) {
            sb.append("000");
        } else if (val < 100) {
            sb.append("00");
        } else if (val < 1000) {
            sb.append('0');
        }
        sb.append(val);
    }
}
