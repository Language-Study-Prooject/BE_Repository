package com.mzc.secondproject.serverless.domain.chatting.repository;

import com.mzc.secondproject.serverless.common.config.AwsClients;
import com.mzc.secondproject.serverless.common.config.EnvConfig;
import com.mzc.secondproject.serverless.domain.chatting.model.RoomToken;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;

import java.util.Optional;

public class RoomTokenRepository {
	
	private static final Logger logger = LoggerFactory.getLogger(RoomTokenRepository.class);
	private static final String TABLE_NAME = EnvConfig.getRequired("CHAT_TABLE_NAME");
	
	private final DynamoDbTable<RoomToken> table;
	
	public RoomTokenRepository() {
		this.table = AwsClients.dynamoDbEnhanced().table(TABLE_NAME, TableSchema.fromBean(RoomToken.class));
	}
	
	public RoomToken save(RoomToken roomToken) {
		logger.info("Saving room token for user: {} in room: {}",
				roomToken.getUserId(), roomToken.getRoomId());
		table.putItem(roomToken);
		return roomToken;
	}
	
	public void delete(String token) {
		Key key = Key.builder()
				.partitionValue("TOKEN#" + token)
				.sortValue("METADATA")
				.build();
		
		table.deleteItem(key);
		logger.info("Deleted room token: {}", token);
	}
	
	public Optional<RoomToken> findByToken(String token) {
		Key key = Key.builder()
				.partitionValue("TOKEN#" + token)
				.sortValue("METADATA")
				.build();
		
		RoomToken roomToken = table.getItem(key);
		return Optional.ofNullable(roomToken);
	}
}
