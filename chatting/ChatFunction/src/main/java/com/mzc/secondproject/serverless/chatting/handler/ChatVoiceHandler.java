package com.mzc.secondproject.serverless.chatting.handler;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mzc.secondproject.serverless.chatting.dto.ApiResponse;
import com.mzc.secondproject.serverless.chatting.model.ChatMessage;
import com.mzc.secondproject.serverless.chatting.repository.ChatMessageRepository;
import com.mzc.secondproject.serverless.chatting.service.PollyService;
import com.mzc.secondproject.serverless.chatting.service.PollyService.VoiceSynthesisResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Optional;

public class ChatVoiceHandler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    private static final Logger logger = LoggerFactory.getLogger(ChatVoiceHandler.class);
    private static final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    private final PollyService pollyService;
    private final ChatMessageRepository messageRepository;

    public ChatVoiceHandler() {
        this.pollyService = new PollyService();
        this.messageRepository = new ChatMessageRepository();
    }

    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent request, Context context) {
        logger.info("Received voice synthesis request");

        try {
            if (!"POST".equals(request.getHttpMethod())) {
                return createResponse(405, ApiResponse.error("Method not allowed"));
            }

            String body = request.getBody();
            Map<String, String> requestBody = gson.fromJson(body, Map.class);
            String messageId = requestBody.get("messageId");
            String roomId = requestBody.get("roomId");
            String voice = requestBody.getOrDefault("voice", "FEMALE");

            if (messageId == null || messageId.isEmpty()) {
                return createResponse(400, ApiResponse.error("messageId is required"));
            }
            if (roomId == null || roomId.isEmpty()) {
                return createResponse(400, ApiResponse.error("roomId is required"));
            }

            // 메시지 조회
            Optional<ChatMessage> messageOpt = messageRepository.findByRoomIdAndMessageId(roomId, messageId);
            if (messageOpt.isEmpty()) {
                return createResponse(404, ApiResponse.error("Message not found"));
            }

            ChatMessage message = messageOpt.get();
            boolean isMale = "MALE".equalsIgnoreCase(voice);

            // 캐시된 음성 키 확인
            String cachedKey = isMale ? message.getMaleVoiceKey() : message.getFemaleVoiceKey();

            String audioUrl;
            boolean cached;

            if (cachedKey != null && !cachedKey.isEmpty()) {
                // 캐시 히트: DynamoDB에 키가 있으면 S3에서 URL 생성
                logger.info("DB cache hit for message: {}, voice: {}", messageId, voice);
                audioUrl = pollyService.getPresignedUrl(cachedKey);
                cached = true;
            } else {
                // 캐시 미스: Polly 변환 → S3 저장 → DynamoDB 업데이트
                VoiceSynthesisResult result = pollyService.synthesizeSpeechForMessage(
                        messageId, message.getContent(), voice);

                // DynamoDB에 S3 키 저장
                if (isMale) {
                    message.setMaleVoiceKey(result.getS3Key());
                } else {
                    message.setFemaleVoiceKey(result.getS3Key());
                }
                messageRepository.save(message);

                audioUrl = result.getAudioUrl();
                cached = result.isCached();
            }

            return createResponse(200, ApiResponse.success(
                    cached ? "Speech retrieved from cache" : "Speech synthesized",
                    Map.of(
                            "audioUrl", audioUrl,
                            "cached", cached
                    )
            ));

        } catch (Exception e) {
            logger.error("Error synthesizing speech", e);
            return createResponse(500, ApiResponse.error("Internal server error: " + e.getMessage()));
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
