package com.mzc.secondproject.serverless.domain.user.model;

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
public class User {
	
	private String pk;          // USER#{cognitoSub}
	private String sk;          // METADATA
	private String gsi1pk;      // EMAIL#{email}
	private String gsi1sk;      // USER#{cognitoSub}
	private String gsi2pk;      // LEVEL#{level}
	private String gsi2sk;      // USER#{cognitoSub}
	
	private String cognitoSub;  // Cognito sub (Primary ID)
	private String email;
	private String nickname;
	private String level;
	private String createdAt;
	private String updatedAt;
	private String lastLoginAt;
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
