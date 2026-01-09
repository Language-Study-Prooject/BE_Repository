package com.mzc.secondproject.serverless.common.constants;

public final class DynamoDbKey {

    private DynamoDbKey() {}

    // Partition/Sort Key Attributes
    public static final String PARTITION_KEY = "PK";
    public static final String SORT_KEY = "SK";

    // GSI Key Attributes
    public static final String GSI1_PK = "GSI1PK";
    public static final String GSI1_SK = "GSI1SK";
    public static final String GSI2_PK = "GSI2PK";
    public static final String GSI2_SK = "GSI2SK";

    // Index Names
    public static final String GSI1 = "GSI1";
    public static final String GSI2 = "GSI2";

    // Entity Prefixes
    public static final String PREFIX_ROOM = "ROOM#";
    public static final String PREFIX_MESSAGE = "MSG#";
    public static final String PREFIX_USER = "USER#";
    public static final String PREFIX_WORD = "WORD#";
    public static final String PREFIX_CONNECTION = "CONNECTION#";
    public static final String PREFIX_DAILY = "DAILY#";
    public static final String PREFIX_LEVEL = "LEVEL#";
    public static final String PREFIX_CATEGORY = "CATEGORY#";
    public static final String PREFIX_DATE = "DATE#";
    public static final String PREFIX_STATUS = "STATUS#";
    public static final String PREFIX_TEST = "TEST#";
    public static final String PREFIX_TOKEN = "TOKEN#";

    // Suffix
    public static final String SUFFIX_METADATA = "METADATA";
    public static final String SUFFIX_REVIEW = "#REVIEW";
    public static final String SUFFIX_STATUS = "#STATUS";
    public static final String SUFFIX_GROUP = "#GROUP";

    // Special Keys
    public static final String ROOMS_ALL = "ROOMS";
    public static final String DAILY_ALL = "DAILY#ALL";

    // Key Builder Methods
    public static String roomPk(String roomId) {
        return PREFIX_ROOM + roomId;
    }

    public static String messageSk(String messageId) {
        return PREFIX_MESSAGE + messageId;
    }

    public static String userPk(String userId) {
        return PREFIX_USER + userId;
    }

    public static String wordPk(String wordId) {
        return PREFIX_WORD + wordId;
    }

    public static String wordSk(String wordId) {
        return PREFIX_WORD + wordId;
    }

    public static String connectionPk(String connectionId) {
        return PREFIX_CONNECTION + connectionId;
    }

    public static String dailyPk(String userId) {
        return PREFIX_DAILY + userId;
    }

    public static String dateSk(String date) {
        return PREFIX_DATE + date;
    }

    public static String levelPk(String level) {
        return PREFIX_LEVEL + level;
    }

    public static String categoryPk(String category) {
        return PREFIX_CATEGORY + category;
    }

    public static String statusSk(String status) {
        return PREFIX_STATUS + status;
    }

    public static String userReviewPk(String userId) {
        return PREFIX_USER + userId + SUFFIX_REVIEW;
    }

    public static String userStatusPk(String userId) {
        return PREFIX_USER + userId + SUFFIX_STATUS;
    }

    public static String userGroupPk(String userId) {
        return PREFIX_USER + userId + SUFFIX_GROUP;
    }

    public static String testPk(String testId) {
        return PREFIX_TEST + testId;
    }

    public static String tokenPk(String token) {
        return PREFIX_TOKEN + token;
    }
}
