package com.mzc.secondproject.serverless.common.enums;

import java.util.Arrays;

public enum Difficulty {
    EASY("easy", "쉬움"),
    NORMAL("normal", "보통"),
    HARD("hard", "어려움");

    private final String code;
    private final String displayName;

    Difficulty(String code, String displayName) {
        this.code = code;
        this.displayName = displayName;
    }

    public String getCode() {
        return code;
    }

    public String getDisplayName() {
        return displayName;
    }

    public static boolean isValid(String value) {
        if (value == null) return false;
        return Arrays.stream(values())
                .anyMatch(d -> d.name().equalsIgnoreCase(value) || d.code.equalsIgnoreCase(value));
    }

    public static Difficulty fromString(String value) {
        if (value == null) {
            throw new IllegalArgumentException("Difficulty value cannot be null");
        }
        return Arrays.stream(values())
                .filter(d -> d.name().equalsIgnoreCase(value) || d.code.equalsIgnoreCase(value))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown Difficulty: " + value));
    }

    public static Difficulty fromStringOrDefault(String value, Difficulty defaultValue) {
        if (value == null || !isValid(value)) {
            return defaultValue;
        }
        return fromString(value);
    }
}
