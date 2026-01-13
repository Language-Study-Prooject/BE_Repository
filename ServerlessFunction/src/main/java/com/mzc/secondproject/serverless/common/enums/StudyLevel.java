package com.mzc.secondproject.serverless.common.enums;

import java.util.Arrays;

public enum StudyLevel {
	BEGINNER("beginner", "초급"),
	INTERMEDIATE("intermediate", "중급"),
	ADVANCED("advanced", "고급");
	
	private final String code;
	private final String displayName;
	
	StudyLevel(String code, String displayName) {
		this.code = code;
		this.displayName = displayName;
	}
	
	public static boolean isValid(String value) {
		if (value == null) return false;
		return Arrays.stream(values())
				.anyMatch(level -> level.name().equalsIgnoreCase(value) || level.code.equalsIgnoreCase(value));
	}
	
	public static StudyLevel fromString(String value) {
		if (value == null) {
			throw new IllegalArgumentException("StudyLevel value cannot be null");
		}
		return Arrays.stream(values())
				.filter(level -> level.name().equalsIgnoreCase(value) || level.code.equalsIgnoreCase(value))
				.findFirst()
				.orElseThrow(() -> new IllegalArgumentException("Unknown StudyLevel: " + value));
	}
	
	public static StudyLevel fromStringOrDefault(String value, StudyLevel defaultValue) {
		if (value == null || !isValid(value)) {
			return defaultValue;
		}
		return fromString(value);
	}
	
	public String getCode() {
		return code;
	}
	
	public String getDisplayName() {
		return displayName;
	}
}
