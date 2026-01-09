package com.mzc.secondproject.serverless.domain.vocabulary.handler;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.mzc.secondproject.serverless.common.dto.ApiResponse;
import com.mzc.secondproject.serverless.common.dto.PaginatedResult;
import com.mzc.secondproject.serverless.domain.vocabulary.dto.request.BatchGetWordsRequest;
import com.mzc.secondproject.serverless.domain.vocabulary.dto.request.CreateWordRequest;
import com.mzc.secondproject.serverless.domain.vocabulary.dto.request.CreateWordsBatchRequest;
import com.mzc.secondproject.serverless.common.router.HandlerRouter;
import com.mzc.secondproject.serverless.common.router.Route;
import com.mzc.secondproject.serverless.common.util.ResponseUtil;
import static com.mzc.secondproject.serverless.common.util.ResponseUtil.createResponse;
import com.mzc.secondproject.serverless.domain.vocabulary.model.Word;
import com.mzc.secondproject.serverless.domain.vocabulary.service.WordCommandService;
import com.mzc.secondproject.serverless.domain.vocabulary.service.WordQueryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class WordHandler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    private static final Logger logger = LoggerFactory.getLogger(WordHandler.class);

    private final WordCommandService commandService;
    private final WordQueryService queryService;
    private final HandlerRouter router;

    public WordHandler() {
        this.commandService = new WordCommandService();
        this.queryService = new WordQueryService();
        this.router = initRouter();
    }

    private HandlerRouter initRouter() {
        return new HandlerRouter().addRoutes(
                Route.post("/words/batch/get", this::getWordsBatch),
                Route.post("/words/batch", this::createWordsBatch),
                Route.get("/words/search", this::searchWords),
                Route.post("/words", this::createWord),
                Route.get("/words", this::getWords),
                Route.get("/words/{wordId}", this::getWord),
                Route.put("/words/{wordId}", this::updateWord),
                Route.delete("/words/{wordId}", this::deleteWord)
        );
    }

    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent request, Context context) {
        logger.info("Received request: {} {}", request.getHttpMethod(), request.getPath());
        return router.route(request);
    }

    private APIGatewayProxyResponseEvent createWord(APIGatewayProxyRequestEvent request) {
        String body = request.getBody();
        CreateWordRequest req = ResponseUtil.gson().fromJson(body, CreateWordRequest.class);

        if (req.getEnglish() == null || req.getEnglish().isEmpty()) {
            return createResponse(400, ApiResponse.fail("english is required"));
        }
        if (req.getKorean() == null || req.getKorean().isEmpty()) {
            return createResponse(400, ApiResponse.fail("korean is required"));
        }

        String level = req.getLevel() != null ? req.getLevel() : "BEGINNER";
        String category = req.getCategory() != null ? req.getCategory() : "DAILY";

        Word word = commandService.createWord(req.getEnglish(), req.getKorean(), req.getExample(), level, category);
        return createResponse(201, ApiResponse.ok("Word created", word));
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

        PaginatedResult<Word> wordPage = queryService.getWords(level, category, limit, cursor);

        Map<String, Object> result = new HashMap<>();
        result.put("words", wordPage.items());
        result.put("nextCursor", wordPage.nextCursor());
        result.put("hasMore", wordPage.hasMore());

        return createResponse(200, ApiResponse.ok("Words retrieved", result));
    }

    private APIGatewayProxyResponseEvent getWord(APIGatewayProxyRequestEvent request) {
        Map<String, String> pathParams = request.getPathParameters();
        String wordId = pathParams != null ? pathParams.get("wordId") : null;

        if (wordId == null) {
            return createResponse(400, ApiResponse.fail("wordId is required"));
        }

        Optional<Word> optWord = queryService.getWord(wordId);
        if (optWord.isEmpty()) {
            return createResponse(404, ApiResponse.fail("Word not found"));
        }

        return createResponse(200, ApiResponse.ok("Word retrieved", optWord.get()));
    }

    private APIGatewayProxyResponseEvent updateWord(APIGatewayProxyRequestEvent request) {
        Map<String, String> pathParams = request.getPathParameters();
        String wordId = pathParams != null ? pathParams.get("wordId") : null;

        if (wordId == null) {
            return createResponse(400, ApiResponse.fail("wordId is required"));
        }

        String body = request.getBody();
        Map<String, Object> requestBody = ResponseUtil.gson().fromJson(body, Map.class);

        Word word = commandService.updateWord(wordId, requestBody);
        return createResponse(200, ApiResponse.ok("Word updated", word));
    }

    private APIGatewayProxyResponseEvent deleteWord(APIGatewayProxyRequestEvent request) {
        Map<String, String> pathParams = request.getPathParameters();
        String wordId = pathParams != null ? pathParams.get("wordId") : null;

        if (wordId == null) {
            return createResponse(400, ApiResponse.fail("wordId is required"));
        }

        commandService.deleteWord(wordId);
        return createResponse(200, ApiResponse.ok("Word deleted", null));
    }

    private APIGatewayProxyResponseEvent createWordsBatch(APIGatewayProxyRequestEvent request) {
        String body = request.getBody();
        CreateWordsBatchRequest req = ResponseUtil.gson().fromJson(body, CreateWordsBatchRequest.class);

        if (req.getWords() == null || req.getWords().isEmpty()) {
            return createResponse(400, ApiResponse.fail("words array is required"));
        }

        WordCommandService.BatchResult result = commandService.createWordsBatch(req.getWords());

        Map<String, Object> response = new HashMap<>();
        response.put("successCount", result.successCount());
        response.put("failCount", result.failCount());
        response.put("totalRequested", result.totalRequested());

        return createResponse(201, ApiResponse.ok("Batch completed", response));
    }

    private APIGatewayProxyResponseEvent searchWords(APIGatewayProxyRequestEvent request) {
        Map<String, String> queryParams = request.getQueryStringParameters();

        String query = queryParams != null ? queryParams.get("q") : null;
        String cursor = queryParams != null ? queryParams.get("cursor") : null;

        if (query == null || query.isEmpty()) {
            return createResponse(400, ApiResponse.fail("q (query) parameter is required"));
        }

        int limit = 20;
        if (queryParams.get("limit") != null) {
            limit = Math.min(Integer.parseInt(queryParams.get("limit")), 50);
        }

        PaginatedResult<Word> wordPage = queryService.searchWords(query, limit, cursor);

        Map<String, Object> result = new HashMap<>();
        result.put("words", wordPage.items());
        result.put("query", query);
        result.put("nextCursor", wordPage.nextCursor());
        result.put("hasMore", wordPage.hasMore());

        return createResponse(200, ApiResponse.ok("Search completed", result));
    }

    private APIGatewayProxyResponseEvent getWordsBatch(APIGatewayProxyRequestEvent request) {
        String body = request.getBody();
        BatchGetWordsRequest req = ResponseUtil.gson().fromJson(body, BatchGetWordsRequest.class);

        if (req.getWordIds() == null || req.getWordIds().isEmpty()) {
            return createResponse(400, ApiResponse.fail("wordIds array is required"));
        }

        if (req.getWordIds().size() > 100) {
            return createResponse(400, ApiResponse.fail("Maximum 100 wordIds allowed per request"));
        }

        List<Word> words = queryService.getWordsByIds(req.getWordIds());

        Map<String, Object> result = new HashMap<>();
        result.put("words", words);
        result.put("requestedCount", req.getWordIds().size());
        result.put("retrievedCount", words.size());

        return createResponse(200, ApiResponse.ok("Words retrieved", result));
    }
}
