package com.mzc.secondproject.serverless.domain.opic.handler;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.google.gson.*;
import com.mzc.secondproject.serverless.common.config.AwsClients;
import com.mzc.secondproject.serverless.common.service.PollyService;
import com.mzc.secondproject.serverless.common.util.CognitoUtil;
import com.mzc.secondproject.serverless.common.util.JwtUtil;
import com.mzc.secondproject.serverless.common.util.ResponseGenerator;
import com.mzc.secondproject.serverless.domain.opic.dto.request.CreateSessionRequest;
import com.mzc.secondproject.serverless.domain.opic.dto.request.SubmitAnswerRequest;
import com.mzc.secondproject.serverless.domain.opic.dto.response.CreateSessionResponse;
import com.mzc.secondproject.serverless.domain.opic.dto.response.FeedbackResponse;
import com.mzc.secondproject.serverless.domain.opic.dto.response.QuestionResponse;
import com.mzc.secondproject.serverless.domain.opic.model.OPIcAnswer;
import com.mzc.secondproject.serverless.domain.opic.model.OPIcQuestion;
import com.mzc.secondproject.serverless.domain.opic.model.OPIcSession;
import com.mzc.secondproject.serverless.domain.opic.repository.OPIcRepository;
import com.mzc.secondproject.serverless.domain.opic.service.EmailService;
import com.mzc.secondproject.serverless.domain.opic.service.FeedbackService;
import com.mzc.secondproject.serverless.domain.opic.service.TranscribeProxyService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;
import software.amazon.awssdk.services.sns.model.PublishRequest;

import java.lang.reflect.Type;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * OPIc 세션 통합 Handler
 * - 세션 생성/조회
 * - 질문 조회 (Polly 음성 URL 포함)
 * - 답변 제출 (비동기: SNS → AnswerProcessHandler)
 * - 답변 상태 조회 (폴링)
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
	private final EmailService emailService;

	public OPIcSessionHandler() {
		this.repository = new OPIcRepository();
		this.pollyService = new PollyService(OPIC_BUCKET, "opic/voice/questions/");
		this.transcribeService = new TranscribeProxyService();
		this.feedbackService = new FeedbackService();
		this.emailService = new EmailService();
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
					&& !path.contains("/questions") && !path.contains("/upload-url") && !path.contains("/answers")) {
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

			// POST /opic/sessions/{sessionId}/answers - 답변 제출 (비동기)
			if ("POST".equals(httpMethod) && path.contains("/answers")) {
				return submitAnswer(event, userId);
			}

			// GET /opic/sessions/{sessionId}/answers/{questionIndex}/status - 답변 상태 조회 (폴링)
			if ("GET".equals(httpMethod) && path.matches(".*/answers/\\d+/status")) {
				return getAnswerStatus(event, userId);
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

		logger.info("세션 생성 요청: userId={}, topic={}, subTopic={}, targetLevel={}",
				userId, request.topic(), request.subTopic(), request.targetLevel());

		// 질문 세트 조회 (주제+소주제로 조회)
		List<OPIcQuestion> questions = repository.findQuestionsByTopicAndSubTopic(
				request.topic(),
				request.subTopic()
		);

		// 질문 데이터 없음 예외 처리
		if (questions.isEmpty()) {
			String msg = String.format("해당 주제(%s)와 소주제(%s)에 해당하는 질문이 없습니다.",
					request.topic(), request.subTopic());
			return ResponseGenerator.notFound(msg);
		}

		// 질문 3개 랜덤 선택
		Collections.shuffle(questions);
		List<OPIcQuestion> selectedQuestions = questions.stream()
				.limit(3)
				.sorted(Comparator.comparingInt(OPIcQuestion::getOrderInSet))
				.collect(Collectors.toList());

		// 질문 ID 목록 추출
		List<String> questionIds = selectedQuestions.stream()
				.map(OPIcQuestion::getQuestionId)
				.collect(Collectors.toList());

		// 세션 저장
		OPIcSession session = repository.createSession(
				userId,
				request.topic(),
				request.subTopic(),
				request.targetLevel(),
				questionIds
		);

		// 첫 번째 질문 응답 생성
		OPIcQuestion firstQuestion = selectedQuestions.get(0);
		String audioUrl = generateQuestionAudioUrl(firstQuestion);

		QuestionResponse questionResponse = new QuestionResponse(
				firstQuestion.getQuestionId(),
				firstQuestion.getQuestionText(),
				audioUrl,
				1,
				3
		);

		return ResponseGenerator.ok(
				new CreateSessionResponse(session.getSessionId(), questionResponse, 3)
		);
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

		int currentIndex = session.getCurrentQuestionIndex();

		// 모든 질문 완료 확인
		if (currentIndex >= session.getTotalQuestions()) {
			Map<String, Object> response = new LinkedHashMap<>();
			response.put("completed", true);
			response.put("message", "모든 질문이 완료되었습니다. 세션을 완료해주세요.");
			return ResponseGenerator.ok(response);
		}

		// 다음 질문 조회
		String questionId = session.getQuestionIds().get(currentIndex);
		OPIcQuestion question = repository.findQuestionById(questionId)
				.orElseThrow(() -> new RuntimeException("질문을 찾을 수 없습니다."));

		String audioUrl = generateQuestionAudioUrl(question);

		QuestionResponse questionResponse = new QuestionResponse(
				question.getQuestionId(),
				question.getQuestionText(),
				audioUrl,
				currentIndex + 1,
				session.getTotalQuestions()
		);

		Map<String, Object> response = new LinkedHashMap<>();
		response.put("question", questionResponse);
		response.put("hasNextQuestion", (currentIndex + 1) < session.getTotalQuestions());

		return ResponseGenerator.ok(response);
	}

	/**
	 * GET /opic/sessions/{sessionId}/upload-url
	 * Presigned URL 발급
	 */
	private APIGatewayProxyResponseEvent getUploadUrl(APIGatewayProxyRequestEvent event, String userId) {
		String sessionId = event.getPathParameters().get("sessionId");

		OPIcSession session = repository.findSessionById(sessionId).orElse(null);
		if (session == null) {
			return ResponseGenerator.notFound("세션을 찾을 수 없습니다.");
		}
		if (!session.getUserId().equals(userId)) {
			return ResponseGenerator.forbidden("접근 권한이 없습니다.");
		}

		String fileId = UUID.randomUUID().toString();
		String s3Key = String.format("opic/answers/%s/%s/%s.webm", userId, sessionId, fileId);

		PutObjectRequest putObjectRequest = PutObjectRequest.builder()
				.bucket(OPIC_BUCKET)
				.key(s3Key)
				.contentType("audio/webm")
				.build();

		PutObjectPresignRequest presignRequest = PutObjectPresignRequest.builder()
				.putObjectRequest(putObjectRequest)
				.signatureDuration(Duration.ofMinutes(10))
				.build();

		String uploadUrl = AwsClients.s3Presigner()
				.presignPutObject(presignRequest)
				.url()
				.toString();

		Map<String, String> response = new LinkedHashMap<>();
		response.put("uploadUrl", uploadUrl);
		response.put("s3Key", s3Key);

		return ResponseGenerator.ok(response);
	}

	/**
	 * POST /opic/sessions/{sessionId}/answers
	 * 답변 제출 (비동기 - SNS로 처리 위임)
	 */
	private APIGatewayProxyResponseEvent submitAnswer(APIGatewayProxyRequestEvent event, String userId) {
		String sessionId = event.getPathParameters().get("sessionId");
		SubmitAnswerRequest request = gson.fromJson(event.getBody(), SubmitAnswerRequest.class);

		logger.info("답변 제출 (비동기): sessionId={}, s3Key={}", sessionId, request.audioS3Key());

		// 세션 검증
		OPIcSession session = repository.findSessionById(sessionId).orElse(null);
		if (session == null) {
			return ResponseGenerator.notFound("세션을 찾을 수 없습니다.");
		}
		if (!session.getUserId().equals(userId)) {
			return ResponseGenerator.forbidden("접근 권한이 없습니다.");
		}

		// 현재 질문 인덱스 확인
		int currentIndex = session.getCurrentQuestionIndex();
		if (currentIndex >= session.getTotalQuestions()) {
			return ResponseGenerator.badRequest("이미 모든 질문에 답변했습니다.");
		}

		String questionId = session.getQuestionIds().get(currentIndex);

		// Answer 레코드 생성 (PROCESSING 상태)
		OPIcAnswer answer = new OPIcAnswer();
		answer.setSessionId(sessionId);
		answer.setQuestionId(questionId);
		answer.setQuestionIndex(currentIndex);
		answer.setAudioS3Key(request.audioS3Key());
		answer.setStatus(OPIcAnswer.AnswerStatus.PROCESSING);
		answer.setAttemptCount(0);
		answer.setCreatedAt(Instant.now());

		repository.saveAnswer(answer);
		logger.info("Answer 생성 (PROCESSING): sessionId={}, questionIndex={}", sessionId, currentIndex);

		// SNS로 비동기 처리 요청
		publishToSNS(sessionId, questionId, request.audioS3Key(),
				session.getTargetLevel(), currentIndex, session.getTotalQuestions());

		// 즉시 응답 (HTTP 202 Accepted)
		Map<String, Object> response = new LinkedHashMap<>();
		response.put("sessionId", sessionId);
		response.put("questionIndex", currentIndex);
		response.put("status", "PROCESSING");
		response.put("message", "답변을 처리 중입니다. 잠시 후 결과를 확인하세요.");
		response.put("pollingUrl", String.format("/opic/sessions/%s/answers/%d/status", sessionId, currentIndex));

		return ResponseGenerator.ok(response);
	}

	/**
	 * SNS 발행 (비동기 처리 요청)
	 */
	private void publishToSNS(String sessionId, String questionId, String audioS3Key,
							  String targetLevel, int currentIndex, int totalQuestions) {
		try {
			String topicArn = System.getenv("ANSWER_PROCESS_TOPIC_ARN");

			if (topicArn == null || topicArn.isEmpty()) {
				logger.error("ANSWER_PROCESS_TOPIC_ARN 환경변수가 설정되지 않았습니다.");
				return;
			}

			Map<String, Object> message = new LinkedHashMap<>();
			message.put("sessionId", sessionId);
			message.put("questionId", questionId);
			message.put("audioS3Key", audioS3Key);
			message.put("targetLevel", targetLevel);
			message.put("currentIndex", currentIndex);
			message.put("totalQuestions", totalQuestions);

			AwsClients.sns().publish(PublishRequest.builder()
					.topicArn(topicArn)
					.message(gson.toJson(message))
					.build());

			logger.info("SNS 발행 완료: sessionId={}, questionIndex={}", sessionId, currentIndex);
		} catch (Exception e) {
			logger.error("SNS 발행 실패: {}", e.getMessage(), e);
			// 실패해도 일단 진행 (폴링에서 PROCESSING 상태로 계속 보임)
		}
	}

	/**
	 * GET /opic/sessions/{sessionId}/answers/{questionIndex}/status
	 * 답변 상태 조회 (폴링용)
	 */
	private APIGatewayProxyResponseEvent getAnswerStatus(APIGatewayProxyRequestEvent event, String userId) {
		String sessionId = event.getPathParameters().get("sessionId");
		String questionIndexStr = event.getPathParameters().get("questionIndex");
		int questionIndex = Integer.parseInt(questionIndexStr);

		logger.info("답변 상태 조회: sessionId={}, questionIndex={}", sessionId, questionIndex);

		// 세션 권한 확인
		OPIcSession session = repository.findSessionById(sessionId).orElse(null);
		if (session == null) {
			return ResponseGenerator.notFound("세션을 찾을 수 없습니다.");
		}
		if (!session.getUserId().equals(userId)) {
			return ResponseGenerator.forbidden("접근 권한이 없습니다.");
		}

		// 답변 조회
		OPIcAnswer answer = repository.findAnswer(sessionId, questionIndex).orElse(null);
		if (answer == null) {
			return ResponseGenerator.notFound("답변을 찾을 수 없습니다.");
		}

		Map<String, Object> response = new LinkedHashMap<>();
		response.put("sessionId", sessionId);
		response.put("questionIndex", questionIndex);
		response.put("status", answer.getStatus().name());

		if (answer.getStatus() == OPIcAnswer.AnswerStatus.PROCESSING) {
			response.put("message", "아직 처리 중입니다...");
			return ResponseGenerator.ok(response);
		}

		if (answer.getStatus() == OPIcAnswer.AnswerStatus.FAILED) {
			response.put("message", "처리에 실패했습니다. 다시 시도해주세요.");
			return ResponseGenerator.ok(response);
		}

		// COMPLETED - 전체 결과 반환
		response.put("transcript", answer.getTranscript());

		// feedback 객체 구성
		Map<String, Object> feedback = new LinkedHashMap<>();
		if (answer.getGrammarFeedback() != null && !answer.getGrammarFeedback().isEmpty()) {
			try {
				feedback.put("errors", gson.fromJson(answer.getGrammarFeedback(), List.class));
			} catch (Exception e) {
				feedback.put("errors", new ArrayList<>());
			}
		} else {
			feedback.put("errors", new ArrayList<>());
		}
		feedback.put("correctedAnswer", answer.getContentFeedback());
		feedback.put("sampleAnswer", answer.getSampleAnswer());
		response.put("feedback", feedback);

		boolean hasNext = (questionIndex + 1) < session.getTotalQuestions();
		response.put("hasNextQuestion", hasNext);
		response.put("currentQuestion", questionIndex + 1);
		response.put("totalQuestions", session.getTotalQuestions());

		if (hasNext) {
			response.put("nextQuestionNumber", questionIndex + 2);
		}

		return ResponseGenerator.ok(response);
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

		// COMPLETED 상태인 답변만 카운트
		long completedAnswers = answers.stream()
				.filter(a -> a.getStatus() == OPIcAnswer.AnswerStatus.COMPLETED)
				.count();

		if (completedAnswers < session.getTotalQuestions()) {
			return ResponseGenerator.badRequest(
					String.format("아직 %d개의 질문에 답변하지 않았습니다.",
							session.getTotalQuestions() - completedAnswers)
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

		// 이메일 발송 (비동기로 처리해도 됨 - 현재는 동기)
		try {
			String userEmail = CognitoUtil.extractEmail(event).orElse(null);
			String userName = CognitoUtil.extractNickname(event).orElse("학습자");

			if (userEmail != null && !userEmail.isEmpty()) {
				emailService.sendOPIcReportEmail(userEmail, userName, sessionReport);
				logger.info("리포트 이메일 발송 완료: to={}", userEmail);
			}
		} catch (Exception e) {
			// 이메일 실패해도 세션 완료는 성공 처리
			logger.warn("리포트 이메일 발송 실패 (무시됨): {}", e.getMessage());
		}

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
