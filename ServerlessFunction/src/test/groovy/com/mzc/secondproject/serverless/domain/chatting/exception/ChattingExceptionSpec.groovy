package com.mzc.secondproject.serverless.domain.chatting.exception

import spock.lang.Specification

class ChattingExceptionSpec extends Specification {

    // ==================== 채팅방(Room) 관련 예외 Tests ====================

    def "roomNotFound: roomId 포함"() {
        given:
        def roomId = "room123"

        when:
        def exception = ChattingException.roomNotFound(roomId)

        then:
        exception.getMessage().contains(roomId)
        exception.getErrorCode() == ChattingErrorCode.ROOM_NOT_FOUND
        exception.getStatusCode() == 404
    }

    def "roomAlreadyExists: roomName 포함"() {
        given:
        def roomName = "영어 스터디방"

        when:
        def exception = ChattingException.roomAlreadyExists(roomName)

        then:
        exception.getMessage().contains(roomName)
        exception.getErrorCode() == ChattingErrorCode.ROOM_ALREADY_EXISTS
        exception.getStatusCode() == 409
    }

    def "roomFull: roomId와 maxCapacity 포함"() {
        when:
        def exception = ChattingException.roomFull("room123", 10)

        then:
        exception.getMessage().contains("10")
        exception.getErrorCode() == ChattingErrorCode.ROOM_FULL
    }

    def "roomClosed: roomId 포함"() {
        given:
        def roomId = "closedRoom456"

        when:
        def exception = ChattingException.roomClosed(roomId)

        then:
        exception.getMessage().contains(roomId)
        exception.getMessage().contains("종료")
        exception.getErrorCode() == ChattingErrorCode.ROOM_CLOSED
    }

    def "roomInvalidPassword: 적절한 메시지"() {
        when:
        def exception = ChattingException.roomInvalidPassword("room123")

        then:
        exception.getMessage().contains("비밀번호")
        exception.getErrorCode() == ChattingErrorCode.ROOM_INVALID_PASSWORD
    }

    def "roomNotOwner: userId와 roomId 포함"() {
        when:
        def exception = ChattingException.roomNotOwner("user123", "room456")

        then:
        exception.getMessage().contains("user123")
        exception.getMessage().contains("room456")
        exception.getMessage().contains("방장")
        exception.getErrorCode() == ChattingErrorCode.ROOM_NOT_OWNER
    }

    // ==================== 메시지(Message) 관련 예외 Tests ====================

    def "messageNotFound: messageId 포함"() {
        given:
        def messageId = "msg123"

        when:
        def exception = ChattingException.messageNotFound(messageId)

        then:
        exception.getMessage().contains(messageId)
        exception.getErrorCode() == ChattingErrorCode.MESSAGE_NOT_FOUND
    }

    def "messageTooLong: length와 maxLength 포함"() {
        when:
        def exception = ChattingException.messageTooLong(1500, 1000)

        then:
        exception.getMessage().contains("1500")
        exception.getMessage().contains("1000")
        exception.getErrorCode() == ChattingErrorCode.MESSAGE_TOO_LONG
    }

    def "invalidMessageType: type 포함"() {
        given:
        def type = "INVALID_TYPE"

        when:
        def exception = ChattingException.invalidMessageType(type)

        then:
        exception.getMessage().contains(type)
        exception.getErrorCode() == ChattingErrorCode.INVALID_MESSAGE_TYPE
    }

    // ==================== 참여자(Member) 관련 예외 Tests ====================

    def "notRoomMember: userId와 roomId 포함"() {
        when:
        def exception = ChattingException.notRoomMember("user123", "room456")

        then:
        exception.getMessage().contains("user123")
        exception.getMessage().contains("room456")
        exception.getErrorCode() == ChattingErrorCode.NOT_ROOM_MEMBER
    }

    def "alreadyJoined: userId와 roomId 포함"() {
        when:
        def exception = ChattingException.alreadyJoined("user123", "room456")

        then:
        exception.getMessage().contains("user123")
        exception.getMessage().contains("room456")
        exception.getMessage().contains("이미 참여")
        exception.getErrorCode() == ChattingErrorCode.ALREADY_JOINED
    }

    def "invalidRoomToken: 기본 메시지"() {
        when:
        def exception = ChattingException.invalidRoomToken()

        then:
        exception.getErrorCode() == ChattingErrorCode.INVALID_ROOM_TOKEN
    }

    def "invalidRoomToken: 커스텀 메시지"() {
        given:
        def reason = "토큰이 만료되었습니다"

        when:
        def exception = ChattingException.invalidRoomToken(reason)

        then:
        exception.getMessage() == reason
    }

    // ==================== 채팅 레벨 관련 예외 Tests ====================

    def "invalidChatLevel: level 포함"() {
        given:
        def level = "EXPERT"

        when:
        def exception = ChattingException.invalidChatLevel(level)

        then:
        exception.getMessage().contains(level)
        exception.getErrorCode() == ChattingErrorCode.INVALID_CHAT_LEVEL
    }

    // ==================== 연결 관련 예외 Tests ====================

    def "connectionFailed: cause 포함"() {
        given:
        def cause = new RuntimeException("Connection refused")

        when:
        def exception = ChattingException.connectionFailed(cause)

        then:
        exception.getCause() == cause
        exception.getErrorCode() == ChattingErrorCode.CONNECTION_FAILED
    }

    def "connectionTimeout: connectionId 포함"() {
        given:
        def connectionId = "conn123"

        when:
        def exception = ChattingException.connectionTimeout(connectionId)

        then:
        exception.getMessage().contains(connectionId)
        exception.getMessage().contains("시간")
        exception.getErrorCode() == ChattingErrorCode.CONNECTION_TIMEOUT
    }
}
