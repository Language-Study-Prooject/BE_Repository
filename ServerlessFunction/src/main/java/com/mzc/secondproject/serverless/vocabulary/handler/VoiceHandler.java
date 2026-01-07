package com.mzc.secondproject.serverless.vocabulary.handler;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mzc.secondproject.serverless.common.dto.ApiResponse;
import com.mzc.secondproject.serverless.vocabulary.model.Word;
import com.mzc.secondproject.serverless.vocabulary.repository.WordRepository;
import com.mzc.secondproject.serverless.vocabulary.service.PollyService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class VoiceHandler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    private static final Logger logger = LoggerFactory.getLogger(VoiceHandler.class);
    private static final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    private final WordRepository wordRepository;
    private final PollyService pollyService;

    public VoiceHandler() {
        this.wordRepository = new WordRepository();
        this.pollyService = new PollyService();
    }

    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent request, Context context) {
        String httpMethod = request.getHttpMethod();
        String path = request.getPath();

        logger.info("Received request: {} {}", httpMethod, path);

        try {
            // POST /vocab/voice/synthesize - 음성 합성
            if ("POST".equals(httpMethod) && path.endsWith("/synthesize")) {
                return synthesizeSpeech(request);
            }

            return createResponse(404, ApiResponse.error("Not found"));

        } catch (Exception e) {
            logger.error("Error handling request", e);
            return createResponse(500, ApiResponse.error("Internal server error: " + e.getMessage()));
        }
    }

    private APIGatewayProxyResponseEvent synthesizeSpeech(APIGatewayProxyRequestEvent request) {
        String body = request.getBody();
        Map<String, Object> requestBody = gson.fromJson(body, Map.class);

        String wordId = (String) requestBody.get("wordId");
        String voice = (String) requestBody.getOrDefault("voice", "FEMALE");

        if (wordId == null || wordId.isEmpty()) {
            return createResponse(400, ApiResponse.error("wordId is required"));
        }

        // 단어 조회
        Optional<Word> optWord = wordRepository.findById(wordId);
        if (optWord.isEmpty()) {
            return createResponse(404, ApiResponse.error("Word not found"));
        }

        Word word = optWord.get();
        boolean isMale = "MALE".equalsIgnoreCase(voice);

        // 캐시 확인: DynamoDB에 저장된 S3 키 확인
        String cachedKey = isMale ? word.getMaleVoiceKey() : word.getFemaleVoiceKey();
        String audioUrl;
        boolean cached = false;

        if (cachedKey != null && !cachedKey.isEmpty()) {
            // DB에 캐시 키가 있으면 Pre-signed URL 생성
            audioUrl = pollyService.getPresignedUrl(cachedKey);
            cached = true;
            logger.info("Cache hit from DB: wordId={}, voice={}", wordId, voice);
        } else {
            // 캐시 미스: Polly 변환 후 S3 저장
            PollyService.VoiceSynthesisResult result = pollyService.synthesizeSpeechForWord(
                    wordId, word.getEnglish(), voice);

            audioUrl = result.getAudioUrl();
            cached = result.isCached();

            // DynamoDB에 S3 키 저장
            if (isMale) {
                word.setMaleVoiceKey(result.getS3Key());
            } else {
                word.setFemaleVoiceKey(result.getS3Key());
            }
            wordRepository.save(word);
            logger.info("Saved voice cache to DB: wordId={}, voice={}", wordId, voice);
        }

        Map<String, Object> responseData = new HashMap<>();
        responseData.put("wordId", wordId);
        responseData.put("english", word.getEnglish());
        responseData.put("voice", voice);
        responseData.put("audioUrl", audioUrl);
        responseData.put("cached", cached);

        return createResponse(200, ApiResponse.success("Speech synthesized", responseData));
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
