package com.mzc.secondproject.serverless.common.constants;

/**
 * DynamoDB 공통 키 상수 및 빌더
 * 모든 도메인에서 공통으로 사용되는 키 패턴 정의
 */
public final class DynamoDbKey {
	
	// Partition/Sort Key Attributes
	public static final String PK = "PK";
	public static final String SK = "SK";
	// GSI Key Attributes
	public static final String GSI1_PK = "GSI1PK";
	public static final String GSI1_SK = "GSI1SK";
	public static final String GSI2_PK = "GSI2PK";
	public static final String GSI2_SK = "GSI2SK";
	public static final String GSI3_PK = "GSI3PK";
	public static final String GSI3_SK = "GSI3SK";
	// Index Names
	public static final String GSI1 = "GSI1";
	public static final String GSI2 = "GSI2";
	public static final String GSI3 = "GSI3";
	// Common Sort Key
	public static final String METADATA = "METADATA";
	// 공용 Entity Prefix
	public static final String USER = "USER#";
	
	private DynamoDbKey() {
	}
	
	/**
	 * 사용자 PK 생성 (공통)
	 * 여러 도메인에서 동일한 패턴으로 사용
	 */
	public static String userPk(String userId) {
		return USER + userId;
	}
}
