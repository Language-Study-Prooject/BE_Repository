package com.mzc.secondproject.serverless.domain.chatting.handler;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.mzc.secondproject.serverless.common.router.HandlerRouter;
import com.mzc.secondproject.serverless.common.router.Route;
import com.mzc.secondproject.serverless.common.util.ResponseGenerator;
import com.mzc.secondproject.serverless.common.util.WebSocketBroadcaster;
import com.mzc.secondproject.serverless.domain.chatting.dto.response.CommandResult;
import com.mzc.secondproject.serverless.domain.chatting.dto.response.GameStatusResponse;
import com.mzc.secondproject.serverless.domain.chatting.dto.response.ScoreboardResponse;
import com.mzc.secondproject.serverless.domain.chatting.enums.GameStatus;
import com.mzc.secondproject.serverless.domain.chatting.enums.MessageType;
import com.mzc.secondproject.serverless.domain.chatting.exception.ChattingErrorCode;
import com.mzc.secondproject.serverless.domain.chatting.model.ChatMessage;
import com.mzc.secondproject.serverless.domain.chatting.model.ChatRoom;
import com.mzc.secondproject.serverless.domain.chatting.model.Connection;
import com.mzc.secondproject.serverless.domain.chatting.repository.ChatRoomRepository;
import com.mzc.secondproject.serverless.domain.chatting.repository.ConnectionRepository;
import com.mzc.secondproject.serverless.domain.chatting.service.GameService;
import com.mzc.secondproject.serverless.domain.ranking.service.KinesisEventPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.*;

/**
 * 게임 REST API 핸들러
 */
public class GameHandler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

	private static final Logger logger = LoggerFactory.getLogger(GameHandler.class);

	private final GameService gameService;
	private final ChatRoomRepository chatRoomRepository;
	private final ConnectionRepository connectionRepository;
	private final WebSocketBroadcaster broadcaster;
	private final KinesisEventPublisher eventPublisher;
	private final HandlerRouter router;

	public GameHandler() {
		this.gameService = new GameService();
		this.chatRoomRepository = new ChatRoomRepository();
		this.connectionRepository = new ConnectionRepository();
		this.broadcaster = new WebSocketBroadcaster();
		this.eventPublisher = new KinesisEventPublisher();
		this.router = initRouter();
	}

	private HandlerRouter initRouter() {
		return new HandlerRouter().addRoutes(
				Route.postAuth("/rooms/{roomId}/game/start", this::startGame),
				Route.postAuth("/rooms/{roomId}/game/stop", this::stopGame),
				Route.getAuth("/rooms/{roomId}/game/status", this::getGameStatus),
				Route.getAuth("/rooms/{roomId}/game/scores", this::getScores)
		);
	}

	@Override
	public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent request, Context context) {
		logger.info("Received request: {} {}", request.getHttpMethod(), request.getPath());
		return router.route(request);
	}

	/**
	 * POST /rooms/{roomId}/game/start - 게임 시작
	 */
	private APIGatewayProxyResponseEvent startGame(APIGatewayProxyRequestEvent request, String userId) {
		String roomId = request.getPathParameters().get("roomId");

		GameService.GameStartResult result = gameService.startGame(roomId, userId);

		if (!result.success()) {
			return ResponseGenerator.fail(ChattingErrorCode.GAME_START_FAILED, result.error());
		}

		// WebSocket으로 게임 시작 알림 브로드캐스트
		broadcastGameStart(roomId, result);

		GameStatusResponse response = GameStatusResponse.from(result.room(), result.drawerOrder());
		return ResponseGenerator.ok("Game started", response);
	}

	/**
	 * POST /rooms/{roomId}/game/stop - 게임 중단
	 */
	private APIGatewayProxyResponseEvent stopGame(APIGatewayProxyRequestEvent request, String userId) {
		String roomId = request.getPathParameters().get("roomId");

		// 게임 종료 전 점수 조회 (이벤트 발행용)
		Optional<ChatRoom> optRoom = chatRoomRepository.findById(roomId);
		Map<String, Integer> scoresBeforeStop = optRoom.map(ChatRoom::getScores).orElse(Map.of());

		CommandResult result = gameService.stopGame(roomId, userId);

		if (!result.success()) {
			return ResponseGenerator.fail(ChattingErrorCode.GAME_STOP_FAILED, result.message());
		}

		// 랭킹 이벤트 발행
		publishGameEndEvents(roomId, scoresBeforeStop);

		// WebSocket으로 게임 종료 알림 브로드캐스트
		broadcastSystemMessage(roomId, result.message(), MessageType.GAME_END);

		return ResponseGenerator.ok("Game stopped", Map.of("message", result.message()));
	}

	/**
	 * 게임 종료 시 랭킹 이벤트 발행
	 */
	private void publishGameEndEvents(String roomId, Map<String, Integer> scores) {
		if (scores == null || scores.isEmpty()) {
			return;
		}

		// 1등 찾기
		String winnerId = null;
		int maxScore = 0;
		for (Map.Entry<String, Integer> entry : scores.entrySet()) {
			if (entry.getValue() > maxScore) {
				maxScore = entry.getValue();
				winnerId = entry.getKey();
			}
		}

		// 각 참가자에게 GAME_PLAYED 이벤트 발행
		for (Map.Entry<String, Integer> entry : scores.entrySet()) {
			String participantId = entry.getKey();
			int score = entry.getValue();

			eventPublisher.publishGamePlayed(participantId, roomId, score);

			// 1등에게 GAME_WON 이벤트 추가 발행
			if (participantId.equals(winnerId) && score > 0) {
				eventPublisher.publishGameWon(participantId, roomId, score);
			}
		}

		logger.info("Game end events published: roomId={}, participants={}", roomId, scores.size());
	}

	/**
	 * GET /rooms/{roomId}/game/status - 게임 상태 조회
	 */
	private APIGatewayProxyResponseEvent getGameStatus(APIGatewayProxyRequestEvent request, String userId) {
		String roomId = request.getPathParameters().get("roomId");

		Optional<ChatRoom> optRoom = chatRoomRepository.findById(roomId);
		if (optRoom.isEmpty()) {
			return ResponseGenerator.fail(ChattingErrorCode.ROOM_NOT_FOUND);
		}

		ChatRoom room = optRoom.get();
		GameStatusResponse response = GameStatusResponse.from(room, room.getDrawerOrder());

		return ResponseGenerator.ok("Game status retrieved", response);
	}

	/**
	 * GET /rooms/{roomId}/game/scores - 점수 조회
	 */
	private APIGatewayProxyResponseEvent getScores(APIGatewayProxyRequestEvent request, String userId) {
		String roomId = request.getPathParameters().get("roomId");

		Optional<ChatRoom> optRoom = chatRoomRepository.findById(roomId);
		if (optRoom.isEmpty()) {
			return ResponseGenerator.fail(ChattingErrorCode.ROOM_NOT_FOUND);
		}

		ChatRoom room = optRoom.get();
		ScoreboardResponse response = ScoreboardResponse.from(room);

		return ResponseGenerator.ok("Scores retrieved", response);
	}

	/**
	 * 게임 시작 브로드캐스트
	 */
	private void broadcastGameStart(String roomId, GameService.GameStartResult result) {
		String messageId = UUID.randomUUID().toString();
		String now = Instant.now().toString();

		String message = String.format("""
				🎮 게임 시작!
				총 %d 라운드

				라운드 1 시작!
				출제자: %s
				""",
				result.room().getTotalRounds(),
				result.room().getCurrentDrawerId());

		Map<String, Object> gameStartMessage = new HashMap<>();
		gameStartMessage.put("messageId", messageId);
		gameStartMessage.put("roomId", roomId);
		gameStartMessage.put("userId", "SYSTEM");
		gameStartMessage.put("content", message);
		gameStartMessage.put("messageType", MessageType.GAME_START.getCode());
		gameStartMessage.put("createdAt", now);
		gameStartMessage.put("gameStatus", result.room().getGameStatus());
		gameStartMessage.put("currentRound", result.room().getCurrentRound());
		gameStartMessage.put("totalRounds", result.room().getTotalRounds());
		gameStartMessage.put("currentDrawerId", result.room().getCurrentDrawerId());
		gameStartMessage.put("drawerOrder", result.drawerOrder());

		List<Connection> connections = connectionRepository.findByRoomId(roomId);
		String broadcastPayload = ResponseGenerator.gson().toJson(gameStartMessage);
		broadcaster.broadcast(connections, broadcastPayload);

		logger.info("Game start broadcasted: roomId={}", roomId);
	}

	/**
	 * 시스템 메시지 브로드캐스트
	 */
	private void broadcastSystemMessage(String roomId, String message, MessageType messageType) {
		String messageId = UUID.randomUUID().toString();
		String now = Instant.now().toString();

		Map<String, Object> systemMessage = new HashMap<>();
		systemMessage.put("messageId", messageId);
		systemMessage.put("roomId", roomId);
		systemMessage.put("userId", "SYSTEM");
		systemMessage.put("content", message);
		systemMessage.put("messageType", messageType.getCode());
		systemMessage.put("createdAt", now);

		List<Connection> connections = connectionRepository.findByRoomId(roomId);
		String broadcastPayload = ResponseGenerator.gson().toJson(systemMessage);
		broadcaster.broadcast(connections, broadcastPayload);

		logger.info("System message broadcasted: roomId={}, type={}", roomId, messageType);
	}
}
