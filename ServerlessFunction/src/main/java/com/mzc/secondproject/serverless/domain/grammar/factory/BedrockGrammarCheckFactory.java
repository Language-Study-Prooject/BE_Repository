package com.mzc.secondproject.serverless.domain.grammar.factory;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mzc.secondproject.serverless.common.config.AwsClients;
import com.mzc.secondproject.serverless.domain.grammar.dto.response.ConversationResponse;
import com.mzc.secondproject.serverless.domain.grammar.dto.response.GrammarCheckResponse;
import com.mzc.secondproject.serverless.domain.grammar.dto.response.GrammarError;
import com.mzc.secondproject.serverless.domain.grammar.enums.GrammarErrorType;
import com.mzc.secondproject.serverless.domain.grammar.enums.GrammarLevel;
import com.mzc.secondproject.serverless.domain.grammar.exception.GrammarException;
import com.mzc.secondproject.serverless.domain.grammar.streaming.StreamingCallback;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.bedrockruntime.model.InvokeModelRequest;
import software.amazon.awssdk.services.bedrockruntime.model.InvokeModelResponse;
import software.amazon.awssdk.services.bedrockruntime.model.InvokeModelWithResponseStreamRequest;
import software.amazon.awssdk.services.bedrockruntime.model.InvokeModelWithResponseStreamResponseHandler;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutionException;

public class BedrockGrammarCheckFactory implements GrammarCheckFactory {
	
	private static final Logger logger = LoggerFactory.getLogger(BedrockGrammarCheckFactory.class);
	private static final Gson gson = new Gson();
	
	private static final String MODEL_ID = "anthropic.claude-3-haiku-20240307-v1:0";
	private static final int MAX_TOKENS = 2048;
	
	@Override
	public GrammarCheckResponse checkGrammar(String sentence, GrammarLevel level) {
		logger.info("Checking grammar: level={}, sentence={}", level.name(), sentence);
		
		long startTime = System.currentTimeMillis();
		
		try {
			String systemPrompt = buildSystemPrompt(level);
			String userPrompt = buildUserPrompt(sentence);
			
			JsonObject requestBody = buildRequestBody(userPrompt, systemPrompt);
			
			InvokeModelRequest request = InvokeModelRequest.builder()
					.modelId(MODEL_ID)
					.contentType("application/json")
					.accept("application/json")
					.body(SdkBytes.fromUtf8String(gson.toJson(requestBody)))
					.build();
			
			InvokeModelResponse response = AwsClients.bedrock().invokeModel(request);
			
			String responseBody = response.body().asUtf8String();
			JsonObject jsonResponse = gson.fromJson(responseBody, JsonObject.class);
			
			String content = jsonResponse.getAsJsonArray("content")
					.get(0).getAsJsonObject()
					.get("text").getAsString();
			
			long processingTime = System.currentTimeMillis() - startTime;
			logger.info("Grammar check completed in {}ms", processingTime);
			
			return parseGrammarResponse(sentence, content);
			
		} catch (GrammarException e) {
			throw e;
		} catch (Exception e) {
			logger.error("Error checking grammar", e);
			throw GrammarException.bedrockApiError(e);
		}
	}
	
	private String buildSystemPrompt(GrammarLevel level) {
		String basePrompt = """
				You are an expert English grammar checker. Analyze the given sentence and provide feedback.
				
				You MUST respond in the following JSON format only, with no additional text:
				{
				  "correctedSentence": "the corrected sentence",
				  "score": 85,
				  "isCorrect": false,
				  "errors": [
				    {
				      "type": "VERB_TENSE",
				      "original": "goed",
				      "corrected": "went",
				      "explanation": "explanation here",
				      "startIndex": 2,
				      "endIndex": 6
				    }
				  ],
				  "feedback": "overall feedback message"
				}
				
				Error types: VERB_TENSE, SUBJECT_VERB_AGREEMENT, ARTICLE, PREPOSITION, WORD_ORDER, PLURAL_SINGULAR, PRONOUN, SPELLING, PUNCTUATION, WORD_CHOICE, SENTENCE_STRUCTURE, OTHER
				
				Score should be 0-100 based on grammar correctness.
				If the sentence is correct, set isCorrect to true, errors to empty array, and score to 100.
				""";
		
		return switch (level) {
			case BEGINNER -> basePrompt + """
					
					Additional instructions for BEGINNER level:
					- Provide explanations in simple English
					- Include Korean translations for key grammar concepts in the explanation
					- Be encouraging in feedback
					""";
			case INTERMEDIATE -> basePrompt + """
					
					Additional instructions for INTERMEDIATE level:
					- Provide clear explanations in English
					- Focus on common grammar patterns
					""";
			case ADVANCED -> basePrompt + """
					
					Additional instructions for ADVANCED level:
					- Provide detailed grammar explanations
					- Include nuanced usage notes
					- Mention style improvements if applicable
					""";
		};
	}
	
	private String buildUserPrompt(String sentence) {
		return String.format("Please check the grammar of this sentence: \"%s\"", sentence);
	}
	
	private JsonObject buildRequestBody(String userPrompt, String systemPrompt) {
		JsonObject requestBody = new JsonObject();
		requestBody.addProperty("anthropic_version", "bedrock-2023-05-31");
		requestBody.addProperty("max_tokens", MAX_TOKENS);
		requestBody.addProperty("system", systemPrompt);
		
		JsonArray messages = new JsonArray();
		JsonObject userMessage = new JsonObject();
		userMessage.addProperty("role", "user");
		userMessage.addProperty("content", userPrompt);
		messages.add(userMessage);
		
		requestBody.add("messages", messages);
		
		return requestBody;
	}
	
	private GrammarCheckResponse parseGrammarResponse(String originalSentence, String aiResponse) {
		try {
			String jsonContent = extractJson(aiResponse);
			JsonObject json = gson.fromJson(jsonContent, JsonObject.class);
			return parseGrammarCheckFromJson(originalSentence, json);
		} catch (GrammarException e) {
			throw e;
		} catch (Exception e) {
			logger.error("Failed to parse grammar response: length={}",
					aiResponse != null ? aiResponse.length() : 0, e);
			throw GrammarException.bedrockResponseParseError(
					aiResponse != null && aiResponse.length() > 200
							? aiResponse.substring(0, 200) + "..."
							: aiResponse);
		}
	}
	
	private String extractJson(String response) {
		int start = response.indexOf('{');
		int end = response.lastIndexOf('}');
		if (start != -1 && end != -1 && end > start) {
			return response.substring(start, end + 1);
		}
		return response;
	}
	
	private GrammarErrorType parseErrorType(String type) {
		try {
			return GrammarErrorType.valueOf(type);
		} catch (IllegalArgumentException e) {
			return GrammarErrorType.OTHER;
		}
	}
	
	private Integer getIntOrNull(JsonObject obj, String key) {
		JsonElement element = obj.get(key);
		if (element == null || element.isJsonNull()) {
			return null;
		}
		return element.getAsInt();
	}
	
	public ConversationResponse generateConversation(String sessionId, String message, GrammarLevel level, String conversationHistory) {
		logger.info("Generating conversation: sessionId={}, level={}", sessionId, level.name());
		
		long startTime = System.currentTimeMillis();
		
		try {
			String systemPrompt = buildConversationSystemPrompt(level);
			String userPrompt = buildConversationUserPrompt(message, conversationHistory);
			
			JsonObject requestBody = buildRequestBody(userPrompt, systemPrompt);
			
			InvokeModelRequest request = InvokeModelRequest.builder()
					.modelId(MODEL_ID)
					.contentType("application/json")
					.accept("application/json")
					.body(SdkBytes.fromUtf8String(gson.toJson(requestBody)))
					.build();
			
			InvokeModelResponse response = AwsClients.bedrock().invokeModel(request);
			
			String responseBody = response.body().asUtf8String();
			JsonObject jsonResponse = gson.fromJson(responseBody, JsonObject.class);
			
			String content = jsonResponse.getAsJsonArray("content")
					.get(0).getAsJsonObject()
					.get("text").getAsString();
			
			long processingTime = System.currentTimeMillis() - startTime;
			logger.info("Conversation generated in {}ms", processingTime);
			
			return parseConversationResponse(sessionId, message, content);
			
		} catch (GrammarException e) {
			throw e;
		} catch (Exception e) {
			logger.error("Error generating conversation", e);
			throw GrammarException.bedrockApiError(e);
		}
	}
	
	private String buildConversationSystemPrompt(GrammarLevel level) {
		String basePrompt = """
				You are a friendly English conversation partner who also helps with grammar.
				When the user sends a message:
				1. First, check their grammar and provide corrections if needed
				2. Then respond naturally to continue the conversation
				3. Provide a helpful learning tip
				
				You MUST respond in the following JSON format only, with no additional text:
				{
				  "grammarCheck": {
				    "correctedSentence": "the corrected sentence",
				    "score": 85,
				    "isCorrect": false,
				    "errors": [
				      {
				        "type": "VERB_TENSE",
				        "original": "goed",
				        "corrected": "went",
				        "explanation": "explanation here"
				      }
				    ],
				    "feedback": "brief grammar feedback"
				  },
				  "aiResponse": "Your natural conversational response here",
				  "conversationTip": "A helpful tip for the learner"
				}
				
				Error types: VERB_TENSE, SUBJECT_VERB_AGREEMENT, ARTICLE, PREPOSITION, WORD_ORDER, PLURAL_SINGULAR, PRONOUN, SPELLING, PUNCTUATION, WORD_CHOICE, SENTENCE_STRUCTURE, OTHER
				""";
		
		return switch (level) {
			case BEGINNER -> basePrompt + """
					
					For BEGINNER level:
					- Use simple vocabulary in your response
					- Keep sentences short
					- Include Korean translations for difficult words in parentheses
					- Be very encouraging
					- Provide basic grammar tips
					""";
			case INTERMEDIATE -> basePrompt + """
					
					For INTERMEDIATE level:
					- Use natural everyday English
					- Introduce new vocabulary naturally
					- Provide practical grammar tips
					""";
			case ADVANCED -> basePrompt + """
					
					For ADVANCED level:
					- Use sophisticated vocabulary and idioms
					- Challenge the learner
					- Provide advanced grammar and style tips
					""";
		};
	}
	
	private String buildConversationUserPrompt(String message, String conversationHistory) {
		StringBuilder prompt = new StringBuilder();
		
		if (conversationHistory != null && !conversationHistory.isEmpty()) {
			prompt.append("Previous conversation:\n");
			prompt.append(conversationHistory);
			prompt.append("\n\n");
		}
		
		prompt.append("User's message: \"").append(message).append("\"");
		
		return prompt.toString();
	}
	
	private ConversationResponse parseConversationResponse(String sessionId, String originalMessage, String aiResponse) {
		try {
			String jsonContent = extractJson(aiResponse);
			JsonObject json = gson.fromJson(jsonContent, JsonObject.class);
			
			JsonObject grammarCheckObj = json.getAsJsonObject("grammarCheck");
			GrammarCheckResponse grammarCheck = parseGrammarCheckFromJson(originalMessage, grammarCheckObj);
			
			String conversationResponse = json.get("aiResponse").getAsString();
			String tip = json.get("conversationTip").getAsString();
			
			return ConversationResponse.builder()
					.sessionId(sessionId)
					.grammarCheck(grammarCheck)
					.aiResponse(conversationResponse)
					.conversationTip(tip)
					.build();
			
		} catch (Exception e) {
			logger.error("Failed to parse conversation response: length={}",
					aiResponse != null ? aiResponse.length() : 0, e);
			throw GrammarException.bedrockResponseParseError(
					aiResponse != null && aiResponse.length() > 200
							? aiResponse.substring(0, 200) + "..."
							: aiResponse);
		}
	}
	
	private GrammarCheckResponse parseGrammarCheckFromJson(String originalSentence, JsonObject json) {
		String correctedSentence = json.get("correctedSentence").getAsString();
		int score = json.get("score").getAsInt();
		boolean isCorrect = json.get("isCorrect").getAsBoolean();
		String feedback = json.get("feedback").getAsString();
		
		List<GrammarError> errors = new ArrayList<>();
		JsonArray errorsArray = json.getAsJsonArray("errors");
		if (errorsArray != null) {
			for (JsonElement element : errorsArray) {
				JsonObject errorObj = element.getAsJsonObject();
				GrammarError error = GrammarError.builder()
						.type(parseErrorType(errorObj.get("type").getAsString()))
						.original(errorObj.get("original").getAsString())
						.corrected(errorObj.get("corrected").getAsString())
						.explanation(errorObj.get("explanation").getAsString())
						.startIndex(getIntOrNull(errorObj, "startIndex"))
						.endIndex(getIntOrNull(errorObj, "endIndex"))
						.build();
				errors.add(error);
			}
		}
		
		return GrammarCheckResponse.builder()
				.originalSentence(originalSentence)
				.correctedSentence(correctedSentence)
				.score(score)
				.errors(errors)
				.feedback(feedback)
				.isCorrect(isCorrect)
				.build();
	}
	
	/**
	 * Streaming 방식으로 대화 생성
	 * 토큰이 생성될 때마다 콜백을 통해 실시간 전송
	 */
	public void generateConversationStreaming(
			String sessionId,
			String message,
			GrammarLevel level,
			String conversationHistory,
			StreamingCallback callback) {
		
		logger.info("Generating streaming conversation: sessionId={}, level={}", sessionId, level.name());
		
		long startTime = System.currentTimeMillis();
		
		try {
			String systemPrompt = buildStreamingConversationPrompt(level);
			String userPrompt = buildConversationUserPrompt(message, conversationHistory);
			
			JsonObject requestBody = buildStreamingRequestBody(userPrompt, systemPrompt);
			
			InvokeModelWithResponseStreamRequest request = InvokeModelWithResponseStreamRequest.builder()
					.modelId(MODEL_ID)
					.body(SdkBytes.fromUtf8String(gson.toJson(requestBody)))
					.build();
			
			StringBuilder fullResponse = new StringBuilder();
			
			// Visitor 패턴으로 스트리밍 응답 처리
			var visitor = InvokeModelWithResponseStreamResponseHandler.Visitor.builder()
					.onChunk(chunk -> {
						try {
							JsonObject response = gson.fromJson(chunk.bytes().asUtf8String(), JsonObject.class);
							String type = response.has("type") ? response.get("type").getAsString() : "";
							
							if (Objects.equals(type, "content_block_delta")) {
								JsonObject delta = response.getAsJsonObject("delta");
								if (delta != null && delta.has("text")) {
									String text = delta.get("text").getAsString();
									fullResponse.append(text);
									callback.onToken(text);
								}
							}
						} catch (Exception e) {
							logger.warn("Failed to parse chunk: {}", e.getMessage());
						}
					})
					.build();
			
			var handler = InvokeModelWithResponseStreamResponseHandler.builder()
					.subscriber(visitor)
					.onError(error -> {
						logger.error("Streaming error", error);
						callback.onError(error);
					})
					.onComplete(() -> {
						long processingTime = System.currentTimeMillis() - startTime;
						logger.info("Streaming conversation completed in {}ms", processingTime);
						
						try {
							ConversationResponse response = parseStreamingResponse(
									sessionId, message, fullResponse.toString());
							callback.onComplete(response);
						} catch (Exception e) {
							logger.error("Failed to parse streaming response", e);
							callback.onError(e);
						}
					})
					.build();
			
			AwsClients.bedrockAsync().invokeModelWithResponseStream(request, handler).get();
			
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			logger.error("Streaming conversation interrupted", e);
			callback.onError(e);
		} catch (ExecutionException e) {
			logger.error("Error in streaming conversation", e);
			callback.onError(e.getCause() != null ? e.getCause() : e);
		} catch (Exception e) {
			logger.error("Error in streaming conversation", e);
			callback.onError(e);
		}
	}
	
	private String buildStreamingConversationPrompt(GrammarLevel level) {
		// Streaming에서는 JSON 형식 대신 자연스러운 텍스트 응답 후 JSON 메타데이터
		String basePrompt = """
				You are a friendly English conversation partner who also helps with grammar.
				When the user sends a message:
				1. First, respond naturally to continue the conversation
				2. Then provide grammar feedback if there are errors
				
				IMPORTANT: Structure your response EXACTLY like this:
				[RESPONSE]
				Your natural conversational response here. Keep it friendly and engaging.
				[/RESPONSE]
				
				[GRAMMAR]
				{
				  "correctedSentence": "the corrected sentence",
				  "score": 85,
				  "isCorrect": false,
				  "errors": [
				    {
				      "type": "VERB_TENSE",
				      "original": "goed",
				      "corrected": "went",
				      "explanation": "explanation here",
				      "startIndex": 2,
				      "endIndex": 6
				    }
				  ],
				  "feedback": "brief grammar feedback"
				}
				[/GRAMMAR]
				
				[TIP]
				A helpful learning tip for the user.
				[/TIP]
				
				Error types: VERB_TENSE, SUBJECT_VERB_AGREEMENT, ARTICLE, PREPOSITION, WORD_ORDER, PLURAL_SINGULAR, PRONOUN, SPELLING, PUNCTUATION, WORD_CHOICE, SENTENCE_STRUCTURE, OTHER
				
				If the sentence is grammatically correct, set isCorrect to true and errors to empty array.
				""";
		
		return switch (level) {
			case BEGINNER -> basePrompt + """
					
					For BEGINNER level:
					- Use simple vocabulary in your response
					- Keep sentences short
					- Include Korean translations for difficult words in parentheses
					- Be very encouraging
					- Provide basic grammar tips
					""";
			case INTERMEDIATE -> basePrompt + """
					
					For INTERMEDIATE level:
					- Use natural everyday English
					- Introduce new vocabulary naturally
					- Provide practical grammar tips
					""";
			case ADVANCED -> basePrompt + """
					
					For ADVANCED level:
					- Use sophisticated vocabulary and idioms
					- Challenge the learner
					- Provide advanced grammar and style tips
					""";
		};
	}
	
	private JsonObject buildStreamingRequestBody(String userPrompt, String systemPrompt) {
		JsonObject requestBody = new JsonObject();
		requestBody.addProperty("anthropic_version", "bedrock-2023-05-31");
		requestBody.addProperty("max_tokens", MAX_TOKENS);
		requestBody.addProperty("system", systemPrompt);
		// Streaming을 위해 stop_sequences 추가하지 않음
		
		JsonArray messages = new JsonArray();
		JsonObject userMessage = new JsonObject();
		userMessage.addProperty("role", "user");
		userMessage.addProperty("content", userPrompt);
		messages.add(userMessage);
		
		requestBody.add("messages", messages);
		
		return requestBody;
	}
	
	/**
	 * Streaming 응답 파싱 (섹션 기반)
	 */
	public ConversationResponse parseStreamingResponse(String sessionId, String originalMessage, String fullResponse) {
		try {
			String aiResponse = extractSection(fullResponse, "RESPONSE");
			String grammarJson = extractSection(fullResponse, "GRAMMAR");
			String tip = extractSection(fullResponse, "TIP");
			
			GrammarCheckResponse grammarCheck;
			if (grammarJson != null && !grammarJson.isEmpty()) {
				JsonObject json = gson.fromJson(grammarJson, JsonObject.class);
				grammarCheck = parseGrammarCheckFromJson(originalMessage, json);
			} else {
				// 문법 오류가 없는 경우 기본값
				grammarCheck = GrammarCheckResponse.builder()
						.originalSentence(originalMessage)
						.correctedSentence(originalMessage)
						.score(100)
						.isCorrect(true)
						.errors(new ArrayList<>())
						.feedback("Perfect!")
						.build();
			}
			
			return ConversationResponse.builder()
					.sessionId(sessionId)
					.grammarCheck(grammarCheck)
					.aiResponse(aiResponse != null ? aiResponse.trim() : "")
					.conversationTip(tip != null ? tip.trim() : "")
					.build();
			
		} catch (Exception e) {
			logger.error("Failed to parse streaming response: length={}",
					fullResponse != null ? fullResponse.length() : 0, e);
			throw GrammarException.bedrockResponseParseError(
					fullResponse != null && fullResponse.length() > 200
							? fullResponse.substring(0, 200) + "..."
							: fullResponse);
		}
	}
	
	private String extractSection(String text, String sectionName) {
		String startTag = "[" + sectionName + "]";
		String endTag = "[/" + sectionName + "]";
		
		int startIndex = text.indexOf(startTag);
		int endIndex = text.indexOf(endTag);
		
		if (startIndex != -1 && endIndex != -1 && endIndex > startIndex) {
			return text.substring(startIndex + startTag.length(), endIndex).trim();
		}
		return null;
	}
}
