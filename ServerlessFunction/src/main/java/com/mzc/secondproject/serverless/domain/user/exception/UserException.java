package com.mzc.secondproject.serverless.domain.user.exception;

import com.mzc.secondproject.serverless.common.exception.ServerlessException;

/**
 * User 도메인 예외 클래스
 */
public class UserException extends ServerlessException {
	
	private UserException(UserErrorCode errorCode) {
		super(errorCode);
	}
	
	private UserException(UserErrorCode errorCode, String message) {
		super(errorCode, message);
	}
	
	private UserException(UserErrorCode errorCode, Throwable cause) {
		super(errorCode, cause);
	}
	
	
	/**
	 * 팩토리 메서드들
	 */
	
	// 사용자 조회 관련
	public static UserException userNotFound(String cognitoSub) {
		return (UserException) new UserException(UserErrorCode.USER_NOT_FOUND,
				String.format("사용자를 찾을 수 없습니다 (ID: %s)", cognitoSub))
				.addDetail("cognitoSub", cognitoSub);
	}
	
	// 프로필 수정 관련
	public static UserException invalidNickname(String nickname, int minLength, int maxLength) {
		return (UserException) new UserException(UserErrorCode.INVALID_NICKNAME,
				String.format("닉네임은 %d~%d자여야 합니다 (입력: '%s', 길이: %d)",
						minLength, maxLength, nickname, nickname != null ? nickname.length() : 0))
				.addDetail("nickname", nickname)
				.addDetail("minLength", minLength)
				.addDetail("maxLength", maxLength);
	}
	
	public static UserException invalidLevel(String level) {
		return (UserException) new UserException(UserErrorCode.INVALID_LEVEL,
				String.format("유효하지 않은 레벨입니다: '%s' (BEGINNER, INTERMEDIATE, ADVANCED 중 선택)", level))
				.addDetail("invalidValue", level);
	}
	
	// 이미지 업로드 관련
	public static UserException invalidImageType(String contentType) {
		return (UserException) new UserException(UserErrorCode.INVALID_IMAGE_TYPE,
				String.format("지원하지 않는 이미지 형식입니다: '%s' (jpeg, png, gif, webp만 가능)", contentType))
				.addDetail("contentType", contentType);
	}
	
	public static UserException imageUploadFailed(Throwable cause) {
		return (UserException) new UserException(UserErrorCode.IMAGE_UPLOAD_FAILED, cause);
	}
	
	public static UserException imageUploadFailed(String reason) {
		return (UserException) new UserException(UserErrorCode.IMAGE_UPLOAD_FAILED, reason);
	}
	
	// Cognito 동기화 관련
	public static UserException cognitoSyncFailed(String cognitoSub, Throwable cause) {
		return (UserException) new UserException(UserErrorCode.COGNITO_SYNC_FAILED, cause)
				.addDetail("cognitoSub", cognitoSub);
	}
}
