package com.mzc.secondproject.serverless.domain.vocabulary.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbAttribute;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSortKey;

import java.util.List;

/**
 * 사용자 커스텀 단어장 그룹
 * PK: USER#{userId}#GROUP
 * SK: GROUP#{groupId}
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@DynamoDbBean
public class WordGroup {

    private String pk;          // USER#{userId}#GROUP
    private String sk;          // GROUP#{groupId}

    private String groupId;
    private String userId;
    private String groupName;   // TOEIC, TOEFL, 내 단어장 등
    private String description;
    private List<String> wordIds;
    private Integer wordCount;

    private String createdAt;
    private String updatedAt;

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
}
