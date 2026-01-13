package com.mzc.secondproject.serverless.domain.vocabulary.enums;

import java.util.Arrays;

public enum WordCategory {
	DAILY("daily", "일상"),
	BUSINESS("business", "비즈니스"),
	ACADEMIC("academic", "학술"),
	TRAVEL("travel", "여행"),
	TECHNOLOGY("technology", "기술");
	
	private final String code;
	private final String displayName;
	
	WordCategory(String code, String displayName) {
		this.code = code;
		this.displayName = displayName;
	}
	
	public static boolean isValid(String value) {
		if (value == null) return false;
		return Arrays.stream(values())
				.anyMatch(cat -> cat.name().equalsIgnoreCase(value) || cat.code.equalsIgnoreCase(value));
	}
	
	public static WordCategory fromString(String value) {
		if (value == null) {
			throw new IllegalArgumentException("WordCategory value cannot be null");
		}
		return Arrays.stream(values())
				.filter(cat -> cat.name().equalsIgnoreCase(value) || cat.code.equalsIgnoreCase(value))
				.findFirst()
				.orElseThrow(() -> new IllegalArgumentException("Unknown WordCategory: " + value));
	}
	
	public static WordCategory fromStringOrDefault(String value, WordCategory defaultValue) {
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
