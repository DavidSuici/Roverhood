package com.suici.roverhood;

import org.threeten.bp.Instant;
import org.threeten.bp.ZoneId;
import org.threeten.bp.format.DateTimeFormatter;

public class DateUtil {

    // Format: "30 April 2025 - 12:30"
    private static final DateTimeFormatter DISPLAY_FORMATTER = DateTimeFormatter.ofPattern("dd MMMM yyyy - HH:mm")
            .withZone(ZoneId.systemDefault());

    // Converts a Unix timestamp (in seconds) to a formatted date string for display.
    public static String formatTimestamp(long timestamp) {
        return DISPLAY_FORMATTER.format(Instant.ofEpochSecond(timestamp));
    }
}