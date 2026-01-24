package com.mzc.secondproject.serverless.domain.chatting.service;

import com.mzc.secondproject.serverless.domain.chatting.model.Connection;
import com.mzc.secondproject.serverless.domain.chatting.model.WordChainSession;
import com.mzc.secondproject.serverless.domain.chatting.repository.ConnectionRepository;
import com.mzc.secondproject.serverless.domain.chatting.repository.WordChainSessionRepository;
import com.mzc.secondproject.serverless.domain.user.model.User;
import com.mzc.secondproject.serverless.domain.user.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 끝말잇기 게임 서비스
 */
public class WordChainService {

	private static final Logger logger = LoggerFactory.getLogger(WordChainService.class);

	// 게임 시작 단어 후보 (쉬운 3-5글자 단어)
	private static final List<String> STARTER_WORDS = List.of(
			"apple", "house", "water", "happy", "green", "music", "paper",
			"table", "chair", "phone", "smile", "dream", "light", "earth",
			"ocean", "river", "cloud", "sugar", "lemon", "tiger", "eagle"
	);

	private final WordChainSessionRepository sessionRepository;
	private final ConnectionRepository connectionRepository;
	private final UserRepository userRepository;
	private final DictionaryService dictionaryService;
	private final Random random;

	public WordChainService() {
		this(new WordChainSessionRepository(),
				new ConnectionRepository(),
				new UserRepository(),
				new DictionaryService());
	}

	public WordChainService(WordChainSessionRepository sessionRepository,
	                        ConnectionRepository connectionRepository,
	                        UserRepository userRepository,
	                        DictionaryService dictionaryService) {
		this.sessionRepository = sessionRepository;
		this.connectionRepository = connectionRepository;
		this.userRepository = userRepository;
		this.dictionaryService = dictionaryService;
		this.random = new Random();
	}

	/**
	 * 게임 시작
	 */
	public GameStartResult startGame(String roomId, String userId) {
		// 이미 진행 중인 게임 확인
		Optional<WordChainSession> existingSession = sessionRepository.findActiveByRoomId(roomId);
		if (existingSession.isPresent()) {
			return GameStartResult.error("이미 진행 중인 게임이 있습니다.");
		}

		// 접속자 확인
		List<Connection> connections = connectionRepository.findByRoomId(roomId);
		if (connections.size() < 2) {
			return GameStartResult.error("최소 2명 이상 필요합니다.");
		}

		// 플레이어 순서 랜덤 셔플
		List<String> players = connections.stream()
				.map(Connection::getUserId)
				.collect(Collectors.toList());
		Collections.shuffle(players);

		// 시작 단어 선택
		String starterWord = STARTER_WORDS.get(random.nextInt(STARTER_WORDS.size()));
		char nextLetter = starterWord.charAt(starterWord.length() - 1);

		// 세션 생성
		String sessionId = UUID.randomUUID().toString();
		String now = Instant.now().toString();
		long currentTime = System.currentTimeMillis();
		int timeLimit = WordChainSession.calculateTimeLimit(1);

		WordChainSession session = WordChainSession.builder()
				.pk("WORDCHAIN#" + sessionId)
				.sk("METADATA")
				.gsi1pk("ROOM#" + roomId)
				.gsi1sk("WORDCHAIN#" + now)
				.sessionId(sessionId)
				.roomId(roomId)
				.gameType("wordchain")
				.status("PLAYING")
				.startedBy(userId)
				.startedAt(currentTime)
				.currentRound(1)
				.currentPlayerId(players.get(0))
				.currentWord(starterWord)
				.nextLetter(nextLetter)
				.turnStartTime(currentTime)
				.timeLimit(timeLimit)
				.players(players)
				.activePlayers(new ArrayList<>(players))
				.eliminatedPlayers(new ArrayList<>())
				.scores(new HashMap<>())
				.usedWords(new ArrayList<>(List.of(starterWord.toLowerCase())))
				.wordDefinitions(new HashMap<>())
				.build();

		// 시작 단어 정의 조회
		DictionaryService.DictionaryResult starterResult = dictionaryService.lookupWord(starterWord);
		if (starterResult.getDefinition().isPresent()) {
			session.getWordDefinitions().put(starterWord.toLowerCase(), starterResult.getDefinition().get());
		}

		sessionRepository.save(session);

		logger.info("WordChain game started: sessionId={}, roomId={}, players={}",
				sessionId, roomId, players.size());

		return GameStartResult.success(session, starterWord, nextLetter, players.get(0));
	}

	/**
	 * 단어 제출
	 */
	public WordSubmitResult submitWord(String roomId, String userId, String word) {
		Optional<WordChainSession> optSession = sessionRepository.findActiveByRoomId(roomId);
		if (optSession.isEmpty()) {
			return WordSubmitResult.error("진행 중인 게임이 없습니다.");
		}

		WordChainSession session = optSession.get();

		// 본인 턴인지 확인
		if (!session.isCurrentTurn(userId)) {
			return WordSubmitResult.error("당신의 차례가 아닙니다.");
		}

		// 시간 초과 확인
		long elapsed = System.currentTimeMillis() - session.getTurnStartTime();
		if (elapsed > session.getTimeLimit() * 1000L) {
			return handleTimeout(session, userId);
		}

		String normalizedWord = word.trim().toLowerCase();

		// 첫 글자 확인
		if (normalizedWord.charAt(0) != session.getNextLetter()) {
			return WordSubmitResult.wrongLetter(session.getNextLetter());
		}

		// 중복 단어 확인
		if (session.isWordUsed(normalizedWord)) {
			return WordSubmitResult.error("이미 사용된 단어입니다: " + normalizedWord);
		}

		// 사전 API로 유효성 검증
		DictionaryService.DictionaryResult dictResult = dictionaryService.lookupWord(normalizedWord);
		if (!dictResult.isValid()) {
			return WordSubmitResult.invalidWord(dictResult.errorMessage());
		}

		// 정답 처리
		int score = WordChainSession.calculateScore(elapsed, normalizedWord.length(), session.getTimeLimit());
		session.addScore(userId, score);
		session.addUsedWord(normalizedWord, dictResult.getDefinition().orElse(null));

		// 다음 턴 준비
		char nextLetter = normalizedWord.charAt(normalizedWord.length() - 1);
		String nextPlayerId = session.getNextPlayerId();
		int nextRound = session.getCurrentRound() + 1;
		int nextTimeLimit = WordChainSession.calculateTimeLimit(nextRound);

		session.setCurrentRound(nextRound);
		session.setCurrentWord(normalizedWord);
		session.setNextLetter(nextLetter);
		session.setCurrentPlayerId(nextPlayerId);
		session.setTurnStartTime(System.currentTimeMillis());
		session.setTimeLimit(nextTimeLimit);

		sessionRepository.save(session);

		String nickname = getNickname(userId);

		logger.info("Word accepted: sessionId={}, word={}, player={}, score={}",
				session.getSessionId(), normalizedWord, userId, score);

		return WordSubmitResult.correct(
				session,
				normalizedWord,
				dictResult.getDefinition().orElse(null),
				dictResult.getPhonetic().orElse(null),
				score,
				nextLetter,
				nextPlayerId,
				nextTimeLimit,
				nickname
		);
	}

	/**
	 * 타임아웃 처리
	 */
	public WordSubmitResult handleTimeout(String roomId, String userId) {
		Optional<WordChainSession> optSession = sessionRepository.findActiveByRoomId(roomId);
		if (optSession.isEmpty()) {
			return WordSubmitResult.error("진행 중인 게임이 없습니다.");
		}
		return handleTimeout(optSession.get(), userId);
	}

	private WordSubmitResult handleTimeout(WordChainSession session, String userId) {
		// 플레이어 탈락
		session.eliminatePlayer(userId);
		String nickname = getNickname(userId);

		logger.info("Player eliminated (timeout): sessionId={}, player={}",
				session.getSessionId(), userId);

		// 게임 종료 확인
		if (session.isGameOver()) {
			return finishGame(session, "TIMEOUT");
		}

		// 다음 턴 준비
		String nextPlayerId = session.getNextPlayerId();
		int nextRound = session.getCurrentRound() + 1;
		int nextTimeLimit = WordChainSession.calculateTimeLimit(nextRound);

		session.setCurrentRound(nextRound);
		session.setCurrentPlayerId(nextPlayerId);
		session.setTurnStartTime(System.currentTimeMillis());
		session.setTimeLimit(nextTimeLimit);

		sessionRepository.save(session);

		return WordSubmitResult.timeout(
				session,
				userId,
				nickname,
				nextPlayerId,
				nextTimeLimit
		);
	}

	/**
	 * 게임 종료
	 */
	public WordSubmitResult finishGame(WordChainSession session, String reason) {
		long endTime = System.currentTimeMillis();
		long ttl = Instant.now().plusSeconds(7 * 24 * 60 * 60).getEpochSecond(); // 7일 보관

		session.setStatus("FINISHED");
		session.setEndedAt(endTime);
		session.setTtl(ttl);
		sessionRepository.save(session);

		String winnerId = session.getWinner();
		String winnerNickname = winnerId != null ? getNickname(winnerId) : null;

		// 최종 순위 계산
		List<RankEntry> ranking = buildRanking(session);

		logger.info("WordChain game finished: sessionId={}, winner={}, reason={}",
				session.getSessionId(), winnerId, reason);

		return WordSubmitResult.gameEnd(session, winnerId, winnerNickname, ranking);
	}

	/**
	 * 게임 강제 종료
	 */
	public WordSubmitResult stopGame(String roomId, String userId) {
		Optional<WordChainSession> optSession = sessionRepository.findActiveByRoomId(roomId);
		if (optSession.isEmpty()) {
			return WordSubmitResult.error("진행 중인 게임이 없습니다.");
		}

		WordChainSession session = optSession.get();

		// 게임 시작자만 종료 가능
		if (!userId.equals(session.getStartedBy())) {
			return WordSubmitResult.error("게임 시작자만 종료할 수 있습니다.");
		}

		return finishGame(session, "STOPPED");
	}

	/**
	 * 순위 계산
	 */
	private List<RankEntry> buildRanking(WordChainSession session) {
		List<RankEntry> ranking = new ArrayList<>();

		// 점수 기준 정렬
		Map<String, Integer> scores = session.getScores() != null
				? session.getScores()
				: new HashMap<>();

		// 활성 플레이어 (생존자) 먼저
		if (session.getActivePlayers() != null) {
			for (String playerId : session.getActivePlayers()) {
				ranking.add(new RankEntry(
						playerId,
						getNickname(playerId),
						scores.getOrDefault(playerId, 0),
						false
				));
			}
		}

		// 탈락 플레이어 (역순으로 - 나중에 탈락한 사람이 순위 높음)
		if (session.getEliminatedPlayers() != null) {
			List<String> eliminated = new ArrayList<>(session.getEliminatedPlayers());
			Collections.reverse(eliminated);
			for (String playerId : eliminated) {
				ranking.add(new RankEntry(
						playerId,
						getNickname(playerId),
						scores.getOrDefault(playerId, 0),
						true
				));
			}
		}

		return ranking;
	}

	/**
	 * 닉네임 조회
	 */
	private String getNickname(String userId) {
		return userRepository.findByCognitoSub(userId)
				.map(User::getNickname)
				.orElse(userId);
	}

	// ========== Result DTOs ==========

	public record GameStartResult(
			boolean success,
			String error,
			WordChainSession session,
			String starterWord,
			Character nextLetter,
			String firstPlayerId
	) {
		public static GameStartResult success(WordChainSession session, String word, char letter, String playerId) {
			return new GameStartResult(true, null, session, word, letter, playerId);
		}

		public static GameStartResult error(String message) {
			return new GameStartResult(false, message, null, null, null, null);
		}
	}

	public record WordSubmitResult(
			ResultType type,
			String error,
			WordChainSession session,
			// 정답 시
			String word,
			String definition,
			String phonetic,
			int score,
			Character nextLetter,
			String nextPlayerId,
			int nextTimeLimit,
			String playerNickname,
			// 타임아웃 시
			String eliminatedPlayerId,
			String eliminatedNickname,
			// 게임 종료 시
			String winnerId,
			String winnerNickname,
			List<RankEntry> ranking
	) {
		public enum ResultType {
			CORRECT, WRONG_LETTER, INVALID_WORD, TIMEOUT, GAME_END, ERROR
		}

		public static WordSubmitResult correct(WordChainSession session, String word, String definition,
		                                       String phonetic, int score, char nextLetter,
		                                       String nextPlayerId, int nextTimeLimit, String nickname) {
			return new WordSubmitResult(ResultType.CORRECT, null, session, word, definition, phonetic,
					score, nextLetter, nextPlayerId, nextTimeLimit, nickname,
					null, null, null, null, null);
		}

		public static WordSubmitResult wrongLetter(char expected) {
			return new WordSubmitResult(ResultType.WRONG_LETTER,
					String.format("'%c'로 시작하는 단어를 입력하세요.", expected),
					null, null, null, null, 0, null, null, 0, null,
					null, null, null, null, null);
		}

		public static WordSubmitResult invalidWord(String reason) {
			return new WordSubmitResult(ResultType.INVALID_WORD, reason,
					null, null, null, null, 0, null, null, 0, null,
					null, null, null, null, null);
		}

		public static WordSubmitResult timeout(WordChainSession session, String eliminatedId, String eliminatedNick,
		                                       String nextPlayerId, int nextTimeLimit) {
			return new WordSubmitResult(ResultType.TIMEOUT, null, session, null, null, null, 0,
					session.getNextLetter(), nextPlayerId, nextTimeLimit, null,
					eliminatedId, eliminatedNick, null, null, null);
		}

		public static WordSubmitResult gameEnd(WordChainSession session, String winnerId, String winnerNick,
		                                       List<RankEntry> ranking) {
			return new WordSubmitResult(ResultType.GAME_END, null, session, null, null, null, 0,
					null, null, 0, null, null, null, winnerId, winnerNick, ranking);
		}

		public static WordSubmitResult error(String message) {
			return new WordSubmitResult(ResultType.ERROR, message, null, null, null, null, 0,
					null, null, 0, null, null, null, null, null, null);
		}
	}

	public record RankEntry(
			String playerId,
			String nickname,
			int score,
			boolean eliminated
	) {
	}
}
