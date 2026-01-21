package com.mzc.secondproject.serverless.domain.grammar.repository;

import com.mzc.secondproject.serverless.common.config.AwsClients;
import com.mzc.secondproject.serverless.common.config.EnvConfig;
import com.mzc.secondproject.serverless.common.dto.PaginatedResult;
import com.mzc.secondproject.serverless.common.util.CursorUtil;
import com.mzc.secondproject.serverless.domain.grammar.constants.GrammarKey;
import com.mzc.secondproject.serverless.domain.grammar.model.GrammarMessage;
import com.mzc.secondproject.serverless.domain.grammar.model.GrammarSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.model.Page;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryEnhancedRequest;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public class GrammarSessionRepository {
	
	private static final Logger logger = LoggerFactory.getLogger(GrammarSessionRepository.class);
	private static final String TABLE_NAME = EnvConfig.getRequired("CHAT_TABLE_NAME");
	
	private final DynamoDbTable<GrammarSession> sessionTable;
	private final DynamoDbTable<GrammarMessage> messageTable;

	/**
	 * 기본 생성자 (Lambda에서 사용)
	 */
	public GrammarSessionRepository() {
		this(AwsClients.dynamoDbEnhanced());
	}

	/**
	 * 의존성 주입 생성자 (테스트 용이성)
	 */
	public GrammarSessionRepository(DynamoDbEnhancedClient enhancedClient) {
		this.sessionTable = enhancedClient.table(TABLE_NAME, TableSchema.fromBean(GrammarSession.class));
		this.messageTable = enhancedClient.table(TABLE_NAME, TableSchema.fromBean(GrammarMessage.class));
	}
	
	// ============ Session CRUD ============
	
	public GrammarSession saveSession(GrammarSession session) {
		logger.info("Saving session: sessionId={}", session.getSessionId());
		sessionTable.putItem(session);
		return session;
	}
	
	public Optional<GrammarSession> findSessionById(String userId, String sessionId) {
		Key key = Key.builder()
				.partitionValue(GrammarKey.sessionPk(userId))
				.sortValue(GrammarKey.sessionSk(sessionId))
				.build();
		return Optional.ofNullable(sessionTable.getItem(key));
	}
	
	public PaginatedResult<GrammarSession> findSessionsByUserId(String userId, int limit, String cursor) {
		QueryConditional queryConditional = QueryConditional
				.sortBeginsWith(Key.builder()
						.partitionValue(GrammarKey.sessionPk(userId))
						.sortValue(GrammarKey.SESSION_SK_PREFIX)
						.build());
		
		QueryEnhancedRequest.Builder requestBuilder = QueryEnhancedRequest.builder()
				.queryConditional(queryConditional)
				.scanIndexForward(false)
				.limit(limit);
		
		if (cursor != null && !cursor.isEmpty()) {
			Map<String, AttributeValue> exclusiveStartKey = CursorUtil.decode(cursor);
			if (exclusiveStartKey != null) {
				requestBuilder.exclusiveStartKey(exclusiveStartKey);
			}
		}
		
		Page<GrammarSession> page = sessionTable.query(requestBuilder.build()).iterator().next();
		String nextCursor = CursorUtil.encode(page.lastEvaluatedKey());
		
		return new PaginatedResult<>(page.items(), nextCursor);
	}
	
	public void deleteSession(String userId, String sessionId) {
		Key key = Key.builder()
				.partitionValue(GrammarKey.sessionPk(userId))
				.sortValue(GrammarKey.sessionSk(sessionId))
				.build();
		sessionTable.deleteItem(key);
		logger.info("Session deleted: sessionId={}", sessionId);
	}
	
	// ============ Message CRUD ============
	
	public GrammarMessage saveMessage(GrammarMessage message) {
		logger.info("Saving message: messageId={}", message.getMessageId());
		messageTable.putItem(message);
		return message;
	}
	
	public PaginatedResult<GrammarMessage> findMessagesBySessionId(String sessionId, int limit, String cursor) {
		QueryConditional queryConditional = QueryConditional
				.sortBeginsWith(Key.builder()
						.partitionValue(GrammarKey.messageGsi1Pk(sessionId))
						.sortValue(GrammarKey.MSG_PREFIX)
						.build());
		
		QueryEnhancedRequest.Builder requestBuilder = QueryEnhancedRequest.builder()
				.queryConditional(queryConditional)
				.scanIndexForward(false)
				.limit(limit);
		
		if (cursor != null && !cursor.isEmpty()) {
			Map<String, AttributeValue> exclusiveStartKey = CursorUtil.decode(cursor);
			if (exclusiveStartKey != null) {
				requestBuilder.exclusiveStartKey(exclusiveStartKey);
			}
		}
		
		Page<GrammarMessage> page = messageTable.index("GSI1")
				.query(requestBuilder.build())
				.iterator()
				.next();
		
		String nextCursor = CursorUtil.encode(page.lastEvaluatedKey());
		return new PaginatedResult<>(page.items(), nextCursor);
	}
	
	public List<GrammarMessage> findRecentMessagesBySessionId(String sessionId, int limit) {
		QueryConditional queryConditional = QueryConditional
				.sortBeginsWith(Key.builder()
						.partitionValue(GrammarKey.messageGsi1Pk(sessionId))
						.sortValue(GrammarKey.MSG_PREFIX)
						.build());
		
		QueryEnhancedRequest request = QueryEnhancedRequest.builder()
				.queryConditional(queryConditional)
				.scanIndexForward(false)
				.limit(limit)
				.build();
		
		return messageTable.index("GSI1")
				.query(request)
				.iterator()
				.next()
				.items();
	}
}
