package com.mzc.secondproject.serverless.domain.opic.service;

import com.google.gson.*;
import com.mzc.secondproject.serverless.common.config.AwsClients;
import com.mzc.secondproject.serverless.common.util.JsonUtil;
import com.mzc.secondproject.serverless.domain.opic.dto.response.FeedbackResponse;
import com.mzc.secondproject.serverless.domain.opic.dto.response.SessionReportResponse;
import com.mzc.secondproject.serverless.domain.opic.dto.response.SpeakingError;
import com.mzc.secondproject.serverless.domain.opic.enums.SpeakingErrorType;
import com.mzc.secondproject.serverless.domain.opic.exception.OPIcException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.bedrockruntime.model.InvokeModelRequest;
import software.amazon.awssdk.services.bedrockruntime.model.InvokeModelResponse;

import java.util.ArrayList;
import java.util.List;

/**
 * OPIc 피드백 생성 서비스
 */
public class FeedbackService {
	
	private static final Logger logger = LoggerFactory.getLogger(FeedbackService.class);
	private static final Gson gson = new GsonBuilder().setPrettyPrinting().create();
	private static final String MODEL_ID = "anthropic.claude-3-haiku-20240307-v1:0";
	private static final int MAX_TOKENS = 2000;
	
	/**
	 * 사용자 답변에 대한 피드백 생성
	 */
	public FeedbackResponse generateFeedback(String question, String userAnswer, String targetLevel) {
		logger.info("피드백 생성, 대상 Level: {}", targetLevel);
		
		String prompt = buildFeedbackPrompt(question, userAnswer, targetLevel);
		String response = invokeClaude(prompt);
		String jsonResponse = JsonUtil.extractJson(response);
		
		return parseFeedbackResponse(jsonResponse);
	}
	
	
	/**
	 * 세션 종합 리포트 생성
	 */
	public SessionReportResponse generateSessionReport(String sessionSummary, String targetLevel) {
		logger.info("세션 리포트 생성, 대상 Level: {}", targetLevel);
		
		String prompt = buildSessionReportPrompt(sessionSummary, targetLevel);
		String response = invokeClaude(prompt);
		String jsonResponse = JsonUtil.extractJson(response);
		
		return parseSessionReportResponse(jsonResponse);
	}
	
	
	/**
	 * 개별 질문 피드백 프롬프트
	 */
	private String buildFeedbackPrompt(String question, String userAnswer, String targetLevel) {
		return String.format("""
				You are an expert OPIc speaking evaluator.
				
				## Question
				%s
				
				## User's Answer
				%s
				
				## Target Level
				%s
				
				## Task
				Analyze the answer and provide feedback in the following JSON format only:
				
				{
				    "errors": [
				        {
				            "type": "GRAMMAR | EXPRESSION | VOCABULARY",
				            "original": "원본 표현",
				            "corrected": "교정된 표현",
				            "explanation": "설명 (한국어)"
				        }
				    ],
				    "correctedAnswer": "전체 교정된 답변 (영어)",
				    "sampleAnswer": "목표 레벨에 맞는 모범 답변 (영어, 4-6문장)"
				}
				
				Error types:
				- GRAMMAR: 문법 오류 (시제, 관사, 주어-동사 일치 등)
				- EXPRESSION: 더 자연스러운 표현 제안
				- VOCABULARY: 더 적절하거나 풍부한 어휘 제안
				
				Rules:
				1. errors 배열은 최대 5개까지만 포함
				2. 오류가 없으면 errors는 빈 배열 []
				3. explanation은 한국어로 간결하게
				4. sampleAnswer는 목표 레벨에 맞는 자연스러운 답변
				
				Respond with ONLY the JSON, no markdown code blocks.
				""", question, userAnswer, targetLevel);
	}
	
	/**
	 * 세션 종합 리포트 프롬프트
	 */
	private String buildSessionReportPrompt(String sessionSummary, String targetLevel) {
		return String.format("""
				You are an expert OPIc speaking coach creating a comprehensive session report.
				
				## Session Summary (Questions and Answers)
				%s
				
				## Target Level
				%s
				
				## Task
				Generate a detailed learning report in the following JSON format only:
				
				{
				    "estimatedLevel": "NL | NM | NH | IL | IM1 | IM2 | IM3 | IH | AL",
				    "overallScore": 0-100,
				    "strengths": ["잘한 점 1 (한국어)", "잘한 점 2", "잘한 점 3"],
				    "weaknesses": ["개선할 점 1 (한국어)", "개선할 점 2", "개선할 점 3"],
				    "feedback": "종합 피드백 (한국어, 3-4문장, 격려하는 톤)",
				    "recommendations": ["학습 추천 1 (한국어)", "학습 추천 2"]
				}
				
				Evaluation criteria:
				- Task completion: 질문에 적절히 답했는가
				- Fluency: 유창성, 자연스러움
				- Grammar: 문법 정확도
				- Vocabulary: 어휘 다양성
				- Content: 내용의 구체성
				
				Be encouraging but honest. Provide specific, actionable feedback in Korean.
				Respond with ONLY the JSON, no markdown code blocks.
				""", sessionSummary, targetLevel);
	}
	
	
	/**
	 * Claude 호출 (일반 텍스트 응답)
	 */
	private String invokeClaude(String prompt) {
		try {
			JsonObject requestBody = buildRequestBody(prompt);
			
			InvokeModelRequest request = InvokeModelRequest.builder()
					.modelId(MODEL_ID)
					.contentType("application/json")
					.body(SdkBytes.fromUtf8String(gson.toJson(requestBody)))
					.build();
			
			long startTime = System.currentTimeMillis();
			InvokeModelResponse response = AwsClients.bedrock().invokeModel(request);
			long elapsed = System.currentTimeMillis() - startTime;
			
			logger.info("Bedrock 응답 수신: {}ms", elapsed);
			
			JsonObject responseJson = JsonParser.parseString(
					response.body().asUtf8String()
			).getAsJsonObject();
			
			return responseJson
					.getAsJsonArray("content")
					.get(0)
					.getAsJsonObject()
					.get("text")
					.getAsString();
			
		} catch (Exception e) {
			logger.error("Bedrock 호출 실패", e);
			throw new OPIcException.BedrockApiException(e.getMessage(), e);
		}
	}
	
	/**
	 * Bedrock 요청 Body 생성
	 */
	private JsonObject buildRequestBody(String prompt) {
		JsonObject requestBody = new JsonObject();
		requestBody.addProperty("anthropic_version", "bedrock-2023-05-31");
		requestBody.addProperty("max_tokens", MAX_TOKENS);
		
		JsonArray messages = new JsonArray();
		JsonObject userMessage = new JsonObject();
		userMessage.addProperty("role", "user");
		userMessage.addProperty("content", prompt);
		messages.add(userMessage);
		
		requestBody.add("messages", messages);
		return requestBody;
	}
	
	// ==================== 응답 파싱 ====================
	
	/**
	 * 피드백 응답 파싱
	 * <p>
	 * Claude 응답 JSON 구조:
	 * {
	 * "errors": [{ "type": "GRAMMAR", "original": "...", "corrected": "...", "explanation": "..." }],
	 * "correctedAnswer": "...",
	 * "sampleAnswer": "..."
	 * }
	 */
	private FeedbackResponse parseFeedbackResponse(String jsonResponse) {
		try {
			JsonObject json = JsonParser.parseString(jsonResponse).getAsJsonObject();
			
			// errors 배열 파싱
			List<SpeakingError> errors = parseErrors(json.getAsJsonArray("errors"));
			
			// 응답 DTO 생성
			return new FeedbackResponse(
					errors,
					json.get("correctedAnswer").getAsString(),
					json.get("sampleAnswer").getAsString()
			);
			
		} catch (Exception e) {
			logger.error("피드백 파싱 실패: {}", jsonResponse, e);
			throw new OPIcException.FeedbackParseException(jsonResponse, e);
		}
	}
	
	/**
	 * 세션 리포트 응답 파싱
	 * <p>
	 * Claude 응답 JSON 구조:
	 * {
	 * "estimatedLevel": "IM2",
	 * "overallScore": 72,
	 * "strengths": ["...", "..."],
	 * "weaknesses": ["...", "..."],
	 * "feedback": "...",
	 * "recommendations": ["...", "..."]
	 * }
	 */
	private SessionReportResponse parseSessionReportResponse(String jsonResponse) {
		try {
			JsonObject json = JsonParser.parseString(jsonResponse).getAsJsonObject();
			
			return new SessionReportResponse(
					json.get("estimatedLevel").getAsString(),
					json.get("overallScore").getAsInt(),
					JsonUtil.toStringList(json.getAsJsonArray("strengths")),
					JsonUtil.toStringList(json.getAsJsonArray("weaknesses")),
					json.get("feedback").getAsString(),
					JsonUtil.toStringList(json.getAsJsonArray("recommendations"))
			);
			
		} catch (Exception e) {
			logger.error("세션 리포트 파싱 실패: {}", jsonResponse, e);
			throw new OPIcException.ReportParseException(jsonResponse, e);
		}
	}
	
	/**
	 * errors 배열 파싱
	 */
	private List<SpeakingError> parseErrors(JsonArray errorsArray) {
		List<SpeakingError> errors = new ArrayList<>();
		
		if (errorsArray == null || errorsArray.isEmpty()) {
			return errors;
		}
		
		for (JsonElement el : errorsArray) {
			JsonObject obj = el.getAsJsonObject();
			errors.add(SpeakingError.builder()
					.type(parseErrorType(obj.get("type").getAsString()))
					.original(obj.get("original").getAsString())
					.corrected(obj.get("corrected").getAsString())
					.explanation(obj.get("explanation").getAsString())
					.build());
		}
		
		return errors;
	}
	
	
	/**
	 * 오류 타입 문자열 -> Enum 변환
	 */
	private SpeakingErrorType parseErrorType(String typeStr) {
		try {
			// "GRAMMAR | EXPRESSION | VOCABULARY" 형태 처리
			String cleaned = typeStr.replace(" ", "").split("\\|")[0].trim();
			return SpeakingErrorType.valueOf(cleaned.toUpperCase());
		} catch (Exception e) {
			logger.warn("알 수 없는 오류 타입: {}, 기본값 GRAMMAR 사용", typeStr);
			return SpeakingErrorType.GRAMMAR;
		}
	}
	
}
