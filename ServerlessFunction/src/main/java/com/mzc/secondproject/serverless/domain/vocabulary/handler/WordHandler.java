package com.mzc.secondproject.serverless.domain.vocabulary.handler;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.mzc.secondproject.serverless.common.dto.ApiResponse;
import com.mzc.secondproject.serverless.common.dto.PaginatedResult;
import com.mzc.secondproject.serverless.common.util.ResponseUtil;
import static com.mzc.secondproject.serverless.common.util.ResponseUtil.createResponse;
import com.mzc.secondproject.serverless.domain.vocabulary.model.Word;
import com.mzc.secondproject.serverless.domain.vocabulary.service.WordService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class WordHandler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    private static final Logger logger = LoggerFactory.getLogger(WordHandler.class);

    private final WordService wordService;

    public WordHandler() {
        this.wordService = new WordService();
    }

    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent request, Context context) {
        String httpMethod = request.getHttpMethod();
        String path = request.getPath();

        logger.info("Received request: {} {}", httpMethod, path);

        try {
            if ("POST".equals(httpMethod) && path.endsWith("/batch")) {
                return createWordsBatch(request);
            }

            if ("GET".equals(httpMethod) && path.endsWith("/search")) {
                return searchWords(request);
            }

            if ("POST".equals(httpMethod) && path.endsWith("/words")) {
                return createWord(request);
            }

            if ("GET".equals(httpMethod) && path.endsWith("/words")) {
                return getWords(request);
            }

            if ("GET".equals(httpMethod) && path.contains("/words/") && !path.contains("/search") && !path.contains("/batch")) {
                return getWord(request);
            }

            if ("PUT".equals(httpMethod) && path.contains("/words/")) {
                return updateWord(request);
            }

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
        Map<String, Object> requestBody = ResponseUtil.gson().fromJson(body, Map.class);

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

        Word word = wordService.createWord(english, korean, example, level, category);
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

        PaginatedResult<Word> wordPage = wordService.getWords(level, category, limit, cursor);

        Map<String, Object> result = new HashMap<>();
        result.put("words", wordPage.getItems());
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

        Optional<Word> optWord = wordService.getWord(wordId);
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

        String body = request.getBody();
        Map<String, Object> requestBody = ResponseUtil.gson().fromJson(body, Map.class);

        try {
            Word word = wordService.updateWord(wordId, requestBody);
            return createResponse(200, ApiResponse.success("Word updated", word));
        } catch (IllegalArgumentException e) {
            return createResponse(404, ApiResponse.error(e.getMessage()));
        }
    }

    private APIGatewayProxyResponseEvent deleteWord(APIGatewayProxyRequestEvent request) {
        Map<String, String> pathParams = request.getPathParameters();
        String wordId = pathParams != null ? pathParams.get("wordId") : null;

        if (wordId == null) {
            return createResponse(400, ApiResponse.error("wordId is required"));
        }

        try {
            wordService.deleteWord(wordId);
            return createResponse(200, ApiResponse.success("Word deleted", null));
        } catch (IllegalArgumentException e) {
            return createResponse(404, ApiResponse.error(e.getMessage()));
        }
    }

    @SuppressWarnings("unchecked")
    private APIGatewayProxyResponseEvent createWordsBatch(APIGatewayProxyRequestEvent request) {
        String body = request.getBody();
        Map<String, Object> requestBody = ResponseUtil.gson().fromJson(body, Map.class);

        List<Map<String, Object>> wordsList = (List<Map<String, Object>>) requestBody.get("words");
        if (wordsList == null || wordsList.isEmpty()) {
            return createResponse(400, ApiResponse.error("words array is required"));
        }

        WordService.BatchResult result = wordService.createWordsBatch(wordsList);

        Map<String, Object> response = new HashMap<>();
        response.put("successCount", result.successCount());
        response.put("failCount", result.failCount());
        response.put("totalRequested", result.totalRequested());

        return createResponse(201, ApiResponse.success("Batch completed", response));
    }

    private APIGatewayProxyResponseEvent searchWords(APIGatewayProxyRequestEvent request) {
        Map<String, String> queryParams = request.getQueryStringParameters();

        String query = queryParams != null ? queryParams.get("q") : null;
        String cursor = queryParams != null ? queryParams.get("cursor") : null;

        if (query == null || query.isEmpty()) {
            return createResponse(400, ApiResponse.error("q (query) parameter is required"));
        }

        int limit = 20;
        if (queryParams.get("limit") != null) {
            limit = Math.min(Integer.parseInt(queryParams.get("limit")), 50);
        }

        PaginatedResult<Word> wordPage = wordService.searchWords(query, limit, cursor);

        Map<String, Object> result = new HashMap<>();
        result.put("words", wordPage.getItems());
        result.put("query", query);
        result.put("nextCursor", wordPage.getNextCursor());
        result.put("hasMore", wordPage.hasMore());

        return createResponse(200, ApiResponse.success("Search completed", result));
    }
}
