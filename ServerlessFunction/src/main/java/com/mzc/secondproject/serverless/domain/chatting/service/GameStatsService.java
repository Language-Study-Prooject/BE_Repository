package com.mzc.secondproject.serverless.domain.chatting.service;

import com.mzc.secondproject.serverless.domain.badge.model.UserBadge;
import com.mzc.secondproject.serverless.domain.badge.service.BadgeService;
import com.mzc.secondproject.serverless.domain.chatting.model.ChatRoom;
import com.mzc.secondproject.serverless.domain.chatting.model.GameRound;
import com.mzc.secondproject.serverless.domain.chatting.repository.GameRoundRepository;
import com.mzc.secondproject.serverless.domain.stats.model.UserStats;
import com.mzc.secondproject.serverless.domain.stats.repository.UserStatsRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * 게임 통계 및 뱃지 연동 서비스
 */
public class GameStatsService {
	
	private static final Logger logger = LoggerFactory.getLogger(GameStatsService.class);
	private static final long QUICK_GUESS_THRESHOLD_MS = 5000; // 5초
	
	private final UserStatsRepository userStatsRepository;
	private final GameRoundRepository gameRoundRepository;
	private final BadgeService badgeService;
	
	public GameStatsService() {
		this.userStatsRepository = new UserStatsRepository();
		this.gameRoundRepository = new GameRoundRepository();
		this.badgeService = new BadgeService();
	}
	
	/**
	 * 게임 종료 시 모든 참가자 통계 업데이트
	 */
	public Map<String, List<UserBadge>> updateGameStats(ChatRoom room) {
		Map<String, List<UserBadge>> newBadges = new HashMap<>();
		String roomId = room.getRoomId();
		
		// 모든 라운드 조회
		List<GameRound> rounds = gameRoundRepository.findByRoomId(roomId);
		
		// 참가자별 통계 수집
		Map<String, Integer> scores = room.getScores() != null ? room.getScores() : Map.of();
		Set<String> participants = new HashSet<>(scores.keySet());
		if (room.getDrawerOrder() != null) {
			participants.addAll(room.getDrawerOrder());
		}
		
		// 1등 찾기
		String winner = findWinner(scores);
		
		// 각 참가자 통계 업데이트
		for (String userId : participants) {
			List<UserBadge> badges = updateUserGameStats(userId, scores.getOrDefault(userId, 0),
					userId.equals(winner), rounds);
			if (!badges.isEmpty()) {
				newBadges.put(userId, badges);
			}
		}
		
		logger.info("Game stats updated: roomId={}, participants={}, winner={}",
				roomId, participants.size(), winner);
		
		return newBadges;
	}
	
	/**
	 * 개별 사용자 게임 통계 업데이트
	 */
	private List<UserBadge> updateUserGameStats(String userId, int score, boolean isWinner,
	                                            List<GameRound> rounds) {
		// 라운드별 통계 수집
		int correctGuesses = 0;
		int quickGuesses = 0;
		int perfectDraws = 0;
		
		for (GameRound round : rounds) {
			// 정답 횟수
			if (round.getCorrectGuessers() != null && round.getCorrectGuessers().contains(userId)) {
				correctGuesses++;
				
				// 빠른 정답 체크 (5초 이내)
				if (round.getGuessTimes() != null) {
					Long guessTime = round.getGuessTimes().get(userId);
					if (guessTime != null && guessTime <= QUICK_GUESS_THRESHOLD_MS) {
						quickGuesses++;
					}
				}
			}
			
			// 완벽한 출제자 체크
			if (userId.equals(round.getDrawerId())) {
				// 출제자일 때 전원 정답인지 확인
				if (round.getEndReason() != null && "ALL_CORRECT".equals(round.getEndReason())) {
					perfectDraws++;
				}
			}
		}
		
		// Atomic 업데이트
		userStatsRepository.incrementGameStats(
				userId,
				1, // gamesPlayed
				isWinner ? 1 : 0, // gamesWon
				correctGuesses,
				score,
				quickGuesses,
				perfectDraws
		);
		
		// 뱃지 체크를 위해 업데이트된 통계 조회
		UserStats stats = userStatsRepository.findTotalStats(userId).orElse(null);
		List<UserBadge> newBadges = badgeService.checkAndAwardBadges(userId, stats);
		
		logger.info("User game stats updated: userId={}, correctGuesses={}, newBadges={}",
				userId, correctGuesses, newBadges.size());
		
		return newBadges;
	}
	
	/**
	 * 정답 시 즉시 통계 업데이트 (빠른 정답 뱃지용)
	 */
	public List<UserBadge> updateOnCorrectAnswer(String userId, long elapsedTimeMs) {
		if (elapsedTimeMs > QUICK_GUESS_THRESHOLD_MS) {
			return List.of();
		}
		
		// 빠른 정답만 업데이트
		userStatsRepository.incrementGameStats(userId, 0, 0, 0, 0, 1, 0);
		
		UserStats stats = userStatsRepository.findTotalStats(userId).orElse(null);
		return badgeService.checkAndAwardBadges(userId, stats);
	}
	
	private String findWinner(Map<String, Integer> scores) {
		if (scores == null || scores.isEmpty()) {
			return null;
		}
		return scores.entrySet().stream()
				.max(Map.Entry.comparingByValue())
				.map(Map.Entry::getKey)
				.orElse(null);
	}
}
