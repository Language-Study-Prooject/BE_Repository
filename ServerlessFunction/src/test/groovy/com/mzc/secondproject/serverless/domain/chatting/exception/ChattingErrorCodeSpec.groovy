package com.mzc.secondproject.serverless.domain.chatting.exception

import spock.lang.Specification
import spock.lang.Unroll

class ChattingErrorCodeSpec extends Specification {

    def "모든 에러 코드의 도메인은 CHATTING"() {
        expect:
        ChattingErrorCode.values().every { it.getDomain() == "CHATTING" }
    }

    @Unroll
    def "에러 코드 '#errorCode': code=#expectedCode, statusCode=#expectedStatusCode"() {
        expect:
        errorCode.getCode() == expectedCode
        errorCode.getStatusCode() == expectedStatusCode
        errorCode.getMessage() != null
        !errorCode.getMessage().isEmpty()

        where:
        errorCode                                  | expectedCode | expectedStatusCode
        ChattingErrorCode.ROOM_NOT_FOUND           | "ROOM_001"   | 404
        ChattingErrorCode.ROOM_ALREADY_EXISTS      | "ROOM_002"   | 409
        ChattingErrorCode.ROOM_FULL                | "ROOM_003"   | 400
        ChattingErrorCode.ROOM_CLOSED              | "ROOM_004"   | 400
        ChattingErrorCode.ROOM_INVALID_PASSWORD    | "ROOM_005"   | 401
        ChattingErrorCode.ROOM_NOT_OWNER           | "ROOM_006"   | 403
        ChattingErrorCode.MESSAGE_NOT_FOUND        | "MSG_001"    | 404
        ChattingErrorCode.MESSAGE_TOO_LONG         | "MSG_002"    | 400
        ChattingErrorCode.INVALID_MESSAGE_TYPE     | "MSG_003"    | 400
        ChattingErrorCode.NOT_ROOM_MEMBER          | "MEMBER_001" | 403
        ChattingErrorCode.ALREADY_JOINED           | "MEMBER_002" | 409
        ChattingErrorCode.INVALID_ROOM_TOKEN       | "MEMBER_003" | 401
        ChattingErrorCode.INVALID_CHAT_LEVEL       | "LEVEL_001"  | 400
        ChattingErrorCode.CONNECTION_FAILED        | "CONN_001"   | 500
        ChattingErrorCode.CONNECTION_TIMEOUT       | "CONN_002"   | 408
        ChattingErrorCode.GAME_START_FAILED        | "GAME_001"   | 400
        ChattingErrorCode.GAME_STOP_FAILED         | "GAME_002"   | 400
        ChattingErrorCode.GAME_NOT_IN_PROGRESS     | "GAME_003"   | 400
        ChattingErrorCode.GAME_ALREADY_IN_PROGRESS | "GAME_004"   | 409
        ChattingErrorCode.NOT_GAME_STARTER         | "GAME_005"   | 403
    }

    def "모든 에러 코드 개수 확인"() {
        expect: "20개의 에러 코드 존재"
        ChattingErrorCode.values().length == 20
    }

    def "채팅방 관련 에러 코드들 (ROOM_XXX)"() {
        expect:
        ChattingErrorCode.ROOM_NOT_FOUND.getCode().startsWith("ROOM_")
        ChattingErrorCode.ROOM_ALREADY_EXISTS.getCode().startsWith("ROOM_")
        ChattingErrorCode.ROOM_FULL.getCode().startsWith("ROOM_")
        ChattingErrorCode.ROOM_CLOSED.getCode().startsWith("ROOM_")
        ChattingErrorCode.ROOM_INVALID_PASSWORD.getCode().startsWith("ROOM_")
        ChattingErrorCode.ROOM_NOT_OWNER.getCode().startsWith("ROOM_")
    }

    def "메시지 관련 에러 코드들 (MSG_XXX)"() {
        expect:
        ChattingErrorCode.MESSAGE_NOT_FOUND.getCode().startsWith("MSG_")
        ChattingErrorCode.MESSAGE_TOO_LONG.getCode().startsWith("MSG_")
        ChattingErrorCode.INVALID_MESSAGE_TYPE.getCode().startsWith("MSG_")
    }

    def "게임 관련 에러 코드들 (GAME_XXX)"() {
        expect:
        ChattingErrorCode.GAME_START_FAILED.getCode().startsWith("GAME_")
        ChattingErrorCode.GAME_STOP_FAILED.getCode().startsWith("GAME_")
        ChattingErrorCode.GAME_NOT_IN_PROGRESS.getCode().startsWith("GAME_")
        ChattingErrorCode.GAME_ALREADY_IN_PROGRESS.getCode().startsWith("GAME_")
        ChattingErrorCode.NOT_GAME_STARTER.getCode().startsWith("GAME_")
    }
}
