package com.mzc.secondproject.serverless.domain.speaking.handler;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mzc.secondproject.serverless.common.util.JwtUtil;
import com.mzc.secondproject.serverless.domain.speaking.dto.response.SpeakingResponse;
import com.mzc.secondproject.serverless.domain.speaking.service.SpeakingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Optional;

/**
 * Speaking  API 핸들러
 *
 * POST /api/speaking/chat - 대화 (음성 또는 텍스트)
 * POST /api/speaking/reset - 대화 초기화
 */
public class SpeakingHandler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    private static final Logger logger = LoggerFactory.getLogger(SpeakingHandler.class);
    private static final Gson gson = new GsonBuilder().create();

    private static final Map<String, String> CORS_HEADERS = Map.of(
            "Content-Type", "application/json",
            "Access-Control-Allow-Origin", "*",
            "Access-Control-Allow-Headers", "Content-Type,Authorization",
            "Access-Control-Allow-Methods", "POST,OPTIONS"
    );

    private final SpeakingService speakingService;

    public SpeakingHandler() {
        this.speakingService = new SpeakingService();
    }

    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent event, Context context) {
        logger.info("Speaking  API request received");

        // OPTIONS 요청 처리 (CORS preflight)
        if ("OPTIONS".equalsIgnoreCase(event.getHttpMethod())) {
            return response(200, Map.of("message", "OK"));
        }

        try {
            // 사용자 인증 정보 추출 (Cognito Authorizer -> requestContext)
            if (event.getRequestContext() == null || event.getRequestContext().getAuthorizer() == null) {
                logger.error("No Authorizer found in request context");
                return response(401, Map.of("error", "Unauthorized: User context missing"));
            }

            Map<String, Object> authorizer = event.getRequestContext().getAuthorizer();
            Map<String, Object> claims = (Map<String, Object>) authorizer.get("claims");

            if (claims == null) {
                return response(401, Map.of("error", "Unauthorized: Claims missing"));
            }

            String userId = (String) claims.get("sub"); // Cognito User Pool의 고유 ID (UUID 형태)

            // 요청 정보 추출
            String path = event.getPath();
            String body = event.getBody();

            logger.info("Processing request: path={}, userId={}", path, userId);

            // 라우팅
            if (path != null && path.endsWith("/chat")) {
                return handleChat(userId, body);
            } else if (path != null && path.endsWith("/reset")) {
                return handleReset(userId, body);
            } else {
                return response(404, Map.of("error", "Not found"));
            }

        } catch (Exception e) {
            logger.error("Error processing request: {}", e.getMessage(), e);
            return response(500, Map.of("error", "Internal server error: " + e.getMessage()));
        }
    }

    /**
     * 대화 처리 (음성 또는 텍스트)
     */
    private APIGatewayProxyResponseEvent handleChat(String userId, String body) {
        if (body == null || body.isEmpty()) {
            return response(400, Map.of("error", "Request body is required"));
        }

        JsonObject request = JsonParser.parseString(body).getAsJsonObject();

        String sessionId = request.has("sessionId") && !request.get("sessionId").isJsonNull()
                ? request.get("sessionId").getAsString() : null;
        String level = request.has("level") && !request.get("level").isJsonNull()
                ? request.get("level").getAsString() : "INTERMEDIATE";
        String audio = request.has("audio") && !request.get("audio").isJsonNull()
                ? request.get("audio").getAsString() : null;
        String text = request.has("text") && !request.get("text").isJsonNull()
                ? request.get("text").getAsString() : null;

        SpeakingResponse result;

        if (audio != null && !audio.isEmpty()) {
            // 음성 입력 처리
            logger.info("Processing voice event");
            result = speakingService.processVoiceInput(sessionId, userId, audio, level);
        } else if (text != null && !text.trim().isEmpty()) {
            // 텍스트 입력 처리
            logger.info("Processing text event: {}", text);
            result = speakingService.processTextInput(sessionId, userId, text.trim(), level);
        } else {
            return response(400, Map.of("error", "Either 'audio' or 'text' is required"));
        }

        return response(200, Map.of(
                "sessionId", result.sessionId(),
                "userTranscript", result.userTranscript(),
                "aiText", result.aiText(),
                "aiAudioUrl", result.aiAudioUrl(),
                "confidence", result.confidence()
        ));
    }

    /**
     * 대화 초기화
     */
    private APIGatewayProxyResponseEvent handleReset(String userId, String body) {
        if (body == null || body.isEmpty()) {
            return response(400, Map.of("error", "Request body is required"));
        }

        JsonObject request = JsonParser.parseString(body).getAsJsonObject();
        String sessionId = request.has("sessionId") ? request.get("sessionId").getAsString() : null;

        if (sessionId == null || sessionId.isEmpty()) {
            return response(400, Map.of("error", "sessionId is required"));
        }

        speakingService.resetConversation(sessionId);

        return response(200, Map.of(
                "message", "Conversation reset successfully",
                "sessionId", sessionId
        ));
    }

    private APIGatewayProxyResponseEvent response(int statusCode, Map<String, Object> body) {
        return new APIGatewayProxyResponseEvent()
                .withStatusCode(statusCode)
                .withHeaders(CORS_HEADERS)
                .withBody(gson.toJson(body));
    }


}
