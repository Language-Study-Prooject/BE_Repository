package com.mzc.secondproject.serverless.common.constants;

public final class DynamoDbKey {

    private DynamoDbKey() {}

    // Partition/Sort Key Attributes
    public static final String PK = "PK";
    public static final String SK = "SK";

    // GSI Key Attributes
    public static final String GSI1_PK = "GSI1PK";
    public static final String GSI1_SK = "GSI1SK";
    public static final String GSI2_PK = "GSI2PK";
    public static final String GSI2_SK = "GSI2SK";

    // Index Names
    public static final String GSI1 = "GSI1";
    public static final String GSI2 = "GSI2";

    // Common Sort Key
    public static final String METADATA = "METADATA";

    // 공용 Entity Prefix
    public static final String USER = "USER#";
}
