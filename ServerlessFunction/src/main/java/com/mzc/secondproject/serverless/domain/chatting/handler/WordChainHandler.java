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
import com.mzc.secondproject.serverless.domain.chatting.enums.MessageType;
import com.mzc.secondproject.serverless.domain.chatting.exception.ChattingErrorCode;
import com.mzc.secondproject.serverless.domain.chatting.model.Connection;
import com.mzc.secondproject.serverless.domain.chatting.model.WordChainSession;
import com.mzc.secondproject.serverless.domain.chatting.repository.ConnectionRepository;
import com.mzc.secondproject.serverless.domain.chatting.repository.WordChainSessionRepository;
import com.mzc.secondproject.serverless.domain.chatting.service.WordChainService;
import com.mzc.secondproject.serverless.domain.chatting.service.WordChainService.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.*;

/**
 * 끝말잇기(Word Chain) 게임 REST API 핸들러
 */
public class WordChainHandler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

	private static final Logger logger = LoggerFactory.getLogger(WordChainHandler.class);
	private static final String DOMAIN_WORDCHAIN = "wordchain";

	private final WordChainService wordChainService;
	private final WordChainSessionRepository sessionRepository;
	private final ConnectionRepository connectionRepository;
	private final WebSocketBroadcaster broadcaster;
	private final HandlerRouter router;

	/**
	 * 기본 생성자 (Lambda에서 사용)
	 */
	public WordChainHandler() {
		this(new WordChainService(),
				new WordChainSessionRepository(),
				new ConnectionRepository(),
				new WebSocketBroadcaster());
	}

	/**
	 * 의존성 주입 생성자 (테스트 용이성)
	 */
	public WordChainHandler(WordChainService wordChainService,
	                        WordChainSessionRepository sessionRepository,
	                        ConnectionRepository connectionRepository,
	                        WebSocketBroadcaster broadcaster) {
		this.wordChainService = wordChainService;
		this.sessionRepository = sessionRepository;
		this.connectionRepository = connectionRepository;
		this.broadcaster = broadcaster;
		this.router = initRouter();
	}

	private HandlerRouter initRouter() {
		return new HandlerRouter().addRoutes(
				Route.postAuth("/rooms/{roomId}/wordchain/start", this::startGame),
				Route.postAuth("/rooms/{roomId}/wordchain/submit", this::submitWord),
				Route.postAuth("/rooms/{roomId}/wordchain/timeout", this::handleTimeout),
				Route.postAuth("/rooms/{roomId}/wordchain/stop", this::stopGame),
				Route.getAuth("/rooms/{roomId}/wordchain/status", this::getGameStatus)
		);
	}

	@Override
	public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent request, Context context) {
		logger.info("Received request: {} {}", request.getHttpMethod(), request.getPath());
		return router.route(request);
	}

	/**
	 * POST /rooms/{roomId}/wordchain/start - 게임 시작
	 */
	private APIGatewayProxyResponseEvent startGame(APIGatewayProxyRequestEvent request, String userId) {
		String roomId = request.getPathParameters().get("roomId");

		GameStartResult result = wordChainService.startGame(roomId, userId);

		if (!result.success()) {
			return ResponseGenerator.fail(ChattingErrorCode.GAME_START_FAILED, result.error());
		}

		// WebSocket으로 게임 시작 알림 브로드캐스트
		broadcastGameStart(roomId, result);

		Map<String, Object> response = buildGameStatusResponse(result.session());
		return ResponseGenerator.ok("Word Chain game started", response);
	}

	/**
	 * POST /rooms/{roomId}/wordchain/submit - 단어 제출
	 */
	private APIGatewayProxyResponseEvent submitWord(APIGatewayProxyRequestEvent request, String userId) {
		String roomId = request.getPathParameters().get("roomId");

		@SuppressWarnings("unchecked")
		Map<String, String> body = ResponseGenerator.gson().fromJson(request.getBody(), Map.class);
		String word = body.get("word");

		if (word == null || word.isBlank()) {
			return ResponseGenerator.fail(ChattingErrorCode.INVALID_INPUT, "단어를 입력해주세요.");
		}

		WordSubmitResult result = wordChainService.submitWord(roomId, userId, word);

		// 결과에 따라 브로드캐스트
		broadcastWordResult(roomId, result);

		return buildSubmitResponse(result);
	}

	/**
	 * POST /rooms/{roomId}/wordchain/timeout - 타임아웃 처리
	 */
	private APIGatewayProxyResponseEvent handleTimeout(APIGatewayProxyRequestEvent request, String userId) {
		String roomId = request.getPathParameters().get("roomId");

		WordSubmitResult result = wordChainService.handleTimeout(roomId, userId);

		// 타임아웃 결과 브로드캐스트
		broadcastWordResult(roomId, result);

		return buildSubmitResponse(result);
	}

	/**
	 * POST /rooms/{roomId}/wordchain/stop - 게임 중단
	 */
	private APIGatewayProxyResponseEvent stopGame(APIGatewayProxyRequestEvent request, String userId) {
		String roomId = request.getPathParameters().get("roomId");

		WordSubmitResult result = wordChainService.stopGame(roomId, userId);

		if (result.type() == WordSubmitResult.ResultType.ERROR) {
			return ResponseGenerator.fail(ChattingErrorCode.GAME_STOP_FAILED, result.error());
		}

		// 게임 종료 브로드캐스트
		broadcastWordResult(roomId, result);

		return ResponseGenerator.ok("Game stopped", Map.of("message", "게임이 종료되었습니다."));
	}

	/**
	 * GET /rooms/{roomId}/wordchain/status - 게임 상태 조회
	 */
	private APIGatewayProxyResponseEvent getGameStatus(APIGatewayProxyRequestEvent request, String userId) {
		String roomId = request.getPathParameters().get("roomId");

		Optional<WordChainSession> optSession = sessionRepository.findActiveByRoomId(roomId);
		if (optSession.isEmpty()) {
			return ResponseGenerator.ok("No active game", Map.of("gameStatus", "NONE"));
		}

		Map<String, Object> response = buildGameStatusResponse(optSession.get());
		return ResponseGenerator.ok("Game status retrieved", response);
	}

	/**
	 * 게임 상태 응답 빌드
	 */
	private Map<String, Object> buildGameStatusResponse(WordChainSession session) {
		Map<String, Object> response = new LinkedHashMap<>();
		response.put("sessionId", session.getSessionId());
		response.put("gameStatus", session.getStatus());
		response.put("currentRound", session.getCurrentRound());
		response.put("currentPlayerId", session.getCurrentPlayerId());
		response.put("currentWord", session.getCurrentWord());
		response.put("nextLetter", session.getNextLetter());
		response.put("timeLimit", session.getTimeLimit());
		response.put("turnStartTime", session.getTurnStartTime());
		response.put("serverTime", System.currentTimeMillis());
		response.put("activePlayers", session.getActivePlayers());
		response.put("eliminatedPlayers", session.getEliminatedPlayers());
		response.put("scores", session.getScores() != null ? session.getScores() : Map.of());
		response.put("usedWords", session.getUsedWords());
		return response;
	}

	/**
	 * 단어 제출 결과 응답 빌드
	 */
	private APIGatewayProxyResponseEvent buildSubmitResponse(WordSubmitResult result) {
		Map<String, Object> response = new LinkedHashMap<>();
		response.put("resultType", result.type().name());

		switch (result.type()) {
			case CORRECT -> {
				response.put("word", result.word());
				response.put("definition", result.definition());
				response.put("phonetic", result.phonetic());
				response.put("score", result.score());
				response.put("nextLetter", result.nextLetter());
				response.put("nextPlayerId", result.nextPlayerId());
				response.put("nextTimeLimit", result.nextTimeLimit());
				return ResponseGenerator.ok("Correct!", response);
			}
			case WRONG_LETTER, INVALID_WORD -> {
				response.put("error", result.error());
				return ResponseGenerator.ok("Wrong answer", response);
			}
			case TIMEOUT -> {
				response.put("eliminatedPlayerId", result.eliminatedPlayerId());
				response.put("eliminatedNickname", result.eliminatedNickname());
				response.put("nextPlayerId", result.nextPlayerId());
				response.put("nextTimeLimit", result.nextTimeLimit());
				return ResponseGenerator.ok("Timeout", response);
			}
			case GAME_END -> {
				response.put("winnerId", result.winnerId());
				response.put("winnerNickname", result.winnerNickname());
				response.put("ranking", result.ranking());
				if (result.session() != null) {
					response.put("usedWords", result.session().getUsedWords());
					response.put("wordDefinitions", result.session().getWordDefinitions());
				}
				return ResponseGenerator.ok("Game ended", response);
			}
			case ERROR -> {
				return ResponseGenerator.fail(ChattingErrorCode.GAME_ACTION_FAILED, result.error());
			}
			default -> {
				return ResponseGenerator.fail(ChattingErrorCode.GAME_ACTION_FAILED, "Unknown result type");
			}
		}
	}

	// ========== WebSocket Broadcast Methods ==========

	/**
	 * 게임 시작 브로드캐스트
	 */
	private void broadcastGameStart(String roomId, GameStartResult result) {
		WordChainSession session = result.session();
		String messageId = UUID.randomUUID().toString();
		String now = Instant.now().toString();
		long serverTime = System.currentTimeMillis();

		String message = String.format("""
						🎮 끝말잇기 시작!
						시작 단어: %s
						다음 글자: '%c'

						첫 번째 차례: %s
						제한 시간: %d초
						""",
				result.starterWord(),
				result.nextLetter(),
				result.firstPlayerId(),
				session.getTimeLimit());

		Map<String, Object> payload = new LinkedHashMap<>();
		payload.put("domain", DOMAIN_WORDCHAIN);
		payload.put("messageId", messageId);
		payload.put("roomId", roomId);
		payload.put("userId", "SYSTEM");
		payload.put("content", message);
		payload.put("messageType", MessageType.WORDCHAIN_START.getCode());
		payload.put("createdAt", now);
		payload.put("timestamp", serverTime);
		payload.put("sessionId", session.getSessionId());
		payload.put("starterWord", result.starterWord());
		payload.put("nextLetter", result.nextLetter());
		payload.put("currentPlayerId", result.firstPlayerId());
		payload.put("timeLimit", session.getTimeLimit());
		payload.put("turnStartTime", session.getTurnStartTime());
		payload.put("serverTime", serverTime);
		payload.put("players", session.getPlayers());
		payload.put("activePlayers", session.getActivePlayers());

		broadcastToRoom(roomId, payload);
		logger.info("WordChain game start broadcasted: roomId={}, starterWord={}",
				roomId, result.starterWord());
	}

	/**
	 * 단어 제출 결과 브로드캐스트
	 */
	private void broadcastWordResult(String roomId, WordSubmitResult result) {
		String messageId = UUID.randomUUID().toString();
		String now = Instant.now().toString();
		long serverTime = System.currentTimeMillis();

		Map<String, Object> payload = new LinkedHashMap<>();
		payload.put("domain", DOMAIN_WORDCHAIN);
		payload.put("messageId", messageId);
		payload.put("roomId", roomId);
		payload.put("userId", "SYSTEM");
		payload.put("createdAt", now);
		payload.put("timestamp", serverTime);
		payload.put("serverTime", serverTime);
		payload.put("resultType", result.type().name());

		switch (result.type()) {
			case CORRECT -> {
				payload.put("messageType", MessageType.WORDCHAIN_CORRECT.getCode());
				payload.put("content", String.format("✅ %s: \"%s\" (+%d점)\n뜻: %s\n다음 글자: '%c'",
						result.playerNickname(),
						result.word(),
						result.score(),
						result.definition() != null ? result.definition() : "(정의 없음)",
						result.nextLetter()));
				payload.put("word", result.word());
				payload.put("definition", result.definition());
				payload.put("phonetic", result.phonetic());
				payload.put("score", result.score());
				payload.put("nextLetter", result.nextLetter());
				payload.put("nextPlayerId", result.nextPlayerId());
				payload.put("nextTimeLimit", result.nextTimeLimit());
				payload.put("playerNickname", result.playerNickname());
				if (result.session() != null) {
					payload.put("turnStartTime", result.session().getTurnStartTime());
					payload.put("scores", result.session().getScores());
				}
			}
			case WRONG_LETTER -> {
				payload.put("messageType", MessageType.WORDCHAIN_WRONG.getCode());
				payload.put("content", result.error());
				payload.put("error", result.error());
			}
			case INVALID_WORD -> {
				payload.put("messageType", MessageType.WORDCHAIN_WRONG.getCode());
				payload.put("content", "❌ " + result.error());
				payload.put("error", result.error());
			}
			case TIMEOUT -> {
				payload.put("messageType", MessageType.WORDCHAIN_TIMEOUT.getCode());
				payload.put("content", String.format("⏰ %s 시간 초과! 탈락!",
						result.eliminatedNickname()));
				payload.put("eliminatedPlayerId", result.eliminatedPlayerId());
				payload.put("eliminatedNickname", result.eliminatedNickname());
				payload.put("nextPlayerId", result.nextPlayerId());
				payload.put("nextTimeLimit", result.nextTimeLimit());
				if (result.session() != null) {
					payload.put("nextLetter", result.session().getNextLetter());
					payload.put("turnStartTime", result.session().getTurnStartTime());
					payload.put("activePlayers", result.session().getActivePlayers());
				}
			}
			case GAME_END -> {
				payload.put("messageType", MessageType.WORDCHAIN_END.getCode());
				String winnerMsg = result.winnerId() != null
						? String.format("🏆 승자: %s!", result.winnerNickname())
						: "게임 종료!";
				payload.put("content", winnerMsg);
				payload.put("winnerId", result.winnerId());
				payload.put("winnerNickname", result.winnerNickname());
				payload.put("ranking", result.ranking());
				if (result.session() != null) {
					payload.put("usedWords", result.session().getUsedWords());
					payload.put("wordDefinitions", result.session().getWordDefinitions());
					payload.put("scores", result.session().getScores());
				}
			}
			case ERROR -> {
				// 에러는 브로드캐스트하지 않음 (요청자에게만 응답)
				return;
			}
		}

		broadcastToRoom(roomId, payload);
		logger.info("WordChain result broadcasted: roomId={}, type={}", roomId, result.type());
	}

	/**
	 * 방에 메시지 브로드캐스트
	 */
	private void broadcastToRoom(String roomId, Map<String, Object> payload) {
		List<Connection> connections = connectionRepository.findByRoomId(roomId);
		String jsonPayload = ResponseGenerator.gson().toJson(payload);
		broadcaster.broadcast(connections, jsonPayload);
	}
}
