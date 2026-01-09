package com.mzc.secondproject.serverless.domain.vocabulary.service;

import com.mzc.secondproject.serverless.common.dto.PaginatedResult;
import com.mzc.secondproject.serverless.domain.vocabulary.model.DailyStudy;
import com.mzc.secondproject.serverless.domain.vocabulary.model.TestResult;
import com.mzc.secondproject.serverless.domain.vocabulary.model.UserWord;
import com.mzc.secondproject.serverless.domain.vocabulary.repository.DailyStudyRepository;
import com.mzc.secondproject.serverless.domain.vocabulary.repository.TestResultRepository;
import com.mzc.secondproject.serverless.domain.vocabulary.repository.UserWordRepository;
import com.mzc.secondproject.serverless.domain.vocabulary.repository.WordRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class StatsService {

    private static final Logger logger = LoggerFactory.getLogger(StatsService.class);

    private final UserWordRepository userWordRepository;
    private final DailyStudyRepository dailyStudyRepository;
    private final TestResultRepository testResultRepository;
    private final WordRepository wordRepository;

    public StatsService() {
        this.userWordRepository = new UserWordRepository();
        this.dailyStudyRepository = new DailyStudyRepository();
        this.testResultRepository = new TestResultRepository();
        this.wordRepository = new WordRepository();
    }

    public Map<String, Object> getOverallStats(String userId) {
        Map<String, Integer> wordStatusCounts = new HashMap<>();
        wordStatusCounts.put("NEW", 0);
        wordStatusCounts.put("LEARNING", 0);
        wordStatusCounts.put("REVIEWING", 0);
        wordStatusCounts.put("MASTERED", 0);

        int totalCorrect = 0;
        int totalIncorrect = 0;

        String cursor = null;
        do {
            PaginatedResult<UserWord> page = userWordRepository.findByUserIdWithPagination(userId, 100, cursor);
            for (UserWord userWord : page.items()) {
                String status = userWord.getStatus();
                wordStatusCounts.merge(status, 1, Integer::sum);
                totalCorrect += userWord.getCorrectCount() != null ? userWord.getCorrectCount() : 0;
                totalIncorrect += userWord.getIncorrectCount() != null ? userWord.getIncorrectCount() : 0;
            }
            cursor = page.nextCursor();
        } while (cursor != null);

        int totalWords = wordStatusCounts.values().stream().mapToInt(Integer::intValue).sum();

        PaginatedResult<TestResult> testPage = testResultRepository.findByUserIdWithPagination(userId, 100, null);
        List<TestResult> testResults = testPage.items();

        double avgSuccessRate = testResults.stream()
                .mapToDouble(TestResult::getSuccessRate)
                .average()
                .orElse(0.0);

        PaginatedResult<DailyStudy> dailyPage = dailyStudyRepository.findByUserIdWithPagination(userId, 365, null);
        int studyDays = dailyPage.items().size();
        int completedDays = (int) dailyPage.items().stream()
                .filter(d -> Boolean.TRUE.equals(d.getIsCompleted()))
                .count();

        Map<String, Object> stats = new HashMap<>();
        stats.put("totalWords", totalWords);
        stats.put("wordStatusCounts", wordStatusCounts);
        stats.put("totalCorrect", totalCorrect);
        stats.put("totalIncorrect", totalIncorrect);
        stats.put("accuracy", totalCorrect + totalIncorrect > 0
                ? (totalCorrect * 100.0 / (totalCorrect + totalIncorrect)) : 0);
        stats.put("testCount", testResults.size());
        stats.put("avgSuccessRate", avgSuccessRate);
        stats.put("studyDays", studyDays);
        stats.put("completedDays", completedDays);
        stats.put("completionRate", studyDays > 0 ? (completedDays * 100.0 / studyDays) : 0);

        return stats;
    }

    public DailyStatsResult getDailyStats(String userId, int limit, String cursor) {
        PaginatedResult<DailyStudy> dailyPage = dailyStudyRepository.findByUserIdWithPagination(userId, limit, cursor);

        List<Map<String, Object>> dailyStats = dailyPage.items().stream()
                .map(daily -> {
                    Map<String, Object> stat = new HashMap<>();
                    stat.put("date", daily.getDate());
                    stat.put("totalWords", daily.getTotalWords());
                    stat.put("learnedCount", daily.getLearnedCount());
                    stat.put("isCompleted", daily.getIsCompleted());
                    stat.put("progress", daily.getTotalWords() > 0
                            ? (daily.getLearnedCount() * 100.0 / daily.getTotalWords()) : 0);
                    return stat;
                })
                .toList();

        return new DailyStatsResult(dailyStats, dailyPage.nextCursor(), dailyPage.hasMore());
    }

    public Map<String, Object> getWeaknessAnalysis(String userId) {
        List<UserWord> allUserWords = new ArrayList<>();
        String cursor = null;
        do {
            PaginatedResult<UserWord> page = userWordRepository.findByUserIdWithPagination(userId, 100, cursor);
            allUserWords.addAll(page.items());
            cursor = page.nextCursor();
        } while (cursor != null);

        if (allUserWords.isEmpty()) {
            Map<String, Object> emptyResult = new HashMap<>();
            emptyResult.put("weakestWords", List.of());
            emptyResult.put("categoryAnalysis", Map.of());
            emptyResult.put("levelAnalysis", Map.of());
            emptyResult.put("suggestions", List.of());
            return emptyResult;
        }

        List<Map<String, Object>> weakestWords = allUserWords.stream()
                .filter(uw -> uw.getIncorrectCount() != null && uw.getIncorrectCount() > 0)
                .sorted(Comparator.comparingInt(UserWord::getIncorrectCount).reversed())
                .limit(10)
                .map(uw -> {
                    Map<String, Object> wordInfo = new HashMap<>();
                    wordInfo.put("wordId", uw.getWordId());
                    wordInfo.put("incorrectCount", uw.getIncorrectCount());
                    wordInfo.put("correctCount", uw.getCorrectCount());
                    wordInfo.put("status", uw.getStatus());

                    wordRepository.findById(uw.getWordId()).ifPresent(word -> {
                        wordInfo.put("english", word.getEnglish());
                        wordInfo.put("korean", word.getKorean());
                        wordInfo.put("level", word.getLevel());
                        wordInfo.put("category", word.getCategory());
                    });

                    int total = (uw.getCorrectCount() != null ? uw.getCorrectCount() : 0) +
                                (uw.getIncorrectCount() != null ? uw.getIncorrectCount() : 0);
                    wordInfo.put("accuracy", total > 0 ?
                            (uw.getCorrectCount() != null ? uw.getCorrectCount() * 100.0 / total : 0) : 0);

                    return wordInfo;
                })
                .collect(Collectors.toList());

        Map<String, Map<String, Object>> categoryAnalysis = new HashMap<>();
        Map<String, Map<String, Object>> levelAnalysis = new HashMap<>();

        for (UserWord uw : allUserWords) {
            wordRepository.findById(uw.getWordId()).ifPresent(word -> {
                String category = word.getCategory();
                String level = word.getLevel();

                int correct = uw.getCorrectCount() != null ? uw.getCorrectCount() : 0;
                int incorrect = uw.getIncorrectCount() != null ? uw.getIncorrectCount() : 0;

                categoryAnalysis.computeIfAbsent(category, k -> {
                    Map<String, Object> stats = new HashMap<>();
                    stats.put("totalCorrect", 0);
                    stats.put("totalIncorrect", 0);
                    stats.put("wordCount", 0);
                    return stats;
                });
                Map<String, Object> catStats = categoryAnalysis.get(category);
                catStats.put("totalCorrect", (Integer) catStats.get("totalCorrect") + correct);
                catStats.put("totalIncorrect", (Integer) catStats.get("totalIncorrect") + incorrect);
                catStats.put("wordCount", (Integer) catStats.get("wordCount") + 1);

                levelAnalysis.computeIfAbsent(level, k -> {
                    Map<String, Object> stats = new HashMap<>();
                    stats.put("totalCorrect", 0);
                    stats.put("totalIncorrect", 0);
                    stats.put("wordCount", 0);
                    return stats;
                });
                Map<String, Object> lvlStats = levelAnalysis.get(level);
                lvlStats.put("totalCorrect", (Integer) lvlStats.get("totalCorrect") + correct);
                lvlStats.put("totalIncorrect", (Integer) lvlStats.get("totalIncorrect") + incorrect);
                lvlStats.put("wordCount", (Integer) lvlStats.get("wordCount") + 1);
            });
        }

        categoryAnalysis.values().forEach(stats -> {
            int correct = (Integer) stats.get("totalCorrect");
            int incorrect = (Integer) stats.get("totalIncorrect");
            int total = correct + incorrect;
            stats.put("accuracy", total > 0 ? (correct * 100.0 / total) : 0);
        });

        levelAnalysis.values().forEach(stats -> {
            int correct = (Integer) stats.get("totalCorrect");
            int incorrect = (Integer) stats.get("totalIncorrect");
            int total = correct + incorrect;
            stats.put("accuracy", total > 0 ? (correct * 100.0 / total) : 0);
        });

        List<String> suggestions = new ArrayList<>();

        categoryAnalysis.entrySet().stream()
                .filter(e -> (Integer) e.getValue().get("wordCount") >= 3)
                .min(Comparator.comparingDouble(e -> (Double) e.getValue().get("accuracy")))
                .ifPresent(e -> suggestions.add(
                        String.format("%s 카테고리의 정확도가 %.1f%%로 가장 낮습니다. 집중 학습을 권장합니다.",
                                e.getKey(), e.getValue().get("accuracy"))));

        levelAnalysis.entrySet().stream()
                .filter(e -> (Integer) e.getValue().get("wordCount") >= 3)
                .min(Comparator.comparingDouble(e -> (Double) e.getValue().get("accuracy")))
                .ifPresent(e -> suggestions.add(
                        String.format("%s 레벨의 정확도가 %.1f%%입니다. 이 레벨의 단어들을 더 복습해보세요.",
                                e.getKey(), e.getValue().get("accuracy"))));

        if (!weakestWords.isEmpty()) {
            suggestions.add(String.format("자주 틀리는 단어 %d개가 있습니다. 북마크하여 집중 복습하세요.",
                    weakestWords.size()));
        }

        Map<String, Object> result = new HashMap<>();
        result.put("weakestWords", weakestWords);
        result.put("categoryAnalysis", categoryAnalysis);
        result.put("levelAnalysis", levelAnalysis);
        result.put("suggestions", suggestions);

        return result;
    }

    public record DailyStatsResult(List<Map<String, Object>> dailyStats, String nextCursor, boolean hasMore) {}
}
