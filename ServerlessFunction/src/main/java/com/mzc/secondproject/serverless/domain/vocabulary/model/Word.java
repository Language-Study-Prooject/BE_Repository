package com.mzc.secondproject.serverless.domain.vocabulary.model;

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
 * 단어 정보 모델
 * PK: WORD#{wordId}
 * SK: METADATA
 * GSI1: LEVEL#{level} / WORD#{wordId} - 난이도별 조회
 * GSI2: CATEGORY#{category} / WORD#{wordId} - 카테고리별 조회
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@DynamoDbBean
public class Word {

    private String pk;          // WORD#{wordId}
    private String sk;          // METADATA
    private String gsi1pk;      // LEVEL#{level}
    private String gsi1sk;      // WORD#{wordId}
    private String gsi2pk;      // CATEGORY#{category}
    private String gsi2sk;      // WORD#{wordId}

    private String wordId;
    private String english;     // 영어 단어
    private String korean;      // 한국어 뜻
    private String example;     // 예문
    private String level;       // BEGINNER, INTERMEDIATE, ADVANCED
    private String category;    // DAILY, BUSINESS, ACADEMIC, etc.
    private String createdAt;
    private Long ttl;

    // 음성 캐시용 S3 키 (vocab/voice/{wordId}_{voice}.mp3)
    private String maleVoiceKey;
    private String femaleVoiceKey;

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
