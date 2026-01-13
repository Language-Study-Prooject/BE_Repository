package com.mzc.secondproject.serverless.domain.badge.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.*;

/**
 * 사용자 뱃지
 * PK: USER#{userId}#BADGE
 * SK: BADGE#{badgeType}
 * GSI1: BADGE#ALL / EARNED#{earnedAt} - 최근 획득 뱃지 전체 조회
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@DynamoDbBean
public class UserBadge {
	
	private String pk;          // USER#{userId}#BADGE
	private String sk;          // BADGE#{badgeType}
	private String gsi1pk;      // BADGE#ALL
	private String gsi1sk;      // EARNED#{earnedAt}
	
	private String odUserId;
	private String badgeType;   // BadgeType enum name
	private String name;
	private String description;
	private String imageUrl;
	private String category;
	private Integer threshold;
	private Integer progress;   // 현재 진행도 (획득 시점의 값)
	
	private String earnedAt;
	private String createdAt;
	
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
	
	@DynamoDbAttribute("userId")
	public String getOdUserId() {
		return odUserId;
	}
}
