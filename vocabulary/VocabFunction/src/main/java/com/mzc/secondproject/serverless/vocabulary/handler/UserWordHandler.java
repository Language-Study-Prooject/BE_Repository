package com.mzc.secondproject.serverless.vocabulary.handler;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mzc.secondproject.serverless.vocabulary.dto.ApiResponse;
import com.mzc.secondproject.serverless.vocabulary.model.UserWord;
import com.mzc.secondproject.serverless.vocabulary.repository.UserWordRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class UserWordHandler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    private static final Logger logger = LoggerFactory.getLogger(UserWordHandler.class);
    private static final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    private final UserWordRepository userWordRepository;

    public UserWordHandler() {
        this.userWordRepository = new UserWordRepository();
    }

    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent request, Context context) {
        String httpMethod = request.getHttpMethod();
        String path = request.getPath();

        logger.info("Received request: {} {}", httpMethod, path);

        try {
            // GET /vocab/users/{userId}/words - 사용자 단어 목록
            if ("GET".equals(httpMethod) && path.endsWith("/words")) {
                return getUserWords(request);
            }

            // GET /vocab/users/{userId}/words/{wordId} - 사용자 단어 상세
            if ("GET".equals(httpMethod) && path.contains("/words/")) {
                return getUserWord(request);
            }

            // PUT /vocab/users/{userId}/words/{wordId}/tag - 태그 변경
            if ("PUT".equals(httpMethod) && path.endsWith("/tag")) {
                return updateUserWordTag(request);
            }

            // PUT /vocab/users/{userId}/words/{wordId} - 학습 상태 업데이트
            if ("PUT".equals(httpMethod) && path.contains("/words/")) {
                return updateUserWord(request);
            }

            return createResponse(404, ApiResponse.error("Not found"));

        } catch (Exception e) {
            logger.error("Error handling request", e);
            return createResponse(500, ApiResponse.error("Internal server error: " + e.getMessage()));
        }
    }

    private APIGatewayProxyResponseEvent getUserWords(APIGatewayProxyRequestEvent request) {
        Map<String, String> pathParams = request.getPathParameters();
        Map<String, String> queryParams = request.getQueryStringParameters();

        String userId = pathParams != null ? pathParams.get("userId") : null;
        String status = queryParams != null ? queryParams.get("status") : null;
        String cursor = queryParams != null ? queryParams.get("cursor") : null;
        String bookmarked = queryParams != null ? queryParams.get("bookmarked") : null;
        String incorrectOnly = queryParams != null ? queryParams.get("incorrectOnly") : null;

        if (userId == null) {
            return createResponse(400, ApiResponse.error("userId is required"));
        }

        int limit = 20;
        if (queryParams != null && queryParams.get("limit") != null) {
            limit = Math.min(Integer.parseInt(queryParams.get("limit")), 50);
        }

        UserWordRepository.UserWordPage userWordPage;

        // 필터 우선순위: bookmarked > incorrectOnly > status > 전체
        if ("true".equalsIgnoreCase(bookmarked)) {
            userWordPage = userWordRepository.findBookmarkedWords(userId, limit, cursor);
        } else if ("true".equalsIgnoreCase(incorrectOnly)) {
            userWordPage = userWordRepository.findIncorrectWords(userId, limit, cursor);
        } else if (status != null && !status.isEmpty()) {
            userWordPage = userWordRepository.findByUserIdAndStatus(userId, status, limit, cursor);
        } else {
            userWordPage = userWordRepository.findByUserIdWithPagination(userId, limit, cursor);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("userWords", userWordPage.getUserWords());
        result.put("nextCursor", userWordPage.getNextCursor());
        result.put("hasMore", userWordPage.hasMore());

        return createResponse(200, ApiResponse.success("User words retrieved", result));
    }

    private APIGatewayProxyResponseEvent getUserWord(APIGatewayProxyRequestEvent request) {
        Map<String, String> pathParams = request.getPathParameters();
        String userId = pathParams != null ? pathParams.get("userId") : null;
        String wordId = pathParams != null ? pathParams.get("wordId") : null;

        if (userId == null || wordId == null) {
            return createResponse(400, ApiResponse.error("userId and wordId are required"));
        }

        Optional<UserWord> optUserWord = userWordRepository.findByUserIdAndWordId(userId, wordId);
        if (optUserWord.isEmpty()) {
            return createResponse(404, ApiResponse.error("UserWord not found"));
        }

        return createResponse(200, ApiResponse.success("UserWord retrieved", optUserWord.get()));
    }

    private APIGatewayProxyResponseEvent updateUserWord(APIGatewayProxyRequestEvent request) {
        Map<String, String> pathParams = request.getPathParameters();
        String userId = pathParams != null ? pathParams.get("userId") : null;
        String wordId = pathParams != null ? pathParams.get("wordId") : null;

        if (userId == null || wordId == null) {
            return createResponse(400, ApiResponse.error("userId and wordId are required"));
        }

        String body = request.getBody();
        Map<String, Object> requestBody = gson.fromJson(body, Map.class);

        // 정답/오답 여부
        Boolean isCorrect = (Boolean) requestBody.get("isCorrect");
        if (isCorrect == null) {
            return createResponse(400, ApiResponse.error("isCorrect is required"));
        }

        Optional<UserWord> optUserWord = userWordRepository.findByUserIdAndWordId(userId, wordId);
        UserWord userWord;
        String now = Instant.now().toString();

        if (optUserWord.isEmpty()) {
            // 새로운 UserWord 생성
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

        // Spaced Repetition 알고리즘 적용
        applySpacedRepetition(userWord, isCorrect);
        userWord.setUpdatedAt(now);
        userWord.setLastReviewedAt(now);

        // GSI 업데이트
        userWord.setGsi1sk("DATE#" + userWord.getNextReviewAt());
        userWord.setGsi2sk("STATUS#" + userWord.getStatus());

        userWordRepository.save(userWord);

        logger.info("Updated user word: userId={}, wordId={}, isCorrect={}", userId, wordId, isCorrect);
        return createResponse(200, ApiResponse.success("UserWord updated", userWord));
    }

    /**
     * 사용자 단어 태그 변경 (북마크, 즐겨찾기, 난이도)
     */
    private APIGatewayProxyResponseEvent updateUserWordTag(APIGatewayProxyRequestEvent request) {
        Map<String, String> pathParams = request.getPathParameters();
        String userId = pathParams != null ? pathParams.get("userId") : null;
        String wordId = pathParams != null ? pathParams.get("wordId") : null;

        if (userId == null || wordId == null) {
            return createResponse(400, ApiResponse.error("userId and wordId are required"));
        }

        String body = request.getBody();
        Map<String, Object> requestBody = gson.fromJson(body, Map.class);

        Optional<UserWord> optUserWord = userWordRepository.findByUserIdAndWordId(userId, wordId);
        UserWord userWord;
        String now = Instant.now().toString();

        if (optUserWord.isEmpty()) {
            // 새로운 UserWord 생성 (태그만 설정)
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

        // 태그 업데이트
        if (requestBody.containsKey("bookmarked")) {
            userWord.setBookmarked((Boolean) requestBody.get("bookmarked"));
        }
        if (requestBody.containsKey("favorite")) {
            userWord.setFavorite((Boolean) requestBody.get("favorite"));
        }
        if (requestBody.containsKey("difficulty")) {
            String difficulty = (String) requestBody.get("difficulty");
            if (difficulty != null && (difficulty.equals("EASY") || difficulty.equals("NORMAL") || difficulty.equals("HARD"))) {
                userWord.setDifficulty(difficulty);
            } else if (difficulty != null) {
                return createResponse(400, ApiResponse.error("difficulty must be EASY, NORMAL, or HARD"));
            }
        }

        userWord.setUpdatedAt(now);
        userWordRepository.save(userWord);

        logger.info("Updated user word tag: userId={}, wordId={}", userId, wordId);
        return createResponse(200, ApiResponse.success("Tag updated", userWord));
    }

    /**
     * SM-2 Spaced Repetition 알고리즘 적용
     */
    private void applySpacedRepetition(UserWord userWord, boolean isCorrect) {
        if (isCorrect) {
            userWord.setCorrectCount(userWord.getCorrectCount() + 1);
            userWord.setRepetitions(userWord.getRepetitions() + 1);

            // 간격 계산
            if (userWord.getRepetitions() == 1) {
                userWord.setInterval(1);
            } else if (userWord.getRepetitions() == 2) {
                userWord.setInterval(6);
            } else {
                int newInterval = (int) Math.round(userWord.getInterval() * userWord.getEaseFactor());
                userWord.setInterval(newInterval);
            }

            // 상태 업데이트
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

            // easeFactor 감소 (최소 1.3)
            double newEaseFactor = userWord.getEaseFactor() - 0.2;
            userWord.setEaseFactor(Math.max(1.3, newEaseFactor));
        }

        // 다음 복습일 계산
        LocalDate nextReview = LocalDate.now().plusDays(userWord.getInterval());
        userWord.setNextReviewAt(nextReview.toString());
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
