package com.mzc.secondproject.serverless.domain.chatting.model;

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
public class Connection {
	
	private String pk;          // CONN#{connectionId}
	private String sk;          // METADATA
	private String gsi1pk;      // ROOM#{roomId} - 방별 연결 조회용
	private String gsi1sk;      // CONN#{connectionId}
	private String gsi2pk;      // USER#{userId} - 사용자별 연결 조회용
	private String gsi2sk;      // CONN#{connectionId}
	
	private String connectionId;
	private String userId;
	private String nickname;
	private String roomId;
	private String connectedAt;
	private Long ttl;           // 10분 후 자동 삭제
	
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
	
	@DynamoDbSecondaryPartitionKey(indexNames = "GSI2")
	@DynamoDbAttribute("GSI2PK")
	public String getGsi2pk() {
		return gsi2pk;
	}
	
	@DynamoDbSecondarySortKey(indexNames = "GSI2")
	@DynamoDbAttribute("GSI2SK")
	public String getGsi2sk() {
		return gsi2sk;
	}
}
