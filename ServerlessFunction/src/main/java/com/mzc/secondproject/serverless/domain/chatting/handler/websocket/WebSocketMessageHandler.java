package com.mzc.secondproject.serverless.domain.chatting.handler.websocket;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mzc.secondproject.serverless.common.util.WebSocketBroadcaster;
import com.mzc.secondproject.serverless.common.util.WebSocketEventUtil;
import com.mzc.secondproject.serverless.common.util.WebSocketMessageHelper;
import com.mzc.secondproject.serverless.domain.chatting.dto.response.CommandResult;
import com.mzc.secondproject.serverless.domain.chatting.dto.response.ScoreUpdateMessage;
import com.mzc.secondproject.serverless.domain.chatting.enums.MessageType;
import com.mzc.secondproject.serverless.domain.chatting.model.ChatMessage;
import com.mzc.secondproject.serverless.domain.chatting.model.Connection;
import com.mzc.secondproject.serverless.domain.chatting.model.GameSession;
import com.mzc.secondproject.serverless.domain.chatting.repository.ChatRoomRepository;
import com.mzc.secondproject.serverless.domain.chatting.repository.ConnectionRepository;
import com.mzc.secondproject.serverless.domain.chatting.repository.GameSessionRepository;
import com.mzc.secondproject.serverless.domain.chatting.service.ChatMessageService;
import com.mzc.secondproject.serverless.domain.chatting.service.CommandService;
import com.mzc.secondproject.serverless.domain.chatting.service.GameService;
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
	private final GameSessionRepository gameSessionRepository;
	private final WebSocketBroadcaster broadcaster;
	private final CommandService commandService;
	private final GameService gameService;

	public WebSocketMessageHandler() {
		this.chatMessageService = new ChatMessageService();
		this.chatRoomRepository = new ChatRoomRepository();
		this.connectionRepository = new ConnectionRepository();
		this.gameSessionRepository = new GameSessionRepository();
		this.broadcaster = new WebSocketBroadcaster();
		this.commandService = new CommandService();
		this.gameService = new GameService();
	}
	
	@Override
	public Map<String, Object> handleRequest(Map<String, Object> event, Context context) {
		logger.info("WebSocket message event: {}", event);
		
		try {
			String connectionId = WebSocketEventUtil.extractConnectionId(event);
			String body = (String) event.get("body");
			
			if (body == null || body.isEmpty()) {
				return WebSocketEventUtil.badRequest("Message body is required");
			}
			
			MessagePayload payload = gson.fromJson(body, MessagePayload.class);
			
			if (payload.roomId == null || payload.userId == null) {
				return WebSocketEventUtil.badRequest("roomId and userId are required");
			}
			
			String messageType = payload.messageType != null ? payload.messageType : "TEXT";
			
			// 메시지 타입별 처리
			return switch (messageType.toUpperCase()) {
				case "DRAWING", "DRAWING_CLEAR" -> handleDrawingMessage(connectionId, payload, messageType);
				default -> handleRegularMessage(connectionId, payload, messageType);
			};
			
		} catch (Exception e) {
			logger.error("Error handling message: {}", e.getMessage(), e);
			return WebSocketEventUtil.serverError("Internal server error");
		}
	}
	
	/**
	 * 그림 데이터 처리 (DRAWING, DRAWING_CLEAR)
	 * - 저장하지 않음 (실시간 전송만)
	 * - 출제자만 그릴 수 있음
	 * - 본인 제외 브로드캐스트
	 */
	private Map<String, Object> handleDrawingMessage(String connectionId, MessagePayload payload, String messageType) {
		logger.info("Drawing message: type={}, roomId={}, userId={}", messageType, payload.roomId, payload.userId);
		
		// 그림 데이터 메시지 생성 (저장 안 함)
		Map<String, Object> drawingMessage = new HashMap<>();
		drawingMessage.put("domain", WebSocketMessageHelper.DOMAIN_GAME);
		drawingMessage.put("messageType", messageType);
		drawingMessage.put("roomId", payload.roomId);
		drawingMessage.put("userId", payload.userId);
		drawingMessage.put("content", payload.content);
		drawingMessage.put("createdAt", Instant.now().toString());
		drawingMessage.put("timestamp", System.currentTimeMillis());
		
		// 본인 제외 브로드캐스트
		List<Connection> connections = connectionRepository.findByRoomId(payload.roomId);
		List<Connection> otherConnections = connections.stream()
				.filter(c -> !c.getConnectionId().equals(connectionId))
				.toList();
		
		String broadcastPayload = gson.toJson(drawingMessage);
		List<String> failedConnections = broadcaster.broadcast(otherConnections, broadcastPayload);
		
		// 실패한 연결 정리
		for (String failedConnectionId : failedConnections) {
			connectionRepository.delete(failedConnectionId);
			logger.info("Deleted stale connection: {}", failedConnectionId);
		}
		
		logger.info("Drawing broadcasted to {} connections (excluding sender)", otherConnections.size());
		return WebSocketEventUtil.ok("Drawing sent");
	}
	
	/**
	 * 일반 메시지 처리 (TEXT 등)
	 */
	private Map<String, Object> handleRegularMessage(String connectionId, MessagePayload payload, String messageType) {
		if (payload.content == null) {
			return WebSocketEventUtil.badRequest("content is required for text messages");
		}
		
		// 슬래시 명령어 처리
		var commandResult = commandService.processCommand(payload.content, payload.roomId, payload.userId);
		if (commandResult.isPresent()) {
			return handleCommandResult(commandResult.get(), payload.roomId, payload.userId);
		}
		
		// 게임 중 정답 체크
		var answerResult = gameService.checkAnswer(payload.roomId, payload.userId, payload.content);
		if (answerResult.correct()) {
			return handleCorrectAnswer(payload, answerResult);
		}
		
		// 게임 진행 중이면 오답도 저장하지 않음 (추측 메시지는 기록에 남기지 않음)
		if (!answerResult.gameNotActive() && !answerResult.drawer()) {
			// 오답 메시지 브로드캐스트만 수행 (저장 안 함)
			return broadcastGuessMessage(payload);
		}
		
		// 일반 메시지 저장 및 브로드캐스트
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

		// 브로드캐스트 (domain 필드 포함을 위해 Map으로 변환)
		Map<String, Object> broadcastMessage = new HashMap<>();
		broadcastMessage.put("domain", WebSocketMessageHelper.DOMAIN_CHAT);
		broadcastMessage.put("messageId", savedMessage.getMessageId());
		broadcastMessage.put("roomId", savedMessage.getRoomId());
		broadcastMessage.put("userId", savedMessage.getUserId());
		broadcastMessage.put("content", savedMessage.getContent());
		broadcastMessage.put("messageType", savedMessage.getMessageType());
		broadcastMessage.put("createdAt", savedMessage.getCreatedAt());
		broadcastMessage.put("timestamp", System.currentTimeMillis());

		List<Connection> connections = connectionRepository.findByRoomId(payload.roomId);
		String broadcastPayload = gson.toJson(broadcastMessage);
		List<String> failedConnections = broadcaster.broadcast(connections, broadcastPayload);

		// 실패한 연결 정리
		for (String failedConnectionId : failedConnections) {
			connectionRepository.delete(failedConnectionId);
			logger.info("Deleted stale connection: {}", failedConnectionId);
		}

		return WebSocketEventUtil.ok("Message sent");
	}
	
	/**
	 * 게임 추측 메시지 브로드캐스트 (저장 안 함)
	 */
	private Map<String, Object> broadcastGuessMessage(MessagePayload payload) {
		String messageId = UUID.randomUUID().toString();
		String now = Instant.now().toString();
		
		// 추측 메시지 생성 (저장하지 않음)
		Map<String, Object> guessMessage = new HashMap<>();
		guessMessage.put("domain", WebSocketMessageHelper.DOMAIN_GAME);
		guessMessage.put("messageId", messageId);
		guessMessage.put("roomId", payload.roomId);
		guessMessage.put("userId", payload.userId);
		guessMessage.put("content", payload.content);
		guessMessage.put("messageType", "GUESS");
		guessMessage.put("createdAt", now);
		guessMessage.put("timestamp", System.currentTimeMillis());
		
		List<Connection> connections = connectionRepository.findByRoomId(payload.roomId);
		String broadcastPayload = gson.toJson(guessMessage);
		List<String> failedConnections = broadcaster.broadcast(connections, broadcastPayload);
		cleanupFailedConnections(failedConnections);
		
		logger.info("Guess message broadcasted (not saved): roomId={}, userId={}", payload.roomId, payload.userId);
		return WebSocketEventUtil.ok("Guess sent");
	}
	
	/**
	 * 정답 처리
	 */
	private Map<String, Object> handleCorrectAnswer(MessagePayload payload, GameService.AnswerCheckResult result) {
		List<Connection> connections = connectionRepository.findByRoomId(payload.roomId);

		// 1. 정답 알림 메시지 브로드캐스트
		broadcastCorrectAnswerMessage(payload, result, connections);

		// 2. 점수 업데이트 메시지 브로드캐스트 (실시간 리더보드)
		gameSessionRepository.findActiveByRoomId(payload.roomId).ifPresent(session -> {
			broadcastScoreUpdate(payload.roomId, payload.userId, result.score(),
					result.scores(), session.getCurrentRound(), session.getTotalRounds(), connections);
		});

		logger.info("Correct answer: roomId={}, userId={}, score={}", payload.roomId, payload.userId, result.score());

		// 전원 정답 시 라운드 종료 처리
		if (result.allCorrect()) {
			handleAllCorrect(payload.roomId);
		}

		return WebSocketEventUtil.ok("Correct answer");
	}
	
	/**
	 * 정답 알림 메시지 브로드캐스트
	 */
	private void broadcastCorrectAnswerMessage(MessagePayload payload, GameService.AnswerCheckResult result, List<Connection> connections) {
		String messageId = UUID.randomUUID().toString();
		String now = Instant.now().toString();

		String message = String.format("🎉 %s님이 정답을 맞췄습니다! (+%d점)", payload.userId, result.score());

		// domain 필드 포함을 위해 Map으로 생성
		Map<String, Object> correctMessage = new HashMap<>();
		correctMessage.put("domain", WebSocketMessageHelper.DOMAIN_GAME);
		correctMessage.put("messageId", messageId);
		correctMessage.put("roomId", payload.roomId);
		correctMessage.put("userId", "SYSTEM");
		correctMessage.put("content", message);
		correctMessage.put("messageType", MessageType.CORRECT_ANSWER.getCode());
		correctMessage.put("createdAt", now);
		correctMessage.put("timestamp", System.currentTimeMillis());

		String broadcastPayload = gson.toJson(correctMessage);
		List<String> failedConnections = broadcaster.broadcast(connections, broadcastPayload);
		cleanupFailedConnections(failedConnections);
	}
	
	/**
	 * 점수 업데이트 메시지 브로드캐스트 (실시간 리더보드)
	 */
	private void broadcastScoreUpdate(String roomId, String scorerId, int scoreGained,
	                                  Map<String, Integer> scores, Integer currentRound,
	                                  Integer totalRounds, List<Connection> connections) {
		if (scores == null || scores.isEmpty()) {
			return;
		}
		
		ScoreUpdateMessage scoreUpdate = ScoreUpdateMessage.from(
				roomId, scorerId, scoreGained, scores,
				currentRound != null ? currentRound : 0,
				totalRounds != null ? totalRounds : 0
		);
		
		String broadcastPayload = gson.toJson(scoreUpdate);
		List<String> failedConnections = broadcaster.broadcast(connections, broadcastPayload);
		cleanupFailedConnections(failedConnections);
		
		logger.info("Score update broadcasted: roomId={}, scorerId={}, scoreGained={}",
				roomId, scorerId, scoreGained);
	}
	
	/**
	 * 실패한 연결 정리
	 */
	private void cleanupFailedConnections(List<String> failedConnections) {
		for (String failedConnectionId : failedConnections) {
			connectionRepository.delete(failedConnectionId);
			logger.info("Deleted stale connection: {}", failedConnectionId);
		}
	}
	
	/**
	 * 전원 정답 시 라운드 종료
	 */
	private void handleAllCorrect(String roomId) {
		CommandResult endResult = gameService.endRound(roomId, "ALL_CORRECT");
		if (endResult != null && !endResult.message().contains("진행 중인 게임이 없습니다")) {
			handleCommandResult(endResult, roomId, "SYSTEM");
		}
	}
	
	/**
	 * 명령어 처리 결과를 브로드캐스트
	 */
	private Map<String, Object> handleCommandResult(CommandResult result, String roomId, String userId) {
		List<Connection> connections = connectionRepository.findByRoomId(roomId);

		// GAME_START는 특별 처리 (출제자에게만 제시어 전송 + serverTime 포함)
		if (result.messageType() == MessageType.GAME_START && result.data() instanceof GameService.GameStartResult gameResult) {
			broadcastGameStart(connections, result, gameResult, roomId);
			return WebSocketEventUtil.ok("Command executed");
		}

		// ROUND_END는 특별 처리 (다음 출제자에게만 제시어 전송 + serverTime 포함)
		if (result.messageType() == MessageType.ROUND_END && result.data() instanceof Map) {
			@SuppressWarnings("unchecked")
			Map<String, Object> data = (Map<String, Object>) result.data();
			broadcastRoundEnd(connections, result, data, roomId);
			return WebSocketEventUtil.ok("Command executed");
		}

		// 일반 시스템 메시지 (게임 관련 명령어 결과)
		String messageId = UUID.randomUUID().toString();
		String now = Instant.now().toString();

		// domain 필드 포함을 위해 Map으로 생성
		Map<String, Object> systemMessage = new HashMap<>();
		systemMessage.put("domain", WebSocketMessageHelper.DOMAIN_GAME);
		systemMessage.put("messageId", messageId);
		systemMessage.put("roomId", roomId);
		systemMessage.put("userId", "SYSTEM");
		systemMessage.put("content", result.message());
		systemMessage.put("messageType", result.messageType().getCode());
		systemMessage.put("createdAt", now);
		systemMessage.put("timestamp", System.currentTimeMillis());

		String broadcastPayload = gson.toJson(systemMessage);
		List<String> failedConnections = broadcaster.broadcast(connections, broadcastPayload);
		cleanupFailedConnections(failedConnections);

		logger.info("Command result broadcasted: type={}, roomId={}", result.messageType(), roomId);
		return WebSocketEventUtil.ok("Command executed");
	}

	/**
	 * GAME_START 메시지 브로드캐스트 - 출제자에게만 제시어 포함, serverTime 추가
	 */
	private void broadcastGameStart(List<Connection> connections, CommandResult result,
			GameService.GameStartResult gameResult, String roomId) {
		String messageId = UUID.randomUUID().toString();
		String now = Instant.now().toString();
		long serverTime = System.currentTimeMillis();

		GameSession session = gameResult.session();
		String currentDrawerId = session.getCurrentDrawerId();

		for (Connection conn : connections) {
			Map<String, Object> message = new HashMap<>();
			message.put("domain", WebSocketMessageHelper.DOMAIN_GAME);
			message.put("messageId", messageId);
			message.put("roomId", roomId);
			message.put("userId", "SYSTEM");
			message.put("content", result.message());
			message.put("messageType", result.messageType().getCode());
			message.put("createdAt", now);
			message.put("timestamp", serverTime);

			// 게임 상태 정보
			message.put("gameStatus", session.getStatus());
			message.put("currentRound", session.getCurrentRound());
			message.put("totalRounds", session.getTotalRounds());
			message.put("currentDrawerId", currentDrawerId);
			message.put("drawerOrder", gameResult.drawerOrder());

			// 타이머 동기화용 필드 (핵심!)
			message.put("roundStartTime", session.getRoundStartTime());
			message.put("serverTime", serverTime);
			message.put("roundDuration", session.getRoundDuration());

			// 출제자에게만 제시어 전송
			if (conn.getUserId().equals(currentDrawerId) && gameResult.firstWord() != null) {
				Map<String, String> wordInfo = new HashMap<>();
				wordInfo.put("wordId", gameResult.firstWord().getWordId());
				wordInfo.put("word", gameResult.firstWord().getEnglish());
				message.put("currentWord", wordInfo);
			}

			String payload = gson.toJson(message);
			try {
				broadcaster.sendToConnection(conn.getConnectionId(), payload);
			} catch (Exception e) {
				logger.warn("Failed to send GAME_START to connection: {}", conn.getConnectionId());
				connectionRepository.delete(conn.getConnectionId());
			}
		}

		logger.info("GAME_START broadcasted: roomId={}, serverTime={}", roomId, serverTime);
	}

	/**
	 * ROUND_END 메시지 브로드캐스트 - 다음 출제자에게만 제시어 포함, serverTime 추가
	 */
	private void broadcastRoundEnd(List<Connection> connections, CommandResult result,
			Map<String, Object> data, String roomId) {
		String messageId = UUID.randomUUID().toString();
		String now = Instant.now().toString();
		long serverTime = System.currentTimeMillis();

		String nextDrawer = (String) data.get("nextDrawer");
		Object nextWordObj = data.get("nextWord");

		for (Connection conn : connections) {
			Map<String, Object> message = new HashMap<>();
			message.put("domain", WebSocketMessageHelper.DOMAIN_GAME);
			message.put("messageId", messageId);
			message.put("roomId", roomId);
			message.put("userId", "SYSTEM");
			message.put("content", result.message());
			message.put("messageType", result.messageType().getCode());
			message.put("createdAt", now);
			message.put("timestamp", serverTime);

			// 기본 데이터 복사 (nextWord 제외)
			Map<String, Object> messageData = new HashMap<>();
			messageData.put("answer", data.get("answer"));
			messageData.put("nextRound", data.get("nextRound"));
			messageData.put("nextDrawer", nextDrawer);
			messageData.put("ranking", data.get("ranking"));
			messageData.put("currentRound", data.get("currentRound"));
			messageData.put("totalRounds", data.get("totalRounds"));

			// 타이머 동기화용 필드 (핵심!)
			messageData.put("serverTime", serverTime);
			if (data.get("roundStartTime") != null) {
				messageData.put("roundStartTime", data.get("roundStartTime"));
			}
			if (data.get("roundDuration") != null) {
				messageData.put("roundDuration", data.get("roundDuration"));
			}

			// 다음 출제자에게만 제시어 전송
			if (conn.getUserId().equals(nextDrawer) && nextWordObj != null) {
				if (nextWordObj instanceof com.mzc.secondproject.serverless.domain.vocabulary.model.Word nextWord) {
					Map<String, String> wordInfo = new HashMap<>();
					wordInfo.put("wordId", nextWord.getWordId());
					wordInfo.put("word", nextWord.getEnglish());
					messageData.put("nextWord", wordInfo);
				}
			}

			message.put("data", messageData);

			String payload = gson.toJson(message);
			try {
				broadcaster.sendToConnection(conn.getConnectionId(), payload);
			} catch (Exception e) {
				logger.warn("Failed to send ROUND_END to connection: {}", conn.getConnectionId());
				connectionRepository.delete(conn.getConnectionId());
			}
		}

		logger.info("ROUND_END broadcasted: roomId={}, serverTime={}", roomId, serverTime);
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
