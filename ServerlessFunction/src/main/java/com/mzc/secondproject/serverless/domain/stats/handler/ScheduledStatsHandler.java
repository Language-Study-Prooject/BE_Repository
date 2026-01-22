package com.mzc.secondproject.serverless.domain.stats.handler;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.ScheduledEvent;
import com.mzc.secondproject.serverless.common.config.AwsClients;
import com.mzc.secondproject.serverless.common.config.EnvConfig;
import com.mzc.secondproject.serverless.domain.stats.repository.UserStatsRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.ScanRequest;
import software.amazon.awssdk.services.dynamodb.model.ScanResponse;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * EventBridge Scheduler Handler
 * 매일 자정에 실행되어 Streak 리셋만 수행
 * <p>
 * 단어 학습 통계는 Write-through 방식으로 markWordLearned에서 직접 업데이트
 */
public class ScheduledStatsHandler implements RequestHandler<ScheduledEvent, String> {
	
	private static final Logger logger = LoggerFactory.getLogger(ScheduledStatsHandler.class);
	private static final String TABLE_NAME = EnvConfig.getRequired("VOCAB_TABLE_NAME");
	private static final int BATCH_SIZE = 25;
	
	private final UserStatsRepository userStatsRepository;
	
	/**
	 * 기본 생성자 (Lambda에서 사용)
	 */
	public ScheduledStatsHandler() {
		this(new UserStatsRepository());
	}
	
	/**
	 * 의존성 주입 생성자 (테스트 용이성)
	 */
	public ScheduledStatsHandler(UserStatsRepository userStatsRepository) {
		this.userStatsRepository = userStatsRepository;
	}
	
	@Override
	public String handleRequest(ScheduledEvent event, Context context) {
		logger.info("Scheduled streak check started: {}", event.getTime());
		
		try {
			String yesterday = LocalDate.now().minusDays(1).toString();
			int resetCount = checkAndResetStreaks(yesterday);
			
			logger.info("Scheduled streak check completed: {} streaks reset", resetCount);
			return "SUCCESS: " + resetCount + " streaks reset";
		} catch (Exception e) {
			logger.error("Scheduled streak check failed", e);
			return "FAILED: " + e.getMessage();
		}
	}
	
	/**
	 * Streak 체크 및 리셋
	 * TOTAL 통계 레코드 중 lastStudyDate가 어제가 아니고 currentStreak > 0인 사용자의 streak을 리셋
	 */
	private int checkAndResetStreaks(String yesterday) {
		logger.info("Checking streaks for date: {}", yesterday);
		
		int resetCount = 0;
		Map<String, AttributeValue> lastEvaluatedKey = null;
		
		do {
			// SK = "STATS#TOTAL"인 레코드만 스캔 (currentStreak > 0 필터)
			ScanRequest.Builder scanBuilder = ScanRequest.builder()
					.tableName(TABLE_NAME)
					.filterExpression("SK = :sk AND currentStreak > :zero AND (attribute_not_exists(lastStudyDate) OR lastStudyDate <> :yesterday)")
					.expressionAttributeValues(Map.of(
							":sk", AttributeValue.builder().s("STATS#TOTAL").build(),
							":zero", AttributeValue.builder().n("0").build(),
							":yesterday", AttributeValue.builder().s(yesterday).build()
					))
					.limit(BATCH_SIZE);
			
			if (lastEvaluatedKey != null) {
				scanBuilder.exclusiveStartKey(lastEvaluatedKey);
			}
			
			ScanResponse response = AwsClients.dynamoDb().scan(scanBuilder.build());
			List<Map<String, AttributeValue>> items = response.items();
			
			for (Map<String, AttributeValue> item : items) {
				String pk = item.get("PK").s();
				// PK 형식: "USERSTATS#{userId}" 에서 userId 추출
				if (pk != null && pk.startsWith("USERSTATS#")) {
					String userId = pk.substring("USERSTATS#".length());
					try {
						resetUserStreak(userId);
						resetCount++;
						logger.debug("Reset streak for user: {}", userId);
					} catch (Exception e) {
						logger.warn("Failed to reset streak for user {}: {}", userId, e.getMessage());
					}
				}
			}
			
			lastEvaluatedKey = response.lastEvaluatedKey();
		} while (lastEvaluatedKey != null && !lastEvaluatedKey.isEmpty());
		
		logger.info("Streak reset completed: {} users processed", resetCount);
		return resetCount;
	}
	
	/**
	 * 사용자의 currentStreak을 0으로 리셋 (longestStreak은 유지)
	 */
	private void resetUserStreak(String userId) {
		userStatsRepository.updateStreak(userId, 0,
				getCurrentLongestStreak(userId),
				LocalDate.now().minusDays(1).toString());
	}
	
	/**
	 * 사용자의 현재 longestStreak 조회
	 */
	private int getCurrentLongestStreak(String userId) {
		return userStatsRepository.findTotalStats(userId)
				.map(stats -> stats.getLongestStreak() != null ? stats.getLongestStreak() : 0)
				.orElse(0);
	}
}
