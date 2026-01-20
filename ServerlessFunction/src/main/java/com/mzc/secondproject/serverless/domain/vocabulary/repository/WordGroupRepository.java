package com.mzc.secondproject.serverless.domain.vocabulary.repository;

import com.mzc.secondproject.serverless.common.config.AwsClients;
import com.mzc.secondproject.serverless.common.config.EnvConfig;
import com.mzc.secondproject.serverless.common.dto.PaginatedResult;
import com.mzc.secondproject.serverless.common.util.CursorUtil;
import com.mzc.secondproject.serverless.domain.vocabulary.model.WordGroup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.model.Page;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryEnhancedRequest;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

import java.util.Map;
import java.util.Optional;

public class WordGroupRepository {
	
	private static final Logger logger = LoggerFactory.getLogger(WordGroupRepository.class);
	private static final String TABLE_NAME = EnvConfig.getRequired("VOCAB_TABLE_NAME");
	
	private final DynamoDbTable<WordGroup> table;
	
	public WordGroupRepository() {
		this.table = AwsClients.dynamoDbEnhanced().table(TABLE_NAME, TableSchema.fromBean(WordGroup.class));
	}
	
	public WordGroup save(WordGroup wordGroup) {
		logger.info("Saving word group: userId={}, groupId={}", wordGroup.getUserId(), wordGroup.getGroupId());
		table.putItem(wordGroup);
		return wordGroup;
	}
	
	public Optional<WordGroup> findByUserIdAndGroupId(String userId, String groupId) {
		Key key = Key.builder()
				.partitionValue("USER#" + userId + "#GROUP")
				.sortValue("GROUP#" + groupId)
				.build();
		
		WordGroup wordGroup = table.getItem(key);
		return Optional.ofNullable(wordGroup);
	}
	
	public void delete(String userId, String groupId) {
		Key key = Key.builder()
				.partitionValue("USER#" + userId + "#GROUP")
				.sortValue("GROUP#" + groupId)
				.build();
		
		table.deleteItem(key);
		logger.info("Deleted word group: userId={}, groupId={}", userId, groupId);
	}
	
	public PaginatedResult<WordGroup> findByUserId(String userId, int limit, String cursor) {
		QueryConditional queryConditional = QueryConditional
				.sortBeginsWith(Key.builder()
						.partitionValue("USER#" + userId + "#GROUP")
						.sortValue("GROUP#")
						.build());
		
		QueryEnhancedRequest.Builder requestBuilder = QueryEnhancedRequest.builder()
				.queryConditional(queryConditional)
				.limit(limit);
		
		if (cursor != null && !cursor.isEmpty()) {
			Map<String, AttributeValue> exclusiveStartKey = CursorUtil.decode(cursor);
			if (exclusiveStartKey != null) {
				requestBuilder.exclusiveStartKey(exclusiveStartKey);
			}
		}
		
		Page<WordGroup> page = table.query(requestBuilder.build()).iterator().next();
		String nextCursor = CursorUtil.encode(page.lastEvaluatedKey());
		
		return new PaginatedResult<>(page.items(), nextCursor);
	}
}
