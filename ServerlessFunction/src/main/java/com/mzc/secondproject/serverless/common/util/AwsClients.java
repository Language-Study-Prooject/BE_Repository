package com.mzc.secondproject.serverless.common.util;

import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.polly.PollyClient;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;

/**
 * AWS SDK 클라이언트 싱글톤 관리
 * Lambda Cold Start 최적화를 위해 static final로 선언
 */
public final class AwsClients {

    private AwsClients() {
        // 인스턴스화 방지
    }

    // DynamoDB
    private static final DynamoDbClient DYNAMO_DB_CLIENT = DynamoDbClient.builder().build();
    private static final DynamoDbEnhancedClient DYNAMO_DB_ENHANCED_CLIENT = DynamoDbEnhancedClient.builder()
            .dynamoDbClient(DYNAMO_DB_CLIENT)
            .build();

    // S3
    private static final S3Client S3_CLIENT = S3Client.builder().build();
    private static final S3Presigner S3_PRESIGNER = S3Presigner.builder().build();

    // Polly
    private static final PollyClient POLLY_CLIENT = PollyClient.builder().build();

    // SNS
    private static final SnsClient SNS_CLIENT = SnsClient.builder().build();

    // Bedrock
    private static final BedrockRuntimeClient BEDROCK_CLIENT = BedrockRuntimeClient.builder().build();

    public static DynamoDbClient dynamoDb() {
        return DYNAMO_DB_CLIENT;
    }

    public static DynamoDbEnhancedClient dynamoDbEnhanced() {
        return DYNAMO_DB_ENHANCED_CLIENT;
    }

    public static S3Client s3() {
        return S3_CLIENT;
    }

    public static S3Presigner s3Presigner() {
        return S3_PRESIGNER;
    }

    public static PollyClient polly() {
        return POLLY_CLIENT;
    }

    public static SnsClient sns() {
        return SNS_CLIENT;
    }

    public static BedrockRuntimeClient bedrock() {
        return BEDROCK_CLIENT;
    }
}
