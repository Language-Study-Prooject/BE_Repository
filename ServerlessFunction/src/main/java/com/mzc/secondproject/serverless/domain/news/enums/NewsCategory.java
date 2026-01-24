package com.mzc.secondproject.serverless.domain.news.enums;

import java.util.Arrays;

/**
 * 뉴스 카테고리
 */
public enum NewsCategory {
	TECH("tech", "기술"),
	BUSINESS("business", "비즈니스"),
	SPORTS("sports", "스포츠"),
	ENTERTAINMENT("entertainment", "엔터테인먼트"),
	WORLD("world", "세계"),
	CULTURE("culture", "문화"),
	SCIENCE("science", "과학");
	
	private final String code;
	private final String displayName;
	
	NewsCategory(String code, String displayName) {
		this.code = code;
		this.displayName = displayName;
	}
	
	public static boolean isValid(String value) {
		if (value == null) return false;
		return Arrays.stream(values())
				.anyMatch(cat -> cat.name().equalsIgnoreCase(value) || cat.code.equalsIgnoreCase(value));
	}
	
	public static NewsCategory fromString(String value) {
		if (value == null) {
			throw new IllegalArgumentException("NewsCategory value cannot be null");
		}
		return Arrays.stream(values())
				.filter(cat -> cat.name().equalsIgnoreCase(value) || cat.code.equalsIgnoreCase(value))
				.findFirst()
				.orElseThrow(() -> new IllegalArgumentException("Unknown NewsCategory: " + value));
	}
	
	public static NewsCategory fromStringOrDefault(String value, NewsCategory defaultValue) {
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
