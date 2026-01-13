package com.mzc.secondproject.serverless.domain.stats.handler;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.DynamodbEvent;
import com.amazonaws.services.lambda.runtime.events.models.dynamodb.AttributeValue;
import com.mzc.secondproject.serverless.domain.badge.model.UserBadge;
import com.mzc.secondproject.serverless.domain.badge.service.BadgeService;
import com.mzc.secondproject.serverless.domain.stats.model.UserStats;
import com.mzc.secondproject.serverless.domain.stats.repository.UserStatsRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * DynamoDB Streams Handler for Stats Aggregation
 * TestResult 저장 시 자동으로 통계 업데이트
 */
public class StatsStreamHandler implements RequestHandler<DynamodbEvent, Void> {
	
	private static final Logger logger = LoggerFactory.getLogger(StatsStreamHandler.class);
	
	private final UserStatsRepository userStatsRepository;
	private final BadgeService badgeService;
	
	public StatsStreamHandler() {
		this.userStatsRepository = new UserStatsRepository();
		this.badgeService = new BadgeService();
	}
	
	@Override
	public Void handleRequest(DynamodbEvent event, Context context) {
		logger.info("Received {} DynamoDB Stream records", event.getRecords().size());
		
		for (DynamodbEvent.DynamodbStreamRecord record : event.getRecords()) {
			try {
				processRecord(record);
			} catch (Exception e) {
				logger.error("Failed to process record: {}", record.getEventID(), e);
			}
		}
		
		return null;
	}
	
	private void processRecord(DynamodbEvent.DynamodbStreamRecord record) {
		String eventName = record.getEventName();
		
		// INSERT 이벤트만 처리 (새로운 테스트 결과)
		if (!"INSERT".equals(eventName)) {
			return;
		}
		
		Map<String, AttributeValue> newImage = record.getDynamodb().getNewImage();
		if (newImage == null) {
			return;
		}
		
		String pk = getStringValue(newImage, "PK");
		String sk = getStringValue(newImage, "SK");
		
		if (pk == null || sk == null) {
			return;
		}
		
		// TestResult 레코드 확인: PK=TEST#{userId}, SK=RESULT#{timestamp}
		if (pk.startsWith("TEST#") && sk.startsWith("RESULT#")) {
			processTestResultInsert(newImage);
		}
	}
	
	private void processTestResultInsert(Map<String, AttributeValue> newImage) {
		String userId = getStringValue(newImage, "userId");
		Integer correctAnswers = getNumberValue(newImage, "correctAnswers");
		Integer incorrectAnswers = getNumberValue(newImage, "incorrectAnswers");
		String testId = getStringValue(newImage, "testId");
		
		if (userId == null || correctAnswers == null || incorrectAnswers == null) {
			logger.warn("Missing required fields in TestResult record");
			return;
		}
		
		logger.info("Processing TestResult: userId={}, testId={}, correct={}, incorrect={}",
				userId, testId, correctAnswers, incorrectAnswers);
		
		// 통계 업데이트
		userStatsRepository.incrementTestStats(userId, correctAnswers, incorrectAnswers);
		
		// Streak 업데이트
		updateStudyStreak(userId);
		
		logger.info("Stats updated for user: {}", userId);
		
		// 뱃지 체크 및 부여
		checkAndAwardBadges(userId, correctAnswers, incorrectAnswers);
	}
	
	private void checkAndAwardBadges(String userId, int correctAnswers, int incorrectAnswers) {
		try {
			Optional<UserStats> totalStats = userStatsRepository.findTotalStats(userId);
			if (totalStats.isEmpty()) {
				return;
			}
			
			// 만점 뱃지 체크 (이번 테스트가 만점인 경우)
			if (incorrectAnswers == 0 && correctAnswers > 0) {
				badgeService.awardBadge(userId, "PERFECT_SCORE");
				logger.info("Perfect score badge awarded to user: {}", userId);
			}
			
			// 기타 뱃지 체크
			List<UserBadge> newBadges = badgeService.checkAndAwardBadges(userId, totalStats.get());
			if (!newBadges.isEmpty()) {
				logger.info("Awarded {} new badges to user: {}", newBadges.size(), userId);
			}
		} catch (Exception e) {
			logger.error("Failed to check badges for user: {}", userId, e);
		}
	}
	
	private void updateStudyStreak(String userId) {
		String today = LocalDate.now().toString();
		
		Optional<com.mzc.secondproject.serverless.domain.stats.model.UserStats> totalStats =
				userStatsRepository.findTotalStats(userId);
		
		int currentStreak = 1;
		int longestStreak = 1;
		
		if (totalStats.isPresent()) {
			var stats = totalStats.get();
			String lastStudyDate = stats.getLastStudyDate();
			
			if (lastStudyDate != null) {
				LocalDate lastDate = LocalDate.parse(lastStudyDate);
				LocalDate todayDate = LocalDate.now();
				
				long daysDiff = todayDate.toEpochDay() - lastDate.toEpochDay();
				
				if (daysDiff == 0) {
					// 오늘 이미 학습 - streak 유지
					return;
				} else if (daysDiff == 1) {
					// 어제 학습 - streak 증가
					currentStreak = (stats.getCurrentStreak() != null ? stats.getCurrentStreak() : 0) + 1;
				} else {
					// 연속 학습 끊김
					currentStreak = 1;
				}
			}
			
			longestStreak = stats.getLongestStreak() != null ?
					Math.max(stats.getLongestStreak(), currentStreak) : currentStreak;
		}
		
		userStatsRepository.updateStreak(userId, currentStreak, longestStreak, today);
	}
	
	private String getStringValue(Map<String, AttributeValue> item, String key) {
		AttributeValue value = item.get(key);
		return value != null ? value.getS() : null;
	}
	
	private Integer getNumberValue(Map<String, AttributeValue> item, String key) {
		AttributeValue value = item.get(key);
		if (value != null && value.getN() != null) {
			return Integer.parseInt(value.getN());
		}
		return null;
	}
}
