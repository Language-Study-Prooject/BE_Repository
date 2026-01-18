package com.mzc.secondproject.serverless.domain.opic.model;

import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.*;

import java.time.Instant;
import java.util.List;


/**
 * OPIc 세션
 */
@DynamoDbBean
public class OPIcSession {

    private String pk;                    // USER#userId
    private String sk;                    // SESSION#date#sessionId
    private String gsi1pk;                // SESSION#sessionId
    private String gsi1sk;                // METADATA

    private String sessionId;
    private String userId;
    private String topic;                 // 대주제 (travel, hobby, work 등)
    private String subTopic;              // 소주제
    private String targetLevel;           // 목표 레벨 (IM1, IM2, IM3, IH, AL)
    private SessionStatus status;         // IN_PROGRESS, COMPLETED, ABANDONED
    private int currentQuestionIndex;     // 현재 진행 중인 질문 (0, 1, 2)
    private int totalQuestions;           // 총 질문 수 (기본 3)
    private List<String> questionIds;     // 질문 ID 목록
    private Instant createdAt;
    private Instant updatedAt;
    private Instant completedAt;
    private int sequenceNumber;           // 인과적 일관성용

    // 종합 결과 (세션 완료 시)
    private String overallScore;          // 종합 예상 등급
    private String overallFeedback;       // 종합 피드백

    @DynamoDbPartitionKey
    @DynamoDbAttribute("PK")
    public String getPk() { return pk; }
    public void setPk(String pk) { this.pk = pk; }

    @DynamoDbSortKey
    @DynamoDbAttribute("SK")
    public String getSk() { return sk; }
    public void setSk(String sk) { this.sk = sk; }

    @DynamoDbSecondaryPartitionKey(indexNames = "GSI1")
    @DynamoDbAttribute("GSI1PK")
    public String getGsi1pk() { return gsi1pk; }
    public void setGsi1pk(String gsi1pk) { this.gsi1pk = gsi1pk; }

    @DynamoDbSecondarySortKey(indexNames = "GSI1")
    @DynamoDbAttribute("GSI1SK")
    public String getGsi1sk() { return gsi1sk; }
    public void setGsi1sk(String gsi1sk) { this.gsi1sk = gsi1sk; }

    @DynamoDbAttribute("sessionId")
    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }

    @DynamoDbAttribute("userId")
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    @DynamoDbAttribute("topic")
    public String getTopic() { return topic; }
    public void setTopic(String topic) { this.topic = topic; }

    @DynamoDbAttribute("subTopic")
    public String getSubTopic() { return subTopic; }
    public void setSubTopic(String subTopic) { this.subTopic = subTopic; }

    @DynamoDbAttribute("targetLevel")
    public String getTargetLevel() { return targetLevel; }
    public void setTargetLevel(String targetLevel) { this.targetLevel = targetLevel; }

    @DynamoDbAttribute("status")
    public SessionStatus getStatus() { return status; }
    public void setStatus(SessionStatus status) { this.status = status; }

    @DynamoDbAttribute("currentQuestionIndex")
    public int getCurrentQuestionIndex() { return currentQuestionIndex; }
    public void setCurrentQuestionIndex(int idx) { this.currentQuestionIndex = idx; }

    @DynamoDbAttribute("totalQuestions")
    public int getTotalQuestions() { return totalQuestions; }
    public void setTotalQuestions(int total) { this.totalQuestions = total; }

    @DynamoDbAttribute("questionIds")
    public List<String> getQuestionIds() { return questionIds; }
    public void setQuestionIds(List<String> ids) { this.questionIds = ids; }

    @DynamoDbAttribute("createdAt")
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    @DynamoDbAttribute("updatedAt")
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }

    @DynamoDbAttribute("completedAt")
    public Instant getCompletedAt() { return completedAt; }
    public void setCompletedAt(Instant completedAt) { this.completedAt = completedAt; }

    @DynamoDbAttribute("sequenceNumber")
    public int getSequenceNumber() { return sequenceNumber; }
    public void setSequenceNumber(int seq) { this.sequenceNumber = seq; }

    @DynamoDbAttribute("overallScore")
    public String getOverallScore() { return overallScore; }
    public void setOverallScore(String score) { this.overallScore = score; }

    @DynamoDbAttribute("overallFeedback")
    public String getOverallFeedback() { return overallFeedback; }
    public void setOverallFeedback(String feedback) { this.overallFeedback = feedback; }

    /**
     * 세션 상태
     */
    public enum SessionStatus {
        IN_PROGRESS,
        COMPLETED,
        ABANDONED
    }
}


