package com.mzc.secondproject.serverless.domain.vocabulary.enums;

import java.util.Arrays;

public enum TestType {
    DAILY("daily", "일일 테스트"),
    WEEKLY("weekly", "주간 테스트"),
    CUSTOM("custom", "사용자 지정 테스트");

    private final String code;
    private final String displayName;

    TestType(String code, String displayName) {
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
                .anyMatch(type -> type.name().equalsIgnoreCase(value) || type.code.equalsIgnoreCase(value));
    }

    public static TestType fromString(String value) {
        if (value == null) {
            throw new IllegalArgumentException("TestType value cannot be null");
        }
        return Arrays.stream(values())
                .filter(type -> type.name().equalsIgnoreCase(value) || type.code.equalsIgnoreCase(value))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown TestType: " + value));
    }

    public static TestType fromStringOrDefault(String value, TestType defaultValue) {
        if (value == null || !isValid(value)) {
            return defaultValue;
        }
        return fromString(value);
    }
}
