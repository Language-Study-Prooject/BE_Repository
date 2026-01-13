package com.mzc.secondproject.serverless.domain.chatting.dto.response;

import com.mzc.secondproject.serverless.domain.chatting.enums.MessageType;

/**
 * 슬래시 명령어 처리 결과
 */
public record CommandResult(
		MessageType messageType,
		String message,
		boolean success,
		Object data
) {

	public static CommandResult success(MessageType messageType, String message) {
		return new CommandResult(messageType, message, true, null);
	}

	public static CommandResult success(MessageType messageType, String message, Object data) {
		return new CommandResult(messageType, message, true, data);
	}

	public static CommandResult error(String message) {
		return new CommandResult(MessageType.SYSTEM_COMMAND, message, false, null);
	}
}
