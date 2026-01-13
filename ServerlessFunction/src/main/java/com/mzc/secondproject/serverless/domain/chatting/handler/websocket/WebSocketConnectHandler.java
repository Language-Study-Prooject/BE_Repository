package com.mzc.secondproject.serverless.domain.chatting.handler.websocket;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.mzc.secondproject.serverless.common.config.WebSocketConfig;
import com.mzc.secondproject.serverless.domain.chatting.model.Connection;
import com.mzc.secondproject.serverless.domain.chatting.model.RoomToken;
import com.mzc.secondproject.serverless.domain.chatting.repository.ConnectionRepository;
import com.mzc.secondproject.serverless.domain.chatting.service.RoomTokenService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * WebSocket $connect 라우트 핸들러
 * roomToken 검증 후 Connection 정보를 DynamoDB에 저장
 */
public class WebSocketConnectHandler implements RequestHandler<Map<String, Object>, Map<String, Object>> {
	
	private static final Logger logger = LoggerFactory.getLogger(WebSocketConnectHandler.class);
	
	private final ConnectionRepository connectionRepository;
	private final RoomTokenService roomTokenService;
	
	public WebSocketConnectHandler() {
		this.connectionRepository = new ConnectionRepository();
		this.roomTokenService = new RoomTokenService();
	}
	
	@Override
	public Map<String, Object> handleRequest(Map<String, Object> event, Context context) {
		logger.info("WebSocket connect event: {}", event);
		
		try {
			String connectionId = extractConnectionId(event);
			Map<String, String> queryParams = extractQueryStringParameters(event);
			
			String roomToken = queryParams.get("roomToken");
			
			if (roomToken == null || roomToken.isEmpty()) {
				logger.warn("Missing roomToken parameter");
				return createResponse(401, "roomToken is required");
			}
			
			// 토큰 검증
			Optional<RoomToken> optToken = roomTokenService.validateToken(roomToken);
			if (optToken.isEmpty()) {
				logger.warn("Invalid or expired roomToken: {}", roomToken);
				return createResponse(401, "Invalid or expired token");
			}
			
			RoomToken token = optToken.get();
			String userId = token.getUserId();
			String roomId = token.getRoomId();
			
			String now = Instant.now().toString();
			long ttl = Instant.now().plusSeconds(WebSocketConfig.connectionTtlSeconds()).getEpochSecond();
			
			Connection connection = Connection.builder()
					.pk("CONN#" + connectionId)
					.sk("METADATA")
					.gsi1pk("ROOM#" + roomId)
					.gsi1sk("CONN#" + connectionId)
					.gsi2pk("USER#" + userId)
					.gsi2sk("CONN#" + connectionId)
					.connectionId(connectionId)
					.userId(userId)
					.roomId(roomId)
					.connectedAt(now)
					.ttl(ttl)
					.build();
			
			connectionRepository.save(connection);
			
			logger.info("Connection saved: connectionId={}, userId={}, roomId={}", connectionId, userId, roomId);
			return createResponse(200, "Connected");
			
		} catch (Exception e) {
			logger.error("Error handling connect: {}", e.getMessage(), e);
			return createResponse(500, "Internal server error");
		}
	}
	
	@SuppressWarnings("unchecked")
	private String extractConnectionId(Map<String, Object> event) {
		Map<String, Object> requestContext = (Map<String, Object>) event.get("requestContext");
		return (String) requestContext.get("connectionId");
	}
	
	@SuppressWarnings("unchecked")
	private Map<String, String> extractQueryStringParameters(Map<String, Object> event) {
		Map<String, String> params = (Map<String, String>) event.get("queryStringParameters");
		return params != null ? params : new HashMap<>();
	}
	
	private Map<String, Object> createResponse(int statusCode, String body) {
		Map<String, Object> response = new HashMap<>();
		response.put("statusCode", statusCode);
		response.put("body", body);
		return response;
	}
}
