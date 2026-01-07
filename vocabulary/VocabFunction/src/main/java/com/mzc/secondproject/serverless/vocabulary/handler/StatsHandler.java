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
import com.mzc.secondproject.serverless.vocabulary.repository.DailyStudyRepository;
import com.mzc.secondproject.serverless.vocabulary.repository.TestResultRepository;
import com.mzc.secondproject.serverless.vocabulary.repository.UserWordRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class StatsHandler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    private static final Logger logger = LoggerFactory.getLogger(StatsHandler.class);
    private static final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    private final UserWordRepository userWordRepository;
    private final DailyStudyRepository dailyStudyRepository;
    private final TestResultRepository testResultRepository;

    public StatsHandler() {
        this.userWordRepository = new UserWordRepository();
        this.dailyStudyRepository = new DailyStudyRepository();
        this.testResultRepository = new TestResultRepository();
    }

    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent request, Context context) {
        String httpMethod = request.getHttpMethod();
        String path = request.getPath();

        logger.info("Received request: {} {}", httpMethod, path);

        try {
            // GET /vocab/stats/{userId} - 전체 통계
            if ("GET".equals(httpMethod) && !path.endsWith("/daily")) {
                return getOverallStats(request);
            }

            // GET /vocab/stats/{userId}/daily - 일별 통계
            if ("GET".equals(httpMethod) && path.endsWith("/daily")) {
                return getDailyStats(request);
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
