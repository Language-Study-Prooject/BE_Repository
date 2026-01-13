package com.mzc.secondproject.serverless.domain.vocabulary.enums;

import java.util.Arrays;

public enum WordStatus {
    NEW("new", "새 단어"),
    LEARNING("learning", "학습 중"),
    REVIEWING("reviewing", "복습 중"),
    MASTERED("mastered", "완료"),
    UNKNOWN("unknown", "모르겠음");

    private final String code;
    private final String displayName;

    WordStatus(String code, String displayName) {
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
                .anyMatch(status -> status.name().equalsIgnoreCase(value) || status.code.equalsIgnoreCase(value));
    }

    public static WordStatus fromString(String value) {
        if (value == null) {
            throw new IllegalArgumentException("WordStatus value cannot be null");
        }
        return Arrays.stream(values())
                .filter(status -> status.name().equalsIgnoreCase(value) || status.code.equalsIgnoreCase(value))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown WordStatus: " + value));
    }

    public static WordStatus fromStringOrDefault(String value, WordStatus defaultValue) {
        if (value == null || !isValid(value)) {
            return defaultValue;
        }
        return fromString(value);
    }
}
