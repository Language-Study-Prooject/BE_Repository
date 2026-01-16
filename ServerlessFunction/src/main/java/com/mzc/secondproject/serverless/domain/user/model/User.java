package com.mzc.secondproject.serverless.domain.user.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.*;

import java.time.Instant;

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
	private String profileUrl;
	private String createdAt;
	private String updatedAt;
	private String lastLoginAt;
	private Long ttl;
	
	/**
	 * 신규 사용자 생성
	 * - Lazy Registration 적용: 최초 프로필 조회 시 DynamoDB에 저장
	 *
	 * @param cognitoSub Cognito User Pool의 sub (UUID)
	 * @param email      이메일
	 * @param nickname   닉네임
	 * @param level      학습 레벨 (BEGINNER/INTERMEDIATE/ADVANCED)
	 * @param profileUrl 프로필 이미지 URL
	 * @return 새로운 User 객체 (DynamoDB 키 패턴 적용됨)
	 */
	public static User createNew(String cognitoSub, String email, String nickname, String level, String profileUrl) {
		String now = Instant.now().toString();
		return User.builder()
				.pk("USER#" + cognitoSub)
				.sk("METADATA")
				.gsi1pk("EMAIL#" + email)
				.gsi1sk("USER#" + cognitoSub)
				.gsi2pk("LEVEL#" + level)
				.gsi2sk("USER#" + cognitoSub)
				.cognitoSub(cognitoSub)
				.email(email)
				.nickname(nickname)
				.level(level)
				.profileUrl(profileUrl)
				.createdAt(now)
				.updatedAt(now)
				.lastLoginAt(now)
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
	
	public void updateLevel(String newLevel) {
		this.level = newLevel;
		this.gsi2pk = "LEVEL#" + newLevel;
		this.updatedAt = Instant.now().toString();
	}
	
	public void updateNickname(String newNickname) {
		this.nickname = newNickname;
		this.updatedAt = Instant.now().toString();
	}
	
	public void updateProfileUrl(String newProfileUrl) {
		this.profileUrl = newProfileUrl;
		this.updatedAt = Instant.now().toString();
	}
	
	public void updateLastLoginAt() {
		this.lastLoginAt = Instant.now().toString();
	}
	
	
}
