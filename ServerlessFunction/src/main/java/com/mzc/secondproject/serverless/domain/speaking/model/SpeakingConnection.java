package com.mzc.secondproject.serverless.domain.speaking.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.*;

/**
 * Speaking WebSocket 연결 정보
 * connectionId ↔ userId 매핑 + 대화 히스토리 저장
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@DynamoDbBean
public class SpeakingConnection {
	
	// DynamoDB Key Prefixes
	public static final String PK_PREFIX = "SPEAKING_CONN#";
	public static final String SK_METADATA = "METADATA";
	public static final String GSI1PK_PREFIX = "SPEAKING_USER#";
	public static final String GSI1SK_PREFIX = "CONN#";
	
	private String pk;          // SPEAKING_CONN#{connectionId}
	private String sk;          // METADATA
	private String gsi1pk;      // SPEAKING_USER#{userId}
	private String gsi1sk;      // CONN#{connectionId}
	
	private String connectionId;
	private String userId;
	private String connectedAt;
	private Long ttl;           // 자동 삭제용
	
	// Speaking 전용 필드
	private String conversationHistory;  // 대화 히스토리 (JSON)
	private String targetLevel;          // 목표 레벨 (BEGINNER, INTERMEDIATE, ADVANCED)
	
	/**
	 * 연결 정보 생성 팩토리 메서드
	 */
	public static SpeakingConnection create(String connectionId, String userId, long ttlSeconds) {
		String now = java.time.Instant.now().toString();
		long ttl = java.time.Instant.now().plusSeconds(ttlSeconds).getEpochSecond();
		
		return SpeakingConnection.builder()
				.pk(PK_PREFIX + connectionId)
				.sk(SK_METADATA)
				.gsi1pk(GSI1PK_PREFIX + userId)
				.gsi1sk(GSI1SK_PREFIX + connectionId)
				.connectionId(connectionId)
				.userId(userId)
				.connectedAt(now)
				.ttl(ttl)
				.conversationHistory("[]")  // 빈 배열로 초기화
				.targetLevel("INTERMEDIATE") // 기본값
				.build();
	}
	
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
