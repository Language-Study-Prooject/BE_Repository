package com.mzc.secondproject.serverless.domain.vocabulary.service;

import com.mzc.secondproject.serverless.domain.vocabulary.model.UserWord;
import com.mzc.secondproject.serverless.domain.vocabulary.repository.UserWordRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class StatisticsService {

    private static final Logger logger = LoggerFactory.getLogger(StatisticsService.class);

    private final UserWordRepository userWordRepository;

    public StatisticsService() {
        this.userWordRepository = new UserWordRepository();
    }

    /**
     * 시험 결과를 처리하여 UserWord 통계를 업데이트
     * @param userId 사용자 ID
     * @param results 시험 결과 목록 (wordId, isCorrect 포함)
     * @return 업데이트된 단어 수
     */
    @SuppressWarnings("unchecked")
    public int processTestResults(String userId, List<Map<String, Object>> results) {
        if (userId == null || results == null) {
            throw new IllegalArgumentException("userId and results are required");
        }

        String now = Instant.now().toString();
        int updatedCount = 0;

        for (Map<String, Object> result : results) {
            String wordId = (String) result.get("wordId");
            Boolean isCorrect = (Boolean) result.get("isCorrect");

            if (wordId == null || isCorrect == null) {
                continue;
            }

            updateUserWordStatistics(userId, wordId, isCorrect, now);
            updatedCount++;
        }

        logger.info("Processed test result for user: {}, {} words updated", userId, updatedCount);
        return updatedCount;
    }

    /**
     * 단일 단어의 학습 결과를 업데이트
     */
    public void updateUserWordStatistics(String userId, String wordId, boolean isCorrect, String now) {
        Optional<UserWord> optUserWord = userWordRepository.findByUserIdAndWordId(userId, wordId);
        UserWord userWord;

        if (optUserWord.isEmpty()) {
            userWord = createNewUserWord(userId, wordId, now);
        } else {
            userWord = optUserWord.get();
        }

        applySpacedRepetition(userWord, isCorrect);
        userWord.setUpdatedAt(now);
        userWord.setLastReviewedAt(now);

        userWord.setGsi1sk("DATE#" + userWord.getNextReviewAt());
        userWord.setGsi2sk("STATUS#" + userWord.getStatus());

        userWordRepository.save(userWord);
    }

    private UserWord createNewUserWord(String userId, String wordId, String now) {
        return UserWord.builder()
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
    }

    /**
     * SM-2 Spaced Repetition 알고리즘 적용
     */
    private void applySpacedRepetition(UserWord userWord, boolean isCorrect) {
        if (isCorrect) {
            userWord.setCorrectCount(userWord.getCorrectCount() + 1);
            userWord.setRepetitions(userWord.getRepetitions() + 1);

            if (userWord.getRepetitions() == 1) {
                userWord.setInterval(1);
            } else if (userWord.getRepetitions() == 2) {
                userWord.setInterval(6);
            } else {
                int newInterval = (int) Math.round(userWord.getInterval() * userWord.getEaseFactor());
                userWord.setInterval(newInterval);
            }

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

            double newEaseFactor = userWord.getEaseFactor() - 0.2;
            userWord.setEaseFactor(Math.max(1.3, newEaseFactor));
        }

        LocalDate nextReview = LocalDate.now().plusDays(userWord.getInterval());
        userWord.setNextReviewAt(nextReview.toString());
    }
}
