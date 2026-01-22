package com.mzc.secondproject.serverless.domain.opic.handler;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.google.gson.*;
import com.mzc.secondproject.serverless.common.config.AwsClients;
import com.mzc.secondproject.serverless.common.service.PollyService;
import com.mzc.secondproject.serverless.common.util.JwtUtil;
import com.mzc.secondproject.serverless.common.util.ResponseGenerator;
import com.mzc.secondproject.serverless.domain.opic.dto.request.CreateSessionRequest;
import com.mzc.secondproject.serverless.domain.opic.dto.request.SubmitAnswerRequest;
import com.mzc.secondproject.serverless.domain.opic.dto.response.FeedbackResponse;
import com.mzc.secondproject.serverless.domain.opic.model.OPIcAnswer;
import com.mzc.secondproject.serverless.domain.opic.model.OPIcQuestion;
import com.mzc.secondproject.serverless.domain.opic.model.OPIcSession;
import com.mzc.secondproject.serverless.domain.opic.repository.OPIcRepository;
import com.mzc.secondproject.serverless.domain.opic.service.FeedbackService;
import com.mzc.secondproject.serverless.domain.opic.service.TranscribeProxyService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.lang.reflect.Type;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * OPIc 세션 통합 Handler
 * - 세션 생성/조회
 * - 질문 조회 (Polly 음성 URL 포함)
 * - 답변 제출 (Transcribe + Bedrock 피드백)
 * - 세션 완료 (종합 리포트)
 */
public class OPIcSessionHandler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {
	
	private static final Logger logger = LoggerFactory.getLogger(OPIcSessionHandler.class);
	private static final Gson gson = new GsonBuilder()
			.setDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
			.registerTypeAdapter(Instant.class, new InstantTypeAdapter())
			.create();
	
	private static final String OPIC_BUCKET = System.getenv("OPIC_BUCKET_NAME");
	
	private final OPIcRepository repository;
	private final PollyService pollyService;
	private final TranscribeProxyService transcribeService;
	private final FeedbackService feedbackService;
	
	public OPIcSessionHandler() {
		this.repository = new OPIcRepository();
		this.pollyService = new PollyService(OPIC_BUCKET, "opic/voice/questions/");
		this.transcribeService = new TranscribeProxyService();
		this.feedbackService = new FeedbackService();
	}
	
	@Override
	public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent event, Context context) {
		String httpMethod = event.getHttpMethod();
		String path = event.getPath();
		
		try {
			
			String userId = extractUserId(event);
			
			
			// POST /opic/sessions - 세션 생성
			if ("POST".equals(httpMethod) && path.equals("/opic/sessions")) {
				return createSession(event, userId);
			}
			
			// GET /opic/sessions - 세션 목록 조회
			if ("GET".equals(httpMethod) && path.equals("/opic/sessions")) {
				return getSessions(userId);
			}
			
			// GET /opic/sessions/{sessionId} - 세션 상세 조회
			if ("GET".equals(httpMethod) && path.matches("/opic/sessions/[^/]+")
					&& !path.contains("/questions") && !path.contains("/upload-url")) {
				return getSession(event, userId);
			}
			
			// GET /opic/sessions/{sessionId}/questions/next - 다음 질문 조회
			if ("GET".equals(httpMethod) && path.contains("/questions/next")) {
				return getNextQuestion(event, userId);
			}
			
			// GET /opic/sessions/{sessionId}/upload-url - Presigned URL 발급
			if ("GET".equals(httpMethod) && path.contains("/upload-url")) {
				return getUploadUrl(event, userId);
			}
			
			// POST /opic/sessions/{sessionId}/answers - 답변 제출
			if ("POST".equals(httpMethod) && path.contains("/answers")) {
				return submitAnswer(event, userId);
			}
			
			// POST /opic/sessions/{sessionId}/complete - 세션 완료
			if ("POST".equals(httpMethod) && path.contains("/complete")) {
				return completeSession(event, userId);
			}
			
			return ResponseGenerator.badRequest("지원하지 않는 요청입니다: " + httpMethod + " " + path);
			
		} catch (Exception e) {
			logger.error("OPIc Handler 에러", e);
			return ResponseGenerator.serverError(e.getMessage());
		}
	}
	
	
	/**
	 * POST /opic/sessions
	 * 세션 생성 + 첫 질문 반환
	 */
	private APIGatewayProxyResponseEvent createSession(APIGatewayProxyRequestEvent event, String userId) {
		CreateSessionRequest request = gson.fromJson(event.getBody(), CreateSessionRequest.class);
		
		logger.info("세션 생성 요청: userId={}, topic={}, level={}",
				userId, request.topic(), request.targetLevel());
		
		// 주제 + 소주제 + 레벨로 질문 세트 조회
		List<OPIcQuestion> questions = repository.findQuestionsByTopicSubTopicAndLevel(
				request.topic(),
				request.subTopic(),
				request.targetLevel()
		);
		
		if (questions.isEmpty()) {
			return ResponseGenerator.notFound("해당 주제/레벨의 질문이 없습니다.");
		}
		
		// 최대 3개 질문 선택 (랜덤 셔플)
		Collections.shuffle(questions);
		List<String> questionIds = questions.stream()
				.limit(3)
				.map(OPIcQuestion::getQuestionId)
				.collect(Collectors.toList());
		
		// 세션 생성
		OPIcSession session = repository.createSession(
				userId,
				request.topic(),
				request.subTopic(),
				request.targetLevel(),
				questionIds
		);
		
		// 첫 질문 Polly 음성 URL 생성 (#368 PollyService 연동)
		OPIcQuestion firstQuestion = questions.get(0);
		String audioUrl = generateQuestionAudioUrl(firstQuestion);
		
		// Response
		Map<String, Object> response = new LinkedHashMap<>();
		response.put("sessionId", session.getSessionId());
		response.put("totalQuestions", session.getTotalQuestions());
		response.put("firstQuestion", Map.of(
				"questionId", firstQuestion.getQuestionId(),
				"questionText", firstQuestion.getQuestionText(),
				"audioUrl", audioUrl,
				"questionNumber", 1,
				"totalQuestions", session.getTotalQuestions()
		));
		
		logger.info("세션 생성 완료: sessionId={}", session.getSessionId());
		return ResponseGenerator.created("세션이 생성되었습니다.", response);
	}
	
	/**
	 * GET /opic/sessions
	 * 사용자의 세션 목록 조회
	 */
	private APIGatewayProxyResponseEvent getSessions(String userId) {
		List<OPIcSession> sessions = repository.findSessionsByUserId(userId, 20);
		
		Map<String, Object> responseBody = new LinkedHashMap<>();
		responseBody.put("isSuccess", true);
		responseBody.put("data", sessions);
		
		return new APIGatewayProxyResponseEvent()
				.withStatusCode(200)
				.withHeaders(Map.of("Content-Type", "application/json"))
				.withBody(gson.toJson(responseBody));
	}
	
	/**
	 * GET /opic/sessions/{sessionId}
	 * 세션 상세 조회
	 */
	private APIGatewayProxyResponseEvent getSession(APIGatewayProxyRequestEvent event, String userId) {
		String sessionId = event.getPathParameters().get("sessionId");
		
		OPIcSession session = repository.findSessionById(sessionId).orElse(null);
		
		if (session == null) {
			return ResponseGenerator.notFound("세션을 찾을 수 없습니다.");
		}
		
		if (!session.getUserId().equals(userId)) {
			return ResponseGenerator.forbidden("접근 권한이 없습니다.");
		}
		
		// 세션에 포함된 답변들도 조회
		List<OPIcAnswer> answers = repository.findAnswersBySessionId(sessionId);
		
		Map<String, Object> response = new LinkedHashMap<>();
		response.put("session", session);
		response.put("answers", answers);
		
		return ResponseGenerator.ok(response);
	}
	
	/**
	 * GET /opic/sessions/{sessionId}/questions/next
	 * 다음 질문 조회 (Polly 음성 URL 포함)
	 */
	private APIGatewayProxyResponseEvent getNextQuestion(APIGatewayProxyRequestEvent event, String userId) {
		String sessionId = event.getPathParameters().get("sessionId");
		
		OPIcSession session = repository.findSessionById(sessionId).orElse(null);
		
		if (session == null) {
			return ResponseGenerator.notFound("세션을 찾을 수 없습니다.");
		}
		
		if (!session.getUserId().equals(userId)) {
			return ResponseGenerator.forbidden("접근 권한이 없습니다.");
		}
		
		// 모든 질문 완료 확인
		int currentIndex = session.getCurrentQuestionIndex();
		if (currentIndex >= session.getTotalQuestions()) {
			return ResponseGenerator.ok(Map.of(
					"completed", true,
					"message", "모든 질문이 완료되었습니다. 세션을 완료해주세요.",
					"sessionId", sessionId
			));
		}
		
		// 다음 질문 조회
		String questionId = session.getQuestionIds().get(currentIndex);
		OPIcQuestion question = repository.findQuestionById(questionId)
				.orElseThrow(() -> new RuntimeException("질문을 찾을 수 없습니다: " + questionId));
		
		// Polly 음성 URL
		String audioUrl = generateQuestionAudioUrl(question);
		
		Map<String, Object> response = new LinkedHashMap<>();
		response.put("questionId", question.getQuestionId());
		response.put("questionText", question.getQuestionText());
		response.put("audioUrl", audioUrl);
		response.put("questionNumber", currentIndex + 1);
		response.put("totalQuestions", session.getTotalQuestions());
		response.put("completed", false);
		
		return ResponseGenerator.ok(response);
	}
	
	/**
	 * GET /opic/sessions/{sessionId}/upload-url
	 * S3 Presigned URL 발급 (음성 업로드용)
	 */
	private APIGatewayProxyResponseEvent getUploadUrl(APIGatewayProxyRequestEvent event, String userId) {
		String sessionId = event.getPathParameters().get("sessionId");
		
		// 세션 검증
		OPIcSession session = repository.findSessionById(sessionId).orElse(null);
		if (session == null || !session.getUserId().equals(userId)) {
			return ResponseGenerator.forbidden("접근 권한이 없습니다.");
		}
		
		// S3 키 생성
		String s3Key = String.format("opic/answers/%s/%s/%s.webm",
				userId,
				sessionId,
				UUID.randomUUID().toString()
		);
		
		// Presigned URL 생성 (5분 유효)
		PutObjectRequest putRequest = PutObjectRequest.builder()
				.bucket(OPIC_BUCKET)
				.key(s3Key)
				.contentType("audio/webm")
				.build();
		
		String presignedUrl = AwsClients.s3Presigner()
				.presignPutObject(PutObjectPresignRequest.builder()
						.putObjectRequest(putRequest)
						.signatureDuration(Duration.ofMinutes(5))
						.build())
				.url()
				.toString();
		
		return ResponseGenerator.ok(Map.of(
				"uploadUrl", presignedUrl,
				"s3Key", s3Key,
				"expiresIn", 300
		));
	}
	
	/**
	 * POST /opic/sessions/{sessionId}/answers
	 * 답변 제출 → STT → AI 피드백
	 */
	private APIGatewayProxyResponseEvent submitAnswer(APIGatewayProxyRequestEvent event, String userId) {
		String sessionId = event.getPathParameters().get("sessionId");
		SubmitAnswerRequest request = gson.fromJson(event.getBody(), SubmitAnswerRequest.class);
		
		logger.info("답변 제출: sessionId={}, s3Key={}", sessionId, request.audioS3Key());
		
		// 세션 검증
		OPIcSession session = repository.findSessionById(sessionId).orElse(null);
		if (session == null) {
			return ResponseGenerator.notFound("세션을 찾을 수 없습니다.");
		}
		if (!session.getUserId().equals(userId)) {
			return ResponseGenerator.forbidden("접근 권한이 없습니다.");
		}
		
		// 현재 질문 조회
		int currentIndex = session.getCurrentQuestionIndex();
		if (currentIndex >= session.getTotalQuestions()) {
			return ResponseGenerator.badRequest("이미 모든 질문에 답변했습니다.");
		}
		
		String questionId = session.getQuestionIds().get(currentIndex);
		OPIcQuestion question = repository.findQuestionById(questionId)
				.orElseThrow(() -> new RuntimeException("질문을 찾을 수 없습니다."));
		
		// Transcribe Proxy 호출 (음성 → 텍스트)
		logger.info("S3에서 오디오 파일 로드: {}", request.audioS3Key());
		
		byte[] audioBytes = AwsClients.s3().getObjectAsBytes(
				software.amazon.awssdk.services.s3.model.GetObjectRequest.builder()
						.bucket(OPIC_BUCKET)
						.key(request.audioS3Key())
						.build()
		).asByteArray();
		
		String audioBase64 = java.util.Base64.getEncoder().encodeToString(audioBytes);
		logger.info("오디오 파일 Base64 변환 완료: {} bytes → {} chars",
				audioBytes.length, audioBase64.length());
		
		// 4. Transcribe Proxy 호출 (Base64 데이터 전송)
		TranscribeProxyService.TranscribeResult transcribeResult =
				transcribeService.transcribe(audioBase64, sessionId);
		
		String transcript = transcribeResult.transcript();
		logger.info("STT 변환 완료: transcript 길이={}", transcript.length());
		
		// Bedrock 피드백 생성
		FeedbackResponse feedback = feedbackService.generateFeedback(
				question.getQuestionText(),
				transcript,
				session.getTargetLevel()
		);
		
		// Answer 저장 - 개별 필드로 분리 저장
		OPIcAnswer answer = new OPIcAnswer();
		answer.setSessionId(sessionId);
		answer.setQuestionId(questionId);
		answer.setQuestionIndex(currentIndex);
		answer.setQuestionText(question.getQuestionText());  // 비정규화
		answer.setAudioS3Key(request.audioS3Key());
		answer.setTranscript(transcript);
		answer.setTranscriptConfidence(transcribeResult.confidence());
		
		// 피드백 개별 필드 저장
		answer.setGrammarFeedback(gson.toJson(feedback.errors()));  // errors → grammarFeedback
		answer.setContentFeedback(feedback.correctedAnswer());      // correctedAnswer → contentFeedback
		answer.setSampleAnswer(feedback.sampleAnswer());            // 모범 답변
		answer.setStatus(OPIcAnswer.AnswerStatus.COMPLETED);
		answer.setAttemptCount(1);
		answer.setCreatedAt(Instant.now());
		answer.setCompletedAt(Instant.now());
		
		repository.saveAnswer(answer);
		
		// 세션 진행 상태 업데이트
		session.setCurrentQuestionIndex(currentIndex + 1);
		repository.updateSession(session);
		
		// Response
		boolean hasNext = (currentIndex + 1) < session.getTotalQuestions();
		
		Map<String, Object> response = new LinkedHashMap<>();
		response.put("transcript", transcript);
		response.put("feedback", feedback);
		response.put("hasNextQuestion", hasNext);
		response.put("currentQuestion", currentIndex + 1);
		response.put("totalQuestions", session.getTotalQuestions());
		
		if (hasNext) {
			response.put("nextQuestionNumber", currentIndex + 2);
		}
		
		logger.info("답변 처리 완료: sessionId={}, questionIndex={}", sessionId, currentIndex);
		return ResponseGenerator.ok("피드백이 생성되었습니다.", response);
	}
	
	/**
	 * POST /opic/sessions/{sessionId}/complete
	 * 세션 완료 + 종합 리포트 생성
	 */
	private APIGatewayProxyResponseEvent completeSession(APIGatewayProxyRequestEvent event, String userId) {
		String sessionId = event.getPathParameters().get("sessionId");
		
		OPIcSession session = repository.findSessionById(sessionId).orElse(null);
		if (session == null) {
			return ResponseGenerator.notFound("세션을 찾을 수 없습니다.");
		}
		if (!session.getUserId().equals(userId)) {
			return ResponseGenerator.forbidden("접근 권한이 없습니다.");
		}
		
		// 모든 질문 답변 완료 확인
		List<OPIcAnswer> answers = repository.findAnswersBySessionId(sessionId);
		if (answers.size() < session.getTotalQuestions()) {
			return ResponseGenerator.badRequest(
					String.format("아직 %d개의 질문에 답변하지 않았습니다.",
							session.getTotalQuestions() - answers.size())
			);
		}
		
		// 세션 요약 생성 (피드백용)
		StringBuilder summaryBuilder = new StringBuilder();
		for (int i = 0; i < answers.size(); i++) {
			OPIcAnswer answer = answers.get(i);
			OPIcQuestion question = repository.findQuestionById(answer.getQuestionId()).orElse(null);
			
			summaryBuilder.append(String.format("### Question %d\n", i + 1));
			if (question != null) {
				summaryBuilder.append("Q: ").append(question.getQuestionText()).append("\n");
			}
			summaryBuilder.append("A: ").append(answer.getTranscript()).append("\n\n");
		}
		
		// 종합 리포트 생성 (Bedrock)
		var sessionReport = feedbackService.generateSessionReport(
				summaryBuilder.toString(),
				session.getTargetLevel()
		);
		
		// 세션 완료 처리
		repository.completeSession(
				session,
				sessionReport.estimatedLevel(),
				gson.toJson(sessionReport)
		);
		
		logger.info("세션 완료: sessionId={}, estimatedLevel={}",
				sessionId, sessionReport.estimatedLevel());
		
		return ResponseGenerator.ok("세션이 완료되었습니다.", sessionReport);
	}
	
	// ==================== 유틸리티 ====================
	
	/**
	 * 질문 음성 URL 생성 (Polly + S3 캐싱)
	 */
	private String generateQuestionAudioUrl(OPIcQuestion question) {
		try {
			PollyService.VoiceSynthesisResult result = pollyService.synthesizeSpeech(
					question.getQuestionId(),
					question.getQuestionText(),
					"FEMALE"
			);
			return result.getAudioUrl();
		} catch (Exception e) {
			logger.warn("Polly 음성 생성 실패, 텍스트만 반환: {}", e.getMessage());
			return null;
		}
	}
	
	/**
	 * JWT 토큰에서 userId 추출
	 */
	private String extractUserId(APIGatewayProxyRequestEvent event) {
		String authHeader = event.getHeaders().get("Authorization");
		
		if (authHeader == null || authHeader.isEmpty()) {
			authHeader = event.getHeaders().get("authorization");
		}
		
		return JwtUtil.extractUserId(authHeader)
				.orElseThrow(() -> new RuntimeException("인증 정보를 찾을 수 없습니다."));
	}
	
	private static class InstantTypeAdapter implements JsonSerializer<Instant>, JsonDeserializer<Instant> {
		@Override
		public JsonElement serialize(Instant src, Type typeOfSrc, JsonSerializationContext context) {
			return new JsonPrimitive(src.toString());
		}
		
		@Override
		public Instant deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context)
				throws JsonParseException {
			return Instant.parse(json.getAsString());
		}
	}
}
