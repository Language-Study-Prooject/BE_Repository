package com.mzc.secondproject.serverless.common.util;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * WebSocket 메시지 생성 헬퍼
 * 모든 메시지에 domain 필드를 포함하여 채팅/게임 구분 지원
 */
public final class WebSocketMessageHelper {
	
	public static final String DOMAIN_CHAT = "chat";
	public static final String DOMAIN_GAME = "game";
	public static final String DOMAIN_ROOM = "room";
	
	private WebSocketMessageHelper() {
	}
	
	/**
	 * 기본 메시지 생성
	 *
	 * @param domain      도메인 ("chat" 또는 "game")
	 * @param messageType 메시지 타입
	 * @param data        메시지 데이터
	 * @return 메시지 Map
	 */
	public static Map<String, Object> createMessage(String domain, String messageType, Object data) {
		Map<String, Object> message = new HashMap<>();
		message.put("domain", domain);
		message.put("messageType", messageType);
		message.put("data", data);
		message.put("timestamp", System.currentTimeMillis());
		return message;
	}
	
	/**
	 * 채팅 메시지 생성
	 */
	public static Map<String, Object> createChatMessage(String messageType, Object data) {
		return createMessage(DOMAIN_CHAT, messageType, data);
	}
	
	/**
	 * 게임 메시지 생성
	 */
	public static Map<String, Object> createGameMessage(String messageType, Object data) {
		return createMessage(DOMAIN_GAME, messageType, data);
	}
	
	/**
	 * 채팅 메시지 빌더 (상세 필드 포함)
	 */
	public static Map<String, Object> buildChatMessage(
			String roomId,
			String userId,
			String content,
			String messageType
	) {
		String messageId = UUID.randomUUID().toString();
		String now = Instant.now().toString();
		
		Map<String, Object> message = new HashMap<>();
		message.put("domain", DOMAIN_CHAT);
		message.put("messageType", messageType);
		message.put("messageId", messageId);
		message.put("roomId", roomId);
		message.put("userId", userId);
		message.put("content", content);
		message.put("createdAt", now);
		message.put("timestamp", System.currentTimeMillis());
		return message;
	}
	
	/**
	 * 게임 메시지 빌더 (상세 필드 포함)
	 */
	public static Map<String, Object> buildGameMessage(
			String roomId,
			String messageType,
			Map<String, Object> gameData
	) {
		String messageId = UUID.randomUUID().toString();
		String now = Instant.now().toString();
		long serverTime = System.currentTimeMillis();
		
		Map<String, Object> message = new HashMap<>();
		message.put("domain", DOMAIN_GAME);
		message.put("messageType", messageType);
		message.put("messageId", messageId);
		message.put("roomId", roomId);
		message.put("userId", "SYSTEM");
		message.put("createdAt", now);
		message.put("timestamp", serverTime);
		message.put("serverTime", serverTime);
		
		if (gameData != null) {
			message.put("data", gameData);
		}
		return message;
	}
	
	/**
	 * 시스템 메시지 생성 (채팅 도메인)
	 */
	public static Map<String, Object> buildSystemMessage(String roomId, String content, String messageType) {
		return buildChatMessage(roomId, "SYSTEM", content, messageType);
	}
	
	/**
	 * 방 상태 변경 메시지 생성
	 *
	 * @param roomId         방 ID
	 * @param status         현재 상태
	 * @param previousStatus 이전 상태
	 * @return 방 상태 변경 메시지
	 */
	public static Map<String, Object> buildRoomStatusChangeMessage(
			String roomId,
			String status,
			String previousStatus
	) {
		String messageId = UUID.randomUUID().toString();
		String now = Instant.now().toString();
		
		Map<String, Object> message = new HashMap<>();
		message.put("domain", DOMAIN_ROOM);
		message.put("messageType", "room_status_change");
		message.put("messageId", messageId);
		message.put("roomId", roomId);
		message.put("status", status);
		message.put("previousStatus", previousStatus);
		message.put("createdAt", now);
		message.put("timestamp", System.currentTimeMillis());
		return message;
	}
	
	/**
	 * 방장 변경 메시지 생성
	 *
	 * @param roomId          방 ID
	 * @param newHostId       새 방장 ID
	 * @param newHostNickname 새 방장 닉네임
	 * @return 방장 변경 메시지
	 */
	public static Map<String, Object> buildHostChangeMessage(
			String roomId,
			String newHostId,
			String newHostNickname
	) {
		String messageId = UUID.randomUUID().toString();
		String now = Instant.now().toString();
		
		Map<String, Object> message = new HashMap<>();
		message.put("domain", DOMAIN_ROOM);
		message.put("messageType", "host_change");
		message.put("messageId", messageId);
		message.put("roomId", roomId);
		message.put("newHostId", newHostId);
		message.put("newHostNickname", newHostNickname);
		message.put("createdAt", now);
		message.put("timestamp", System.currentTimeMillis());
		return message;
	}
}
