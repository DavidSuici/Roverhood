package com.suici.roverhood.utils;

import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.Context;

import org.threeten.bp.Instant;
import org.threeten.bp.ZoneId;
import org.threeten.bp.format.DateTimeFormatter;

import java.util.Calendar;

public class Date {

    // Format: "30 April 2025 - 12:30"
    private static final DateTimeFormatter DISPLAY_FORMATTER = DateTimeFormatter.ofPattern("dd MMMM yyyy - HH:mm")
            .withZone(ZoneId.systemDefault());

    // Converts a Unix timestamp to a formatted date string for display.
    public static String formatTimestamp(long timestamp) {
        return DISPLAY_FORMATTER.format(Instant.ofEpochSecond(timestamp));
    }

    public interface OnPicked {
        void onPicked(long timestampSeconds);
    }

    // Date picker, then time picker, starting from the given timestamp (seconds, phone's time zone).
    // Used by staff to change the date of a post or comment.
    public static void pickDateTime(Context context, long currentSeconds, OnPicked onPicked) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(currentSeconds * 1000L);

        new DatePickerDialog(context, (dateView, year, month, day) -> {
            calendar.set(Calendar.YEAR, year);
            calendar.set(Calendar.MONTH, month);
            calendar.set(Calendar.DAY_OF_MONTH, day);

            new TimePickerDialog(context, (timeView, hour, minute) -> {
                calendar.set(Calendar.HOUR_OF_DAY, hour);
                calendar.set(Calendar.MINUTE, minute);
                calendar.set(Calendar.SECOND, 0);
                onPicked.onPicked(calendar.getTimeInMillis() / 1000L);
            }, calendar.get(Calendar.HOUR_OF_DAY), calendar.get(Calendar.MINUTE), true).show();
        }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH)).show();
    }
}
