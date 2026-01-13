package com.mzc.secondproject.serverless.domain.badge.constants;

/**
 * Badge 도메인 DynamoDB 키 생성 유틸리티
 */
public final class BadgeKey {

    private BadgeKey() {}

    public static final String BADGE_ALL = "BADGE#ALL";

    public static String userBadgePk(String userId) {
        return "USER#" + userId + "#BADGE";
    }

    public static String badgeSk(String badgeType) {
        return "BADGE#" + badgeType;
    }

    public static String earnedSk(String earnedAt) {
        return "EARNED#" + earnedAt;
    }
}
