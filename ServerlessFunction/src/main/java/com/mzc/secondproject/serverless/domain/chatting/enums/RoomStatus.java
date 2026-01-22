package com.mzc.secondproject.serverless.domain.chatting.enums;

import java.util.Arrays;

public enum RoomStatus {
	WAITING("waiting", "대기 중"),
	PLAYING("playing", "게임 중"),
	FINISHED("finished", "종료됨");
	
	private final String code;
	private final String displayName;
	
	RoomStatus(String code, String displayName) {
		this.code = code;
		this.displayName = displayName;
	}
	
	public static boolean isValid(String value) {
		if (value == null) return false;
		return Arrays.stream(values())
				.anyMatch(status -> status.name().equalsIgnoreCase(value) || status.code.equalsIgnoreCase(value));
	}
	
	public static RoomStatus fromString(String value) {
		if (value == null) return WAITING;
		return Arrays.stream(values())
				.filter(status -> status.name().equalsIgnoreCase(value) || status.code.equalsIgnoreCase(value))
				.findFirst()
				.orElse(WAITING);
	}
	
	public String getCode() {
		return code;
	}
	
	public String getDisplayName() {
		return displayName;
	}
}
