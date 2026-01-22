package com.mzc.secondproject.serverless.domain.badge.service;

import com.mzc.secondproject.serverless.common.util.S3PresignUtil;
import com.mzc.secondproject.serverless.domain.badge.constants.BadgeKey;
import com.mzc.secondproject.serverless.domain.badge.enums.BadgeType;
import com.mzc.secondproject.serverless.domain.badge.model.UserBadge;
import com.mzc.secondproject.serverless.domain.badge.repository.BadgeRepository;
import com.mzc.secondproject.serverless.domain.badge.strategy.BadgeConditionStrategy;
import com.mzc.secondproject.serverless.domain.badge.strategy.BadgeConditionStrategyFactory;
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
	
	/**
	 * 기본 생성자 (Lambda에서 사용)
	 */
	public BadgeService() {
		this(new BadgeRepository(), new UserStatsRepository());
	}
	
	/**
	 * 의존성 주입 생성자 (테스트 용이성)
	 */
	public BadgeService(BadgeRepository badgeRepository, UserStatsRepository userStatsRepository) {
		this.badgeRepository = badgeRepository;
		this.userStatsRepository = userStatsRepository;
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
		
		BadgeConditionStrategy strategy = BadgeConditionStrategyFactory.getStrategy(type.getCategory());
		return strategy.checkCondition(type, stats);
	}
	
	private int calculateProgress(BadgeType type, UserStats stats) {
		if (stats == null) return 0;
		
		BadgeConditionStrategy strategy = BadgeConditionStrategyFactory.getStrategy(type.getCategory());
		return strategy.calculateProgress(type, stats);
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
