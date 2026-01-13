package com.mzc.secondproject.serverless.domain.chatting.handler.websocket;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mzc.secondproject.serverless.common.util.WebSocketBroadcaster;
import com.mzc.secondproject.serverless.domain.chatting.model.ChatMessage;
import com.mzc.secondproject.serverless.domain.chatting.model.Connection;
import com.mzc.secondproject.serverless.domain.chatting.repository.ChatRoomRepository;
import com.mzc.secondproject.serverless.domain.chatting.repository.ConnectionRepository;
import com.mzc.secondproject.serverless.domain.chatting.service.ChatMessageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * WebSocket sendMessage 라우트 핸들러
 * 메시지 저장 및 같은 방 연결들에게 브로드캐스트
 */
public class WebSocketMessageHandler implements RequestHandler<Map<String, Object>, Map<String, Object>> {
	
	private static final Logger logger = LoggerFactory.getLogger(WebSocketMessageHandler.class);
	private static final Gson gson = new GsonBuilder().create();
	
	private final ChatMessageService chatMessageService;
	private final ChatRoomRepository chatRoomRepository;
	private final ConnectionRepository connectionRepository;
	private final WebSocketBroadcaster broadcaster;
	
	public WebSocketMessageHandler() {
		this.chatMessageService = new ChatMessageService();
		this.chatRoomRepository = new ChatRoomRepository();
		this.connectionRepository = new ConnectionRepository();
		this.broadcaster = new WebSocketBroadcaster();
	}
	
	@Override
	public Map<String, Object> handleRequest(Map<String, Object> event, Context context) {
		logger.info("WebSocket message event: {}", event);
		
		try {
			String connectionId = extractConnectionId(event);
			String body = (String) event.get("body");
			
			if (body == null || body.isEmpty()) {
				return createResponse(400, "Message body is required");
			}
			
			MessagePayload payload = gson.fromJson(body, MessagePayload.class);
			
			if (payload.roomId == null || payload.userId == null || payload.content == null) {
				return createResponse(400, "roomId, userId, and content are required");
			}
			
			String messageType = payload.messageType != null ? payload.messageType : "TEXT";
			String messageId = UUID.randomUUID().toString();
			String now = Instant.now().toString();
			
			ChatMessage message = ChatMessage.builder()
					.pk("ROOM#" + payload.roomId)
					.sk("MSG#" + now + "#" + messageId)
					.gsi1pk("USER#" + payload.userId)
					.gsi1sk("MSG#" + now)
					.gsi2pk("MSG#" + messageId)
					.gsi2sk("ROOM#" + payload.roomId)
					.messageId(messageId)
					.roomId(payload.roomId)
					.userId(payload.userId)
					.content(payload.content)
					.messageType(messageType)
					.createdAt(now)
					.build();
			
			ChatMessage savedMessage = chatMessageService.saveMessage(message);
			chatRoomRepository.updateLastMessageAt(payload.roomId, now);
			
			logger.info("Message saved: messageId={}, roomId={}", messageId, payload.roomId);
			
			// 브로드캐스트
			List<Connection> connections = connectionRepository.findByRoomId(payload.roomId);
			String broadcastPayload = gson.toJson(savedMessage);
			List<String> failedConnections = broadcaster.broadcast(connections, broadcastPayload);
			
			// 실패한 연결 정리
			for (String failedConnectionId : failedConnections) {
				connectionRepository.delete(failedConnectionId);
				logger.info("Deleted stale connection: {}", failedConnectionId);
			}
			
			return createResponse(200, "Message sent");
			
		} catch (Exception e) {
			logger.error("Error handling message: {}", e.getMessage(), e);
			return createResponse(500, "Internal server error");
		}
	}
	
	@SuppressWarnings("unchecked")
	private String extractConnectionId(Map<String, Object> event) {
		Map<String, Object> requestContext = (Map<String, Object>) event.get("requestContext");
		return (String) requestContext.get("connectionId");
	}
	
	private Map<String, Object> createResponse(int statusCode, String body) {
		Map<String, Object> response = new HashMap<>();
		response.put("statusCode", statusCode);
		response.put("body", body);
		return response;
	}
	
	/**
	 * 메시지 페이로드 DTO
	 */
	private static class MessagePayload {
		String roomId;
		String userId;
		String content;
		String messageType;
	}
}
