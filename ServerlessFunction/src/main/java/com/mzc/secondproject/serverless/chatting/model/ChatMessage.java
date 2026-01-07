package com.mzc.secondproject.serverless.chatting.model;

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

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@DynamoDbBean
public class ChatMessage {

    private String pk;          // ROOM#{roomId}
    private String sk;          // MSG#{timestamp}#{messageId}
    private String gsi1pk;      // USER#{userId}
    private String gsi1sk;      // MSG#{timestamp}
    private String gsi2pk;      // MSG#{messageId} - messageId로 직접 조회용
    private String gsi2sk;      // ROOM#{roomId}

    private String messageId;
    private String roomId;
    private String userId;
    private String content;
    private String messageType; // TEXT, IMAGE, VOICE, AI_RESPONSE
    private String createdAt;
    private Long ttl;

    // 음성 캐시용 S3 키 (voice/{messageId}_{voice}.mp3)
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
