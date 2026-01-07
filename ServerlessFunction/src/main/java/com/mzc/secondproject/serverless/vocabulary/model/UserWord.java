package com.mzc.secondproject.serverless.vocabulary.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbAttribute;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSecondaryPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSecondarySortKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSortKey;

/**
 * 사용자별 단어 학습 상태 (Spaced Repetition)
 * PK: USER#{userId}
 * SK: WORD#{wordId}
 * GSI1: USER#{userId}#REVIEW / DATE#{nextReviewAt} - 복습 예정 조회
 * GSI2: USER#{userId}#STATUS / STATUS#{status} - 상태별 조회
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@DynamoDbBean
public class UserWord {

    private String pk;          // USER#{userId}
    private String sk;          // WORD#{wordId}
    private String gsi1pk;      // USER#{userId}#REVIEW
    private String gsi1sk;      // DATE#{nextReviewAt}
    private String gsi2pk;      // USER#{userId}#STATUS
    private String gsi2sk;      // STATUS#{status}

    private String userId;
    private String wordId;
    private String status;      // NEW, LEARNING, REVIEWING, MASTERED

    // Spaced Repetition 알고리즘 필드
    private Integer interval;       // 복습 간격 (일)
    private Double easeFactor;      // 난이도 계수 (2.5 기본)
    private Integer repetitions;    // 연속 정답 횟수
    private String nextReviewAt;    // 다음 복습 예정일
    private String lastReviewedAt;  // 마지막 복습일

    // 학습 통계
    private Integer correctCount;   // 정답 횟수
    private Integer incorrectCount; // 오답 횟수
    private String createdAt;
    private String updatedAt;
    private Long ttl;

    // 사용자 태그
    private Boolean bookmarked;     // 북마크 여부
    private Boolean favorite;       // 즐겨찾기 여부
    private String difficulty;      // 사용자 지정 난이도 (EASY, NORMAL, HARD)

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

    @DynamoDbSecondaryPartitionKey(indexNames = "GSI2")
    @DynamoDbAttribute("GSI2PK")
    public String getGsi2pk() {
        return gsi2pk;
    }

    @DynamoDbSecondarySortKey(indexNames = "GSI2")
    @DynamoDbAttribute("GSI2SK")
    public String getGsi2sk() {
        return gsi2sk;
    }
}
