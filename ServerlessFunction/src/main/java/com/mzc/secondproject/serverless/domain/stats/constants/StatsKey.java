package com.mzc.secondproject.serverless.domain.stats.constants;

import com.mzc.secondproject.serverless.common.constants.DynamoDbKey;

/**
 * 학습 통계 도메인 키 상수
 */
public final class StatsKey {

    private StatsKey() {}

    // Suffix
    public static final String SUFFIX_STATS = "#STATS";

    // Stats Period Prefixes
    public static final String STATS_DAILY = "DAILY#";
    public static final String STATS_WEEKLY = "WEEKLY#";
    public static final String STATS_MONTHLY = "MONTHLY#";
    public static final String STATS_TOTAL = "TOTAL";

    // Key Builders
    public static String userStatsPk(String userId) {
        return DynamoDbKey.USER + userId + SUFFIX_STATS;
    }

    public static String statsDailySk(String date) {
        return STATS_DAILY + date;
    }

    public static String statsWeeklySk(String yearWeek) {
        return STATS_WEEKLY + yearWeek;
    }

    public static String statsMonthlySk(String yearMonth) {
        return STATS_MONTHLY + yearMonth;
    }

    public static String statsTotalSk() {
        return STATS_TOTAL;
    }
}
