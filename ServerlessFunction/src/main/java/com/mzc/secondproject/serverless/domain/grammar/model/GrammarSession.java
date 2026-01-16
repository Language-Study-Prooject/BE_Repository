package com.mzc.secondproject.serverless.domain.grammar.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@DynamoDbBean
public class GrammarSession {
	
	private String pk;          // GSESSION#{userId}
	private String sk;          // SESSION#{sessionId}
	private String gsi1pk;      // GSESSION#ALL
	private String gsi1sk;      // UPDATED#{timestamp}
	
	private String sessionId;
	private String userId;
	private String level;
	private String topic;
	private Integer messageCount;
	private String lastMessage;
	private String createdAt;
	private String updatedAt;
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
