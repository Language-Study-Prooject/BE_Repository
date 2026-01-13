package com.mzc.secondproject.serverless.domain.chatting.exception;

import com.mzc.secondproject.serverless.common.exception.ServerlessException;

/**
 * 채팅 도메인 예외 클래스
 *
 * 정적 팩토리 메서드를 통해 가독성 높은 예외 생성을 지원합니다.
 *
 * 사용 예시:
 * throw ChattingException.roomNotFound(roomId);
 * throw ChattingException.notRoomMember(userId, roomId);
 */
public class ChattingException extends ServerlessException {

    private ChattingException(ChattingErrorCode errorCode) {
        super(errorCode);
    }

    private ChattingException(ChattingErrorCode errorCode, String message) {
        super(errorCode, message);
    }

    private ChattingException(ChattingErrorCode errorCode, Throwable cause) {
        super(errorCode, cause);
    }

    // === 채팅방(Room) 관련 팩토리 메서드 ===

    public static ChattingException roomNotFound(String roomId) {
        return (ChattingException) new ChattingException(ChattingErrorCode.ROOM_NOT_FOUND,
                String.format("채팅방을 찾을 수 없습니다 (ID: %s)", roomId))
                .addDetail("roomId", roomId);
    }

    public static ChattingException roomAlreadyExists(String roomName) {
        return (ChattingException) new ChattingException(ChattingErrorCode.ROOM_ALREADY_EXISTS,
                String.format("이미 존재하는 채팅방입니다: '%s'", roomName))
                .addDetail("roomName", roomName);
    }

    public static ChattingException roomFull(String roomId, int maxCapacity) {
        return (ChattingException) new ChattingException(ChattingErrorCode.ROOM_FULL,
                String.format("채팅방 인원이 가득 찼습니다 (최대 %d명)", maxCapacity))
                .addDetail("roomId", roomId)
                .addDetail("maxCapacity", maxCapacity);
    }

    public static ChattingException roomClosed(String roomId) {
        return (ChattingException) new ChattingException(ChattingErrorCode.ROOM_CLOSED,
                String.format("종료된 채팅방입니다 (ID: %s)", roomId))
                .addDetail("roomId", roomId);
    }

    public static ChattingException roomInvalidPassword(String roomId) {
        return (ChattingException) new ChattingException(ChattingErrorCode.ROOM_INVALID_PASSWORD,
                "비밀번호가 일치하지 않습니다")
                .addDetail("roomId", roomId);
    }

    public static ChattingException roomNotOwner(String userId, String roomId) {
        return (ChattingException) new ChattingException(ChattingErrorCode.ROOM_NOT_OWNER,
                String.format("방장 권한이 필요합니다 (userId: %s, roomId: %s)", userId, roomId))
                .addDetail("userId", userId)
                .addDetail("roomId", roomId);
    }

    // === 메시지(Message) 관련 팩토리 메서드 ===

    public static ChattingException messageNotFound(String messageId) {
        return (ChattingException) new ChattingException(ChattingErrorCode.MESSAGE_NOT_FOUND,
                String.format("메시지를 찾을 수 없습니다 (ID: %s)", messageId))
                .addDetail("messageId", messageId);
    }

    public static ChattingException messageTooLong(int length, int maxLength) {
        return (ChattingException) new ChattingException(ChattingErrorCode.MESSAGE_TOO_LONG,
                String.format("메시지가 너무 깁니다 (%d자, 최대 %d자)", length, maxLength))
                .addDetail("length", length)
                .addDetail("maxLength", maxLength);
    }

    public static ChattingException invalidMessageType(String type) {
        return (ChattingException) new ChattingException(ChattingErrorCode.INVALID_MESSAGE_TYPE,
                String.format("유효하지 않은 메시지 타입입니다: '%s'", type))
                .addDetail("invalidValue", type);
    }

    // === 참여자(Member) 관련 팩토리 메서드 ===

    public static ChattingException notRoomMember(String userId, String roomId) {
        return (ChattingException) new ChattingException(ChattingErrorCode.NOT_ROOM_MEMBER,
                String.format("채팅방 멤버가 아닙니다 (userId: %s, roomId: %s)", userId, roomId))
                .addDetail("userId", userId)
                .addDetail("roomId", roomId);
    }

    public static ChattingException alreadyJoined(String userId, String roomId) {
        return (ChattingException) new ChattingException(ChattingErrorCode.ALREADY_JOINED,
                String.format("이미 참여 중인 채팅방입니다 (userId: %s, roomId: %s)", userId, roomId))
                .addDetail("userId", userId)
                .addDetail("roomId", roomId);
    }

    public static ChattingException invalidRoomToken() {
        return new ChattingException(ChattingErrorCode.INVALID_ROOM_TOKEN);
    }

    public static ChattingException invalidRoomToken(String reason) {
        return new ChattingException(ChattingErrorCode.INVALID_ROOM_TOKEN, reason);
    }

    // === 채팅 레벨 관련 팩토리 메서드 ===

    public static ChattingException invalidChatLevel(String level) {
        return (ChattingException) new ChattingException(ChattingErrorCode.INVALID_CHAT_LEVEL,
                String.format("유효하지 않은 채팅 레벨입니다: '%s'", level))
                .addDetail("invalidValue", level);
    }

    // === 연결 관련 팩토리 메서드 ===

    public static ChattingException connectionFailed(Throwable cause) {
        return (ChattingException) new ChattingException(ChattingErrorCode.CONNECTION_FAILED, cause);
    }

    public static ChattingException connectionTimeout(String connectionId) {
        return (ChattingException) new ChattingException(ChattingErrorCode.CONNECTION_TIMEOUT,
                String.format("연결 시간이 초과되었습니다 (connectionId: %s)", connectionId))
                .addDetail("connectionId", connectionId);
    }
}
