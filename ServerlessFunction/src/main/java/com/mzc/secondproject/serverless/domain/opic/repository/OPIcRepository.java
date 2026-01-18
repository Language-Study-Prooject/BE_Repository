package com.mzc.secondproject.serverless.domain.opic.repository;

import com.mzc.secondproject.serverless.common.config.AwsClients;
import com.mzc.secondproject.serverless.domain.opic.model.OPIcAnswer;
import com.mzc.secondproject.serverless.domain.opic.model.OPIcQuestion;
import com.mzc.secondproject.serverless.domain.opic.model.OPIcSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.enhanced.dynamodb.*;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryEnhancedRequest;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

public class OPIcRepository {

    private static final Logger logger = LoggerFactory.getLogger(OPIcRepository.class);
    private static final String TABLE_NAME = System.getenv("OPIC_TABLE_NAME");

    private final DynamoDbEnhancedClient enhancedClient;
    private final DynamoDbTable<OPIcSession> sessionTable;
    private final DynamoDbTable<OPIcQuestion> questionTable;
    private final DynamoDbTable<OPIcAnswer> answerTable;

    public OPIcRepository() {
        this.enhancedClient = AwsClients.dynamoDbEnhanced();
        this.sessionTable = enhancedClient.table(TABLE_NAME, TableSchema.fromBean(OPIcSession.class));
        this.questionTable = enhancedClient.table(TABLE_NAME, TableSchema.fromBean(OPIcQuestion.class));
        this.answerTable = enhancedClient.table(TABLE_NAME, TableSchema.fromBean(OPIcAnswer.class));
    }

    // ==================== Session ====================

    /**
     * 새 세션 생성
     */
    public OPIcSession createSession(String userId, String topic, String subTopic,
                                     String targetLevel, List<String> questionIds) {
        String sessionId = UUID.randomUUID().toString();
        String today = LocalDate.now(ZoneId.of("Asia/Seoul"))
                .format(DateTimeFormatter.ISO_LOCAL_DATE);
        Instant now = Instant.now();

        OPIcSession session = new OPIcSession();
        session.setPk("USER#" + userId);
        session.setSk("SESSION#" + today + "#" + sessionId);
        session.setGsi1pk("SESSION#" + sessionId);
        session.setGsi1sk("METADATA");

        session.setSessionId(sessionId);
        session.setUserId(userId);
        session.setTopic(topic);
        session.setSubTopic(subTopic);
        session.setTargetLevel(targetLevel);
        session.setStatus(OPIcSession.SessionStatus.IN_PROGRESS);
        session.setCurrentQuestionIndex(0);
        session.setTotalQuestions(questionIds.size());
        session.setQuestionIds(questionIds);
        session.setCreatedAt(now);
        session.setUpdatedAt(now);
        session.setSequenceNumber(0);

        sessionTable.putItem(session);
        logger.info("Session created: {}", sessionId);

        return session;
    }

    /**
     * 세션 ID로 조회 (GSI1 사용)
     */
    public Optional<OPIcSession> findSessionById(String sessionId) {
        DynamoDbIndex<OPIcSession> gsi1 = sessionTable.index("GSI1");

        QueryConditional queryConditional = QueryConditional.keyEqualTo(
                Key.builder()
                        .partitionValue("SESSION#" + sessionId)
                        .sortValue("METADATA")
                        .build()
        );

        return gsi1.query(QueryEnhancedRequest.builder()
                        .queryConditional(queryConditional)
                        .build())
                .stream()
                .flatMap(page -> page.items().stream())
                .findFirst();
    }

    /**
     * 사용자의 세션 목록 조회 (최신순)
     */
    public List<OPIcSession> findSessionsByUserId(String userId, int limit) {
        QueryConditional queryConditional = QueryConditional.sortBeginsWith(
                Key.builder()
                        .partitionValue("USER#" + userId)
                        .sortValue("SESSION#")
                        .build()
        );

        return sessionTable.query(QueryEnhancedRequest.builder()
                        .queryConditional(queryConditional)
                        .scanIndexForward(false)  // 최신순
                        .limit(limit)
                        .build())
                .stream()
                .flatMap(page -> page.items().stream())
                .collect(Collectors.toList());
    }

    /**
     * 세션 업데이트
     */
    public void updateSession(OPIcSession session) {
        session.setUpdatedAt(Instant.now());
        session.setSequenceNumber(session.getSequenceNumber() + 1);
        sessionTable.putItem(session);
        logger.debug("Session updated: {}", session.getSessionId());
    }

    /**
     * 세션 완료 처리
     */
    public void completeSession(OPIcSession session, String overallScore, String overallFeedback) {
        session.setStatus(OPIcSession.SessionStatus.COMPLETED);
        session.setOverallScore(overallScore);
        session.setOverallFeedback(overallFeedback);
        session.setCompletedAt(Instant.now());
        updateSession(session);
        logger.info("Session completed: {}", session.getSessionId());
    }


    // ==================== Question ====================

    /**
     * 질문 ID로 조회
     */
    public Optional<OPIcQuestion> findQuestionById(String questionId) {
        Key key = Key.builder()
                .partitionValue("QUESTION#" + questionId)
                .sortValue("METADATA")
                .build();

        return Optional.ofNullable(questionTable.getItem(key));
    }

    /**
     * 주제 + 레벨로 질문 조회 (GSI1)
     */
    public List<OPIcQuestion> findQuestionsByTopicAndLevel(String topic, String level) {
        DynamoDbIndex<OPIcQuestion> gsi1 = questionTable.index("GSI1");

        QueryConditional queryConditional = QueryConditional.keyEqualTo(
                Key.builder()
                        .partitionValue("TOPIC#" + topic)
                        .sortValue("LEVEL#" + level)
                        .build()
        );

        return gsi1.query(queryConditional)
                .stream()
                .flatMap(page -> page.items().stream())
                .filter(OPIcQuestion::isActive)
                .collect(Collectors.toList());
    }

    /**
     * 여러 질문 ID로 조회
     */
    public List<OPIcQuestion> findQuestionsByIds(List<String> questionIds) {
        return questionIds.stream()
                .map(this::findQuestionById)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .collect(Collectors.toList());
    }

    /**
     * 질문 저장 (마스터 데이터 등록용)
     */
    public void saveQuestion(OPIcQuestion question) {
        question.setPk("QUESTION#" + question.getQuestionId());
        question.setSk("METADATA");
        question.setGsi1pk("TOPIC#" + question.getTopic());
        question.setGsi1sk("LEVEL#" + question.getLevel());

        questionTable.putItem(question);
        logger.info("Question saved: {}", question.getQuestionId());
    }

    // ==================== Answer ====================

    /**
     * 답변 저장
     */
    public void saveAnswer(OPIcAnswer answer) {
        answer.setPk("SESSION#" + answer.getSessionId());
        answer.setSk(String.format("Q#%02d", answer.getQuestionIndex()));

        if (answer.getCreatedAt() == null) {
            answer.setCreatedAt(Instant.now());
        }

        answerTable.putItem(answer);
        logger.debug("Answer saved: session={}, questionIndex={}",
                answer.getSessionId(), answer.getQuestionIndex());
    }

    /**
     * 세션의 특정 질문 답변 조회
     */
    public Optional<OPIcAnswer> findAnswer(String sessionId, int questionIndex) {
        Key key = Key.builder()
                .partitionValue("SESSION#" + sessionId)
                .sortValue(String.format("Q#%02d", questionIndex))
                .build();

        return Optional.ofNullable(answerTable.getItem(key));
    }

    /**
     * 세션의 모든 답변 조회
     */
    public List<OPIcAnswer> findAnswersBySessionId(String sessionId) {
        QueryConditional queryConditional = QueryConditional.sortBeginsWith(
                Key.builder()
                        .partitionValue("SESSION#" + sessionId)
                        .sortValue("Q#")
                        .build()
        );

        return answerTable.query(queryConditional)
                .stream()
                .flatMap(page -> page.items().stream())
                .collect(Collectors.toList());
    }

    /**
     * 답변 업데이트 (피드백 추가 등)
     */
    public void updateAnswer(OPIcAnswer answer) {
        answerTable.putItem(answer);
        logger.debug("Answer updated: session={}, questionIndex={}",
                answer.getSessionId(), answer.getQuestionIndex());
    }


}
