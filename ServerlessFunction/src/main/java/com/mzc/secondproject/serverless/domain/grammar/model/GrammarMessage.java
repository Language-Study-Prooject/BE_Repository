package com.mzc.secondproject.serverless.domain.grammar.model;

import lombok.*;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@DynamoDbBean
public class GrammarMessage {

	private String pk;          // GSESSION#{userId}
	private String sk;          // MSG#{timestamp}#{messageId}
	private String gsi1pk;      // GSESSION#{sessionId}
	private String gsi1sk;      // MSG#{timestamp}

	private String messageId;
	private String sessionId;
	private String userId;
	private String role;        // USER, ASSISTANT
	private String content;
	private String correctedContent;
	private String errorsJson;
	private Integer grammarScore;
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
