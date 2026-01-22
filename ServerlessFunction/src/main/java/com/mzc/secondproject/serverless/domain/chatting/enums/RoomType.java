package com.mzc.secondproject.serverless.domain.chatting.enums;

import java.util.Arrays;

public enum RoomType {
	CHAT("chat", "채팅방"),
	GAME("game", "게임방");
	
	private final String code;
	private final String displayName;
	
	RoomType(String code, String displayName) {
		this.code = code;
		this.displayName = displayName;
	}
	
	public static boolean isValid(String value) {
		if (value == null) return false;
		return Arrays.stream(values())
				.anyMatch(type -> type.name().equalsIgnoreCase(value) || type.code.equalsIgnoreCase(value));
	}
	
	public static RoomType fromString(String value) {
		if (value == null) return CHAT;
		return Arrays.stream(values())
				.filter(type -> type.name().equalsIgnoreCase(value) || type.code.equalsIgnoreCase(value))
				.findFirst()
				.orElse(CHAT);
	}
	
	public String getCode() {
		return code;
	}
	
	public String getDisplayName() {
		return displayName;
	}
}
