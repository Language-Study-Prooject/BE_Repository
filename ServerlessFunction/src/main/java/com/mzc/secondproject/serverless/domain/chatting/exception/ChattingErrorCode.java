package com.mzc.secondproject.serverless.domain.chatting.exception;

import com.mzc.secondproject.serverless.common.exception.DomainErrorCode;

/**
 * 채팅 도메인 에러 코드
 * <p>
 * 채팅방(Room), 메시지(Message), 참여자(Participant) 관련 에러 코드를 정의합니다.
 */
public enum ChattingErrorCode implements DomainErrorCode {
	
	// 채팅방 관련 에러
	ROOM_NOT_FOUND("ROOM_001", "채팅방을 찾을 수 없습니다", 404),
	ROOM_ALREADY_EXISTS("ROOM_002", "이미 존재하는 채팅방입니다", 409),
	ROOM_FULL("ROOM_003", "채팅방 인원이 가득 찼습니다", 400),
	ROOM_CLOSED("ROOM_004", "종료된 채팅방입니다", 400),
	ROOM_INVALID_PASSWORD("ROOM_005", "비밀번호가 일치하지 않습니다", 401),
	ROOM_NOT_OWNER("ROOM_006", "방장 권한이 필요합니다", 403),
	
	// 메시지 관련 에러
	MESSAGE_NOT_FOUND("MSG_001", "메시지를 찾을 수 없습니다", 404),
	MESSAGE_TOO_LONG("MSG_002", "메시지가 너무 깁니다", 400),
	INVALID_MESSAGE_TYPE("MSG_003", "유효하지 않은 메시지 타입입니다", 400),
	
	// 참여자 관련 에러
	NOT_ROOM_MEMBER("MEMBER_001", "채팅방 멤버가 아닙니다", 403),
	ALREADY_JOINED("MEMBER_002", "이미 참여 중인 채팅방입니다", 409),
	INVALID_ROOM_TOKEN("MEMBER_003", "유효하지 않은 방 토큰입니다", 401),
	
	// 채팅 레벨 관련 에러
	INVALID_CHAT_LEVEL("LEVEL_001", "유효하지 않은 채팅 레벨입니다", 400),
	
	// 연결 관련 에러
	CONNECTION_FAILED("CONN_001", "연결에 실패했습니다", 500),
	CONNECTION_TIMEOUT("CONN_002", "연결 시간이 초과되었습니다", 408),
	
	// 게임 관련 에러
	GAME_START_FAILED("GAME_001", "게임 시작에 실패했습니다", 400),
	GAME_STOP_FAILED("GAME_002", "게임 중단에 실패했습니다", 400),
	GAME_NOT_IN_PROGRESS("GAME_003", "진행 중인 게임이 없습니다", 400),
	GAME_ALREADY_IN_PROGRESS("GAME_004", "이미 게임이 진행 중입니다", 409),
	NOT_GAME_STARTER("GAME_005", "게임 시작자만 중단할 수 있습니다", 403),
	GAME_NOT_FOUND("GAME_006", "게임 세션을 찾을 수 없습니다", 404),
	GAME_NOT_ALLOWED_IN_CHAT_ROOM("GAME_007", "게임은 게임 방에서만 시작할 수 있습니다", 400),
	GAME_RESTART_NOT_ALLOWED("GAME_008", "게임 진행 중에는 재시작할 수 없습니다", 400),
	GAME_START_NOT_HOST("GAME_009", "방장만 게임을 시작할 수 있습니다", 403),
	;
	
	private static final String DOMAIN = "CHATTING";
	
	private final String code;
	private final String message;
	private final int statusCode;
	
	ChattingErrorCode(String code, String message, int statusCode) {
		this.code = code;
		this.message = message;
		this.statusCode = statusCode;
	}
	
	@Override
	public String getDomain() {
		return DOMAIN;
	}
	
	@Override
	public String getCode() {
		return code;
	}
	
	@Override
	public String getMessage() {
		return message;
	}
	
	@Override
	public int getStatusCode() {
		return statusCode;
	}
}
