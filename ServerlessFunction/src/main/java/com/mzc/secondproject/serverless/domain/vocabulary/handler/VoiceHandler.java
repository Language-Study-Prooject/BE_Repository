package com.mzc.secondproject.serverless.domain.vocabulary.handler;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.mzc.secondproject.serverless.common.exception.CommonErrorCode;
import com.mzc.secondproject.serverless.common.service.PollyService;
import com.mzc.secondproject.serverless.common.util.ResponseGenerator;
import com.mzc.secondproject.serverless.common.validation.BeanValidator;
import com.mzc.secondproject.serverless.domain.vocabulary.dto.request.SynthesizeVoiceRequest;
import com.mzc.secondproject.serverless.domain.vocabulary.exception.VocabularyErrorCode;
import com.mzc.secondproject.serverless.domain.vocabulary.model.Word;
import com.mzc.secondproject.serverless.domain.vocabulary.repository.WordRepository;
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
			if ("POST".equals(httpMethod) && path.endsWith("/synthesize")) {
				return synthesizeSpeech(request);
			}
			
			return ResponseGenerator.fail(CommonErrorCode.RESOURCE_NOT_FOUND);
			
		} catch (Exception e) {
			logger.error("Error handling request", e);
			return ResponseGenerator.fail(CommonErrorCode.INTERNAL_SERVER_ERROR);
		}
	}
	
	private APIGatewayProxyResponseEvent synthesizeSpeech(APIGatewayProxyRequestEvent request) {
		String body = request.getBody();
		SynthesizeVoiceRequest req = ResponseGenerator.gson().fromJson(body, SynthesizeVoiceRequest.class);
		
		return BeanValidator.validateAndExecute(req, dto -> {
			String wordId = dto.getWordId();
			String voice = dto.getVoice() != null ? dto.getVoice() : "FEMALE";
			String type = dto.getType() != null ? dto.getType() : "WORD";
			
			Optional<Word> optWord = wordRepository.findById(wordId);
			if (optWord.isEmpty()) {
				return ResponseGenerator.fail(VocabularyErrorCode.WORD_NOT_FOUND);
			}
			
			Word word = optWord.get();
			boolean isMale = "MALE".equalsIgnoreCase(voice);
			boolean isExample = "EXAMPLE".equalsIgnoreCase(type);
			
			if (isExample && (word.getExample() == null || word.getExample().isEmpty())) {
				return ResponseGenerator.fail(VocabularyErrorCode.INVALID_WORD_DATA, "This word has no example sentence");
			}
			
			String textToSynthesize = isExample ? word.getExample() : word.getEnglish();
			String cachedKey = getCachedKey(word, isMale, isExample);
			String audioUrl;
			boolean cached = false;
			
			if (cachedKey != null && !cachedKey.isEmpty()) {
				audioUrl = pollyService.getPresignedUrl(cachedKey);
				cached = true;
				logger.info("Cache hit from DB: wordId={}, voice={}, type={}", wordId, voice, type);
			} else {
				String s3KeySuffix = isExample ? "_example" : "";
				PollyService.VoiceSynthesisResult result = pollyService.synthesizeSpeech(
						wordId + s3KeySuffix, textToSynthesize, voice);
				
				audioUrl = result.getAudioUrl();
				cached = result.isCached();
				
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
			
			return ResponseGenerator.ok("Speech synthesized", responseData);
		});
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
