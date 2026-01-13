package com.mzc.secondproject.serverless.domain.stats.handler;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.ScheduledEvent;
import com.mzc.secondproject.serverless.common.config.AwsClients;
import com.mzc.secondproject.serverless.domain.stats.repository.UserStatsRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.ScanRequest;
import software.amazon.awssdk.services.dynamodb.model.ScanResponse;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * EventBridge Scheduler Handler
 * 매일 자정에 실행되어 단어 학습 통계를 집계
 */
public class ScheduledStatsHandler implements RequestHandler<ScheduledEvent, String> {

    private static final Logger logger = LoggerFactory.getLogger(ScheduledStatsHandler.class);
    private static final String TABLE_NAME = System.getenv("VOCAB_TABLE_NAME");

    private final UserStatsRepository userStatsRepository;

    public ScheduledStatsHandler() {
        this.userStatsRepository = new UserStatsRepository();
    }

    @Override
    public String handleRequest(ScheduledEvent event, Context context) {
        logger.info("Scheduled stats aggregation started: {}", event.getTime());

        try {
            // 어제 날짜 기준으로 집계 (자정에 실행되므로)
            String yesterday = LocalDate.now().minusDays(1).toString();

            aggregateDailyWordStats(yesterday);
            checkAndResetStreaks(yesterday);

            logger.info("Scheduled stats aggregation completed for date: {}", yesterday);
            return "SUCCESS";
        } catch (Exception e) {
            logger.error("Scheduled stats aggregation failed", e);
            return "FAILED: " + e.getMessage();
        }
    }

    /**
     * 일별 단어 학습 통계 집계
     * DailyStudy 레코드에서 학습 완료된 단어 수를 집계
     */
    private void aggregateDailyWordStats(String date) {
        logger.info("Aggregating word stats for date: {}", date);

        // DailyStudy 레코드 스캔 (해당 날짜)
        Map<String, AttributeValue> expressionValues = new HashMap<>();
        expressionValues.put(":pk_prefix", AttributeValue.builder().s("DAILY#").build());
        expressionValues.put(":sk_date", AttributeValue.builder().s("DATE#" + date).build());

        ScanRequest scanRequest = ScanRequest.builder()
                .tableName(TABLE_NAME)
                .filterExpression("begins_with(PK, :pk_prefix) AND SK = :sk_date")
                .expressionAttributeValues(expressionValues)
                .build();

        ScanResponse response = AwsClients.dynamoDb().scan(scanRequest);

        Set<String> processedUsers = new HashSet<>();

        for (Map<String, AttributeValue> item : response.items()) {
            try {
                String pk = item.get("PK").s();
                String userId = pk.replace("DAILY#", "");

                if (processedUsers.contains(userId)) {
                    continue;
                }

                int learnedCount = getListSize(item, "learnedNewWordIds");
                int reviewedCount = getListSize(item, "learnedReviewWordIds");

                if (learnedCount > 0 || reviewedCount > 0) {
                    userStatsRepository.incrementWordsLearned(userId, learnedCount, reviewedCount);
                    processedUsers.add(userId);
                    logger.info("Updated word stats: userId={}, new={}, reviewed={}",
                            userId, learnedCount, reviewedCount);
                }
            } catch (Exception e) {
                logger.error("Failed to process DailyStudy record", e);
            }
        }

        logger.info("Word stats aggregation completed: {} users processed", processedUsers.size());
    }

    /**
     * Streak 체크 및 리셋
     * 어제 학습하지 않은 사용자의 streak을 리셋
     */
    private void checkAndResetStreaks(String yesterday) {
        logger.info("Checking streaks for date: {}", yesterday);

        // 전체 통계가 있는 사용자 중 어제 학습하지 않은 사용자 찾기
        Map<String, AttributeValue> expressionValues = new HashMap<>();
        expressionValues.put(":pk_suffix", AttributeValue.builder().s("#STATS").build());
        expressionValues.put(":sk", AttributeValue.builder().s("TOTAL").build());

        ScanRequest scanRequest = ScanRequest.builder()
                .tableName(TABLE_NAME)
                .filterExpression("contains(PK, :pk_suffix) AND SK = :sk")
                .expressionAttributeValues(expressionValues)
                .build();

        ScanResponse response = AwsClients.dynamoDb().scan(scanRequest);

        int resetCount = 0;
        for (Map<String, AttributeValue> item : response.items()) {
            try {
                String lastStudyDate = item.containsKey("lastStudyDate") ?
                        item.get("lastStudyDate").s() : null;
                Integer currentStreak = item.containsKey("currentStreak") ?
                        Integer.parseInt(item.get("currentStreak").n()) : 0;

                // 마지막 학습일이 어제가 아니고 streak이 0보다 크면 리셋
                if (lastStudyDate != null && !lastStudyDate.equals(yesterday) && currentStreak > 0) {
                    String pk = item.get("PK").s();
                    String userId = pk.replace("USER#", "").replace("#STATS", "");
                    Integer longestStreak = item.containsKey("longestStreak") ?
                            Integer.parseInt(item.get("longestStreak").n()) : currentStreak;

                    userStatsRepository.updateStreak(userId, 0, longestStreak, lastStudyDate);
                    resetCount++;
                    logger.info("Reset streak for user: {}", userId);
                }
            } catch (Exception e) {
                logger.error("Failed to check streak for user", e);
            }
        }

        logger.info("Streak check completed: {} users reset", resetCount);
    }

    private int getListSize(Map<String, AttributeValue> item, String key) {
        if (item.containsKey(key) && item.get(key).l() != null) {
            return item.get(key).l().size();
        }
        return 0;
    }
}
