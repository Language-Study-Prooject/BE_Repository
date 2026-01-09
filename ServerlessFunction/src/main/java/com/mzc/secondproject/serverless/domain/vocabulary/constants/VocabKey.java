package com.mzc.secondproject.serverless.domain.vocabulary.constants;

import com.mzc.secondproject.serverless.common.constants.DynamoDbKey;

public final class VocabKey {

    private VocabKey() {}

    // Prefixes
    public static final String WORD = "WORD#";
    public static final String DAILY = "DAILY#";
    public static final String LEVEL = "LEVEL#";
    public static final String CATEGORY = "CATEGORY#";
    public static final String TEST = "TEST#";
    public static final String DATE = "DATE#";
    public static final String STATUS_PREFIX = "STATUS#";

    // Suffix
    public static final String SUFFIX_REVIEW = "#REVIEW";
    public static final String SUFFIX_STATUS = "#STATUS";
    public static final String SUFFIX_GROUP = "#GROUP";

    // Special Keys
    public static final String DAILY_ALL = "DAILY#ALL";

    // Key Builders
    public static String userPk(String userId) {
        return DynamoDbKey.USER + userId;
    }

    public static String wordPk(String wordId) {
        return WORD + wordId;
    }

    public static String wordSk(String wordId) {
        return WORD + wordId;
    }

    public static String dailyPk(String userId) {
        return DAILY + userId;
    }

    public static String dateSk(String date) {
        return DATE + date;
    }

    public static String levelPk(String level) {
        return LEVEL + level;
    }

    public static String categoryPk(String category) {
        return CATEGORY + category;
    }

    public static String statusSk(String status) {
        return STATUS_PREFIX + status;
    }

    public static String userReviewPk(String userId) {
        return DynamoDbKey.USER + userId + SUFFIX_REVIEW;
    }

    public static String userStatusPk(String userId) {
        return DynamoDbKey.USER + userId + SUFFIX_STATUS;
    }

    public static String userGroupPk(String userId) {
        return DynamoDbKey.USER + userId + SUFFIX_GROUP;
    }

    public static String testPk(String testId) {
        return TEST + testId;
    }
}
