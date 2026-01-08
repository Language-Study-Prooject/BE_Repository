package com.mzc.secondproject.serverless.domain.vocabulary.service;

import com.mzc.secondproject.serverless.common.dto.PaginatedResult;
import com.mzc.secondproject.serverless.domain.vocabulary.model.UserWord;
import com.mzc.secondproject.serverless.domain.vocabulary.model.Word;
import com.mzc.secondproject.serverless.domain.vocabulary.repository.UserWordRepository;
import com.mzc.secondproject.serverless.domain.vocabulary.repository.WordRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

public class UserWordService {

    private static final Logger logger = LoggerFactory.getLogger(UserWordService.class);

    private final UserWordRepository userWordRepository;
    private final WordRepository wordRepository;

    public UserWordService() {
        this.userWordRepository = new UserWordRepository();
        this.wordRepository = new WordRepository();
    }

    public UserWordsResult getUserWords(String userId, String status, String bookmarked,
                                         String incorrectOnly, int limit, String cursor) {
        PaginatedResult<UserWord> userWordPage;

        if ("true".equalsIgnoreCase(bookmarked)) {
            userWordPage = userWordRepository.findBookmarkedWords(userId, limit, cursor);
        } else if ("true".equalsIgnoreCase(incorrectOnly)) {
            userWordPage = userWordRepository.findIncorrectWords(userId, limit, cursor);
        } else if (status != null && !status.isEmpty()) {
            userWordPage = userWordRepository.findByUserIdAndStatus(userId, status, limit, cursor);
        } else {
            userWordPage = userWordRepository.findByUserIdWithPagination(userId, limit, cursor);
        }

        List<Map<String, Object>> enrichedUserWords = enrichWithWordInfo(userWordPage.getItems());

        return new UserWordsResult(enrichedUserWords, userWordPage.getNextCursor(), userWordPage.hasMore());
    }

    public Optional<UserWord> getUserWord(String userId, String wordId) {
        return userWordRepository.findByUserIdAndWordId(userId, wordId);
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

    private List<Map<String, Object>> enrichWithWordInfo(List<UserWord> userWords) {
        if (userWords == null || userWords.isEmpty()) {
            return new ArrayList<>();
        }

        List<String> wordIds = userWords.stream()
                .map(UserWord::getWordId)
                .collect(Collectors.toList());

        List<Word> words = wordRepository.findByIds(wordIds);

        Map<String, Word> wordMap = words.stream()
                .collect(Collectors.toMap(Word::getWordId, w -> w, (w1, w2) -> w1));

        List<Map<String, Object>> enrichedList = new ArrayList<>();
        for (UserWord userWord : userWords) {
            Map<String, Object> enriched = new HashMap<>();

            enriched.put("wordId", userWord.getWordId());
            enriched.put("userId", userWord.getUserId());
            enriched.put("status", userWord.getStatus());
            enriched.put("correctCount", userWord.getCorrectCount());
            enriched.put("incorrectCount", userWord.getIncorrectCount());
            enriched.put("bookmarked", userWord.getBookmarked());
            enriched.put("favorite", userWord.getFavorite());
            enriched.put("difficulty", userWord.getDifficulty());
            enriched.put("nextReviewAt", userWord.getNextReviewAt());
            enriched.put("lastReviewedAt", userWord.getLastReviewedAt());
            enriched.put("repetitions", userWord.getRepetitions());
            enriched.put("interval", userWord.getInterval());

            Word word = wordMap.get(userWord.getWordId());
            if (word != null) {
                enriched.put("english", word.getEnglish());
                enriched.put("korean", word.getKorean());
                enriched.put("level", word.getLevel());
                enriched.put("category", word.getCategory());
                enriched.put("example", word.getExample());
                enriched.put("maleVoiceKey", word.getMaleVoiceKey());
                enriched.put("femaleVoiceKey", word.getFemaleVoiceKey());
            }

            enrichedList.add(enriched);
        }

        return enrichedList;
    }

    public record UserWordsResult(List<Map<String, Object>> userWords, String nextCursor, boolean hasMore) {}
}
