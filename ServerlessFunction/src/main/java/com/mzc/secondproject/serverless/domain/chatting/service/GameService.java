package com.mzc.secondproject.serverless.domain.chatting.service;

import com.mzc.secondproject.serverless.common.dto.PaginatedResult;
import com.mzc.secondproject.serverless.domain.chatting.config.GameConfig;
import com.mzc.secondproject.serverless.domain.chatting.dto.response.CommandResult;
import com.mzc.secondproject.serverless.domain.chatting.enums.GameStatus;
import com.mzc.secondproject.serverless.domain.chatting.enums.MessageType;
import com.mzc.secondproject.serverless.domain.chatting.model.ChatRoom;
import com.mzc.secondproject.serverless.domain.chatting.model.Connection;
import com.mzc.secondproject.serverless.domain.chatting.model.GameRound;
import com.mzc.secondproject.serverless.domain.chatting.repository.ChatRoomRepository;
import com.mzc.secondproject.serverless.domain.chatting.repository.ConnectionRepository;
import com.mzc.secondproject.serverless.domain.chatting.repository.GameRoundRepository;
import com.mzc.secondproject.serverless.domain.vocabulary.model.Word;
import com.mzc.secondproject.serverless.domain.vocabulary.repository.WordRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 캐치마인드 게임 로직 서비스
 */
public class GameService {
	
	private static final Logger logger = LoggerFactory.getLogger(GameService.class);
	
	private final ChatRoomRepository chatRoomRepository;
	private final ConnectionRepository connectionRepository;
	private final GameRoundRepository gameRoundRepository;
	private final WordRepository wordRepository;
	private final GameStatsService gameStatsService;
	
	/**
	 * 기본 생성자 (Lambda에서 사용)
	 */
	public GameService() {
		this(new ChatRoomRepository(), new ConnectionRepository(),
				new GameRoundRepository(), new WordRepository(), new GameStatsService());
	}
	
	/**
	 * 의존성 주입 생성자 (테스트 용이성)
	 */
	public GameService(ChatRoomRepository chatRoomRepository, ConnectionRepository connectionRepository,
	                   GameRoundRepository gameRoundRepository, WordRepository wordRepository,
	                   GameStatsService gameStatsService) {
		this.chatRoomRepository = chatRoomRepository;
		this.connectionRepository = connectionRepository;
		this.gameRoundRepository = gameRoundRepository;
		this.wordRepository = wordRepository;
		this.gameStatsService = gameStatsService;
	}
	
	/**
	 * 게임 시작
	 */
	public GameStartResult startGame(String roomId, String userId) {
		ChatRoom room = chatRoomRepository.findById(roomId)
				.orElseThrow(() -> new IllegalArgumentException("채팅방을 찾을 수 없습니다."));
		
		// 이미 게임 중인지 확인
		GameStatus currentStatus = GameStatus.fromString(room.getGameStatus());
		if (!currentStatus.canStartGame()) {
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
		
		// 게임 상태 업데이트
		room.setGameStatus(GameStatus.PLAYING.name());
		room.setGameStartedBy(userId);
		room.setCurrentRound(1);
		room.setTotalRounds(GameConfig.totalRounds());
		room.setDrawerOrder(drawerOrder);
		room.setScores(new HashMap<>());
		room.setStreaks(new HashMap<>());
		room.setRoundTimeLimit(GameConfig.roundTimeLimit());
		
		// 첫 라운드 설정
		String firstDrawer = drawerOrder.get(0);
		Word firstWord = words.get(0);
		room.setCurrentDrawerId(firstDrawer);
		room.setCurrentWordId(firstWord.getWordId());
		room.setCurrentWord(firstWord.getKorean());
		room.setRoundStartTime(System.currentTimeMillis());
		room.setHintUsed(false);
		room.setCorrectGuessers(new ArrayList<>());
		
		chatRoomRepository.save(room);
		
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
				.startTime(System.currentTimeMillis())
				.hintUsed(false)
				.correctGuessers(new ArrayList<>())
				.guessTimes(new HashMap<>())
				.roundScores(new HashMap<>())
				.createdAt(Instant.now().toString())
				.ttl(ttlSeconds)
				.build();
		
		gameRoundRepository.save(firstRound);
		
		logger.info("Game started: roomId={}, starter={}, rounds={}", roomId, userId, GameConfig.totalRounds());
		
		return GameStartResult.success(room, firstWord, drawerOrder);
	}
	
	/**
	 * 게임 종료
	 */
	public CommandResult stopGame(String roomId, String userId) {
		ChatRoom room = chatRoomRepository.findById(roomId)
				.orElseThrow(() -> new IllegalArgumentException("채팅방을 찾을 수 없습니다."));
		
		GameStatus currentStatus = GameStatus.fromString(room.getGameStatus());
		if (!currentStatus.isGameActive()) {
			return CommandResult.error("진행 중인 게임이 없습니다.");
		}
		
		// 권한 확인
		boolean isOwner = userId.equals(room.getCreatedBy());
		boolean isGameStarter = userId.equals(room.getGameStartedBy());
		
		if (!isOwner && !isGameStarter) {
			return CommandResult.error("게임을 중단할 권한이 없습니다.");
		}
		
		// 게임 종료 처리
		return finishGame(room, "STOPPED");
	}
	
	/**
	 * 정답 체크
	 */
	public AnswerCheckResult checkAnswer(String roomId, String userId, String answer) {
		ChatRoom room = chatRoomRepository.findById(roomId)
				.orElseThrow(() -> new IllegalArgumentException("채팅방을 찾을 수 없습니다."));
		
		// 게임 진행 중인지 확인
		if (!GameStatus.PLAYING.name().equals(room.getGameStatus())) {
			return AnswerCheckResult.gameNotPlaying();
		}
		
		// 출제자는 정답 체크 제외
		if (userId.equals(room.getCurrentDrawerId())) {
			return AnswerCheckResult.drawerCannotGuess();
		}
		
		// 이미 맞춘 사람인지 확인
		if (room.getCorrectGuessers() != null && room.getCorrectGuessers().contains(userId)) {
			return AnswerCheckResult.alreadyGuessedCorrect();
		}
		
		// 정답 체크
		String currentWord = room.getCurrentWord();
		if (!isCorrectAnswer(answer, currentWord)) {
			return AnswerCheckResult.wrongAnswer();
		}
		
		// 정답 처리
		long elapsedTime = System.currentTimeMillis() - room.getRoundStartTime();
		
		// 연속 정답 업데이트 (점수 계산 전에)
		if (room.getStreaks() == null) {
			room.setStreaks(new HashMap<>());
		}
		int currentStreak = room.getStreaks().getOrDefault(userId, 0) + 1;
		room.getStreaks().put(userId, currentStreak);
		
		int score = calculateScore(room, elapsedTime, userId, currentStreak);
		
		// 정답자 목록에 추가
		if (room.getCorrectGuessers() == null) {
			room.setCorrectGuessers(new ArrayList<>());
		}
		room.getCorrectGuessers().add(userId);
		
		// 점수 업데이트
		if (room.getScores() == null) {
			room.setScores(new HashMap<>());
		}
		room.getScores().merge(userId, score, Integer::sum);
		
		// 출제자 점수도 추가
		room.getScores().merge(room.getCurrentDrawerId(), 5, Integer::sum);
		
		chatRoomRepository.save(room);
		
		// 라운드 기록 업데이트
		updateRoundRecord(roomId, room.getCurrentRound(), userId, elapsedTime, score);
		
		// 전원 정답 체크
		List<Connection> connections = connectionRepository.findByRoomId(roomId);
		int nonDrawerCount = (int) connections.stream()
				.filter(c -> !c.getUserId().equals(room.getCurrentDrawerId()))
				.count();
		
		boolean allCorrect = room.getCorrectGuessers().size() >= nonDrawerCount;
		
		logger.info("Answer correct: roomId={}, userId={}, score={}, allCorrect={}",
				roomId, userId, score, allCorrect);
		
		return AnswerCheckResult.correctAnswer(score, elapsedTime, allCorrect, room.getScores());
	}
	
	/**
	 * 라운드 스킵
	 */
	public CommandResult skipRound(String roomId, String userId) {
		ChatRoom room = chatRoomRepository.findById(roomId)
				.orElseThrow(() -> new IllegalArgumentException("채팅방을 찾을 수 없습니다."));
		
		if (!GameStatus.PLAYING.name().equals(room.getGameStatus())) {
			return CommandResult.error("게임이 진행 중이 아닙니다.");
		}
		
		if (!userId.equals(room.getCurrentDrawerId())) {
			return CommandResult.error("출제자만 라운드를 스킵할 수 있습니다.");
		}
		
		return endRound(room, "SKIP");
	}
	
	/**
	 * 힌트 제공
	 */
	public CommandResult provideHint(String roomId, String userId) {
		ChatRoom room = chatRoomRepository.findById(roomId)
				.orElseThrow(() -> new IllegalArgumentException("채팅방을 찾을 수 없습니다."));
		
		if (!GameStatus.PLAYING.name().equals(room.getGameStatus())) {
			return CommandResult.error("게임이 진행 중이 아닙니다.");
		}
		
		if (!userId.equals(room.getCurrentDrawerId())) {
			return CommandResult.error("출제자만 힌트를 제공할 수 있습니다.");
		}
		
		if (Boolean.TRUE.equals(room.getHintUsed())) {
			return CommandResult.error("이번 라운드에서 이미 힌트를 사용했습니다.");
		}
		
		String currentWord = room.getCurrentWord();
		String hint = currentWord.charAt(0) + "○".repeat(currentWord.length() - 1);
		
		room.setHintUsed(true);
		chatRoomRepository.save(room);
		
		// 라운드 기록 업데이트
		gameRoundRepository.findByRoomIdAndRound(roomId, room.getCurrentRound())
				.ifPresent(round -> {
					round.setHintUsed(true);
					gameRoundRepository.save(round);
				});
		
		return CommandResult.success(MessageType.HINT, "💡 힌트: " + hint);
	}
	
	/**
	 * 라운드 종료 처리
	 */
	public CommandResult endRound(ChatRoom room, String reason) {
		String roomId = room.getRoomId();
		Integer currentRound = room.getCurrentRound();
		String answer = room.getCurrentWord();
		
		// 정답 못 맞춘 사용자 연속 정답 초기화
		resetStreaksForNonGuessers(room);
		
		// 라운드 기록 종료
		gameRoundRepository.findByRoomIdAndRound(roomId, currentRound)
				.ifPresent(round -> {
					round.setEndTime(System.currentTimeMillis());
					round.setEndReason(reason);
					gameRoundRepository.save(round);
				});
		
		// 다음 라운드로 진행
		if (currentRound >= room.getTotalRounds()) {
			return finishGame(room, "COMPLETED");
		}
		
		// 현재 접속 중인 사용자 목록 조회
		List<Connection> connections = connectionRepository.findByRoomId(roomId);
		Set<String> connectedUserIds = connections.stream()
				.map(Connection::getUserId)
				.collect(Collectors.toSet());
		
		// 접속자가 2명 미만이면 게임 종료
		if (connectedUserIds.size() < 2) {
			return finishGame(room, "NOT_ENOUGH_PLAYERS");
		}
		
		// 다음 라운드 준비 - 접속 중인 사용자 중에서만 출제자 선택
		int nextRound = currentRound + 1;
		String nextDrawer = selectNextDrawer(room.getDrawerOrder(), connectedUserIds, nextRound);
		
		// 다음 단어 추출
		String level = room.getLevel() != null ? room.getLevel() : "beginner";
		List<Word> words = getRandomWords(level, 1);
		if (words.isEmpty()) {
			return finishGame(room, "NO_WORDS");
		}
		Word nextWord = words.get(0);
		
		// 상태 업데이트
		room.setCurrentRound(nextRound);
		room.setCurrentDrawerId(nextDrawer);
		room.setCurrentWordId(nextWord.getWordId());
		room.setCurrentWord(nextWord.getKorean());
		room.setRoundStartTime(System.currentTimeMillis());
		room.setHintUsed(false);
		room.setCorrectGuessers(new ArrayList<>());
		
		chatRoomRepository.save(room);
		
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
				.startTime(System.currentTimeMillis())
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
		List<Map<String, Object>> ranking = buildRankingList(room.getScores());
		
		Map<String, Object> data = new HashMap<>();
		data.put("answer", answer);
		data.put("nextRound", nextRound);
		data.put("nextDrawer", nextDrawer);
		data.put("nextWord", nextWord);
		data.put("ranking", ranking);
		data.put("currentRound", currentRound);
		data.put("totalRounds", room.getTotalRounds());
		// 타이머 동기화용 필드 추가
		data.put("roundStartTime", room.getRoundStartTime());
		data.put("roundDuration", room.getRoundTimeLimit() != null ? room.getRoundTimeLimit() : GameConfig.roundTimeLimit());

		return CommandResult.success(MessageType.ROUND_END, message, data);
	}
	
	/**
	 * 게임 완전 종료
	 */
	private CommandResult finishGame(ChatRoom room, String reason) {
		room.setGameStatus(GameStatus.FINISHED.name());
		chatRoomRepository.save(room);
		
		// 게임 통계 업데이트 및 뱃지 체크
		try {
			var newBadges = gameStatsService.updateGameStats(room);
			logger.info("Game stats updated: roomId={}, newBadges={}", room.getRoomId(), newBadges.size());
		} catch (Exception e) {
			logger.error("Failed to update game stats: roomId={}, error={}", room.getRoomId(), e.getMessage());
		}
		
		// 최종 점수 정렬
		StringBuilder sb = new StringBuilder("🎮 게임 종료!\n\n📊 최종 순위:\n");
		if (room.getScores() != null && !room.getScores().isEmpty()) {
			List<Map.Entry<String, Integer>> sorted = room.getScores().entrySet().stream()
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
		
		logger.info("Game finished: roomId={}, reason={}", room.getRoomId(), reason);
		
		return CommandResult.success(MessageType.GAME_END, sb.toString(), room.getScores());
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
	 */
	private List<Word> getRandomWords(String level, int count) {
		PaginatedResult<Word> result = wordRepository.findByLevelWithPagination(level, 50, null);
		List<Word> words = new ArrayList<>(result.items());
		Collections.shuffle(words);
		return words.stream().limit(count).collect(Collectors.toList());
	}
	
	/**
	 * 정답 체크 로직
	 */
	private boolean isCorrectAnswer(String input, String answer) {
		if (input == null || answer == null) return false;
		
		String normalizedInput = input.trim().toLowerCase().replace(" ", "");
		String normalizedAnswer = answer.trim().toLowerCase().replace(" ", "");
		
		return normalizedInput.equals(normalizedAnswer);
	}
	
	/**
	 * 점수 계산
	 *
	 * @param room          채팅방
	 * @param elapsedTimeMs 경과 시간 (밀리초)
	 * @param userId        사용자 ID
	 * @param streak        연속 정답 수
	 * @return 계산된 점수
	 */
	private int calculateScore(ChatRoom room, long elapsedTimeMs, String userId, int streak) {
		int baseScore = 10;
		
		// 시간 보너스 (빨리 맞출수록 높은 점수): (제한시간 - 경과시간) * 0.5
		int elapsedSeconds = (int) (elapsedTimeMs / 1000);
		int timeLimit = room.getRoundTimeLimit() != null ? room.getRoundTimeLimit() : GameConfig.roundTimeLimit();
		int timeBonus = Math.max(0, (int) ((timeLimit - elapsedSeconds) * 0.5));
		
		// 연속 정답 보너스: 연속정답수 * 2
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
	private void resetStreaksForNonGuessers(ChatRoom room) {
		if (room.getStreaks() == null || room.getStreaks().isEmpty()) {
			return;
		}
		
		List<String> correctGuessers = room.getCorrectGuessers() != null
				? room.getCorrectGuessers()
				: List.of();
		
		// 정답 못 맞춘 사용자의 연속 정답 초기화
		room.getStreaks().keySet().stream()
				.filter(userId -> !correctGuessers.contains(userId))
				.forEach(userId -> room.getStreaks().put(userId, 0));
		
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
	
	// ========== Result DTOs ==========
	
	public record GameStartResult(
			boolean success,
			String error,
			ChatRoom room,
			Word firstWord,
			List<String> drawerOrder
	) {
		public static GameStartResult success(ChatRoom room, Word word, List<String> order) {
			return new GameStartResult(true, null, room, word, order);
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
