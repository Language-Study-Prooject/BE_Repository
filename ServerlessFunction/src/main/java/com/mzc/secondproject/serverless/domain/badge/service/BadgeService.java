package com.mzc.secondproject.serverless.domain.badge.service;

import com.mzc.secondproject.serverless.common.util.S3PresignUtil;
import com.mzc.secondproject.serverless.domain.badge.constants.BadgeKey;
import com.mzc.secondproject.serverless.domain.badge.enums.BadgeType;
import com.mzc.secondproject.serverless.domain.badge.model.UserBadge;
import com.mzc.secondproject.serverless.domain.badge.repository.BadgeRepository;
import com.mzc.secondproject.serverless.domain.stats.model.UserStats;
import com.mzc.secondproject.serverless.domain.stats.repository.UserStatsRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class BadgeService {
	
	private static final Logger logger = LoggerFactory.getLogger(BadgeService.class);
	
	private final BadgeRepository badgeRepository;
	private final UserStatsRepository userStatsRepository;
	
	public BadgeService() {
		this.badgeRepository = new BadgeRepository();
		this.userStatsRepository = new UserStatsRepository();
	}
	
	/**
	 * 사용자의 획득한 뱃지 목록 조회
	 */
	public List<UserBadge> getUserBadges(String userId) {
		return badgeRepository.findByUserId(userId);
	}
	
	/**
	 * 전체 뱃지 목록 조회 (획득 여부 포함)
	 */
	public List<BadgeInfo> getAllBadgesWithStatus(String userId) {
		List<UserBadge> earnedBadges = badgeRepository.findByUserId(userId);
		Map<String, UserBadge> earnedMap = earnedBadges.stream()
				.collect(Collectors.toMap(UserBadge::getBadgeType, b -> b));
		
		UserStats totalStats = userStatsRepository.findTotalStats(userId).orElse(null);
		
		List<BadgeInfo> result = new ArrayList<>();
		for (BadgeType type : BadgeType.values()) {
			UserBadge earned = earnedMap.get(type.name());
			int currentProgress = calculateProgress(type, totalStats);
			
			result.add(new BadgeInfo(
					type.name(),
					type.getName(),
					type.getDescription(),
					S3PresignUtil.getBadgeImageUrl(type.getImageFile()),
					type.getCategory(),
					type.getThreshold(),
					currentProgress,
					earned != null,
					earned != null ? earned.getEarnedAt() : null
			));
		}
		
		return result;
	}
	
	/**
	 * 뱃지 획득 체크 및 부여
	 * 통계가 업데이트될 때 호출
	 */
	public List<UserBadge> checkAndAwardBadges(String userId, UserStats stats) {
		List<UserBadge> newBadges = new ArrayList<>();
		String now = Instant.now().toString();
		
		for (BadgeType type : BadgeType.values()) {
			// 이미 획득한 뱃지는 스킵
			if (badgeRepository.hasBadge(userId, type.name())) {
				continue;
			}
			
			// 조건 체크
			if (checkBadgeCondition(type, stats)) {
				UserBadge badge = createBadge(userId, type, now);
				badgeRepository.save(badge);
				newBadges.add(badge);
				logger.info("Badge awarded: userId={}, badge={}", userId, type.name());
			}
		}
		
		return newBadges;
	}
	
	/**
	 * 특정 뱃지 수동 부여 (테스트/관리용)
	 */
	public UserBadge awardBadge(String userId, String badgeType) {
		BadgeType type = BadgeType.fromString(badgeType);
		if (type == null) {
			throw new IllegalArgumentException("Invalid badge type: " + badgeType);
		}
		
		if (badgeRepository.hasBadge(userId, type.name())) {
			return badgeRepository.findByUserIdAndBadgeType(userId, type.name()).orElse(null);
		}
		
		String now = Instant.now().toString();
		UserBadge badge = createBadge(userId, type, now);
		badgeRepository.save(badge);
		
		return badge;
	}
	
	private UserBadge createBadge(String userId, BadgeType type, String now) {
		return UserBadge.builder()
				.pk(BadgeKey.userBadgePk(userId))
				.sk(BadgeKey.badgeSk(type.name()))
				.gsi1pk(BadgeKey.BADGE_ALL)
				.gsi1sk(BadgeKey.earnedSk(now))
				.odUserId(userId)
				.badgeType(type.name())
				.name(type.getName())
				.description(type.getDescription())
				.imageUrl(S3PresignUtil.getBadgeImageUrl(type.getImageFile()))
				.category(type.getCategory())
				.threshold(type.getThreshold())
				.earnedAt(now)
				.createdAt(now)
				.build();
	}
	
	private boolean checkBadgeCondition(BadgeType type, UserStats stats) {
		if (stats == null) return false;

		return switch (type.getCategory()) {
			case "FIRST_STUDY" -> stats.getTestsCompleted() != null && stats.getTestsCompleted() >= 1;
			case "STREAK" -> stats.getCurrentStreak() != null && stats.getCurrentStreak() >= type.getThreshold();
			case "WORDS_LEARNED" -> {
				int total = (stats.getNewWordsLearned() != null ? stats.getNewWordsLearned() : 0)
						+ (stats.getWordsReviewed() != null ? stats.getWordsReviewed() : 0);
				yield total >= type.getThreshold();
			}
			case "PERFECT_TEST" -> false; // 별도 로직 필요 (테스트 결과에서 체크)
			case "TESTS_COMPLETED" ->
					stats.getTestsCompleted() != null && stats.getTestsCompleted() >= type.getThreshold();
			case "ACCURACY" -> {
				if (stats.getQuestionsAnswered() == null || stats.getQuestionsAnswered() == 0) yield false;
				double accuracy = (stats.getCorrectAnswers() * 100.0) / stats.getQuestionsAnswered();
				yield accuracy >= type.getThreshold();
			}
			case "GAMES_PLAYED" ->
					stats.getGamesPlayed() != null && stats.getGamesPlayed() >= type.getThreshold();
			case "GAMES_WON" ->
					stats.getGamesWon() != null && stats.getGamesWon() >= type.getThreshold();
			case "QUICK_GUESSES" ->
					stats.getQuickGuesses() != null && stats.getQuickGuesses() >= type.getThreshold();
			case "PERFECT_DRAWS" ->
					stats.getPerfectDraws() != null && stats.getPerfectDraws() >= type.getThreshold();
			case "ALL_BADGES" -> false; // 별도 로직 필요
			default -> false;
		};
	}
	
	private int calculateProgress(BadgeType type, UserStats stats) {
		if (stats == null) return 0;

		return switch (type.getCategory()) {
			case "FIRST_STUDY" -> stats.getTestsCompleted() != null && stats.getTestsCompleted() >= 1 ? 1 : 0;
			case "STREAK" -> stats.getCurrentStreak() != null ? stats.getCurrentStreak() : 0;
			case "WORDS_LEARNED" -> (stats.getNewWordsLearned() != null ? stats.getNewWordsLearned() : 0)
					+ (stats.getWordsReviewed() != null ? stats.getWordsReviewed() : 0);
			case "TESTS_COMPLETED" -> stats.getTestsCompleted() != null ? stats.getTestsCompleted() : 0;
			case "ACCURACY" -> {
				if (stats.getQuestionsAnswered() == null || stats.getQuestionsAnswered() == 0) yield 0;
				yield (int) ((stats.getCorrectAnswers() * 100.0) / stats.getQuestionsAnswered());
			}
			case "GAMES_PLAYED" -> stats.getGamesPlayed() != null ? stats.getGamesPlayed() : 0;
			case "GAMES_WON" -> stats.getGamesWon() != null ? stats.getGamesWon() : 0;
			case "QUICK_GUESSES" -> stats.getQuickGuesses() != null ? stats.getQuickGuesses() : 0;
			case "PERFECT_DRAWS" -> stats.getPerfectDraws() != null ? stats.getPerfectDraws() : 0;
			default -> 0;
		};
	}
	
	public record BadgeInfo(
			String badgeType,
			String name,
			String description,
			String imageUrl,
			String category,
			int threshold,
			int progress,
			boolean earned,
			String earnedAt
	) {
	}
}
