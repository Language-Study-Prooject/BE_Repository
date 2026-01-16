package com.mzc.secondproject.serverless.domain.chatting.handler.websocket;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mzc.secondproject.serverless.common.util.WebSocketBroadcaster;
import com.mzc.secondproject.serverless.common.util.WebSocketEventUtil;
import com.mzc.secondproject.serverless.domain.chatting.dto.response.CommandResult;
import com.mzc.secondproject.serverless.domain.chatting.dto.response.ScoreUpdateMessage;
import com.mzc.secondproject.serverless.domain.chatting.enums.MessageType;
import com.mzc.secondproject.serverless.domain.chatting.model.ChatMessage;
import com.mzc.secondproject.serverless.domain.chatting.model.Connection;
import com.mzc.secondproject.serverless.domain.chatting.repository.ChatRoomRepository;
import com.mzc.secondproject.serverless.domain.chatting.repository.ConnectionRepository;
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
	private final WebSocketBroadcaster broadcaster;
	private final CommandService commandService;
	private final GameService gameService;
	
	public WebSocketMessageHandler() {
		this.chatMessageService = new ChatMessageService();
		this.chatRoomRepository = new ChatRoomRepository();
		this.connectionRepository = new ConnectionRepository();
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
		drawingMessage.put("messageType", messageType);
		drawingMessage.put("roomId", payload.roomId);
		drawingMessage.put("userId", payload.userId);
		drawingMessage.put("content", payload.content);
		drawingMessage.put("createdAt", Instant.now().toString());
		
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
		
		// 브로드캐스트
		List<Connection> connections = connectionRepository.findByRoomId(payload.roomId);
		String broadcastPayload = gson.toJson(savedMessage);
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
		guessMessage.put("messageId", messageId);
		guessMessage.put("roomId", payload.roomId);
		guessMessage.put("userId", payload.userId);
		guessMessage.put("content", payload.content);
		guessMessage.put("messageType", "GUESS");
		guessMessage.put("createdAt", now);
		
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
		chatRoomRepository.findById(payload.roomId).ifPresent(room -> {
			broadcastScoreUpdate(payload.roomId, payload.userId, result.score(),
					result.scores(), room.getCurrentRound(), room.getTotalRounds(), connections);
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
		
		ChatMessage correctMessage = ChatMessage.builder()
				.pk("ROOM#" + payload.roomId)
				.sk("MSG#" + now + "#" + messageId)
				.gsi1pk("SYSTEM")
				.gsi1sk("MSG#" + now)
				.gsi2pk("MSG#" + messageId)
				.gsi2sk("ROOM#" + payload.roomId)
				.messageId(messageId)
				.roomId(payload.roomId)
				.userId("SYSTEM")
				.content(message)
				.messageType(MessageType.CORRECT_ANSWER.getCode())
				.createdAt(now)
				.build();
		
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
		chatRoomRepository.findById(roomId).ifPresent(room -> {
			CommandResult endResult = gameService.endRound(room, "ALL_CORRECT");
			handleCommandResult(endResult, roomId, "SYSTEM");
		});
	}
	
	/**
	 * 명령어 처리 결과를 브로드캐스트
	 */
	private Map<String, Object> handleCommandResult(CommandResult result, String roomId, String userId) {
		String messageId = UUID.randomUUID().toString();
		String now = Instant.now().toString();
		
		// 시스템 메시지 생성
		ChatMessage systemMessage = ChatMessage.builder()
				.pk("ROOM#" + roomId)
				.sk("MSG#" + now + "#" + messageId)
				.gsi1pk("SYSTEM")
				.gsi1sk("MSG#" + now)
				.gsi2pk("MSG#" + messageId)
				.gsi2sk("ROOM#" + roomId)
				.messageId(messageId)
				.roomId(roomId)
				.userId("SYSTEM")
				.content(result.message())
				.messageType(result.messageType().getCode())
				.createdAt(now)
				.build();
		
		// 명령어 결과는 저장하지 않고 브로드캐스트만 수행
		List<Connection> connections = connectionRepository.findByRoomId(roomId);
		String broadcastPayload = gson.toJson(systemMessage);
		List<String> failedConnections = broadcaster.broadcast(connections, broadcastPayload);
		
		// 실패한 연결 정리
		for (String failedConnectionId : failedConnections) {
			connectionRepository.delete(failedConnectionId);
			logger.info("Deleted stale connection: {}", failedConnectionId);
		}
		
		logger.info("Command result broadcasted: type={}, roomId={}", result.messageType(), roomId);
		return WebSocketEventUtil.ok("Command executed");
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
