package com.mzc.secondproject.serverless.vocabulary.handler;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mzc.secondproject.serverless.vocabulary.dto.ApiResponse;
import com.mzc.secondproject.serverless.vocabulary.model.Word;
import com.mzc.secondproject.serverless.vocabulary.repository.WordRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public class WordHandler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    private static final Logger logger = LoggerFactory.getLogger(WordHandler.class);
    private static final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    private final WordRepository wordRepository;

    public WordHandler() {
        this.wordRepository = new WordRepository();
    }

    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent request, Context context) {
        String httpMethod = request.getHttpMethod();
        String path = request.getPath();

        logger.info("Received request: {} {}", httpMethod, path);

        try {
            // POST /vocab/words - 단어 생성
            if ("POST".equals(httpMethod) && path.endsWith("/words")) {
                return createWord(request);
            }

            // GET /vocab/words - 단어 목록 조회
            if ("GET".equals(httpMethod) && path.endsWith("/words")) {
                return getWords(request);
            }

            // GET /vocab/words/{wordId} - 단어 상세 조회
            if ("GET".equals(httpMethod) && path.contains("/words/")) {
                return getWord(request);
            }

            // PUT /vocab/words/{wordId} - 단어 수정
            if ("PUT".equals(httpMethod) && path.contains("/words/")) {
                return updateWord(request);
            }

            // DELETE /vocab/words/{wordId} - 단어 삭제
            if ("DELETE".equals(httpMethod) && path.contains("/words/")) {
                return deleteWord(request);
            }

            return createResponse(404, ApiResponse.error("Not found"));

        } catch (Exception e) {
            logger.error("Error handling request", e);
            return createResponse(500, ApiResponse.error("Internal server error: " + e.getMessage()));
        }
    }

    private APIGatewayProxyResponseEvent createWord(APIGatewayProxyRequestEvent request) {
        String body = request.getBody();
        Map<String, Object> requestBody = gson.fromJson(body, Map.class);

        String english = (String) requestBody.get("english");
        String korean = (String) requestBody.get("korean");
        String example = (String) requestBody.get("example");
        String level = (String) requestBody.getOrDefault("level", "BEGINNER");
        String category = (String) requestBody.getOrDefault("category", "DAILY");

        if (english == null || english.isEmpty()) {
            return createResponse(400, ApiResponse.error("english is required"));
        }
        if (korean == null || korean.isEmpty()) {
            return createResponse(400, ApiResponse.error("korean is required"));
        }

        String wordId = UUID.randomUUID().toString();
        String now = Instant.now().toString();

        Word word = Word.builder()
                .pk("WORD#" + wordId)
                .sk("METADATA")
                .gsi1pk("LEVEL#" + level)
                .gsi1sk("WORD#" + wordId)
                .gsi2pk("CATEGORY#" + category)
                .gsi2sk("WORD#" + wordId)
                .wordId(wordId)
                .english(english)
                .korean(korean)
                .example(example)
                .level(level)
                .category(category)
                .createdAt(now)
                .build();

        wordRepository.save(word);

        logger.info("Created word: {}", wordId);
        return createResponse(201, ApiResponse.success("Word created", word));
    }

    private APIGatewayProxyResponseEvent getWords(APIGatewayProxyRequestEvent request) {
        Map<String, String> queryParams = request.getQueryStringParameters();

        String level = queryParams != null ? queryParams.get("level") : null;
        String category = queryParams != null ? queryParams.get("category") : null;
        String cursor = queryParams != null ? queryParams.get("cursor") : null;

        int limit = 20;
        if (queryParams != null && queryParams.get("limit") != null) {
            limit = Math.min(Integer.parseInt(queryParams.get("limit")), 50);
        }

        WordRepository.WordPage wordPage;
        if (level != null && !level.isEmpty()) {
            wordPage = wordRepository.findByLevelWithPagination(level, limit, cursor);
        } else if (category != null && !category.isEmpty()) {
            wordPage = wordRepository.findByCategoryWithPagination(category, limit, cursor);
        } else {
            // 기본: BEGINNER 레벨
            wordPage = wordRepository.findByLevelWithPagination("BEGINNER", limit, cursor);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("words", wordPage.getWords());
        result.put("nextCursor", wordPage.getNextCursor());
        result.put("hasMore", wordPage.hasMore());

        return createResponse(200, ApiResponse.success("Words retrieved", result));
    }

    private APIGatewayProxyResponseEvent getWord(APIGatewayProxyRequestEvent request) {
        Map<String, String> pathParams = request.getPathParameters();
        String wordId = pathParams != null ? pathParams.get("wordId") : null;

        if (wordId == null) {
            return createResponse(400, ApiResponse.error("wordId is required"));
        }

        Optional<Word> optWord = wordRepository.findById(wordId);
        if (optWord.isEmpty()) {
            return createResponse(404, ApiResponse.error("Word not found"));
        }

        return createResponse(200, ApiResponse.success("Word retrieved", optWord.get()));
    }

    private APIGatewayProxyResponseEvent updateWord(APIGatewayProxyRequestEvent request) {
        Map<String, String> pathParams = request.getPathParameters();
        String wordId = pathParams != null ? pathParams.get("wordId") : null;

        if (wordId == null) {
            return createResponse(400, ApiResponse.error("wordId is required"));
        }

        Optional<Word> optWord = wordRepository.findById(wordId);
        if (optWord.isEmpty()) {
            return createResponse(404, ApiResponse.error("Word not found"));
        }

        Word word = optWord.get();
        String body = request.getBody();
        Map<String, Object> requestBody = gson.fromJson(body, Map.class);

        if (requestBody.containsKey("english")) {
            word.setEnglish((String) requestBody.get("english"));
        }
        if (requestBody.containsKey("korean")) {
            word.setKorean((String) requestBody.get("korean"));
        }
        if (requestBody.containsKey("example")) {
            word.setExample((String) requestBody.get("example"));
        }
        if (requestBody.containsKey("level")) {
            String newLevel = (String) requestBody.get("level");
            word.setLevel(newLevel);
            word.setGsi1pk("LEVEL#" + newLevel);
        }
        if (requestBody.containsKey("category")) {
            String newCategory = (String) requestBody.get("category");
            word.setCategory(newCategory);
            word.setGsi2pk("CATEGORY#" + newCategory);
        }

        wordRepository.save(word);

        logger.info("Updated word: {}", wordId);
        return createResponse(200, ApiResponse.success("Word updated", word));
    }

    private APIGatewayProxyResponseEvent deleteWord(APIGatewayProxyRequestEvent request) {
        Map<String, String> pathParams = request.getPathParameters();
        String wordId = pathParams != null ? pathParams.get("wordId") : null;

        if (wordId == null) {
            return createResponse(400, ApiResponse.error("wordId is required"));
        }

        Optional<Word> optWord = wordRepository.findById(wordId);
        if (optWord.isEmpty()) {
            return createResponse(404, ApiResponse.error("Word not found"));
        }

        wordRepository.delete(wordId);

        logger.info("Deleted word: {}", wordId);
        return createResponse(200, ApiResponse.success("Word deleted", null));
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
