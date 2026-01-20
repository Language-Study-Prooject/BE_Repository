package com.mzc.secondproject.serverless.domain.badge.repository;

import com.mzc.secondproject.serverless.common.config.AwsClients;
import com.mzc.secondproject.serverless.domain.badge.constants.BadgeKey;
import com.mzc.secondproject.serverless.domain.badge.model.UserBadge;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryEnhancedRequest;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class BadgeRepository {
	
	private static final Logger logger = LoggerFactory.getLogger(BadgeRepository.class);
	private static final String TABLE_NAME = System.getenv("VOCAB_TABLE_NAME");
	
	private final DynamoDbTable<UserBadge> table;
	
	public BadgeRepository() {
		this.table = AwsClients.dynamoDbEnhanced().table(TABLE_NAME, TableSchema.fromBean(UserBadge.class));
	}
	
	public void save(UserBadge badge) {
		table.putItem(badge);
		logger.info("Saved badge: userId={}, badgeType={}", badge.getOdUserId(), badge.getBadgeType());
	}
	
	public Optional<UserBadge> findByUserIdAndBadgeType(String userId, String badgeType) {
		Key key = Key.builder()
				.partitionValue(BadgeKey.userBadgePk(userId))
				.sortValue(BadgeKey.badgeSk(badgeType))
				.build();
		UserBadge badge = table.getItem(key);
		return Optional.ofNullable(badge);
	}
	
	public List<UserBadge> findByUserId(String userId) {
		QueryConditional queryConditional = QueryConditional.keyEqualTo(
				Key.builder().partitionValue(BadgeKey.userBadgePk(userId)).build()
		);
		
		QueryEnhancedRequest request = QueryEnhancedRequest.builder()
				.queryConditional(queryConditional)
				.build();
		
		List<UserBadge> badges = new ArrayList<>();
		table.query(request).items().forEach(badges::add);
		
		logger.info("Found {} badges for user: {}", badges.size(), userId);
		return badges;
	}
	
	public boolean hasBadge(String userId, String badgeType) {
		return findByUserIdAndBadgeType(userId, badgeType).isPresent();
	}
}
