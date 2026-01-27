package com.mzc.secondproject.serverless.domain.opic.handler;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.SNSEvent;
import com.google.gson.Gson;
import com.mzc.secondproject.serverless.common.config.AwsClients;
import com.mzc.secondproject.serverless.domain.opic.dto.response.FeedbackResponse;
import com.mzc.secondproject.serverless.domain.opic.model.OPIcAnswer;
import com.mzc.secondproject.serverless.domain.opic.model.OPIcQuestion;
import com.mzc.secondproject.serverless.domain.opic.model.OPIcSession;
import com.mzc.secondproject.serverless.domain.opic.repository.OPIcRepository;
import com.mzc.secondproject.serverless.domain.opic.service.FeedbackService;
import com.mzc.secondproject.serverless.domain.opic.service.TranscribeProxyService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.Map;

/**
 * SNS 트리거로 답변 비동기 처리
 * - Transcribe (STT)
 * - Bedrock 피드백 생성
 * - Answer 상태 업데이트
 */
public class AnswerProcessHandler implements RequestHandler<SNSEvent, Void> {

    private static final Logger logger = LoggerFactory.getLogger(AnswerProcessHandler.class);
    private static final String OPIC_BUCKET = System.getenv("OPIC_BUCKET_NAME");

    private final Gson gson = new Gson();
    private final OPIcRepository repository = new OPIcRepository();
    private final TranscribeProxyService transcribeService = new TranscribeProxyService();
    private final FeedbackService feedbackService = new FeedbackService();

    @Override
    public Void handleRequest(SNSEvent event, Context context) {
        for (SNSEvent.SNSRecord record : event.getRecords()) {
            processMessage(record.getSNS().getMessage());
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private void processMessage(String messageBody) {
        Map<String, Object> message = gson.fromJson(messageBody, Map.class);

        String sessionId = (String) message.get("sessionId");
        String questionId = (String) message.get("questionId");
        String audioS3Key = (String) message.get("audioS3Key");
        String targetLevel = (String) message.get("targetLevel");
        int currentIndex = ((Number) message.get("currentIndex")).intValue();
        int totalQuestions = ((Number) message.get("totalQuestions")).intValue();

        logger.info("비동기 처리 시작: sessionId={}, questionIndex={}", sessionId, currentIndex);

        try {
            // Answer 조회 (sessionId + questionIndex로 조회)
            OPIcAnswer answer = repository.findAnswer(sessionId, currentIndex)
                    .orElseThrow(() -> new RuntimeException(
                            String.format("Answer not found: sessionId=%s, questionIndex=%d", sessionId, currentIndex)));

            // Question 조회
            OPIcQuestion question = repository.findQuestionById(questionId)
                    .orElseThrow(() -> new RuntimeException("Question not found: " + questionId));

            // 1. S3에서 오디오 로드
            logger.info("S3에서 오디오 파일 로드: {}", audioS3Key);
            byte[] audioBytes = AwsClients.s3().getObjectAsBytes(
                    software.amazon.awssdk.services.s3.model.GetObjectRequest.builder()
                            .bucket(OPIC_BUCKET)
                            .key(audioS3Key)
                            .build()
            ).asByteArray();

            String audioBase64 = java.util.Base64.getEncoder().encodeToString(audioBytes);
            logger.info("오디오 Base64 변환: {} bytes → {} chars", audioBytes.length, audioBase64.length());

            // 2. Transcribe (STT)
            TranscribeProxyService.TranscribeResult transcribeResult =
                    transcribeService.transcribe(audioBase64, sessionId);

            String transcript = transcribeResult.transcript();
            logger.info("STT 완료: transcript 길이={}", transcript.length());

            // 3. Bedrock 피드백
            FeedbackResponse feedback = feedbackService.generateFeedback(
                    question.getQuestionText(),
                    transcript,
                    targetLevel
            );
            logger.info("피드백 생성 완료");

            // 4. Answer 업데이트 (COMPLETED)
            answer.setQuestionText(question.getQuestionText());
            answer.setTranscript(transcript);
            answer.setTranscriptConfidence(transcribeResult.confidence());
            answer.setGrammarFeedback(gson.toJson(feedback.errors()));
            answer.setContentFeedback(feedback.correctedAnswer());
            answer.setSampleAnswer(feedback.sampleAnswer());
            answer.setStatus(OPIcAnswer.AnswerStatus.COMPLETED);
            answer.setAttemptCount(answer.getAttemptCount() + 1);
            answer.setCompletedAt(Instant.now());

            repository.saveAnswer(answer);

            // 5. 세션 업데이트 (currentQuestionIndex 증가)
            OPIcSession session = repository.findSessionById(sessionId).orElse(null);
            if (session != null) {
                session.setCurrentQuestionIndex(currentIndex + 1);
                repository.updateSession(session);
            }

            logger.info("비동기 처리 완료: sessionId={}, questionIndex={}", sessionId, currentIndex);

        } catch (Exception e) {
            logger.error("비동기 처리 실패: sessionId={}, questionIndex={}, error={}",
                    sessionId, currentIndex, e.getMessage(), e);

            // 실패 상태로 업데이트
            try {
                OPIcAnswer answer = repository.findAnswer(sessionId, currentIndex).orElse(null);
                if (answer != null) {
                    answer.setStatus(OPIcAnswer.AnswerStatus.FAILED);
                    answer.setAttemptCount(answer.getAttemptCount() + 1);
                    repository.saveAnswer(answer);
                    logger.info("Answer 상태 FAILED로 업데이트: sessionId={}, questionIndex={}", sessionId, currentIndex);
                }
            } catch (Exception ex) {
                logger.error("실패 상태 업데이트 실패", ex);
            }
        }
    }
}














































































