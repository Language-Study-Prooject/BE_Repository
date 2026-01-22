package com.mzc.secondproject.serverless.domain.speaking.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.*;

/**
 * Speaking WebSocket 연결 정보
 * connectionId ↔ userId 매핑 + 대화 히스토리 저장
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@DynamoDbBean
public class SpeakingSession {

    // DynamoDB Key Prefixes
    public static final String PK_PREFIX = "SPEAKING_SESSION#";
    public static final String SK_METADATA = "METADATA";
    public static final String GSI1PK_PREFIX = "SPEAKING_USER#";
    public static final String GSI1SK_PREFIX = "SESSION#";

    private String pk;          // SPEAKING_SESSION#{sessionId}
    private String sk;          // METADATA
    private String gsi1pk;      // SPEAKING_USER#{userId}
    private String gsi1sk;      // SESSION#{sessionId}

    private String sessionId;
    private String userId;
    private String createdAt;
    private String updatedAt;
    private Long ttl;           // 자동 삭제용 (24시간)

    // Speaking 전용 필드
    private String conversationHistory;  // 대화 히스토리 (JSON)
    private String targetLevel;          // 목표 레벨 (BEGINNER, INTERMEDIATE, ADVANCED)

    /**
     * 세션 생성 팩토리 메서드
     */
    public static SpeakingSession create(String sessionId, String userId, String level) {
        String now = java.time.Instant.now().toString();
        // 24시간 후 자동 삭제
        long ttl = java.time.Instant.now().plusSeconds(86400).getEpochSecond();

        return SpeakingSession.builder()
                .pk(PK_PREFIX + sessionId)
                .sk(SK_METADATA)
                .gsi1pk(GSI1PK_PREFIX + userId)
                .gsi1sk(GSI1SK_PREFIX + sessionId)
                .sessionId(sessionId)
                .userId(userId)
                .createdAt(now)
                .updatedAt(now)
                .ttl(ttl)
                .conversationHistory("[]")
                .targetLevel(level != null ? level.toUpperCase() : "INTERMEDIATE")
                .build();
    }

    /**
     * 업데이트 시간 갱신
     */
    public void touch() {
        this.updatedAt = java.time.Instant.now().toString();
        // TTL 연장 (24시간)
        this.ttl = java.time.Instant.now().plusSeconds(86400).getEpochSecond();
    }

    @DynamoDbPartitionKey
    @DynamoDbAttribute("PK")
    public String getPk() {
        return pk;
    }

    @DynamoDbSortKey
    @DynamoDbAttribute("SK")
    public String getSk() {
        return sk;
    }

    @DynamoDbSecondaryPartitionKey(indexNames = "GSI1")
    @DynamoDbAttribute("GSI1PK")
    public String getGsi1pk() {
        return gsi1pk;
    }

    @DynamoDbSecondarySortKey(indexNames = "GSI1")
    @DynamoDbAttribute("GSI1SK")
    public String getGsi1sk() {
        return gsi1sk;
    }
}