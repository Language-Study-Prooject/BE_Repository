package com.mzc.secondproject.serverless.domain.vocabulary.service;

import com.mzc.secondproject.serverless.domain.vocabulary.model.UserWord;
import com.mzc.secondproject.serverless.domain.vocabulary.repository.UserWordRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;

/**
 * UserWord 변경 전용 서비스 (CQRS Command)
 */
public class UserWordCommandService {

    private static final Logger logger = LoggerFactory.getLogger(UserWordCommandService.class);

    private final UserWordRepository userWordRepository;

    public UserWordCommandService() {
        this.userWordRepository = new UserWordRepository();
    }

    public UserWord updateUserWord(String userId, String wordId, boolean isCorrect) {
        Optional<UserWord> optUserWord = userWordRepository.findByUserIdAndWordId(userId, wordId);
        UserWord userWord;
        String now = Instant.now().toString();

        if (optUserWord.isEmpty()) {
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

        applySpacedRepetition(userWord, isCorrect);
        userWord.setUpdatedAt(now);
        userWord.setLastReviewedAt(now);

        userWord.setGsi1sk("DATE#" + userWord.getNextReviewAt());
        userWord.setGsi2sk("STATUS#" + userWord.getStatus());

        userWordRepository.save(userWord);

        logger.info("Updated user word: userId={}, wordId={}, isCorrect={}", userId, wordId, isCorrect);
        return userWord;
    }

    public UserWord updateUserWordTag(String userId, String wordId, Boolean bookmarked,
                                       Boolean favorite, String difficulty) {
        Optional<UserWord> optUserWord = userWordRepository.findByUserIdAndWordId(userId, wordId);
        UserWord userWord;
        String now = Instant.now().toString();

        if (optUserWord.isEmpty()) {
            userWord = UserWord.builder()
                    .pk("USER#" + userId)
                    .sk("WORD#" + wordId)
                    .gsi1pk("USER#" + userId + "#REVIEW")
                    .gsi2pk("USER#" + userId + "#STATUS")
                    .gsi2sk("STATUS#NEW")
                    .userId(userId)
                    .wordId(wordId)
                    .status("NEW")
                    .interval(1)
                    .easeFactor(2.5)
                    .repetitions(0)
                    .correctCount(0)
                    .incorrectCount(0)
                    .bookmarked(false)
                    .favorite(false)
                    .createdAt(now)
                    .build();
        } else {
            userWord = optUserWord.get();
        }

        if (bookmarked != null) {
            userWord.setBookmarked(bookmarked);
        }
        if (favorite != null) {
            userWord.setFavorite(favorite);
        }
        if (difficulty != null) {
            if (!difficulty.equals("EASY") && !difficulty.equals("NORMAL") && !difficulty.equals("HARD")) {
                throw new IllegalArgumentException("difficulty must be EASY, NORMAL, or HARD");
            }
            userWord.setDifficulty(difficulty);
        }

        userWord.setUpdatedAt(now);
        userWordRepository.save(userWord);

        logger.info("Updated user word tag: userId={}, wordId={}", userId, wordId);
        return userWord;
    }

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
