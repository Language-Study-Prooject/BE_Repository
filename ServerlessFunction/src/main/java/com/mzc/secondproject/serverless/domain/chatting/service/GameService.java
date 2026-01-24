package com.mzc.secondproject.serverless.domain.chatting.service;

import com.mzc.secondproject.serverless.common.dto.PaginatedResult;
import com.mzc.secondproject.serverless.domain.chatting.config.GameConfig;
import com.mzc.secondproject.serverless.domain.chatting.dto.response.CommandResult;
import com.mzc.secondproject.serverless.domain.chatting.enums.GameStatus;
import com.mzc.secondproject.serverless.domain.chatting.enums.MessageType;
import com.mzc.secondproject.serverless.domain.chatting.model.ChatRoom;
import com.mzc.secondproject.serverless.domain.chatting.model.Connection;
import com.mzc.secondproject.serverless.domain.chatting.model.GameRound;
import com.mzc.secondproject.serverless.domain.chatting.model.GameSession;
import com.mzc.secondproject.serverless.domain.chatting.repository.ChatRoomRepository;
import com.mzc.secondproject.serverless.domain.chatting.repository.ConnectionRepository;
import com.mzc.secondproject.serverless.domain.chatting.repository.GameRoundRepository;
import com.mzc.secondproject.serverless.domain.chatting.repository.GameSessionRepository;
import com.mzc.secondproject.serverless.domain.notification.service.NotificationPublisher;
import com.mzc.secondproject.serverless.domain.vocabulary.model.Word;
import com.mzc.secondproject.serverless.domain.vocabulary.repository.WordRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 캐치마인드 게임 로직 서비스
 * GameSession 모델을 사용하여 게임 상태 관리
 */
public class GameService {
	
	private static final Logger logger = LoggerFactory.getLogger(GameService.class);
	
	private final ChatRoomRepository chatRoomRepository;
	private final ConnectionRepository connectionRepository;
	private final GameRoundRepository gameRoundRepository;
	private final GameSessionRepository gameSessionRepository;
	private final WordRepository wordRepository;
	private final GameStatsService gameStatsService;
	private final GameSchedulerClient gameSchedulerClient;
	private final NotificationPublisher notificationPublisher;

	/**
	 * 기본 생성자 (Lambda에서 사용)
	 */
	public GameService() {
		this(new ChatRoomRepository(), new ConnectionRepository(),
				new GameRoundRepository(), new GameSessionRepository(),
				new WordRepository(), new GameStatsService(), new GameSchedulerClient(),
				NotificationPublisher.getInstance());
	}

	/**
	 * 의존성 주입 생성자 (테스트 용이성)
	 */
	public GameService(ChatRoomRepository chatRoomRepository, ConnectionRepository connectionRepository,
	                   GameRoundRepository gameRoundRepository, GameSessionRepository gameSessionRepository,
	                   WordRepository wordRepository, GameStatsService gameStatsService,
	                   GameSchedulerClient gameSchedulerClient, NotificationPublisher notificationPublisher) {
		this.chatRoomRepository = chatRoomRepository;
		this.connectionRepository = connectionRepository;
		this.gameRoundRepository = gameRoundRepository;
		this.gameSessionRepository = gameSessionRepository;
		this.wordRepository = wordRepository;
		this.gameStatsService = gameStatsService;
		this.gameSchedulerClient = gameSchedulerClient;
		this.notificationPublisher = notificationPublisher;
	}
	
	/**
	 * 게임 재시작
	 */
	public GameStartResult restartGame(String roomId, String userId) {
		ChatRoom room = chatRoomRepository.findById(roomId)
				.orElseThrow(() -> new IllegalArgumentException("채팅방을 찾을 수 없습니다."));
		
		// 방장 권한 확인
		if (!userId.equals(room.getHostId()) && !userId.equals(room.getCreatedBy())) {
			return GameStartResult.error("방장만 게임을 시작할 수 있습니다.");
		}
		
		// 방 타입 검증
		if (room.getType() == null || !"GAME".equalsIgnoreCase(room.getType())) {
			return GameStartResult.error("게임은 게임 방에서만 시작할 수 있습니다.");
		}
		
		// FINISHED 상태인지 확인 (이미 게임이 끝났어야 재시작 가능)
		Optional<GameSession> existingSession = gameSessionRepository.findActiveByRoomId(roomId);
		if (existingSession.isPresent()) {
			return GameStartResult.error("게임 진행 중에는 재시작할 수 없습니다.");
		}
		
		// 접속자 확인
		List<Connection> connections = connectionRepository.findByRoomId(roomId);
		if (connections.size() < 2) {
			return GameStartResult.error("최소 2명 이상 접속해야 게임을 시작할 수 있습니다.");
		}
		
		// 기존 startGame 로직 재사용 - 내부적으로 startGame 호출
		return startGame(roomId, userId);
	}
	
	/**
	 * 게임 시작
	 */
	public GameStartResult startGame(String roomId, String userId) {
		ChatRoom room = chatRoomRepository.findById(roomId)
				.orElseThrow(() -> new IllegalArgumentException("채팅방을 찾을 수 없습니다."));
		
		// 방 타입 검증 - GAME 타입만 게임 시작 가능
		String roomType = room.getType();
		if (roomType == null || !"GAME".equalsIgnoreCase(roomType)) {
			return GameStartResult.error("게임은 게임 방에서만 시작할 수 있습니다.");
		}
		
		// 이미 활성 게임 세션이 있는지 확인
		Optional<GameSession> existingSession = gameSessionRepository.findActiveByRoomId(roomId);
		if (existingSession.isPresent()) {
			return GameStartResult.error("이미 게임이 진행 중입니다.");
		}
		
		// 접속자 확인
		List<Connection> connections = connectionRepository.findByRoomId(roomId);
		if (connections.size() < 2) {
			return GameStartResult.error("최소 2명 이상 접속해야 게임을 시작할 수 있습니다.");
		}
		
		// 출제 순서 생성 (랜덤 셔플)
		List<String> drawerOrder = connections.stream()
				.map(Connection::getUserId)
				.collect(Collectors.toList());
		Collections.shuffle(drawerOrder);
		
		// 제시어 추출 (난이도별)
		String level = room.getLevel() != null ? room.getLevel() : "beginner";
		List<Word> words = getRandomWords(level, GameConfig.totalRounds());
		
		if (words.size() < GameConfig.totalRounds()) {
			return GameStartResult.error("단어가 부족합니다. 관리자에게 문의하세요.");
		}
		
		// 게임 세션 생성
		String gameSessionId = UUID.randomUUID().toString();
		String now = Instant.now().toString();
		long currentTime = System.currentTimeMillis();
		
		String firstDrawer = drawerOrder.get(0);
		Word firstWord = words.get(0);
		
		GameSession session = GameSession.builder()
				.pk("GAME#" + gameSessionId)
				.sk("METADATA")
				.gsi1pk("ROOM#" + roomId)
				.gsi1sk("GAME#" + now)
				.gameSessionId(gameSessionId)
				.roomId(roomId)
				.gameType("catchmind")
				.status(GameStatus.PLAYING.name())
				.startedBy(userId)
				.startedAt(currentTime)
				.currentRound(1)
				.totalRounds(GameConfig.totalRounds())
				.currentDrawerId(firstDrawer)
				.currentWordId(firstWord.getWordId())
				.currentWord(firstWord.getKorean())
				.currentWordEnglish(firstWord.getEnglish())
				.roundStartTime(currentTime)
				.roundDuration(GameConfig.roundTimeLimit())
				.scores(new HashMap<>())
				.streaks(new HashMap<>())
				.players(new ArrayList<>(drawerOrder))
				.drawerOrder(drawerOrder)
				.hintUsed(false)
				.correctGuessers(new ArrayList<>())
				.build();
		
		gameSessionRepository.save(session);
		
		// 게임 자동 종료 스케줄 생성 (7분 후)
		GameSchedulerClient.ScheduleResult scheduleResult = gameSchedulerClient.createGameEndSchedule(gameSessionId, roomId);
		if (scheduleResult.success()) {
			session.setScheduleRuleArn(scheduleResult.scheduleArn());
			session.setGameEndScheduledAt(scheduleResult.scheduledAtMs());
			gameSessionRepository.save(session);
		}
		
		// ChatRoom에 활성 게임 세션 ID 연결 및 상태 업데이트 (GSI1SK 포함)
		room.setActiveGameSessionId(gameSessionId);
		chatRoomRepository.updateStatus(room, "PLAYING");
		
		// 첫 라운드 기록 생성 (7일 후 자동 삭제)
		long ttlSeconds = Instant.now().plusSeconds(7 * 24 * 60 * 60).getEpochSecond();
		GameRound firstRound = GameRound.builder()
				.pk("ROOM#" + roomId + "#GAME")
				.sk("ROUND#1")
				.roomId(roomId)
				.roundNumber(1)
				.drawerId(firstDrawer)
				.wordId(firstWord.getWordId())
				.word(firstWord.getKorean())
				.wordEnglish(firstWord.getEnglish())
				.startTime(currentTime)
				.hintUsed(false)
				.correctGuessers(new ArrayList<>())
				.guessTimes(new HashMap<>())
				.roundScores(new HashMap<>())
				.createdAt(now)
				.ttl(ttlSeconds)
				.build();
		
		gameRoundRepository.save(firstRound);
		
		logger.info("Game started: roomId={}, sessionId={}, starter={}, rounds={}",
				roomId, gameSessionId, userId, GameConfig.totalRounds());
		
		return GameStartResult.success(session, firstWord, drawerOrder);
	}
	
	/**
	 * 게임 종료
	 */
	public CommandResult stopGame(String roomId, String userId) {
		ChatRoom room = chatRoomRepository.findById(roomId)
				.orElseThrow(() -> new IllegalArgumentException("채팅방을 찾을 수 없습니다."));
		
		GameSession session = gameSessionRepository.findActiveByRoomId(roomId)
				.orElse(null);
		
		if (session == null || !session.isActive()) {
			return CommandResult.error("진행 중인 게임이 없습니다.");
		}
		
		// 권한 확인
		boolean isOwner = userId.equals(room.getCreatedBy());
		boolean isGameStarter = userId.equals(session.getStartedBy());
		
		if (!isOwner && !isGameStarter) {
			return CommandResult.error("게임을 중단할 권한이 없습니다.");
		}
		
		// 게임 종료 처리
		return finishGame(session, room, "STOPPED");
	}
	
	/**
	 * 정답 체크
	 */
	public AnswerCheckResult checkAnswer(String roomId, String userId, String answer) {
		GameSession session = gameSessionRepository.findActiveByRoomId(roomId)
				.orElse(null);
		
		// 게임 진행 중인지 확인
		if (session == null || !GameStatus.PLAYING.name().equals(session.getStatus())) {
			return AnswerCheckResult.gameNotPlaying();
		}
		
		// 출제자는 정답 체크 제외
		if (session.isDrawer(userId)) {
			return AnswerCheckResult.drawerCannotGuess();
		}
		
		// 이미 맞춘 사람인지 확인
		if (session.hasAlreadyGuessedCorrect(userId)) {
			return AnswerCheckResult.alreadyGuessedCorrect();
		}
		
		// 정답 체크 (한국어 또는 영어 둘 다 허용)
		String koreanWord = session.getCurrentWord();
		String englishWord = session.getCurrentWordEnglish();
		if (!isCorrectAnswer(answer, koreanWord, englishWord)) {
			return AnswerCheckResult.wrongAnswer();
		}
		
		// 정답 처리
		long elapsedTime = System.currentTimeMillis() - session.getRoundStartTime();
		
		// 연속 정답 업데이트 (점수 계산 전에)
		int currentStreak = session.incrementStreak(userId);
		
		int score = calculateScore(session, elapsedTime, userId, currentStreak);
		
		// 정답자 목록에 추가
		session.addCorrectGuesser(userId);
		
		// 점수 업데이트
		session.addScore(userId, score);
		
		// 출제자 점수도 추가
		session.addScore(session.getCurrentDrawerId(), 5);
		
		gameSessionRepository.save(session);
		
		// 라운드 기록 업데이트
		updateRoundRecord(roomId, session.getCurrentRound(), userId, elapsedTime, score);
		
		// 전원 정답 체크
		List<Connection> connections = connectionRepository.findByRoomId(roomId);
		int nonDrawerCount = (int) connections.stream()
				.filter(c -> !c.getUserId().equals(session.getCurrentDrawerId()))
				.count();
		
		boolean allCorrect = session.getCorrectGuessers().size() >= nonDrawerCount;
		
		logger.info("Answer correct: roomId={}, userId={}, score={}, allCorrect={}",
				roomId, userId, score, allCorrect);
		
		return AnswerCheckResult.correctAnswer(score, elapsedTime, allCorrect, session.getScores());
	}
	
	/**
	 * 라운드 스킵
	 */
	public CommandResult skipRound(String roomId, String userId) {
		ChatRoom room = chatRoomRepository.findById(roomId)
				.orElseThrow(() -> new IllegalArgumentException("채팅방을 찾을 수 없습니다."));
		
		GameSession session = gameSessionRepository.findActiveByRoomId(roomId)
				.orElse(null);
		
		if (session == null || !GameStatus.PLAYING.name().equals(session.getStatus())) {
			return CommandResult.error("게임이 진행 중이 아닙니다.");
		}
		
		if (!session.isDrawer(userId)) {
			return CommandResult.error("출제자만 라운드를 스킵할 수 있습니다.");
		}
		
		return endRound(session, room, "SKIP");
	}
	
	/**
	 * 힌트 제공
	 */
	public CommandResult provideHint(String roomId, String userId) {
		GameSession session = gameSessionRepository.findActiveByRoomId(roomId)
				.orElse(null);
		
		if (session == null || !GameStatus.PLAYING.name().equals(session.getStatus())) {
			return CommandResult.error("게임이 진행 중이 아닙니다.");
		}
		
		if (!session.isDrawer(userId)) {
			return CommandResult.error("출제자만 힌트를 제공할 수 있습니다.");
		}
		
		if (Boolean.TRUE.equals(session.getHintUsed())) {
			return CommandResult.error("이번 라운드에서 이미 힌트를 사용했습니다.");
		}
		
		String currentWord = session.getCurrentWord();
		String hint = currentWord.charAt(0) + "○".repeat(currentWord.length() - 1);
		
		session.setHintUsed(true);
		gameSessionRepository.save(session);
		
		// 라운드 기록 업데이트
		gameRoundRepository.findByRoomIdAndRound(roomId, session.getCurrentRound())
				.ifPresent(round -> {
					round.setHintUsed(true);
					gameRoundRepository.save(round);
				});
		
		return CommandResult.success(MessageType.HINT, "💡 힌트: " + hint);
	}
	
	/**
	 * 라운드 종료 처리 (GameSession 버전)
	 */
	public CommandResult endRound(GameSession session, ChatRoom room, String reason) {
		String roomId = session.getRoomId();
		Integer currentRound = session.getCurrentRound();
		String answer = session.getCurrentWord();
		
		// 정답 못 맞춘 사용자 연속 정답 초기화
		resetStreaksForNonGuessers(session);
		
		// 라운드 기록 종료
		gameRoundRepository.findByRoomIdAndRound(roomId, currentRound)
				.ifPresent(round -> {
					round.setEndTime(System.currentTimeMillis());
					round.setEndReason(reason);
					gameRoundRepository.save(round);
				});
		
		// 다음 라운드로 진행
		if (currentRound >= session.getTotalRounds()) {
			return finishGame(session, room, "COMPLETED");
		}
		
		// 현재 접속 중인 사용자 목록 조회
		List<Connection> connections = connectionRepository.findByRoomId(roomId);
		Set<String> connectedUserIds = connections.stream()
				.map(Connection::getUserId)
				.collect(Collectors.toSet());
		
		// 접속자가 2명 미만이면 게임 종료
		if (connectedUserIds.size() < 2) {
			return finishGame(session, room, "NOT_ENOUGH_PLAYERS");
		}
		
		// 다음 라운드 준비 - 접속 중인 사용자 중에서만 출제자 선택
		int nextRound = currentRound + 1;
		String nextDrawer = selectNextDrawer(session.getDrawerOrder(), connectedUserIds, nextRound);
		
		// 다음 단어 추출
		String level = room.getLevel() != null ? room.getLevel() : "beginner";
		List<Word> words = getRandomWords(level, 1);
		if (words.isEmpty()) {
			return finishGame(session, room, "NO_WORDS");
		}
		Word nextWord = words.get(0);
		
		long currentTime = System.currentTimeMillis();
		
		// 세션 상태 업데이트
		session.setCurrentRound(nextRound);
		session.setCurrentDrawerId(nextDrawer);
		session.setCurrentWordId(nextWord.getWordId());
		session.setCurrentWord(nextWord.getKorean());
		session.setCurrentWordEnglish(nextWord.getEnglish());
		session.setRoundStartTime(currentTime);
		session.setHintUsed(false);
		session.setCorrectGuessers(new ArrayList<>());
		
		gameSessionRepository.save(session);
		
		// 다음 라운드 기록 생성 (7일 후 자동 삭제)
		long nextTtlSeconds = Instant.now().plusSeconds(7 * 24 * 60 * 60).getEpochSecond();
		GameRound nextRoundRecord = GameRound.builder()
				.pk("ROOM#" + roomId + "#GAME")
				.sk("ROUND#" + nextRound)
				.roomId(roomId)
				.roundNumber(nextRound)
				.drawerId(nextDrawer)
				.wordId(nextWord.getWordId())
				.word(nextWord.getKorean())
				.wordEnglish(nextWord.getEnglish())
				.startTime(currentTime)
				.hintUsed(false)
				.correctGuessers(new ArrayList<>())
				.guessTimes(new HashMap<>())
				.roundScores(new HashMap<>())
				.createdAt(Instant.now().toString())
				.ttl(nextTtlSeconds)
				.build();
		
		gameRoundRepository.save(nextRoundRecord);
		
		String message = String.format("라운드 %d 종료! 정답: %s\n\n라운드 %d 시작! 출제자: %s",
				currentRound, answer, nextRound, nextDrawer);
		
		logger.info("Round ended: roomId={}, round={}, reason={}", roomId, currentRound, reason);
		
		// ranking 생성
		List<Map<String, Object>> ranking = buildRankingList(session.getScores());
		
		Map<String, Object> data = new HashMap<>();
		data.put("answer", answer);
		data.put("nextRound", nextRound);
		data.put("nextDrawer", nextDrawer);
		data.put("nextWord", nextWord);
		data.put("ranking", ranking);
		data.put("currentRound", currentRound);
		data.put("totalRounds", session.getTotalRounds());
		// 타이머 동기화용 필드 추가
		data.put("roundStartTime", session.getRoundStartTime());
		data.put("roundDuration", session.getRoundDuration() != null ? session.getRoundDuration() : GameConfig.roundTimeLimit());
		
		return CommandResult.success(MessageType.ROUND_END, message, data);
	}
	
	/**
	 * roomId로 활성 세션을 찾아 라운드 종료 (외부 호출용)
	 */
	public CommandResult endRound(String roomId, String reason) {
		ChatRoom room = chatRoomRepository.findById(roomId)
				.orElseThrow(() -> new IllegalArgumentException("채팅방을 찾을 수 없습니다."));
		
		GameSession session = gameSessionRepository.findActiveByRoomId(roomId)
				.orElse(null);
		
		if (session == null) {
			return CommandResult.error("진행 중인 게임이 없습니다.");
		}
		
		return endRound(session, room, reason);
	}
	
	/**
	 * 게임 완전 종료
	 */
	private CommandResult finishGame(GameSession session, ChatRoom room, String reason) {
		long currentTime = System.currentTimeMillis();
		long ttlSeconds = Instant.now().plusSeconds(30 * 24 * 60 * 60).getEpochSecond(); // 30일 보관
		
		// 자동 종료 스케줄 취소 (TIME_EXPIRED가 아닌 경우에만)
		if (!"TIME_EXPIRED".equals(reason)) {
			gameSchedulerClient.cancelGameEndSchedule(session.getGameSessionId());
		}
		
		// 게임 세션 종료 처리
		gameSessionRepository.finishGame(session.getGameSessionId(), currentTime, ttlSeconds);
		
		// ChatRoom에서 활성 게임 세션 참조 제거 및 상태 업데이트 (GSI1SK 포함)
		room.setActiveGameSessionId(null);
		chatRoomRepository.updateStatus(room, "WAITING");
		
		// 게임 통계 업데이트 및 뱃지 체크
		try {
			var newBadges = gameStatsService.updateGameStats(session);
			logger.info("Game stats updated: roomId={}, newBadges={}", room.getRoomId(), newBadges.size());
		} catch (Exception e) {
			logger.error("Failed to update game stats: roomId={}, error={}", room.getRoomId(), e.getMessage());
		}

		// 게임 종료 알림 발행 (각 플레이어별)
		publishGameEndNotifications(session, room.getRoomId());

		// 최종 점수 정렬
		StringBuilder sb = new StringBuilder("🎮 게임 종료!\n\n📊 최종 순위:\n");
		if (session.getScores() != null && !session.getScores().isEmpty()) {
			List<Map.Entry<String, Integer>> sorted = session.getScores().entrySet().stream()
					.sorted((a, b) -> b.getValue().compareTo(a.getValue()))
					.toList();
			
			int rank = 1;
			for (Map.Entry<String, Integer> entry : sorted) {
				String medal = switch (rank) {
					case 1 -> "🥇";
					case 2 -> "🥈";
					case 3 -> "🥉";
					default -> rank + "위";
				};
				sb.append(String.format("  %s %s: %d점\n", medal, entry.getKey(), entry.getValue()));
				rank++;
			}
		} else {
			sb.append("  점수 없음");
		}
		
		logger.info("Game finished: roomId={}, sessionId={}, reason={}",
				room.getRoomId(), session.getGameSessionId(), reason);
		
		return CommandResult.success(MessageType.GAME_END, sb.toString(), session.getScores());
	}
	
	/**
	 * 시간 만료로 인한 게임 자동 종료 (GameAutoCloseHandler에서 호출)
	 */
	public CommandResult finishGameByTimeout(String gameSessionId) {
		GameSession session = gameSessionRepository.findById(gameSessionId).orElse(null);
		if (session == null) {
			logger.warn("Game session not found for auto-close: {}", gameSessionId);
			return CommandResult.error("게임 세션을 찾을 수 없습니다.");
		}
		
		// 이미 종료된 게임이면 무시
		if (!session.isActive()) {
			logger.info("Game already finished, skipping auto-close: {}", gameSessionId);
			return CommandResult.error("이미 종료된 게임입니다.");
		}
		
		ChatRoom room = chatRoomRepository.findById(session.getRoomId()).orElse(null);
		if (room == null) {
			logger.warn("Room not found for auto-close: {}", session.getRoomId());
			return CommandResult.error("채팅방을 찾을 수 없습니다.");
		}
		
		logger.info("Auto-closing game due to time expiration: sessionId={}, roomId={}",
				gameSessionId, session.getRoomId());
		
		return finishGame(session, room, "TIME_EXPIRED");
	}
	
	/**
	 * 접속 중인 사용자 중에서 다음 출제자 선택
	 */
	private String selectNextDrawer(List<String> drawerOrder, Set<String> connectedUserIds, int roundNumber) {
		// 원래 순서에서 시작 인덱스 계산
		int startIndex = (roundNumber - 1) % drawerOrder.size();
		
		// 접속 중인 사용자를 찾을 때까지 순회
		for (int i = 0; i < drawerOrder.size(); i++) {
			int index = (startIndex + i) % drawerOrder.size();
			String candidate = drawerOrder.get(index);
			if (connectedUserIds.contains(candidate)) {
				return candidate;
			}
		}
		
		// 원래 순서에 있는 사람이 모두 나갔으면, 접속 중인 아무나 선택
		return connectedUserIds.iterator().next();
	}
	
	/**
	 * 랜덤 단어 추출
	 * VocabTable은 LEVEL#BEGINNER 형식(대문자)으로 저장되어 있으므로
	 * ChatRoom의 level(소문자)을 대문자로 변환
	 */
	private List<Word> getRandomWords(String level, int count) {
		// ChatRoom.level은 소문자(beginner), VocabTable GSI1PK는 대문자(BEGINNER)
		String normalizedLevel = level != null ? level.toUpperCase() : "BEGINNER";
		PaginatedResult<Word> result = wordRepository.findByLevelWithPagination(normalizedLevel, 50, null);
		List<Word> words = new ArrayList<>(result.items());
		Collections.shuffle(words);
		return words.stream().limit(count).collect(Collectors.toList());
	}
	
	/**
	 * 정답 체크 로직 (한국어 또는 영어 둘 다 허용)
	 */
	private boolean isCorrectAnswer(String input, String koreanAnswer, String englishAnswer) {
		if (input == null) return false;
		
		String normalizedInput = input.trim().toLowerCase().replace(" ", "");
		
		// 한국어 정답 체크
		if (koreanAnswer != null) {
			String normalizedKorean = koreanAnswer.trim().toLowerCase().replace(" ", "");
			if (normalizedInput.equals(normalizedKorean)) {
				return true;
			}
		}
		
		// 영어 정답 체크
		if (englishAnswer != null) {
			String normalizedEnglish = englishAnswer.trim().toLowerCase().replace(" ", "");
			if (normalizedInput.equals(normalizedEnglish)) {
				return true;
			}
		}
		
		return false;
	}
	
	/**
	 * 점수 계산
	 */
	private int calculateScore(GameSession session, long elapsedTimeMs, String userId, int streak) {
		int baseScore = 10;
		
		// 시간 보너스 (빨리 맞출수록 높은 점수)
		int elapsedSeconds = (int) (elapsedTimeMs / 1000);
		int timeLimit = session.getRoundDuration() != null ? session.getRoundDuration() : GameConfig.roundTimeLimit();
		int timeBonus = Math.max(0, (int) ((timeLimit - elapsedSeconds) * 0.5));
		
		// 연속 정답 보너스
		int streakBonus = streak * 2;
		
		logger.info("Score calculation: base={}, timeBonus={}, streakBonus={}, total={}",
				baseScore, timeBonus, streakBonus, baseScore + timeBonus + streakBonus);
		
		return baseScore + timeBonus + streakBonus;
	}
	
	/**
	 * 라운드 기록 업데이트
	 */
	private void updateRoundRecord(String roomId, Integer roundNumber, String userId, long elapsedTime, int score) {
		gameRoundRepository.findByRoomIdAndRound(roomId, roundNumber)
				.ifPresent(round -> {
					if (round.getCorrectGuessers() == null) {
						round.setCorrectGuessers(new ArrayList<>());
					}
					round.getCorrectGuessers().add(userId);
					
					if (round.getGuessTimes() == null) {
						round.setGuessTimes(new HashMap<>());
					}
					round.getGuessTimes().put(userId, elapsedTime);
					
					if (round.getRoundScores() == null) {
						round.setRoundScores(new HashMap<>());
					}
					round.getRoundScores().put(userId, score);
					
					gameRoundRepository.save(round);
				});
	}
	
	/**
	 * 정답 못 맞춘 사용자 연속 정답 초기화
	 */
	private void resetStreaksForNonGuessers(GameSession session) {
		if (session.getStreaks() == null || session.getStreaks().isEmpty()) {
			return;
		}
		
		List<String> correctGuessers = session.getCorrectGuessers() != null
				? session.getCorrectGuessers()
				: List.of();
		
		// 정답 못 맞춘 사용자의 연속 정답 초기화
		session.getStreaks().keySet().stream()
				.filter(userId -> !correctGuessers.contains(userId))
				.forEach(userId -> session.getStreaks().put(userId, 0));
		
		logger.info("Reset streaks for non-guessers: correctGuessers={}", correctGuessers);
	}
	
	/**
	 * 점수 맵을 순위 리스트로 변환
	 */
	private List<Map<String, Object>> buildRankingList(Map<String, Integer> scores) {
		if (scores == null || scores.isEmpty()) {
			return List.of();
		}

		List<Map.Entry<String, Integer>> sorted = scores.entrySet().stream()
				.sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
				.toList();

		List<Map<String, Object>> ranking = new ArrayList<>();
		for (int i = 0; i < sorted.size(); i++) {
			Map<String, Object> entry = new HashMap<>();
			entry.put("rank", i + 1);
			entry.put("userId", sorted.get(i).getKey());
			entry.put("score", sorted.get(i).getValue());
			ranking.add(entry);
		}
		return ranking;
	}

	/**
	 * 게임 종료 알림 발행
	 */
	private void publishGameEndNotifications(GameSession session, String roomId) {
		if (session.getScores() == null || session.getScores().isEmpty()) {
			return;
		}

		List<Map.Entry<String, Integer>> sorted = session.getScores().entrySet().stream()
				.sorted((a, b) -> b.getValue().compareTo(a.getValue()))
				.toList();

		int totalPlayers = sorted.size();

		for (int i = 0; i < sorted.size(); i++) {
			int rank = i + 1;
			String userId = sorted.get(i).getKey();
			int score = sorted.get(i).getValue();
			boolean isWinner = rank == 1;

			notificationPublisher.publishGameEnd(
					userId,
					roomId,
					session.getGameSessionId(),
					rank,
					totalPlayers,
					score,
					isWinner
			);
		}
	}

	// ========== Result DTOs ==========
	
	public record GameStartResult(
			boolean success,
			String error,
			GameSession session,
			Word firstWord,
			List<String> drawerOrder
	) {
		public static GameStartResult success(GameSession session, Word word, List<String> order) {
			return new GameStartResult(true, null, session, word, order);
		}
		
		public static GameStartResult error(String message) {
			return new GameStartResult(false, message, null, null, null);
		}
	}
	
	public record AnswerCheckResult(
			boolean correct,
			boolean drawer,
			boolean alreadyGuessed,
			boolean gameNotActive,
			boolean allCorrect,
			int score,
			long elapsedTime,
			Map<String, Integer> scores
	) {
		public static AnswerCheckResult correctAnswer(int score, long elapsed, boolean allCorrect, Map<String, Integer> scores) {
			return new AnswerCheckResult(true, false, false, false, allCorrect, score, elapsed, scores);
		}
		
		public static AnswerCheckResult wrongAnswer() {
			return new AnswerCheckResult(false, false, false, false, false, 0, 0, null);
		}
		
		public static AnswerCheckResult drawerCannotGuess() {
			return new AnswerCheckResult(false, true, false, false, false, 0, 0, null);
		}
		
		public static AnswerCheckResult alreadyGuessedCorrect() {
			return new AnswerCheckResult(false, false, true, false, false, 0, 0, null);
		}
		
		public static AnswerCheckResult gameNotPlaying() {
			return new AnswerCheckResult(false, false, false, true, false, 0, 0, null);
		}
	}
}
