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
import com.mzc.secondproject.serverless.vocabulary.model.Word;
import com.mzc.secondproject.serverless.vocabulary.repository.DailyStudyRepository;
import com.mzc.secondproject.serverless.vocabulary.repository.TestResultRepository;
import com.mzc.secondproject.serverless.vocabulary.repository.WordRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public class TestHandler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    private static final Logger logger = LoggerFactory.getLogger(TestHandler.class);
    private static final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    private final TestResultRepository testResultRepository;
    private final DailyStudyRepository dailyStudyRepository;
    private final WordRepository wordRepository;

    public TestHandler() {
        this.testResultRepository = new TestResultRepository();
        this.dailyStudyRepository = new DailyStudyRepository();
        this.wordRepository = new WordRepository();
    }

    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent request, Context context) {
        String httpMethod = request.getHttpMethod();
        String path = request.getPath();

        logger.info("Received request: {} {}", httpMethod, path);

        try {
            // POST /vocab/test/{userId}/start - 시험 시작
            if ("POST".equals(httpMethod) && path.endsWith("/start")) {
                return startTest(request);
            }

            // POST /vocab/test/{userId}/submit - 답안 제출
            if ("POST".equals(httpMethod) && path.endsWith("/submit")) {
                return submitAnswer(request);
            }

            // GET /vocab/test/{userId}/results - 시험 결과 조회
            if ("GET".equals(httpMethod) && path.endsWith("/results")) {
                return getTestResults(request);
            }

            return createResponse(404, ApiResponse.error("Not found"));

        } catch (Exception e) {
            logger.error("Error handling request", e);
            return createResponse(500, ApiResponse.error("Internal server error: " + e.getMessage()));
        }
    }

    private APIGatewayProxyResponseEvent startTest(APIGatewayProxyRequestEvent request) {
        Map<String, String> pathParams = request.getPathParameters();
        String userId = pathParams != null ? pathParams.get("userId") : null;

        if (userId == null) {
            return createResponse(400, ApiResponse.error("userId is required"));
        }

        String body = request.getBody();
        Map<String, Object> requestBody = gson.fromJson(body, Map.class);
        String testType = (String) requestBody.getOrDefault("testType", "DAILY");

        String today = LocalDate.now().toString();

        // 오늘 학습한 단어 기반 시험
        Optional<DailyStudy> optDailyStudy = dailyStudyRepository.findByUserIdAndDate(userId, today);
        if (optDailyStudy.isEmpty()) {
            return createResponse(404, ApiResponse.error("No daily study found for today"));
        }

        DailyStudy dailyStudy = optDailyStudy.get();
        List<String> allWordIds = new ArrayList<>();
        if (dailyStudy.getNewWordIds() != null) allWordIds.addAll(dailyStudy.getNewWordIds());
        if (dailyStudy.getReviewWordIds() != null) allWordIds.addAll(dailyStudy.getReviewWordIds());

        if (allWordIds.isEmpty()) {
            return createResponse(400, ApiResponse.error("No words to test"));
        }

        // 시험 문제 생성
        List<Map<String, Object>> questions = new ArrayList<>();
        for (String wordId : allWordIds) {
            Optional<Word> optWord = wordRepository.findById(wordId);
            if (optWord.isPresent()) {
                Word word = optWord.get();
                Map<String, Object> question = new HashMap<>();
                question.put("wordId", word.getWordId());
                question.put("english", word.getEnglish());
                question.put("example", word.getExample());
                questions.add(question);
            }
        }

        String testId = UUID.randomUUID().toString();
        String now = Instant.now().toString();

        Map<String, Object> result = new HashMap<>();
        result.put("testId", testId);
        result.put("testType", testType);
        result.put("questions", questions);
        result.put("totalQuestions", questions.size());
        result.put("startedAt", now);

        logger.info("Started test: userId={}, testId={}, questions={}", userId, testId, questions.size());
        return createResponse(200, ApiResponse.success("Test started", result));
    }

    @SuppressWarnings("unchecked")
    private APIGatewayProxyResponseEvent submitAnswer(APIGatewayProxyRequestEvent request) {
        Map<String, String> pathParams = request.getPathParameters();
        String userId = pathParams != null ? pathParams.get("userId") : null;

        if (userId == null) {
            return createResponse(400, ApiResponse.error("userId is required"));
        }

        String body = request.getBody();
        Map<String, Object> requestBody = gson.fromJson(body, Map.class);

        String testId = (String) requestBody.get("testId");
        String testType = (String) requestBody.getOrDefault("testType", "DAILY");
        List<Map<String, Object>> answers = (List<Map<String, Object>>) requestBody.get("answers");

        if (testId == null || answers == null) {
            return createResponse(400, ApiResponse.error("testId and answers are required"));
        }

        String now = Instant.now().toString();
        String today = LocalDate.now().toString();

        int correctCount = 0;
        int incorrectCount = 0;
        List<String> incorrectWordIds = new ArrayList<>();

        for (Map<String, Object> answer : answers) {
            String wordId = (String) answer.get("wordId");
            String userAnswer = (String) answer.get("answer");

            Optional<Word> optWord = wordRepository.findById(wordId);
            if (optWord.isPresent()) {
                Word word = optWord.get();
                // 대소문자 무시, 공백 제거 후 비교
                boolean isCorrect = word.getKorean().trim().equalsIgnoreCase(userAnswer.trim());

                if (isCorrect) {
                    correctCount++;
                } else {
                    incorrectCount++;
                    incorrectWordIds.add(wordId);
                }
            }
        }

        int totalQuestions = answers.size();
        double successRate = totalQuestions > 0 ? (correctCount * 100.0 / totalQuestions) : 0;

        TestResult testResult = TestResult.builder()
                .pk("TEST#" + userId)
                .sk("RESULT#" + now)
                .gsi1pk("TEST#ALL")
                .gsi1sk("DATE#" + today)
                .testId(testId)
                .userId(userId)
                .testType(testType)
                .totalQuestions(totalQuestions)
                .correctAnswers(correctCount)
                .incorrectAnswers(incorrectCount)
                .successRate(successRate)
                .incorrectWordIds(incorrectWordIds)
                .startedAt((String) requestBody.get("startedAt"))
                .completedAt(now)
                .build();

        testResultRepository.save(testResult);

        logger.info("Test submitted: userId={}, testId={}, successRate={}%", userId, testId, successRate);
        return createResponse(200, ApiResponse.success("Test submitted", testResult));
    }

    private APIGatewayProxyResponseEvent getTestResults(APIGatewayProxyRequestEvent request) {
        Map<String, String> pathParams = request.getPathParameters();
        Map<String, String> queryParams = request.getQueryStringParameters();

        String userId = pathParams != null ? pathParams.get("userId") : null;
        String cursor = queryParams != null ? queryParams.get("cursor") : null;

        if (userId == null) {
            return createResponse(400, ApiResponse.error("userId is required"));
        }

        int limit = 10;
        if (queryParams != null && queryParams.get("limit") != null) {
            limit = Math.min(Integer.parseInt(queryParams.get("limit")), 50);
        }

        TestResultRepository.TestResultPage resultPage = testResultRepository.findByUserIdWithPagination(userId, limit, cursor);

        Map<String, Object> result = new HashMap<>();
        result.put("testResults", resultPage.getTestResults());
        result.put("nextCursor", resultPage.getNextCursor());
        result.put("hasMore", resultPage.hasMore());

        return createResponse(200, ApiResponse.success("Test results retrieved", result));
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
