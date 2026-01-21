package com.mzc.secondproject.serverless.domain.chatting.handler;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.mzc.secondproject.serverless.common.router.HandlerRouter;
import com.mzc.secondproject.serverless.common.router.Route;
import com.mzc.secondproject.serverless.common.util.ResponseGenerator;
import com.mzc.secondproject.serverless.common.util.WebSocketBroadcaster;
import com.mzc.secondproject.serverless.common.util.WebSocketMessageHelper;
import com.mzc.secondproject.serverless.domain.chatting.dto.response.CommandResult;
import com.mzc.secondproject.serverless.domain.chatting.dto.response.GameStatusResponse;
import com.mzc.secondproject.serverless.domain.chatting.dto.response.ScoreboardResponse;
import com.mzc.secondproject.serverless.domain.chatting.enums.MessageType;
import com.mzc.secondproject.serverless.domain.chatting.exception.ChattingErrorCode;
import com.mzc.secondproject.serverless.domain.chatting.model.Connection;
import com.mzc.secondproject.serverless.domain.chatting.model.GameSession;
import com.mzc.secondproject.serverless.domain.chatting.repository.ConnectionRepository;
import com.mzc.secondproject.serverless.domain.chatting.repository.GameSessionRepository;
import com.mzc.secondproject.serverless.domain.chatting.service.GameService;
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
	private final GameSessionRepository gameSessionRepository;
	private final ConnectionRepository connectionRepository;
	private final WebSocketBroadcaster broadcaster;
	private final HandlerRouter router;

	/**
	 * 기본 생성자 (Lambda에서 사용)
	 */
	public GameHandler() {
		this(new GameService(), new GameSessionRepository(), new ConnectionRepository(), new WebSocketBroadcaster());
	}

	/**
	 * 의존성 주입 생성자 (테스트 용이성)
	 */
	public GameHandler(GameService gameService, GameSessionRepository gameSessionRepository,
					   ConnectionRepository connectionRepository, WebSocketBroadcaster broadcaster) {
		this.gameService = gameService;
		this.gameSessionRepository = gameSessionRepository;
		this.connectionRepository = connectionRepository;
		this.broadcaster = broadcaster;
		this.router = initRouter();
	}

	private HandlerRouter initRouter() {
		return new HandlerRouter().addRoutes(
				Route.postAuth("/rooms/{roomId}/game/start", this::startGame),
				Route.postAuth("/rooms/{roomId}/game/stop", this::stopGame),
				Route.postAuth("/rooms/{roomId}/game/restart", this::restartGame),
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

		// WebSocket으로 게임 시작 알림 브로드캐스트 (출제자에게 currentWord 포함)
		broadcastGameStart(roomId, result);

		// REST 응답에도 출제자에게 currentWord 포함
		Map<String, Object> response = buildGameStatusResponse(result.session(), userId);
		return ResponseGenerator.ok("Game started", response);
	}

	/**
	 * POST /rooms/{roomId}/game/stop - 게임 중단
	 */
	private APIGatewayProxyResponseEvent stopGame(APIGatewayProxyRequestEvent request, String userId) {
		String roomId = request.getPathParameters().get("roomId");

		CommandResult result = gameService.stopGame(roomId, userId);

		if (!result.success()) {
			return ResponseGenerator.fail(ChattingErrorCode.GAME_STOP_FAILED, result.message());
		}

		// WebSocket으로 게임 종료 알림 브로드캐스트
		broadcastSystemMessage(roomId, result.message(), MessageType.GAME_END);

		return ResponseGenerator.ok("Game stopped", Map.of("message", result.message()));
	}

	/**
	 * POST /rooms/{roomId}/game/restart - 게임 재시작
	 */
	private APIGatewayProxyResponseEvent restartGame(APIGatewayProxyRequestEvent request, String userId) {
		String roomId = request.getPathParameters().get("roomId");

		GameService.GameStartResult result = gameService.restartGame(roomId, userId);

		if (!result.success()) {
			return ResponseGenerator.fail(ChattingErrorCode.GAME_START_FAILED, result.error());
		}

		// WebSocket으로 게임 시작 알림 브로드캐스트 (출제자에게 currentWord 포함)
		broadcastGameStart(roomId, result);

		// REST 응답에도 출제자에게 currentWord 포함
		Map<String, Object> response = buildGameStatusResponse(result.session(), userId);
		return ResponseGenerator.ok("Game restarted", response);
	}

	/**
	 * GET /rooms/{roomId}/game/status - 게임 상태 조회
	 */
	private APIGatewayProxyResponseEvent getGameStatus(APIGatewayProxyRequestEvent request, String userId) {
		String roomId = request.getPathParameters().get("roomId");

		Optional<GameSession> optSession = gameSessionRepository.findActiveByRoomId(roomId);
		if (optSession.isEmpty()) {
			// 게임이 없는 경우 빈 상태 반환
			return ResponseGenerator.ok("No active game", Map.of("gameStatus", "NONE"));
		}

		GameSession session = optSession.get();

		// 출제자에게만 currentWord 포함
		Map<String, Object> response = buildGameStatusResponse(session, userId);

		return ResponseGenerator.ok("Game status retrieved", response);
	}

	/**
	 * 게임 상태 응답 빌드 (출제자에게만 currentWord 포함)
	 */
	private Map<String, Object> buildGameStatusResponse(GameSession session, String userId) {
		Map<String, Object> response = new LinkedHashMap<>();
		response.put("gameStatus", session.getStatus());
		response.put("currentRound", session.getCurrentRound());
		response.put("totalRounds", session.getTotalRounds());
		response.put("currentDrawerId", session.getCurrentDrawerId());
		response.put("roundStartTime", session.getRoundStartTime());
		response.put("serverTime", System.currentTimeMillis());
		response.put("roundDuration", session.getRoundDuration());
		response.put("drawerOrder", session.getDrawerOrder());
		response.put("scores", session.getScores() != null ? session.getScores() : Map.of());
		response.put("hintUsed", session.getHintUsed());
		response.put("correctGuessers", session.getCorrectGuessers());

		// 출제자에게만 현재 단어 포함
		if (userId != null && userId.equals(session.getCurrentDrawerId())) {
			Map<String, String> currentWord = new HashMap<>();
			currentWord.put("wordId", session.getCurrentWordId());
			currentWord.put("word", session.getCurrentWord());
			response.put("currentWord", currentWord);
		}

		return response;
	}

	/**
	 * GET /rooms/{roomId}/game/scores - 점수 조회
	 */
	private APIGatewayProxyResponseEvent getScores(APIGatewayProxyRequestEvent request, String userId) {
		String roomId = request.getPathParameters().get("roomId");

		Optional<GameSession> optSession = gameSessionRepository.findActiveByRoomId(roomId);
		if (optSession.isEmpty()) {
			return ResponseGenerator.ok("No active game", Map.of("scores", Map.of()));
		}

		GameSession session = optSession.get();
		ScoreboardResponse response = ScoreboardResponse.from(session);

		return ResponseGenerator.ok("Scores retrieved", response);
	}

	/**
	 * 게임 시작 브로드캐스트
	 * 모든 사용자에게 게임 시작 메시지 전송, 출제자에게는 currentWord 포함
	 */
	private void broadcastGameStart(String roomId, GameService.GameStartResult result) {
		String messageId = UUID.randomUUID().toString();
		String now = Instant.now().toString();
		long serverTime = System.currentTimeMillis();

		GameSession session = result.session();
		String drawerId = session.getCurrentDrawerId();

		String message = String.format("""
						🎮 게임 시작!
						총 %d 라운드

						라운드 1 시작!
						출제자: %s
						""",
				session.getTotalRounds(),
				drawerId);

		// 기본 게임 시작 메시지 (모든 사용자용)
		Map<String, Object> gameStartMessage = new HashMap<>();
		gameStartMessage.put("domain", WebSocketMessageHelper.DOMAIN_GAME);
		gameStartMessage.put("messageId", messageId);
		gameStartMessage.put("roomId", roomId);
		gameStartMessage.put("userId", "SYSTEM");
		gameStartMessage.put("content", message);
		gameStartMessage.put("messageType", MessageType.GAME_START.getCode());
		gameStartMessage.put("createdAt", now);
		gameStartMessage.put("timestamp", serverTime);
		gameStartMessage.put("gameStatus", session.getStatus());
		gameStartMessage.put("currentRound", session.getCurrentRound());
		gameStartMessage.put("totalRounds", session.getTotalRounds());
		gameStartMessage.put("currentDrawerId", drawerId);
		gameStartMessage.put("drawerOrder", result.drawerOrder());
		gameStartMessage.put("roundStartTime", session.getRoundStartTime());
		gameStartMessage.put("serverTime", serverTime);
		gameStartMessage.put("roundDuration", session.getRoundDuration());

		List<Connection> connections = connectionRepository.findByRoomId(roomId);

		// 출제자용 메시지 (currentWord 포함)
		Map<String, Object> drawerMessage = new HashMap<>(gameStartMessage);
		Map<String, String> currentWord = new HashMap<>();
		currentWord.put("wordId", session.getCurrentWordId());
		currentWord.put("word", session.getCurrentWord());
		drawerMessage.put("currentWord", currentWord);

		String broadcastPayload = ResponseGenerator.gson().toJson(gameStartMessage);
		String drawerPayload = ResponseGenerator.gson().toJson(drawerMessage);

		// 출제자와 일반 사용자에게 다른 메시지 전송
		for (Connection conn : connections) {
			String payload = conn.getUserId().equals(drawerId) ? drawerPayload : broadcastPayload;
			broadcaster.sendToConnection(conn.getConnectionId(), payload);
		}

		logger.info("Game start broadcasted: roomId={}, drawerId={}", roomId, drawerId);
	}

	/**
	 * 시스템 메시지 브로드캐스트
	 */
	private void broadcastSystemMessage(String roomId, String message, MessageType messageType) {
		String messageId = UUID.randomUUID().toString();
		String now = Instant.now().toString();

		Map<String, Object> systemMessage = new HashMap<>();
		systemMessage.put("domain", WebSocketMessageHelper.DOMAIN_GAME);
		systemMessage.put("messageId", messageId);
		systemMessage.put("roomId", roomId);
		systemMessage.put("userId", "SYSTEM");
		systemMessage.put("content", message);
		systemMessage.put("messageType", messageType.getCode());
		systemMessage.put("createdAt", now);
		systemMessage.put("timestamp", System.currentTimeMillis());

		List<Connection> connections = connectionRepository.findByRoomId(roomId);
		String broadcastPayload = ResponseGenerator.gson().toJson(systemMessage);
		broadcaster.broadcast(connections, broadcastPayload);

		logger.info("System message broadcasted: roomId={}, type={}", roomId, messageType);
	}
}
