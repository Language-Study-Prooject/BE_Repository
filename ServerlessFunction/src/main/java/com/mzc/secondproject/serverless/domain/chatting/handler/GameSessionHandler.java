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
 * 게임 세션 REST API 핸들러
 * 게임 세션 조회 및 재접속 지원
 */
public class GameSessionHandler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

	private static final Logger logger = LoggerFactory.getLogger(GameSessionHandler.class);

	private final GameService gameService;
	private final GameSessionRepository gameSessionRepository;
	private final ConnectionRepository connectionRepository;
	private final WebSocketBroadcaster broadcaster;
	private final HandlerRouter router;

	/**
	 * 기본 생성자 (Lambda에서 사용)
	 */
	public GameSessionHandler() {
		this(new GameService(), new GameSessionRepository(), new ConnectionRepository(), new WebSocketBroadcaster());
	}

	/**
	 * 의존성 주입 생성자 (테스트 용이성)
	 */
	public GameSessionHandler(GameService gameService, GameSessionRepository gameSessionRepository,
							  ConnectionRepository connectionRepository, WebSocketBroadcaster broadcaster) {
		this.gameService = gameService;
		this.gameSessionRepository = gameSessionRepository;
		this.connectionRepository = connectionRepository;
		this.broadcaster = broadcaster;
		this.router = initRouter();
	}

	private HandlerRouter initRouter() {
		return new HandlerRouter().addRoutes(
				// 게임 세션 생성 (roomId 기반)
				Route.postAuth("/rooms/{roomId}/games", this::createGameSession),
				// 게임 세션 조회 (재접속용)
				Route.getAuth("/games/{gameSessionId}", this::getGameSession),
				// 게임 시작
				Route.postAuth("/games/{gameSessionId}/start", this::startGame),
				// 게임 종료
				Route.postAuth("/games/{gameSessionId}/stop", this::stopGame)
		);
	}

	@Override
	public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent request, Context context) {
		logger.info("GameSession API request: {} {}", request.getHttpMethod(), request.getPath());
		return router.route(request);
	}

	/**
	 * POST /rooms/{roomId}/games - 게임 세션 생성 (게임 시작)
	 */
	private APIGatewayProxyResponseEvent createGameSession(APIGatewayProxyRequestEvent request, String userId) {
		String roomId = request.getPathParameters().get("roomId");

		GameService.GameStartResult result = gameService.startGame(roomId, userId);

		if (!result.success()) {
			return ResponseGenerator.fail(ChattingErrorCode.GAME_START_FAILED, result.error());
		}

		// WebSocket으로 게임 시작 알림 브로드캐스트
		broadcastGameStart(roomId, result);

		// 응답 생성 (serverTime 포함)
		Map<String, Object> response = buildGameSessionResponse(result.session(), userId);

		return ResponseGenerator.ok("Game session created", response);
	}

	/**
	 * GET /games/{gameSessionId} - 게임 세션 조회 (재접속용)
	 */
	private APIGatewayProxyResponseEvent getGameSession(APIGatewayProxyRequestEvent request, String userId) {
		String gameSessionId = request.getPathParameters().get("gameSessionId");

		Optional<GameSession> optSession = gameSessionRepository.findById(gameSessionId);
		if (optSession.isEmpty()) {
			return ResponseGenerator.fail(ChattingErrorCode.GAME_NOT_FOUND);
		}

		GameSession session = optSession.get();

		// 응답 생성 (serverTime 포함, 출제자에게만 currentWord 포함)
		Map<String, Object> response = buildGameSessionResponse(session, userId);

		return ResponseGenerator.ok("Game session retrieved", response);
	}

	/**
	 * POST /games/{gameSessionId}/start - 게임 시작 (세션 ID로)
	 */
	private APIGatewayProxyResponseEvent startGame(APIGatewayProxyRequestEvent request, String userId) {
		String gameSessionId = request.getPathParameters().get("gameSessionId");

		Optional<GameSession> optSession = gameSessionRepository.findById(gameSessionId);
		if (optSession.isEmpty()) {
			return ResponseGenerator.fail(ChattingErrorCode.GAME_NOT_FOUND);
		}

		GameSession session = optSession.get();

		// 이미 시작된 게임인지 확인
		if (session.isActive()) {
			return ResponseGenerator.fail(ChattingErrorCode.GAME_START_FAILED, "게임이 이미 진행 중입니다.");
		}

		// roomId로 게임 시작 위임
		GameService.GameStartResult result = gameService.startGame(session.getRoomId(), userId);

		if (!result.success()) {
			return ResponseGenerator.fail(ChattingErrorCode.GAME_START_FAILED, result.error());
		}

		broadcastGameStart(session.getRoomId(), result);

		Map<String, Object> response = buildGameSessionResponse(result.session(), userId);
		return ResponseGenerator.ok("Game started", response);
	}

	/**
	 * POST /games/{gameSessionId}/stop - 게임 종료
	 */
	private APIGatewayProxyResponseEvent stopGame(APIGatewayProxyRequestEvent request, String userId) {
		String gameSessionId = request.getPathParameters().get("gameSessionId");

		Optional<GameSession> optSession = gameSessionRepository.findById(gameSessionId);
		if (optSession.isEmpty()) {
			return ResponseGenerator.fail(ChattingErrorCode.GAME_NOT_FOUND);
		}

		GameSession session = optSession.get();
		CommandResult result = gameService.stopGame(session.getRoomId(), userId);

		if (!result.success()) {
			return ResponseGenerator.fail(ChattingErrorCode.GAME_STOP_FAILED, result.message());
		}

		// WebSocket으로 게임 종료 알림 브로드캐스트
		broadcastSystemMessage(session.getRoomId(), result.message(), MessageType.GAME_END);

		return ResponseGenerator.ok("Game stopped", Map.of(
				"message", result.message(),
				"serverTime", System.currentTimeMillis()
		));
	}

	/**
	 * 게임 세션 응답 빌드 (serverTime 포함)
	 */
	private Map<String, Object> buildGameSessionResponse(GameSession session, String userId) {
		long serverTime = System.currentTimeMillis();

		Map<String, Object> response = new LinkedHashMap<>();
		response.put("gameSessionId", session.getGameSessionId());
		response.put("roomId", session.getRoomId());
		response.put("gameType", session.getGameType());
		response.put("status", session.getStatus());
		response.put("currentRound", session.getCurrentRound());
		response.put("totalRounds", session.getTotalRounds());
		response.put("currentDrawerId", session.getCurrentDrawerId());
		response.put("roundStartTime", session.getRoundStartTime());
		response.put("serverTime", serverTime);  // 핵심! 타이머 동기화용
		response.put("roundDuration", session.getRoundDuration());
		response.put("scores", session.getScores() != null ? session.getScores() : Map.of());
		response.put("players", session.getPlayers() != null ? session.getPlayers() : List.of());
		response.put("drawerOrder", session.getDrawerOrder());
		response.put("hintUsed", session.getHintUsed());

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

		logger.info("Game start broadcasted: roomId={}, sessionId={}, drawerId={}", roomId, session.getGameSessionId(), drawerId);
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
