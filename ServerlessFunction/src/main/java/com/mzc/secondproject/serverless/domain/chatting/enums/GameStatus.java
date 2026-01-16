package com.mzc.secondproject.serverless.domain.chatting.enums;

import java.util.Arrays;

public enum GameStatus {
	NONE("none", "게임 없음"),
	WAITING("waiting", "게임 대기 중"),
	PLAYING("playing", "게임 진행 중"),
	ROUND_END("round_end", "라운드 종료"),
	FINISHED("finished", "게임 종료");
	
	private final String code;
	private final String displayName;
	
	GameStatus(String code, String displayName) {
		this.code = code;
		this.displayName = displayName;
	}
	
	public static boolean isValid(String value) {
		if (value == null) return false;
		return Arrays.stream(values())
				.anyMatch(status -> status.name().equalsIgnoreCase(value) || status.code.equalsIgnoreCase(value));
	}
	
	public static GameStatus fromString(String value) {
		if (value == null) return NONE;
		return Arrays.stream(values())
				.filter(status -> status.name().equalsIgnoreCase(value) || status.code.equalsIgnoreCase(value))
				.findFirst()
				.orElse(NONE);
	}
	
	public String getCode() {
		return code;
	}
	
	public String getDisplayName() {
		return displayName;
	}
	
	public boolean isGameActive() {
		return this == PLAYING || this == ROUND_END;
	}
	
	public boolean canStartGame() {
		return this == NONE || this == FINISHED;
	}
}
