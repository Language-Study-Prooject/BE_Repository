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
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.PublishRequest;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;
import java.util.stream.Collectors;

public class TestHandler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    private static final Logger logger = LoggerFactory.getLogger(TestHandler.class);
    private static final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private static final SnsClient snsClient = SnsClient.builder().build();
    private static final String TEST_RESULT_TOPIC_ARN = System.getenv("TEST_RESULT_TOPIC_ARN");

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

        // 시험 문제 생성 (BatchGetItem으로 한 번에 조회)
        List<Word> words = wordRepository.findByIds(allWordIds);

        // 레벨별로 단어 그룹화하여 오답 후보 준비
        Map<String, List<Word>> wordsByLevel = words.stream()
                .collect(Collectors.groupingBy(Word::getLevel));

        // 각 레벨별 추가 오답 후보 단어 조회 (문제 단어 외의 다른 단어들)
        Map<String, List<String>> distractorsByLevel = new HashMap<>();
        for (String level : wordsByLevel.keySet()) {
            List<String> distractors = getDistractorsForLevel(level, allWordIds);
            distractorsByLevel.put(level, distractors);
        }

        Random random = new Random();
        List<Map<String, Object>> questions = new ArrayList<>();
        for (Word word : words) {
            Map<String, Object> question = new HashMap<>();
            question.put("wordId", word.getWordId());
            question.put("english", word.getEnglish());
            question.put("example", word.getExample());

            // 4지선다 옵션 생성
            List<String> options = generateOptions(word, wordsByLevel, distractorsByLevel, random);
            question.put("options", options);

            questions.add(question);
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
        List<Map<String, Object>> results = new ArrayList<>();

        // 모든 wordId를 추출하여 BatchGetItem으로 한 번에 조회
        List<String> wordIds = answers.stream()
                .map(a -> (String) a.get("wordId"))
                .collect(java.util.stream.Collectors.toList());
        List<Word> words = wordRepository.findByIds(wordIds);

        // wordId -> Word 맵 생성
        Map<String, Word> wordMap = words.stream()
                .collect(java.util.stream.Collectors.toMap(Word::getWordId, w -> w));

        for (Map<String, Object> answer : answers) {
            String wordId = (String) answer.get("wordId");
            String userAnswer = (String) answer.get("answer");

            Word word = wordMap.get(wordId);
            if (word != null) {
                // 대소문자 무시, 공백 제거 후 비교
                boolean isCorrect = word.getKorean().trim().equalsIgnoreCase(userAnswer.trim());

                // 결과 상세 정보 추가
                Map<String, Object> resultItem = new HashMap<>();
                resultItem.put("wordId", wordId);
                resultItem.put("english", word.getEnglish());
                resultItem.put("correctAnswer", word.getKorean());
                resultItem.put("userAnswer", userAnswer);
                resultItem.put("isCorrect", isCorrect);
                results.add(resultItem);

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

        // SNS로 시험 결과 발행 (비동기 통계 처리용)
        publishTestResultToSns(userId, results);

        // 응답 데이터 구성 (results 포함)
        Map<String, Object> responseData = new HashMap<>();
        responseData.put("testId", testId);
        responseData.put("testType", testType);
        responseData.put("totalQuestions", totalQuestions);
        responseData.put("correctCount", correctCount);
        responseData.put("incorrectCount", incorrectCount);
        responseData.put("successRate", successRate);
        responseData.put("results", results);

        logger.info("Test submitted: userId={}, testId={}, successRate={}%", userId, testId, successRate);
        return createResponse(200, ApiResponse.success("Test submitted", responseData));
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

    /**
     * 해당 레벨에서 오답 후보 단어들의 한국어 뜻 목록을 가져옴
     */
    private List<String> getDistractorsForLevel(String level, List<String> excludeWordIds) {
        WordRepository.WordPage wordPage = wordRepository.findByLevelWithPagination(level, 50, null);
        return wordPage.getWords().stream()
                .filter(w -> !excludeWordIds.contains(w.getWordId()))
                .map(Word::getKorean)
                .collect(Collectors.toList());
    }

    /**
     * 4지선다 옵션 생성 (정답 1개 + 오답 3개, 셔플됨)
     */
    private List<String> generateOptions(Word correctWord, Map<String, List<Word>> wordsByLevel,
                                         Map<String, List<String>> distractorsByLevel, Random random) {
        List<String> options = new ArrayList<>();
        String correctAnswer = correctWord.getKorean();
        options.add(correctAnswer);

        String level = correctWord.getLevel();

        // 같은 레벨의 다른 문제 단어들에서 오답 후보 추출
        List<String> sameLevelOptions = wordsByLevel.getOrDefault(level, new ArrayList<>()).stream()
                .filter(w -> !w.getWordId().equals(correctWord.getWordId()))
                .map(Word::getKorean)
                .collect(Collectors.toList());

        // 추가 오답 후보 (문제에 포함되지 않은 단어들)
        List<String> additionalDistractors = distractorsByLevel.getOrDefault(level, new ArrayList<>());

        // 모든 오답 후보 합치기
        List<String> allDistractors = new ArrayList<>();
        allDistractors.addAll(sameLevelOptions);
        allDistractors.addAll(additionalDistractors);

        // 중복 및 정답 제거
        allDistractors = allDistractors.stream()
                .filter(d -> !d.equals(correctAnswer))
                .distinct()
                .collect(Collectors.toList());

        // 랜덤하게 3개 선택
        Collections.shuffle(allDistractors, random);
        int distractorCount = Math.min(3, allDistractors.size());
        for (int i = 0; i < distractorCount; i++) {
            options.add(allDistractors.get(i));
        }

        // 옵션 셔플
        Collections.shuffle(options, random);
        return options;
    }

    /**
     * SNS로 시험 결과 발행 (비동기 통계 처리용)
     */
    private void publishTestResultToSns(String userId, List<Map<String, Object>> results) {
        if (TEST_RESULT_TOPIC_ARN == null || TEST_RESULT_TOPIC_ARN.isEmpty()) {
            logger.warn("TEST_RESULT_TOPIC_ARN is not configured, skipping SNS publish");
            return;
        }

        try {
            Map<String, Object> message = new HashMap<>();
            message.put("userId", userId);
            message.put("results", results);

            String messageJson = gson.toJson(message);

            PublishRequest publishRequest = PublishRequest.builder()
                    .topicArn(TEST_RESULT_TOPIC_ARN)
                    .message(messageJson)
                    .build();

            snsClient.publish(publishRequest);
            logger.info("Published test result to SNS for user: {}", userId);
        } catch (Exception e) {
            // SNS 발행 실패해도 API 응답에는 영향 없음 (fire-and-forget)
            logger.error("Failed to publish test result to SNS for user: {}", userId, e);
        }
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
