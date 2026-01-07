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

import java.util.List;

/**
 * 일일 학습 정보
 * PK: DAILY#{userId}
 * SK: DATE#{date}
 * GSI1: DAILY#ALL / DATE#{date} - 전체 일일 학습 조회
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@DynamoDbBean
public class DailyStudy {

    private String pk;          // DAILY#{userId}
    private String sk;          // DATE#{date}
    private String gsi1pk;      // DAILY#ALL
    private String gsi1sk;      // DATE#{date}

    private String userId;
    private String date;        // yyyy-MM-dd

    // 학습 단어 목록 (55개: 50개 신규 + 5개 복습)
    private List<String> newWordIds;        // 신규 단어 ID 목록 (50개)
    private List<String> reviewWordIds;     // 복습 단어 ID 목록 (5개)
    private List<String> learnedWordIds;    // 학습 완료 단어 ID 목록

    // 진행 상태
    private Integer totalWords;     // 총 단어 수 (55)
    private Integer learnedCount;   // 학습 완료 수
    private Boolean isCompleted;    // 일일 학습 완료 여부

    private String createdAt;
    private String updatedAt;
    private Long ttl;

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
