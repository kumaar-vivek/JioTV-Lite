package com.example.jiotvservice.api;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

public final class PlaybackRequestClock {
    private static final TimeZone IST = TimeZone.getTimeZone("Asia/Kolkata");

    private PlaybackRequestClock() {}

    public static Stamp now() {
        Date now = new Date();
        return new Stamp(format("yyyyMMdd'T'HHmmss", now), format("yyyyMMdd", now));
    }

    private static String format(String pattern, Date date) {
        SimpleDateFormat formatter = new SimpleDateFormat(pattern, Locale.US);
        formatter.setTimeZone(IST);
        return formatter.format(date);
    }

    public static final class Stamp {
        public final String begin;
        public final String srno;

        private Stamp(String begin, String srno) {
            this.begin = begin;
            this.srno = srno;
        }
    }
}
