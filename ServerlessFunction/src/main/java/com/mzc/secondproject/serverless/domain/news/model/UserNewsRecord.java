package com.mzc.secondproject.serverless.domain.news.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.*;

/**
 * 사용자 뉴스 학습 기록
 * PK: USER_NEWS#{userId}
 * SK: READ#{articleId} 또는 BOOKMARK#{articleId}
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@DynamoDbBean
public class UserNewsRecord {
	
	private String pk;          // USER_NEWS#{userId}
	private String sk;          // READ#{articleId} 또는 BOOKMARK#{articleId}
	private String gsi1pk;      // USER_NEWS_STAT#{userId}
	private String gsi1sk;      // {date}#{type}
	
	private String userId;
	private String articleId;
	private String type;        // READ, BOOKMARK
	private String articleTitle;
	private String articleLevel;
	private String articleCategory;
	private String createdAt;
	private Long ttl;
	
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
}
