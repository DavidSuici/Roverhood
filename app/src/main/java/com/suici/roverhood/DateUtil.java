package com.suici.roverhood;

import org.threeten.bp.Instant;
import org.threeten.bp.LocalDateTime;
import org.threeten.bp.ZoneId;
import org.threeten.bp.format.DateTimeFormatter;

public class DateUtil {

    // Format: "30 April 2025 - 12:30"
    private static final DateTimeFormatter DISPLAY_FORMATTER = DateTimeFormatter.ofPattern("dd MMMM yyyy - HH:mm")
            .withZone(ZoneId.systemDefault());

    // Format used for parsing Firebase date strings: "yyyy-MM-dd HH:mm"
    private static final DateTimeFormatter PARSE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    /**
     * Converts a Unix timestamp (in seconds) to a formatted date string for display.
     */
    public static String formatTimestamp(long timestamp) {
        return DISPLAY_FORMATTER.format(Instant.ofEpochSecond(timestamp));
    }

    /**
     * Parses a string like "2025-04-30 12:30" into a Unix timestamp (seconds).
     */
    public static long toTimestamp(String dateTimeStr) {
        LocalDateTime dateTime = LocalDateTime.parse(dateTimeStr, PARSE_FORMATTER);
        return dateTime.atZone(ZoneId.systemDefault()).toEpochSecond();
    }
}