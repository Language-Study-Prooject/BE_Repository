package com.mzc.secondproject.serverless.domain.chatting.constants;

import com.mzc.secondproject.serverless.common.constants.DynamoDbKey;

public final class ChatKey {

    private ChatKey() {}

    // Prefixes
    public static final String ROOM = "ROOM#";
    public static final String MESSAGE = "MSG#";
    public static final String CONNECTION = "CONNECTION#";
    public static final String TOKEN = "TOKEN#";

    // Special Keys
    public static final String ROOMS_ALL = "ROOMS";

    // Key Builders
    public static String roomPk(String roomId) {
        return ROOM + roomId;
    }

    public static String messageSk(String messageId) {
        return MESSAGE + messageId;
    }

    public static String userPk(String userId) {
        return DynamoDbKey.USER + userId;
    }

    public static String connectionPk(String connectionId) {
        return CONNECTION + connectionId;
    }

    public static String tokenPk(String token) {
        return TOKEN + token;
    }
}
