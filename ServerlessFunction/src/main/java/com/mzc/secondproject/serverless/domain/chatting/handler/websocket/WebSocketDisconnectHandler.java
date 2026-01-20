package com.mzc.secondproject.serverless.domain.chatting.handler.websocket;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.mzc.secondproject.serverless.common.util.WebSocketEventUtil;
import com.mzc.secondproject.serverless.domain.chatting.model.ChatRoom;
import com.mzc.secondproject.serverless.domain.chatting.model.Connection;
import com.mzc.secondproject.serverless.domain.chatting.repository.ChatRoomRepository;
import com.mzc.secondproject.serverless.domain.chatting.repository.ConnectionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

	public WebSocketDisconnectHandler() {
		this.connectionRepository = new ConnectionRepository();
		this.chatRoomRepository = new ChatRoomRepository();
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
	 */
	private void resetGameState(String roomId) {
		try {
			Optional<ChatRoom> roomOpt = chatRoomRepository.findById(roomId);

			if (roomOpt.isPresent()) {
				ChatRoom room = roomOpt.get();
				// 게임이 진행 중이었다면 초기화
				if (room.getGameStatus() != null && !"NONE".equals(room.getGameStatus())) {
					room.setGameStatus("NONE");
					room.setCurrentRound(null);
					room.setCurrentDrawerId(null);
					room.setCurrentWord(null);
					room.setCurrentWordId(null);
					room.setDrawerOrder(null);
					room.setScores(null);
					room.setStreaks(null);
					room.setCorrectGuessers(null);
					room.setHintUsed(null);
					room.setRoundStartTime(null);
					room.setGameStartedBy(null);
					chatRoomRepository.save(room);
					logger.info("Game state reset for room: {}", roomId);
				}
			}
		} catch (Exception e) {
			logger.error("Error resetting game state for room {}: {}", roomId, e.getMessage());
		}
	}
}
