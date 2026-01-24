package com.mzc.secondproject.serverless.domain.chatting.repository;

import com.mzc.secondproject.serverless.common.config.AwsClients;
import com.mzc.secondproject.serverless.common.config.EnvConfig;
import com.mzc.secondproject.serverless.domain.chatting.model.GameSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.enhanced.dynamodb.*;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryEnhancedRequest;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.UpdateItemRequest;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * GameSession Repository
 * 게임 세션 CRUD 및 조회 기능 제공
 */
public class GameSessionRepository {
	
	private static final Logger logger = LoggerFactory.getLogger(GameSessionRepository.class);
	private static final String TABLE_NAME = EnvConfig.getRequired("CHAT_TABLE_NAME");
	
	private final DynamoDbTable<GameSession> table;
	
	/**
	 * 기본 생성자 (Lambda에서 사용)
	 */
	public GameSessionRepository() {
		this(AwsClients.dynamoDbEnhanced());
	}
	
	/**
	 * 의존성 주입 생성자 (테스트 용이성)
	 */
	public GameSessionRepository(DynamoDbEnhancedClient enhancedClient) {
		this.table = enhancedClient.table(TABLE_NAME, TableSchema.fromBean(GameSession.class));
	}
	
	/**
	 * 게임 세션 저장
	 */
	public GameSession save(GameSession session) {
		logger.info("Saving game session: {}", session.getGameSessionId());
		table.putItem(session);
		return session;
	}
	
	/**
	 * ID로 게임 세션 조회
	 */
	public Optional<GameSession> findById(String gameSessionId) {
		Key key = Key.builder()
				.partitionValue("GAME#" + gameSessionId)
				.sortValue("METADATA")
				.build();
		
		GameSession session = table.getItem(key);
		return Optional.ofNullable(session);
	}
	
	/**
	 * 게임 세션 삭제
	 */
	public void delete(String gameSessionId) {
		Key key = Key.builder()
				.partitionValue("GAME#" + gameSessionId)
				.sortValue("METADATA")
				.build();
		
		table.deleteItem(key);
		logger.info("Deleted game session: {}", gameSessionId);
	}
	
	/**
	 * roomId로 활성 게임 세션 조회 (PLAYING 또는 ROUND_END 상태)
	 */
	public Optional<GameSession> findActiveByRoomId(String roomId) {
		List<GameSession> sessions = findByRoomId(roomId);
		
		return sessions.stream()
				.filter(GameSession::isActive)
				.findFirst();
	}
	
	/**
	 * roomId로 모든 게임 세션 조회 (최신순)
	 */
	public List<GameSession> findByRoomId(String roomId) {
		QueryConditional queryConditional = QueryConditional
				.keyEqualTo(Key.builder()
						.partitionValue("ROOM#" + roomId)
						.build());
		
		QueryEnhancedRequest request = QueryEnhancedRequest.builder()
				.queryConditional(queryConditional)
				.scanIndexForward(false)  // 최신순
				.build();
		
		DynamoDbIndex<GameSession> gsi1 = table.index("GSI1");
		
		return gsi1.query(request).stream()
				.flatMap(page -> page.items().stream())
				.toList();
	}
	
	/**
	 * 게임 상태 업데이트
	 */
	public void updateStatus(String gameSessionId, String status) {
		Map<String, AttributeValue> key = buildKey(gameSessionId);
		
		Map<String, AttributeValue> expressionValues = new HashMap<>();
		expressionValues.put(":status", AttributeValue.builder().s(status).build());
		
		UpdateItemRequest updateRequest = UpdateItemRequest.builder()
				.tableName(TABLE_NAME)
				.key(key)
				.updateExpression("SET #status = :status")
				.expressionAttributeNames(Map.of("#status", "status"))
				.expressionAttributeValues(expressionValues)
				.build();
		
		AwsClients.dynamoDb().updateItem(updateRequest);
		logger.info("Updated game session status: {} -> {}", gameSessionId, status);
	}
	
	/**
	 * 라운드 정보 업데이트
	 */
	public void updateRoundInfo(String gameSessionId, int currentRound, String drawerId,
	                            String wordId, String word, long roundStartTime, int roundDuration) {
		Map<String, AttributeValue> key = buildKey(gameSessionId);
		
		Map<String, AttributeValue> expressionValues = new HashMap<>();
		expressionValues.put(":round", AttributeValue.builder().n(String.valueOf(currentRound)).build());
		expressionValues.put(":drawer", AttributeValue.builder().s(drawerId).build());
		expressionValues.put(":wordId", AttributeValue.builder().s(wordId).build());
		expressionValues.put(":word", AttributeValue.builder().s(word).build());
		expressionValues.put(":startTime", AttributeValue.builder().n(String.valueOf(roundStartTime)).build());
		expressionValues.put(":duration", AttributeValue.builder().n(String.valueOf(roundDuration)).build());
		expressionValues.put(":hintUsed", AttributeValue.builder().bool(false).build());
		expressionValues.put(":emptyList", AttributeValue.builder().l(List.of()).build());
		
		String updateExpression = "SET currentRound = :round, " +
				"currentDrawerId = :drawer, " +
				"currentWordId = :wordId, " +
				"currentWord = :word, " +
				"roundStartTime = :startTime, " +
				"roundDuration = :duration, " +
				"hintUsed = :hintUsed, " +
				"correctGuessers = :emptyList";
		
		UpdateItemRequest updateRequest = UpdateItemRequest.builder()
				.tableName(TABLE_NAME)
				.key(key)
				.updateExpression(updateExpression)
				.expressionAttributeValues(expressionValues)
				.build();
		
		AwsClients.dynamoDb().updateItem(updateRequest);
		logger.info("Updated round info: gameSession={}, round={}, drawer={}", gameSessionId, currentRound, drawerId);
	}
	
	/**
	 * 점수 업데이트
	 */
	public void updateScores(String gameSessionId, Map<String, Integer> scores) {
		Map<String, AttributeValue> key = buildKey(gameSessionId);
		
		Map<String, AttributeValue> scoresMap = new HashMap<>();
		scores.forEach((userId, score) ->
				scoresMap.put(userId, AttributeValue.builder().n(String.valueOf(score)).build()));
		
		Map<String, AttributeValue> expressionValues = new HashMap<>();
		expressionValues.put(":scores", AttributeValue.builder().m(scoresMap).build());
		
		UpdateItemRequest updateRequest = UpdateItemRequest.builder()
				.tableName(TABLE_NAME)
				.key(key)
				.updateExpression("SET scores = :scores")
				.expressionAttributeValues(expressionValues)
				.build();
		
		AwsClients.dynamoDb().updateItem(updateRequest);
		logger.info("Updated scores for game session: {}", gameSessionId);
	}
	
	/**
	 * 정답자 추가
	 */
	public void addCorrectGuesser(String gameSessionId, String userId) {
		Map<String, AttributeValue> key = buildKey(gameSessionId);
		
		Map<String, AttributeValue> expressionValues = new HashMap<>();
		expressionValues.put(":userId", AttributeValue.builder().l(
				AttributeValue.builder().s(userId).build()
		).build());
		expressionValues.put(":emptyList", AttributeValue.builder().l(List.of()).build());
		
		UpdateItemRequest updateRequest = UpdateItemRequest.builder()
				.tableName(TABLE_NAME)
				.key(key)
				.updateExpression("SET correctGuessers = list_append(if_not_exists(correctGuessers, :emptyList), :userId)")
				.expressionAttributeValues(expressionValues)
				.build();
		
		AwsClients.dynamoDb().updateItem(updateRequest);
		logger.info("Added correct guesser: gameSession={}, userId={}", gameSessionId, userId);
	}
	
	/**
	 * 연속 정답(streak) 업데이트
	 */
	public void updateStreak(String gameSessionId, String userId, int streak) {
		Map<String, AttributeValue> key = buildKey(gameSessionId);
		
		Map<String, AttributeValue> expressionValues = new HashMap<>();
		expressionValues.put(":streak", AttributeValue.builder().n(String.valueOf(streak)).build());
		
		Map<String, String> expressionNames = new HashMap<>();
		expressionNames.put("#streaks", "streaks");
		expressionNames.put("#userId", userId);
		
		UpdateItemRequest updateRequest = UpdateItemRequest.builder()
				.tableName(TABLE_NAME)
				.key(key)
				.updateExpression("SET #streaks.#userId = :streak")
				.expressionAttributeNames(expressionNames)
				.expressionAttributeValues(expressionValues)
				.build();
		
		AwsClients.dynamoDb().updateItem(updateRequest);
		logger.info("Updated streak: gameSession={}, userId={}, streak={}", gameSessionId, userId, streak);
	}
	
	/**
	 * 힌트 사용 처리
	 */
	public void markHintUsed(String gameSessionId) {
		Map<String, AttributeValue> key = buildKey(gameSessionId);
		
		Map<String, AttributeValue> expressionValues = new HashMap<>();
		expressionValues.put(":hintUsed", AttributeValue.builder().bool(true).build());
		
		UpdateItemRequest updateRequest = UpdateItemRequest.builder()
				.tableName(TABLE_NAME)
				.key(key)
				.updateExpression("SET hintUsed = :hintUsed")
				.expressionAttributeValues(expressionValues)
				.build();
		
		AwsClients.dynamoDb().updateItem(updateRequest);
		logger.info("Marked hint used for game session: {}", gameSessionId);
	}
	
	/**
	 * 게임 종료 처리
	 */
	public void finishGame(String gameSessionId, long endedAt, long ttl) {
		Map<String, AttributeValue> key = buildKey(gameSessionId);
		
		Map<String, AttributeValue> expressionValues = new HashMap<>();
		expressionValues.put(":status", AttributeValue.builder().s("FINISHED").build());
		expressionValues.put(":endedAt", AttributeValue.builder().n(String.valueOf(endedAt)).build());
		expressionValues.put(":ttl", AttributeValue.builder().n(String.valueOf(ttl)).build());
		
		UpdateItemRequest updateRequest = UpdateItemRequest.builder()
				.tableName(TABLE_NAME)
				.key(key)
				.updateExpression("SET #status = :status, endedAt = :endedAt, #ttl = :ttl")
				.expressionAttributeNames(Map.of("#status", "status", "#ttl", "ttl"))
				.expressionAttributeValues(expressionValues)
				.build();
		
		AwsClients.dynamoDb().updateItem(updateRequest);
		logger.info("Finished game session: {}", gameSessionId);
	}
	
	/**
	 * DynamoDB 키 빌더 헬퍼
	 */
	private Map<String, AttributeValue> buildKey(String gameSessionId) {
		Map<String, AttributeValue> key = new HashMap<>();
		key.put("PK", AttributeValue.builder().s("GAME#" + gameSessionId).build());
		key.put("SK", AttributeValue.builder().s("METADATA").build());
		return key;
	}
}
