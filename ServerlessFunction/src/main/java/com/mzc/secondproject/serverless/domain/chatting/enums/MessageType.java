package com.mzc.secondproject.serverless.domain.chatting.enums;

import java.util.Arrays;

public enum MessageType {
	TEXT("text", "텍스트"),
	IMAGE("image", "이미지"),
	VOICE("voice", "음성"),
	AI_RESPONSE("ai_response", "AI 응답"),
	
	// 게임 관련 메시지 타입
	GAME_START("game_start", "게임 시작"),
	GAME_END("game_end", "게임 종료"),
	ROUND_START("round_start", "라운드 시작"),
	ROUND_END("round_end", "라운드 종료"),
	DRAWING("drawing", "그림 데이터"),
	DRAWING_CLEAR("drawing_clear", "그림 초기화"),
	CORRECT_ANSWER("correct_answer", "정답"),
	SCORE_UPDATE("score_update", "점수 업데이트"),
	SYSTEM_COMMAND("system_command", "시스템 명령"),
	HINT("hint", "힌트"),

	// 방 관련 메시지 타입
	ROOM_STATUS_CHANGE("room_status_change", "방 상태 변경"),
	HOST_CHANGE("host_change", "방장 변경");
	
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
