package com.mzc.secondproject.serverless.vocabulary.handler;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.SQSEvent;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mzc.secondproject.serverless.vocabulary.model.UserWord;
import com.mzc.secondproject.serverless.vocabulary.repository.UserWordRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * SQS에서 시험 결과 메시지를 받아 UserWord 통계를 업데이트하는 Lambda
 * SNS → SQS → Statistics Lambda 패턴
 */
public class StatisticsHandler implements RequestHandler<SQSEvent, Void> {

    private static final Logger logger = LoggerFactory.getLogger(StatisticsHandler.class);
    private static final Gson gson = new GsonBuilder().create();

    private final UserWordRepository userWordRepository;

    public StatisticsHandler() {
        this.userWordRepository = new UserWordRepository();
    }

    @Override
    public Void handleRequest(SQSEvent event, Context context) {
        logger.info("Received {} messages from SQS", event.getRecords().size());

        for (SQSEvent.SQSMessage message : event.getRecords()) {
            try {
                processMessage(message);
            } catch (Exception e) {
                logger.error("Failed to process message: {}", message.getMessageId(), e);
                // 실패한 메시지는 DLQ로 이동됨 (SQS 설정에 의해)
                throw new RuntimeException("Failed to process message", e);
            }
        }

        return null;
    }

    @SuppressWarnings("unchecked")
    private void processMessage(SQSEvent.SQSMessage message) {
        String body = message.getBody();
        logger.info("Processing message: {}", body);

        Map<String, Object> testResult = gson.fromJson(body, Map.class);

        String userId = (String) testResult.get("userId");
        List<Map<String, Object>> results = (List<Map<String, Object>>) testResult.get("results");

        if (userId == null || results == null) {
            logger.warn("Invalid message format: userId or results is null");
            return;
        }

        String now = Instant.now().toString();

        for (Map<String, Object> result : results) {
            String wordId = (String) result.get("wordId");
            Boolean isCorrect = (Boolean) result.get("isCorrect");

            if (wordId == null || isCorrect == null) {
                continue;
            }

            updateUserWordStatistics(userId, wordId, isCorrect, now);
        }

        logger.info("Successfully processed test result for user: {}, {} words updated", userId, results.size());
    }

    private void updateUserWordStatistics(String userId, String wordId, boolean isCorrect, String now) {
        Optional<UserWord> optUserWord = userWordRepository.findByUserIdAndWordId(userId, wordId);
        UserWord userWord;

        if (optUserWord.isEmpty()) {
            // 새로운 UserWord 생성
            userWord = UserWord.builder()
                    .pk("USER#" + userId)
                    .sk("WORD#" + wordId)
                    .gsi1pk("USER#" + userId + "#REVIEW")
                    .gsi2pk("USER#" + userId + "#STATUS")
                    .userId(userId)
                    .wordId(wordId)
                    .status("NEW")
                    .interval(1)
                    .easeFactor(2.5)
                    .repetitions(0)
                    .correctCount(0)
                    .incorrectCount(0)
                    .createdAt(now)
                    .build();
        } else {
            userWord = optUserWord.get();
        }

        // Spaced Repetition 알고리즘 적용
        applySpacedRepetition(userWord, isCorrect);
        userWord.setUpdatedAt(now);
        userWord.setLastReviewedAt(now);

        // GSI 업데이트
        userWord.setGsi1sk("DATE#" + userWord.getNextReviewAt());
        userWord.setGsi2sk("STATUS#" + userWord.getStatus());

        userWordRepository.save(userWord);
    }

    /**
     * SM-2 Spaced Repetition 알고리즘 적용
     */
    private void applySpacedRepetition(UserWord userWord, boolean isCorrect) {
        if (isCorrect) {
            userWord.setCorrectCount(userWord.getCorrectCount() + 1);
            userWord.setRepetitions(userWord.getRepetitions() + 1);

            // 간격 계산
            if (userWord.getRepetitions() == 1) {
                userWord.setInterval(1);
            } else if (userWord.getRepetitions() == 2) {
                userWord.setInterval(6);
            } else {
                int newInterval = (int) Math.round(userWord.getInterval() * userWord.getEaseFactor());
                userWord.setInterval(newInterval);
            }

            // 상태 업데이트
            if (userWord.getRepetitions() >= 5) {
                userWord.setStatus("MASTERED");
            } else if (userWord.getRepetitions() >= 2) {
                userWord.setStatus("REVIEWING");
            } else {
                userWord.setStatus("LEARNING");
            }
        } else {
            userWord.setIncorrectCount(userWord.getIncorrectCount() + 1);
            userWord.setRepetitions(0);
            userWord.setInterval(1);
            userWord.setStatus("LEARNING");

            // easeFactor 감소 (최소 1.3)
            double newEaseFactor = userWord.getEaseFactor() - 0.2;
            userWord.setEaseFactor(Math.max(1.3, newEaseFactor));
        }

        // 다음 복습일 계산
        LocalDate nextReview = LocalDate.now().plusDays(userWord.getInterval());
        userWord.setNextReviewAt(nextReview.toString());
    }
}
