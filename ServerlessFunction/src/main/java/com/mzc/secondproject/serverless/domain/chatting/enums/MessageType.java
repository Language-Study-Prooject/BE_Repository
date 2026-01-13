package com.mzc.secondproject.serverless.domain.chatting.enums;

import java.util.Arrays;

public enum MessageType {
	TEXT("text", "텍스트"),
	IMAGE("image", "이미지"),
	VOICE("voice", "음성"),
	AI_RESPONSE("ai_response", "AI 응답");
	
	private final String code;
	private final String displayName;
	
	MessageType(String code, String displayName) {
		this.code = code;
		this.displayName = displayName;
	}
	
	public static boolean isValid(String value) {
		if (value == null) return false;
		return Arrays.stream(values())
				.anyMatch(type -> type.name().equalsIgnoreCase(value) || type.code.equalsIgnoreCase(value));
	}
	
	public static MessageType fromString(String value) {
		if (value == null) {
			throw new IllegalArgumentException("MessageType value cannot be null");
		}
		return Arrays.stream(values())
				.filter(type -> type.name().equalsIgnoreCase(value) || type.code.equalsIgnoreCase(value))
				.findFirst()
				.orElseThrow(() -> new IllegalArgumentException("Unknown MessageType: " + value));
	}
	
	public static MessageType fromStringOrDefault(String value, MessageType defaultValue) {
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
