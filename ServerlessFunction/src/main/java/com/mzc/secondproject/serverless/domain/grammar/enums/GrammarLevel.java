package com.mzc.secondproject.serverless.domain.grammar.enums;

import java.util.Arrays;

public enum GrammarLevel {
	BEGINNER("beginner", "초급", "한국어 번역과 쉬운 설명 포함"),
	INTERMEDIATE("intermediate", "중급", "영어 위주 설명"),
	ADVANCED("advanced", "고급", "상세한 문법 규칙 설명");

	private final String code;
	private final String displayName;
	private final String description;

	GrammarLevel(String code, String displayName, String description) {
		this.code = code;
		this.displayName = displayName;
		this.description = description;
	}

	public static boolean isValid(String value) {
		if (value == null) return false;
		return Arrays.stream(values())
				.anyMatch(level -> level.name().equalsIgnoreCase(value) || level.code.equalsIgnoreCase(value));
	}

	public static GrammarLevel fromString(String value) {
		if (value == null) {
			throw new IllegalArgumentException("GrammarLevel value cannot be null");
		}
		return Arrays.stream(values())
				.filter(level -> level.name().equalsIgnoreCase(value) || level.code.equalsIgnoreCase(value))
				.findFirst()
				.orElseThrow(() -> new IllegalArgumentException("Unknown GrammarLevel: " + value));
	}

	public static GrammarLevel fromStringOrDefault(String value, GrammarLevel defaultValue) {
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

	public String getDescription() {
		return description;
	}
}
