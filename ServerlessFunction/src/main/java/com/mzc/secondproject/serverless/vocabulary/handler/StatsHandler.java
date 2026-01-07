package com.mzc.secondproject.serverless.vocabulary.handler;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mzc.secondproject.serverless.vocabulary.dto.ApiResponse;
import com.mzc.secondproject.serverless.vocabulary.model.DailyStudy;
import com.mzc.secondproject.serverless.vocabulary.model.TestResult;
import com.mzc.secondproject.serverless.vocabulary.model.UserWord;
import com.mzc.secondproject.serverless.vocabulary.model.Word;
import com.mzc.secondproject.serverless.vocabulary.repository.DailyStudyRepository;
import com.mzc.secondproject.serverless.vocabulary.repository.TestResultRepository;
import com.mzc.secondproject.serverless.vocabulary.repository.UserWordRepository;
import com.mzc.secondproject.serverless.vocabulary.repository.WordRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class StatsHandler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    private static final Logger logger = LoggerFactory.getLogger(StatsHandler.class);
    private static final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    private final UserWordRepository userWordRepository;
    private final DailyStudyRepository dailyStudyRepository;
    private final TestResultRepository testResultRepository;
    private final WordRepository wordRepository;

    public StatsHandler() {
        this.userWordRepository = new UserWordRepository();
        this.dailyStudyRepository = new DailyStudyRepository();
        this.testResultRepository = new TestResultRepository();
        this.wordRepository = new WordRepository();
    }

    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent request, Context context) {
        String httpMethod = request.getHttpMethod();
        String path = request.getPath();

        logger.info("Received request: {} {}", httpMethod, path);

        try {
            // GET /vocab/stats/{userId}/weakness - 약점 분석
            if ("GET".equals(httpMethod) && path.endsWith("/weakness")) {
                return getWeaknessAnalysis(request);
            }

            // GET /vocab/stats/{userId}/daily - 일별 통계
            if ("GET".equals(httpMethod) && path.endsWith("/daily")) {
                return getDailyStats(request);
            }

            // GET /vocab/stats/{userId} - 전체 통계
            if ("GET".equals(httpMethod)) {
                return getOverallStats(request);
            }

            return createResponse(404, ApiResponse.error("Not found"));

        } catch (Exception e) {
            logger.error("Error handling request", e);
            return createResponse(500, ApiResponse.error("Internal server error: " + e.getMessage()));
        }
    }

    private APIGatewayProxyResponseEvent getOverallStats(APIGatewayProxyRequestEvent request) {
        Map<String, String> pathParams = request.getPathParameters();
        String userId = pathParams != null ? pathParams.get("userId") : null;

        if (userId == null) {
            return createResponse(400, ApiResponse.error("userId is required"));
        }

        // 단어 학습 상태별 통계
        Map<String, Integer> wordStatusCounts = new HashMap<>();
        wordStatusCounts.put("NEW", 0);
        wordStatusCounts.put("LEARNING", 0);
        wordStatusCounts.put("REVIEWING", 0);
        wordStatusCounts.put("MASTERED", 0);

        int totalCorrect = 0;
        int totalIncorrect = 0;

        // 사용자 단어 통계 조회
        String cursor = null;
        do {
            UserWordRepository.UserWordPage page = userWordRepository.findByUserIdWithPagination(userId, 100, cursor);
            for (UserWord userWord : page.getUserWords()) {
                String status = userWord.getStatus();
                wordStatusCounts.merge(status, 1, Integer::sum);
                totalCorrect += userWord.getCorrectCount() != null ? userWord.getCorrectCount() : 0;
                totalIncorrect += userWord.getIncorrectCount() != null ? userWord.getIncorrectCount() : 0;
            }
            cursor = page.getNextCursor();
        } while (cursor != null);

        int totalWords = wordStatusCounts.values().stream().mapToInt(Integer::intValue).sum();

        // 시험 통계
        TestResultRepository.TestResultPage testPage = testResultRepository.findByUserIdWithPagination(userId, 100, null);
        List<TestResult> testResults = testPage.getTestResults();

        double avgSuccessRate = testResults.stream()
                .mapToDouble(TestResult::getSuccessRate)
                .average()
                .orElse(0.0);

        // 학습 일수
        DailyStudyRepository.DailyStudyPage dailyPage = dailyStudyRepository.findByUserIdWithPagination(userId, 365, null);
        int studyDays = dailyPage.getDailyStudies().size();
        int completedDays = (int) dailyPage.getDailyStudies().stream()
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

        return createResponse(200, ApiResponse.success("Stats retrieved", stats));
    }

    private APIGatewayProxyResponseEvent getDailyStats(APIGatewayProxyRequestEvent request) {
        Map<String, String> pathParams = request.getPathParameters();
        Map<String, String> queryParams = request.getQueryStringParameters();

        String userId = pathParams != null ? pathParams.get("userId") : null;
        String cursor = queryParams != null ? queryParams.get("cursor") : null;

        if (userId == null) {
            return createResponse(400, ApiResponse.error("userId is required"));
        }

        int limit = 30;  // 최근 30일
        if (queryParams != null && queryParams.get("limit") != null) {
            limit = Math.min(Integer.parseInt(queryParams.get("limit")), 90);
        }

        DailyStudyRepository.DailyStudyPage dailyPage = dailyStudyRepository.findByUserIdWithPagination(userId, limit, cursor);

        List<Map<String, Object>> dailyStats = dailyPage.getDailyStudies().stream()
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

        Map<String, Object> result = new HashMap<>();
        result.put("dailyStats", dailyStats);
        result.put("nextCursor", dailyPage.getNextCursor());
        result.put("hasMore", dailyPage.hasMore());

        return createResponse(200, ApiResponse.success("Daily stats retrieved", result));
    }

    /**
     * 약점 분석 - 틀린 횟수가 많은 단어, 카테고리/레벨별 정확도 분석
     */
    private APIGatewayProxyResponseEvent getWeaknessAnalysis(APIGatewayProxyRequestEvent request) {
        Map<String, String> pathParams = request.getPathParameters();
        String userId = pathParams != null ? pathParams.get("userId") : null;

        if (userId == null) {
            return createResponse(400, ApiResponse.error("userId is required"));
        }

        // 사용자의 모든 학습 단어 조회
        List<UserWord> allUserWords = new ArrayList<>();
        String cursor = null;
        do {
            UserWordRepository.UserWordPage page = userWordRepository.findByUserIdWithPagination(userId, 100, cursor);
            allUserWords.addAll(page.getUserWords());
            cursor = page.getNextCursor();
        } while (cursor != null);

        if (allUserWords.isEmpty()) {
            Map<String, Object> emptyResult = new HashMap<>();
            emptyResult.put("weakestWords", List.of());
            emptyResult.put("categoryAnalysis", Map.of());
            emptyResult.put("levelAnalysis", Map.of());
            emptyResult.put("suggestions", List.of());
            return createResponse(200, ApiResponse.success("No learning data", emptyResult));
        }

        // 1. 가장 많이 틀린 단어 Top 10
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

                    // 단어 상세 정보 조회
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

        // 2. 카테고리별 정확도 분석
        Map<String, Map<String, Object>> categoryAnalysis = new HashMap<>();
        // 3. 레벨별 정확도 분석
        Map<String, Map<String, Object>> levelAnalysis = new HashMap<>();

        for (UserWord uw : allUserWords) {
            // 단어 정보 조회
            wordRepository.findById(uw.getWordId()).ifPresent(word -> {
                String category = word.getCategory();
                String level = word.getLevel();

                int correct = uw.getCorrectCount() != null ? uw.getCorrectCount() : 0;
                int incorrect = uw.getIncorrectCount() != null ? uw.getIncorrectCount() : 0;

                // 카테고리별 집계
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

                // 레벨별 집계
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

        // 정확도 계산
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

        // 4. 학습 제안 생성
        List<String> suggestions = new ArrayList<>();

        // 가장 약한 카테고리 찾기
        categoryAnalysis.entrySet().stream()
                .filter(e -> (Integer) e.getValue().get("wordCount") >= 3) // 최소 3개 이상 학습한 카테고리만
                .min(Comparator.comparingDouble(e -> (Double) e.getValue().get("accuracy")))
                .ifPresent(e -> suggestions.add(
                        String.format("%s 카테고리의 정확도가 %.1f%%로 가장 낮습니다. 집중 학습을 권장합니다.",
                                e.getKey(), e.getValue().get("accuracy"))));

        // 가장 약한 레벨 찾기
        levelAnalysis.entrySet().stream()
                .filter(e -> (Integer) e.getValue().get("wordCount") >= 3)
                .min(Comparator.comparingDouble(e -> (Double) e.getValue().get("accuracy")))
                .ifPresent(e -> suggestions.add(
                        String.format("%s 레벨의 정확도가 %.1f%%입니다. 이 레벨의 단어들을 더 복습해보세요.",
                                e.getKey(), e.getValue().get("accuracy"))));

        // 많이 틀린 단어가 있는 경우
        if (!weakestWords.isEmpty()) {
            suggestions.add(String.format("자주 틀리는 단어 %d개가 있습니다. 북마크하여 집중 복습하세요.",
                    weakestWords.size()));
        }

        Map<String, Object> result = new HashMap<>();
        result.put("weakestWords", weakestWords);
        result.put("categoryAnalysis", categoryAnalysis);
        result.put("levelAnalysis", levelAnalysis);
        result.put("suggestions", suggestions);

        return createResponse(200, ApiResponse.success("Weakness analysis completed", result));
    }

    private APIGatewayProxyResponseEvent createResponse(int statusCode, Object body) {
        return new APIGatewayProxyResponseEvent()
                .withStatusCode(statusCode)
                .withHeaders(Map.of(
                        "Content-Type", "application/json",
                        "Access-Control-Allow-Origin", "*",
                        "Access-Control-Allow-Methods", "GET,POST,PUT,DELETE,OPTIONS",
                        "Access-Control-Allow-Headers", "Content-Type,Authorization"
                ))
                .withBody(gson.toJson(body));
    }
}
