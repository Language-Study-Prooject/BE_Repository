package com.mzc.secondproject.serverless.domain.chatting.handler.websocket;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.mzc.secondproject.serverless.common.util.WebSocketEventUtil;
import com.mzc.secondproject.serverless.domain.chatting.model.ChatRoom;
import com.mzc.secondproject.serverless.domain.chatting.model.Connection;
import com.mzc.secondproject.serverless.domain.chatting.model.GameSession;
import com.mzc.secondproject.serverless.domain.chatting.repository.ChatRoomRepository;
import com.mzc.secondproject.serverless.domain.chatting.repository.ConnectionRepository;
import com.mzc.secondproject.serverless.domain.chatting.repository.GameSessionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * WebSocket $disconnect 라우트 핸들러
 * 클라이언트 연결 해제 시 Connection 정보를 DynamoDB에서 삭제
 */
public class WebSocketDisconnectHandler implements RequestHandler<Map<String, Object>, Map<String, Object>> {

	private static final Logger logger = LoggerFactory.getLogger(WebSocketDisconnectHandler.class);

	private final ConnectionRepository connectionRepository;
	private final ChatRoomRepository chatRoomRepository;
	private final GameSessionRepository gameSessionRepository;

	public WebSocketDisconnectHandler() {
		this.connectionRepository = new ConnectionRepository();
		this.chatRoomRepository = new ChatRoomRepository();
		this.gameSessionRepository = new GameSessionRepository();
	}

	@Override
	public Map<String, Object> handleRequest(Map<String, Object> event, Context context) {
		logger.info("WebSocket disconnect event: {}", event);

		try {
			String connectionId = WebSocketEventUtil.extractConnectionId(event);

			Optional<Connection> connection = connectionRepository.findByConnectionId(connectionId);

			if (connection.isPresent()) {
				Connection conn = connection.get();
				String roomId = conn.getRoomId();

				connectionRepository.delete(connectionId);
				logger.info("Connection deleted: connectionId={}, userId={}, roomId={}",
						connectionId, conn.getUserId(), roomId);

				// 방에 남은 연결이 없으면 게임 상태 초기화
				List<Connection> remainingConnections = connectionRepository.findByRoomId(roomId);
				if (remainingConnections.isEmpty()) {
					logger.info("No connections left in room {}, resetting game state", roomId);
					resetGameState(roomId);
				}
			} else {
				logger.warn("Connection not found for deletion: connectionId={}", connectionId);
			}

			return WebSocketEventUtil.ok("Disconnected");

		} catch (Exception e) {
			logger.error("Error handling disconnect: {}", e.getMessage(), e);
			return WebSocketEventUtil.serverError("Internal server error");
		}
	}

	/**
	 * 게임 상태 초기화
	 * 새 구조에서는 GameSession을 종료하고 ChatRoom의 상태를 WAITING으로 변경
	 */
	private void resetGameState(String roomId) {
		try {
			// 활성 게임 세션이 있으면 종료
			Optional<GameSession> activeSession = gameSessionRepository.findActiveByRoomId(roomId);
			if (activeSession.isPresent()) {
				GameSession session = activeSession.get();
				long now = Instant.now().toEpochMilli();
				long ttl = now / 1000 + 86400 * 7; // 7일 후 TTL
				gameSessionRepository.finishGame(session.getGameSessionId(), now, ttl);
				logger.info("Game session finished due to empty room: gameSessionId={}", session.getGameSessionId());
			}

			// 채팅방 상태 초기화
			Optional<ChatRoom> roomOpt = chatRoomRepository.findById(roomId);
			if (roomOpt.isPresent()) {
				ChatRoom room = roomOpt.get();
				// 게임이 진행 중이었다면 상태 초기화
				if ("PLAYING".equals(room.getStatus())) {
					chatRoomRepository.updateStatus(room, "WAITING");
					room.setActiveGameSessionId(null);
					chatRoomRepository.save(room);
					logger.info("Room status reset to WAITING for room: {}", roomId);
				}
			}
		} catch (Exception e) {
			logger.error("Error resetting game state for room {}: {}", roomId, e.getMessage());
		}
	}
}
