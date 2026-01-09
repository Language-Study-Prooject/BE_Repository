package com.mzc.secondproject.serverless.domain.vocabulary.handler;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.mzc.secondproject.serverless.common.dto.ApiResponse;
import com.mzc.secondproject.serverless.domain.vocabulary.dto.request.SynthesizeVoiceRequest;
import com.mzc.secondproject.serverless.common.util.ResponseUtil;
import static com.mzc.secondproject.serverless.common.util.ResponseUtil.createResponse;
import com.mzc.secondproject.serverless.domain.vocabulary.model.Word;
import com.mzc.secondproject.serverless.domain.vocabulary.repository.WordRepository;
import com.mzc.secondproject.serverless.common.service.PollyService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class VoiceHandler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    private static final Logger logger = LoggerFactory.getLogger(VoiceHandler.class);
    private static final String BUCKET_NAME = System.getenv("VOCAB_BUCKET_NAME");

    private final WordRepository wordRepository;
    private final PollyService pollyService;

    public VoiceHandler() {
        this.wordRepository = new WordRepository();
        this.pollyService = new PollyService(BUCKET_NAME, "vocab/voice/");
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

            return createResponse(404, ApiResponse.fail("Not found"));

        } catch (Exception e) {
            logger.error("Error handling request", e);
            return createResponse(500, ApiResponse.fail("Internal server error: " + e.getMessage()));
        }
    }

    private APIGatewayProxyResponseEvent synthesizeSpeech(APIGatewayProxyRequestEvent request) {
        String body = request.getBody();
        SynthesizeVoiceRequest req = ResponseUtil.gson().fromJson(body, SynthesizeVoiceRequest.class);

        if (req.getWordId() == null || req.getWordId().isEmpty()) {
            return createResponse(400, ApiResponse.fail("wordId is required"));
        }

        String wordId = req.getWordId();
        String voice = req.getVoice() != null ? req.getVoice() : "FEMALE";
        String type = req.getType() != null ? req.getType() : "WORD";

        // 단어 조회
        Optional<Word> optWord = wordRepository.findById(wordId);
        if (optWord.isEmpty()) {
            return createResponse(404, ApiResponse.fail("Word not found"));
        }

        Word word = optWord.get();
        boolean isMale = "MALE".equalsIgnoreCase(voice);
        boolean isExample = "EXAMPLE".equalsIgnoreCase(type);

        // 예문 요청인데 예문이 없는 경우
        if (isExample && (word.getExample() == null || word.getExample().isEmpty())) {
            return createResponse(400, ApiResponse.fail("This word has no example sentence"));
        }

        // 음성 합성할 텍스트 결정
        String textToSynthesize = isExample ? word.getExample() : word.getEnglish();

        // 캐시 키 결정
        String cachedKey = getCachedKey(word, isMale, isExample);
        String audioUrl;
        boolean cached = false;

        if (cachedKey != null && !cachedKey.isEmpty()) {
            // DB에 캐시 키가 있으면 Pre-signed URL 생성
            audioUrl = pollyService.getPresignedUrl(cachedKey);
            cached = true;
            logger.info("Cache hit from DB: wordId={}, voice={}, type={}", wordId, voice, type);
        } else {
            // 캐시 미스: Polly 변환 후 S3 저장
            String s3KeySuffix = isExample ? "_example" : "";
            PollyService.VoiceSynthesisResult result = pollyService.synthesizeSpeech(
                    wordId + s3KeySuffix, textToSynthesize, voice);

            audioUrl = result.getAudioUrl();
            cached = result.isCached();

            // DynamoDB에 S3 키 저장
            setCachedKey(word, isMale, isExample, result.getS3Key());
            wordRepository.save(word);
            logger.info("Saved voice cache to DB: wordId={}, voice={}, type={}", wordId, voice, type);
        }

        Map<String, Object> responseData = new HashMap<>();
        responseData.put("wordId", wordId);
        responseData.put("text", textToSynthesize);
        responseData.put("type", type);
        responseData.put("voice", voice);
        responseData.put("audioUrl", audioUrl);
        responseData.put("cached", cached);

        return createResponse(200, ApiResponse.ok("Speech synthesized", responseData));
    }

    private String getCachedKey(Word word, boolean isMale, boolean isExample) {
        if (isExample) {
            return isMale ? word.getMaleExampleVoiceKey() : word.getFemaleExampleVoiceKey();
        } else {
            return isMale ? word.getMaleVoiceKey() : word.getFemaleVoiceKey();
        }
    }

    private void setCachedKey(Word word, boolean isMale, boolean isExample, String s3Key) {
        if (isExample) {
            if (isMale) {
                word.setMaleExampleVoiceKey(s3Key);
            } else {
                word.setFemaleExampleVoiceKey(s3Key);
            }
        } else {
            if (isMale) {
                word.setMaleVoiceKey(s3Key);
            } else {
                word.setFemaleVoiceKey(s3Key);
            }
        }
    }
}
