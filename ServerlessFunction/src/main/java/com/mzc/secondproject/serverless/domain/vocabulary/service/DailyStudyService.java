package com.mzc.secondproject.serverless.domain.vocabulary.service;

import com.mzc.secondproject.serverless.common.dto.PaginatedResult;
import com.mzc.secondproject.serverless.domain.vocabulary.model.DailyStudy;
import com.mzc.secondproject.serverless.domain.vocabulary.model.UserWord;
import com.mzc.secondproject.serverless.domain.vocabulary.model.Word;
import com.mzc.secondproject.serverless.domain.vocabulary.repository.DailyStudyRepository;
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

public class DailyStudyService {

    private static final Logger logger = LoggerFactory.getLogger(DailyStudyService.class);

    private static final int NEW_WORDS_COUNT = 50;
    private static final int REVIEW_WORDS_COUNT = 5;

    private final DailyStudyRepository dailyStudyRepository;
    private final UserWordRepository userWordRepository;
    private final WordRepository wordRepository;

    public DailyStudyService() {
        this.dailyStudyRepository = new DailyStudyRepository();
        this.userWordRepository = new UserWordRepository();
        this.wordRepository = new WordRepository();
    }

    public DailyStudyResult getDailyWords(String userId, String level) {
        String today = LocalDate.now().toString();

        Optional<DailyStudy> optDailyStudy = dailyStudyRepository.findByUserIdAndDate(userId, today);

        DailyStudy dailyStudy;
        if (optDailyStudy.isPresent()) {
            dailyStudy = optDailyStudy.get();
        } else {
            if (level == null || level.isEmpty()) {
                throw new IllegalArgumentException("level is required for first daily study (BEGINNER, INTERMEDIATE, ADVANCED)");
            }
            if (!level.equals("BEGINNER") && !level.equals("INTERMEDIATE") && !level.equals("ADVANCED")) {
                throw new IllegalArgumentException("Invalid level. Must be BEGINNER, INTERMEDIATE, or ADVANCED");
            }
            dailyStudy = createDailyStudy(userId, today, level);
        }

        List<Word> newWords = getWordDetails(dailyStudy.getNewWordIds());
        List<Word> reviewWords = getWordDetails(dailyStudy.getReviewWordIds());
        Map<String, Object> progress = calculateProgress(dailyStudy);

        return new DailyStudyResult(dailyStudy, newWords, reviewWords, progress);
    }

    public Map<String, Object> markWordLearned(String userId, String wordId) {
        String today = LocalDate.now().toString();

        Optional<DailyStudy> optDailyStudy = dailyStudyRepository.findByUserIdAndDate(userId, today);
        if (optDailyStudy.isEmpty()) {
            throw new IllegalStateException("Daily study not found");
        }

        DailyStudy dailyStudy = optDailyStudy.get();

        if (dailyStudy.getLearnedWordIds() != null && dailyStudy.getLearnedWordIds().contains(wordId)) {
            return calculateProgress(dailyStudy);
        }

        dailyStudyRepository.addLearnedWord(userId, today, wordId);

        DailyStudy updatedDailyStudy = dailyStudyRepository.findByUserIdAndDate(userId, today).orElse(dailyStudy);

        if (updatedDailyStudy.getLearnedCount() >= updatedDailyStudy.getTotalWords()) {
            updatedDailyStudy.setIsCompleted(true);
            dailyStudyRepository.save(updatedDailyStudy);
        }

        logger.info("Marked word as learned: userId={}, wordId={}", userId, wordId);
        return calculateProgress(updatedDailyStudy);
    }

    private DailyStudy createDailyStudy(String userId, String date, String level) {
        String now = Instant.now().toString();

        PaginatedResult<UserWord> reviewPage = userWordRepository.findReviewDueWords(userId, date, REVIEW_WORDS_COUNT, null);
        List<String> reviewWordIds = reviewPage.getItems().stream()
                .map(UserWord::getWordId)
                .collect(Collectors.toList());

        List<String> newWordIds = getNewWordsForUser(userId, level, NEW_WORDS_COUNT);

        DailyStudy dailyStudy = DailyStudy.builder()
                .pk("DAILY#" + userId)
                .sk("DATE#" + date)
                .gsi1pk("DAILY#ALL")
                .gsi1sk("DATE#" + date)
                .userId(userId)
                .date(date)
                .newWordIds(newWordIds)
                .reviewWordIds(reviewWordIds)
                .learnedWordIds(new ArrayList<>())
                .totalWords(newWordIds.size() + reviewWordIds.size())
                .learnedCount(0)
                .isCompleted(false)
                .createdAt(now)
                .updatedAt(now)
                .build();

        dailyStudyRepository.save(dailyStudy);
        logger.info("Created daily study for user: {}, date: {}", userId, date);

        return dailyStudy;
    }

    private List<String> getNewWordsForUser(String userId, String level, int count) {
        PaginatedResult<UserWord> userWordPage = userWordRepository.findByUserIdWithPagination(userId, 1000, null);
        List<String> learnedWordIds = userWordPage.getItems().stream()
                .map(UserWord::getWordId)
                .collect(Collectors.toList());

        List<String> newWordIds = new ArrayList<>();
        String lastEvaluatedKey = null;

        do {
            PaginatedResult<Word> wordPage = wordRepository.findByLevelWithPagination(level, count * 2, lastEvaluatedKey);
            for (Word word : wordPage.getItems()) {
                if (!learnedWordIds.contains(word.getWordId()) && !newWordIds.contains(word.getWordId())) {
                    newWordIds.add(word.getWordId());
                    if (newWordIds.size() >= count) break;
                }
            }
            lastEvaluatedKey = wordPage.getNextCursor();
        } while (newWordIds.size() < count && lastEvaluatedKey != null);

        logger.info("Selected {} new words for user {} at level {}", newWordIds.size(), userId, level);
        return newWordIds;
    }

    private List<Word> getWordDetails(List<String> wordIds) {
        if (wordIds == null || wordIds.isEmpty()) {
            return new ArrayList<>();
        }
        return wordRepository.findByIds(wordIds);
    }

    private Map<String, Object> calculateProgress(DailyStudy dailyStudy) {
        Map<String, Object> progress = new HashMap<>();
        int total = dailyStudy.getTotalWords();
        int learned = dailyStudy.getLearnedCount();

        progress.put("total", total);
        progress.put("learned", learned);
        progress.put("remaining", total - learned);
        progress.put("percentage", total > 0 ? (learned * 100.0 / total) : 0);
        progress.put("isCompleted", dailyStudy.getIsCompleted());

        return progress;
    }

    public record DailyStudyResult(DailyStudy dailyStudy, List<Word> newWords, List<Word> reviewWords, Map<String, Object> progress) {}
}
