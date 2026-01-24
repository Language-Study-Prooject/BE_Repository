package com.mzc.secondproject.serverless.domain.news.enums;

import java.util.Arrays;

/**
 * 뉴스 퀴즈 유형
 */
public enum QuizType {
	COMPREHENSION("comprehension", "독해 질문", 20),
	WORD_MATCH("word_match", "단어-뜻 매칭", 15),
	FILL_BLANK("fill_blank", "빈칸 채우기", 30);
	
	private final String code;
	private final String displayName;
	private final int defaultPoints;
	
	QuizType(String code, String displayName, int defaultPoints) {
		this.code = code;
		this.displayName = displayName;
		this.defaultPoints = defaultPoints;
	}
	
	public static boolean isValid(String value) {
		if (value == null) return false;
		return Arrays.stream(values())
				.anyMatch(type -> type.name().equalsIgnoreCase(value) || type.code.equalsIgnoreCase(value));
	}
	
	public static QuizType fromString(String value) {
		if (value == null) {
			throw new IllegalArgumentException("QuizType value cannot be null");
		}
		return Arrays.stream(values())
				.filter(type -> type.name().equalsIgnoreCase(value) || type.code.equalsIgnoreCase(value))
				.findFirst()
				.orElseThrow(() -> new IllegalArgumentException("Unknown QuizType: " + value));
	}
	
	public String getCode() {
		return code;
	}
	
	public String getDisplayName() {
		return displayName;
	}
	
	public int getDefaultPoints() {
		return defaultPoints;
	}
}
