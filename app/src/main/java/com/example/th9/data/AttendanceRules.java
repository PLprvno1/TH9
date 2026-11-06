package com.example.th9.data;

public final class AttendanceRules {
    private AttendanceRules() {}
    public static String statusAt(long actual, Long threshold) {
        return threshold != null && Math.floorDiv(actual, 60000) > Math.floorDiv(threshold, 60000) ? "LATE" : "PRESENT";
    }
    public static void require(boolean condition, String message) {
        if (!condition) throw new IllegalArgumentException(message);
    }
}
