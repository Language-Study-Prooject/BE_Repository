package com.mzc.secondproject.serverless.domain.vocabulary.service;

import com.mzc.secondproject.serverless.common.dto.PaginatedResult;
import com.mzc.secondproject.serverless.domain.vocabulary.model.UserWord;
import com.mzc.secondproject.serverless.domain.vocabulary.model.Word;
import com.mzc.secondproject.serverless.domain.vocabulary.repository.UserWordRepository;
import com.mzc.secondproject.serverless.domain.vocabulary.repository.WordRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * UserWord 조회 전용 서비스 (CQRS Query)
 */
public class UserWordQueryService {

    private static final Logger logger = LoggerFactory.getLogger(UserWordQueryService.class);

    private final UserWordRepository userWordRepository;
    private final WordRepository wordRepository;

    public UserWordQueryService() {
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

    /**
     * 오답 노트 조회 - 오답 횟수 기준 내림차순 정렬
     */
    public UserWordsResult getWrongAnswers(String userId, int minCount, int limit, String cursor) {
        PaginatedResult<UserWord> userWordPage = userWordRepository.findIncorrectWords(userId, minCount, limit * 2, cursor);

        // 오답 횟수 기준 내림차순 정렬
        List<UserWord> sorted = userWordPage.getItems().stream()
                .sorted((a, b) -> {
                    int countA = a.getIncorrectCount() != null ? a.getIncorrectCount() : 0;
                    int countB = b.getIncorrectCount() != null ? b.getIncorrectCount() : 0;
                    return Integer.compare(countB, countA);
                })
                .limit(limit)
                .collect(Collectors.toList());

        List<Map<String, Object>> enrichedUserWords = enrichWithWordInfo(sorted);

        return new UserWordsResult(enrichedUserWords, userWordPage.getNextCursor(), userWordPage.hasMore());
    }

    public Optional<UserWord> getUserWord(String userId, String wordId) {
        return userWordRepository.findByUserIdAndWordId(userId, wordId);
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
