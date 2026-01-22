현package com.mzc.secondproject.serverless.domain.speaking.repository;

import com.mzc.secondproject.serverless.common.config.AwsClients;
import com.mzc.secondproject.serverless.common.config.EnvConfig;
import com.mzc.secondproject.serverless.domain.speaking.model.SpeakingConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;

import java.util.Optional;

/**
 * Speaking WebSocket 연결 정보 Repository
 */
public class SpeakingConnectionRepository {

    private static final Logger logger = LoggerFactory.getLogger(SpeakingConnectionRepository.class);
    private static final String TABLE_NAME = EnvConfig.getRequired("CHAT_TABLE_NAME");

    private final DynamoDbTable<SpeakingConnection> table;

    public SpeakingConnectionRepository() {
        this.table = AwsClients.dynamoDbEnhanced().table(
                TABLE_NAME,
                TableSchema.fromBean(SpeakingConnection.class)
        );
    }

    /**
     * 연결 정보 저장
     */
    public void save(SpeakingConnection connection) {
        table.putItem(connection);
        logger.debug("Speaking connection saved: connectionId={}, userId={}",
                connection.getConnectionId(), connection.getUserId());
    }

    /**
     * connectionId로 연결 정보 조회
     */
    public Optional<SpeakingConnection> findByConnectionId(String connectionId) {
        Key key = Key.builder()
                .partitionValue(SpeakingConnection.PK_PREFIX + connectionId)
                .sortValue(SpeakingConnection.SK_METADATA)
                .build();

        SpeakingConnection connection = table.getItem(key);
        return Optional.ofNullable(connection);
    }

    /**
     * 연결 정보 업데이트 (대화 히스토리 등)
     */
    public void update(SpeakingConnection connection) {
        table.putItem(connection);
        logger.debug("Speaking connection updated: connectionId={}", connection.getConnectionId());
    }

    /**
     * 연결 정보 삭제
     */
    public void delete(String connectionId) {
        Key key = Key.builder()
                .partitionValue(SpeakingConnection.PK_PREFIX + connectionId)
                .sortValue(SpeakingConnection.SK_METADATA)
                .build();

        table.deleteItem(key);
        logger.info("Speaking connection deleted: connectionId={}", connectionId);
    }
}