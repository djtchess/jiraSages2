package fr.agile.utils;


import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;

public final class BurnupUtils {

    private BurnupUtils() {
        // Prevent instantiation
    }

    public static Double parseDouble(String value) {
        if (value == null) return null;
        String v = value.trim();
        if (v.isEmpty()) return null;
        v = v.replace(',', '.');
        try { return Double.parseDouble(v); }
        catch (NumberFormatException e) { return null; }
    }

    public static double roundToTwoDecimals(double value) {
        return new BigDecimal(value).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }

    public static final DateTimeFormatter JIRA_DATE_FORMATTER =
            new DateTimeFormatterBuilder()
                    .appendPattern("yyyy-MM-dd'T'HH:mm:ss.SSS")
                    .optionalStart().appendOffset("+HH:MM", "Z").optionalEnd()
                    .optionalStart().appendOffset("+HHMM", "Z").optionalEnd()
                    .toFormatter();

    public static ZonedDateTime parseZonedDateTime(String s) {
        return ZonedDateTime.parse(s, JIRA_DATE_FORMATTER);
    }



}

