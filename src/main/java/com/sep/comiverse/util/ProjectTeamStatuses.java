package com.sep.comiverse.util;

public final class ProjectTeamStatuses {

    public static final String ONGOING = "ongoing";
    public static final String COMPLETED = "completed";
    public static final String PAUSED = "paused";

    private ProjectTeamStatuses() {
    }

    public static String normalize(String status) {
        String value = status == null ? "" : status.trim().toLowerCase();
        if (value.isEmpty()) {
            return ONGOING;
        }
        if (isCompleted(value)) {
            return COMPLETED;
        }
        if (value.equals(PAUSED)) {
            return PAUSED;
        }
        if (value.equals("unclaimed")) {
            return "UNCLAIMED";
        }
        if (isParticipating(value)) {
            return ONGOING;
        }
        return value;
    }

    public static boolean isCompleted(String status) {
        String value = status == null ? "" : status.trim().toLowerCase();
        return value.equals(COMPLETED) || value.equals("complete") || value.equals("done");
    }

    public static boolean isParticipating(String status) {
        String value = status == null ? "" : status.trim().toLowerCase();
        return value.equals("active") || value.equals(ONGOING);
    }
}
