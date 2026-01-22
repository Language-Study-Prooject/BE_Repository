package com.mzc.secondproject.serverless.domain.chatting.handler;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.mzc.secondproject.serverless.common.util.ResponseGenerator;
import com.mzc.secondproject.serverless.common.util.WebSocketBroadcaster;
import com.mzc.secondproject.serverless.common.util.WebSocketMessageHelper;
import com.mzc.secondproject.serverless.domain.chatting.dto.response.CommandResult;
import com.mzc.secondproject.serverless.domain.chatting.enums.MessageType;
import com.mzc.secondproject.serverless.domain.chatting.model.Connection;
import com.mzc.secondproject.serverless.domain.chatting.repository.ConnectionRepository;
import com.mzc.secondproject.serverless.domain.chatting.service.GameService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 게임 자동 종료 Lambda 핸들러
 * EventBridge Scheduler에 의해 게임 시작 7분 후 호출됨
 */
public class GameAutoCloseHandler implements RequestHandler<Map<String, String>, String> {
	
	private static final Logger logger = LoggerFactory.getLogger(GameAutoCloseHandler.class);
	
	private final GameService gameService;
	private final ConnectionRepository connectionRepository;
	private final WebSocketBroadcaster broadcaster;
	
	/**
	 * 기본 생성자 (Lambda에서 사용)
	 */
	public GameAutoCloseHandler() {
		this(new GameService(), new ConnectionRepository(), new WebSocketBroadcaster());
	}
	
	/**
	 * 의존성 주입 생성자 (테스트 용이성)
	 */
	public GameAutoCloseHandler(GameService gameService, ConnectionRepository connectionRepository,
	                            WebSocketBroadcaster broadcaster) {
		this.gameService = gameService;
		this.connectionRepository = connectionRepository;
		this.broadcaster = broadcaster;
	}
	
	@Override
	public String handleRequest(Map<String, String> event, Context context) {
		String gameSessionId = event.get("gameSessionId");
		String roomId = event.get("roomId");
		
		logger.info("Game auto-close triggered: gameSessionId={}, roomId={}", gameSessionId, roomId);
		
		if (gameSessionId == null || roomId == null) {
			logger.error("Missing required parameters: gameSessionId={}, roomId={}", gameSessionId, roomId);
			return "FAILED: Missing parameters";
		}
		
		try {
			// 게임 종료 처리
			CommandResult result = gameService.finishGameByTimeout(gameSessionId);
			
			if (result.success()) {
				// WebSocket으로 게임 종료 알림 브로드캐스트
				broadcastGameEnd(roomId, result.message());
				logger.info("Game auto-closed successfully: gameSessionId={}", gameSessionId);
				return "SUCCESS: Game auto-closed";
			} else {
				logger.info("Game auto-close skipped: gameSessionId={}, reason={}", gameSessionId, result.message());
				return "SKIPPED: " + result.message();
			}
			
		} catch (Exception e) {
			logger.error("Game auto-close failed: gameSessionId={}, error={}", gameSessionId, e.getMessage(), e);
			return "FAILED: " + e.getMessage();
		}
	}
	
	/**
	 * 게임 종료 메시지 브로드캐스트
	 */
	private void broadcastGameEnd(String roomId, String message) {
		String messageId = UUID.randomUUID().toString();
		String now = Instant.now().toString();
		
		Map<String, Object> gameEndMessage = new HashMap<>();
		gameEndMessage.put("domain", WebSocketMessageHelper.DOMAIN_GAME);
		gameEndMessage.put("messageId", messageId);
		gameEndMessage.put("roomId", roomId);
		gameEndMessage.put("userId", "SYSTEM");
		gameEndMessage.put("content", "⏰ 시간 초과! " + message);
		gameEndMessage.put("messageType", MessageType.GAME_END.getCode());
		gameEndMessage.put("createdAt", now);
		gameEndMessage.put("timestamp", System.currentTimeMillis());
		gameEndMessage.put("reason", "TIME_EXPIRED");
		
		List<Connection> connections = connectionRepository.findByRoomId(roomId);
		String broadcastPayload = ResponseGenerator.gson().toJson(gameEndMessage);
		broadcaster.broadcast(connections, broadcastPayload);
		
		logger.info("Game end broadcasted: roomId={}, connections={}", roomId, connections.size());
	}
}
