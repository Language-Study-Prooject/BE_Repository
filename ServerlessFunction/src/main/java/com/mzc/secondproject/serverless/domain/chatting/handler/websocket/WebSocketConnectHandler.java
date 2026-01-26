package com.mzc.secondproject.serverless.domain.chatting.handler.websocket;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.mzc.secondproject.serverless.common.config.WebSocketConfig;
import com.mzc.secondproject.serverless.common.util.WebSocketEventUtil;
import com.mzc.secondproject.serverless.domain.chatting.model.Connection;
import com.mzc.secondproject.serverless.domain.chatting.model.RoomToken;
import com.mzc.secondproject.serverless.domain.chatting.repository.ConnectionRepository;
import com.mzc.secondproject.serverless.domain.chatting.service.RoomTokenService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
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
			String connectionId = WebSocketEventUtil.extractConnectionId(event);
			Map<String, String> queryParams = WebSocketEventUtil.extractQueryStringParameters(event);
			
			String roomToken = queryParams.get("roomToken");
			
			if (roomToken == null || roomToken.isEmpty()) {
				logger.warn("Missing roomToken parameter");
				return WebSocketEventUtil.unauthorized("roomToken is required");
			}
			
			// 토큰 검증
			Optional<RoomToken> optToken = roomTokenService.validateToken(roomToken);
			if (optToken.isEmpty()) {
				logger.warn("Invalid or expired roomToken: {}", roomToken);
				return WebSocketEventUtil.unauthorized("Invalid or expired token");
			}
			
			RoomToken token = optToken.get();
			String userId = token.getUserId();
			String roomId = token.getRoomId();
			String nickname = "Unknown";

			// Cognito Authorizer에서 닉네임 추출
			try {
				if (event.containsKey("requestContext")) {
					Map<String, Object> reqCtx = (Map<String, Object>) event.get("requestContext");

					if (reqCtx.containsKey("authorizer")) {
						Map<String, Object> auth = (Map<String, Object>) reqCtx.get("authorizer");

						Map<String, Object> claims = auth;
						if (auth.containsKey("claims")) {
							claims = (Map<String, Object>) auth.get("claims");
						} else if (auth.containsKey("principalId")) {
							claims = auth;
						}

						if (claims != null) {
							if (claims.get("nickname") != null) {
								nickname = (String) claims.get("nickname");
							} else if (claims.get("custom:nickname") != null) {
								nickname = (String) claims.get("custom:nickname");
							} else if (claims.get("name") != null) {
								nickname = (String) claims.get("name");
							}
						}
					}
				}
				// 닉네임 못찾았으면 UserId 앞부분 표시
				if ("Unknown".equals(nickname) && userId != null && userId.length() > 5) {
					nickname = "User-" + userId.substring(0, 5);
				}
			} catch (Exception ex) {
				logger.warn("닉네임 표시 실패: {}", ex.getMessage());
			}

			// 같은 방에서 기존 연결 삭제 (새로고침 시 중복 연결 방지)
			connectionRepository.deleteUserConnectionsInRoom(userId, roomId);
			
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
					.nickname(nickname)
					.roomId(roomId)
					.connectedAt(now)
					.ttl(ttl)
					.build();
			
			connectionRepository.save(connection);
			
			logger.info("Connection saved: connectionId={}, userId={}, roomId={}", connectionId, userId, roomId);
			return WebSocketEventUtil.ok("Connected");
			
		} catch (Exception e) {
			logger.error("Error handling connect: {}", e.getMessage(), e);
			return WebSocketEventUtil.serverError("Internal server error");
		}
	}
}
