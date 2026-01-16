package com.mzc.secondproject.serverless.domain.ranking.repository;

import com.mzc.secondproject.serverless.domain.ranking.model.UserRanking;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryEnhancedRequest;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.DeleteItemRequest;
import software.amazon.awssdk.services.dynamodb.model.QueryRequest;
import software.amazon.awssdk.services.dynamodb.model.QueryResponse;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.WeekFields;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

public class RankingRepository {

	private static final Logger logger = LoggerFactory.getLogger(RankingRepository.class);
	private static final String TABLE_NAME = System.getenv("VOCAB_TABLE_NAME");

	private final DynamoDbClient dynamoDbClient;
	private final DynamoDbEnhancedClient enhancedClient;
	private final DynamoDbTable<UserRanking> rankingTable;

	public RankingRepository() {
		this.dynamoDbClient = DynamoDbClient.builder()
				.region(Region.of(System.getenv("AWS_REGION_NAME")))
				.httpClient(UrlConnectionHttpClient.builder().build())
				.build();
		this.enhancedClient = DynamoDbEnhancedClient.builder()
				.dynamoDbClient(dynamoDbClient)
				.build();
		this.rankingTable = enhancedClient.table(TABLE_NAME, TableSchema.fromBean(UserRanking.class));
	}

	public void updateScore(String userId, int scoreToAdd) {
		String now = Instant.now().toString();
		LocalDate today = LocalDate.now();

		String dailyPeriod = today.toString();
		String weeklyPeriod = today.getYear() + "-W" + String.format("%02d", today.get(WeekFields.of(Locale.getDefault()).weekOfWeekBasedYear()));
		String monthlyPeriod = today.getYear() + "-" + String.format("%02d", today.getMonthValue());

		updatePeriodScore(userId, "DAILY", dailyPeriod, scoreToAdd, now);
		updatePeriodScore(userId, "WEEKLY", weeklyPeriod, scoreToAdd, now);
		updatePeriodScore(userId, "MONTHLY", monthlyPeriod, scoreToAdd, now);
		updatePeriodScore(userId, "TOTAL", "TOTAL", scoreToAdd, now);
	}

	private void updatePeriodScore(String userId, String periodType, String period, int scoreToAdd, String now) {
		String pk = UserRanking.buildPk(periodType, period);

		Optional<UserRanking> existing = findByPkAndUserId(pk, userId);

		if (existing.isPresent()) {
			UserRanking current = existing.get();
			int newScore = current.getScore() + scoreToAdd;

			deleteItem(pk, current.getSk());

			UserRanking updated = UserRanking.builder()
					.pk(pk)
					.sk(UserRanking.buildSk(newScore, userId))
					.userId(userId)
					.periodType(periodType)
					.period(period)
					.score(newScore)
					.nickname(current.getNickname())
					.profileUrl(current.getProfileUrl())
					.updatedAt(now)
					.build();
			rankingTable.putItem(updated);
		} else {
			UserRanking ranking = UserRanking.builder()
					.pk(pk)
					.sk(UserRanking.buildSk(scoreToAdd, userId))
					.userId(userId)
					.periodType(periodType)
					.period(period)
					.score(scoreToAdd)
					.updatedAt(now)
					.build();
			rankingTable.putItem(ranking);
		}
	}

	private Optional<UserRanking> findByPkAndUserId(String pk, String userId) {
		QueryRequest queryRequest = QueryRequest.builder()
				.tableName(TABLE_NAME)
				.keyConditionExpression("PK = :pk")
				.filterExpression("userId = :userId")
				.expressionAttributeValues(Map.of(
						":pk", AttributeValue.builder().s(pk).build(),
						":userId", AttributeValue.builder().s(userId).build()
				))
				.build();

		QueryResponse response = dynamoDbClient.query(queryRequest);

		if (response.items().isEmpty()) {
			return Optional.empty();
		}

		Map<String, AttributeValue> item = response.items().get(0);
		return Optional.of(UserRanking.builder()
				.pk(item.get("PK").s())
				.sk(item.get("SK").s())
				.userId(item.get("userId").s())
				.periodType(item.containsKey("periodType") ? item.get("periodType").s() : null)
				.period(item.containsKey("period") ? item.get("period").s() : null)
				.score(item.containsKey("score") ? Integer.parseInt(item.get("score").n()) : 0)
				.nickname(item.containsKey("nickname") ? item.get("nickname").s() : null)
				.profileUrl(item.containsKey("profileUrl") ? item.get("profileUrl").s() : null)
				.updatedAt(item.containsKey("updatedAt") ? item.get("updatedAt").s() : null)
				.build());
	}

	private void deleteItem(String pk, String sk) {
		DeleteItemRequest deleteRequest = DeleteItemRequest.builder()
				.tableName(TABLE_NAME)
				.key(Map.of(
						"PK", AttributeValue.builder().s(pk).build(),
						"SK", AttributeValue.builder().s(sk).build()
				))
				.build();
		dynamoDbClient.deleteItem(deleteRequest);
	}

	public List<UserRanking> getTopRankings(String periodType, String period, int limit) {
		String pk = UserRanking.buildPk(periodType, period);

		QueryEnhancedRequest request = QueryEnhancedRequest.builder()
				.queryConditional(QueryConditional.keyEqualTo(Key.builder().partitionValue(pk).build()))
				.limit(limit)
				.build();

		List<UserRanking> rankings = new ArrayList<>();
		rankingTable.query(request).items().forEach(rankings::add);
		return rankings;
	}

	public Optional<UserRanking> getUserRanking(String periodType, String period, String userId) {
		String pk = UserRanking.buildPk(periodType, period);
		return findByPkAndUserId(pk, userId);
	}

	public int getUserRank(String periodType, String period, String userId) {
		String pk = UserRanking.buildPk(periodType, period);
		Optional<UserRanking> userRanking = findByPkAndUserId(pk, userId);

		if (userRanking.isEmpty()) {
			return -1;
		}

		String userSk = userRanking.get().getSk();

		QueryRequest countRequest = QueryRequest.builder()
				.tableName(TABLE_NAME)
				.keyConditionExpression("PK = :pk AND SK < :sk")
				.expressionAttributeValues(Map.of(
						":pk", AttributeValue.builder().s(pk).build(),
						":sk", AttributeValue.builder().s(userSk).build()
				))
				.select(software.amazon.awssdk.services.dynamodb.model.Select.COUNT)
				.build();

		QueryResponse response = dynamoDbClient.query(countRequest);
		return response.count() + 1;
	}
}
