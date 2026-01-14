package com.mzc.secondproject.serverless.domain.grammar.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.*;

/**
 * Grammar WebSocket 연결 정보
 * connectionId ↔ userId 매핑 저장
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@DynamoDbBean
public class GrammarConnection {

	private String pk;          // GRAMMAR_CONN#{connectionId}
	private String sk;          // METADATA
	private String gsi1pk;      // GRAMMAR_USER#{userId}
	private String gsi1sk;      // CONN#{connectionId}

	private String connectionId;
	private String userId;
	private String connectedAt;
	private Long ttl;           // 자동 삭제용

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

	/**
	 * 연결 정보 생성 팩토리 메서드
	 */
	public static GrammarConnection create(String connectionId, String userId, long ttlSeconds) {
		String now = java.time.Instant.now().toString();
		long ttl = java.time.Instant.now().plusSeconds(ttlSeconds).getEpochSecond();

		return GrammarConnection.builder()
				.pk("GRAMMAR_CONN#" + connectionId)
				.sk("METADATA")
				.gsi1pk("GRAMMAR_USER#" + userId)
				.gsi1sk("CONN#" + connectionId)
				.connectionId(connectionId)
				.userId(userId)
				.connectedAt(now)
				.ttl(ttl)
				.build();
	}
}
