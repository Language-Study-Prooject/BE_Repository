package com.mzc.secondproject.serverless.common.exception;

/**
 * 공통/시스템 예외 클래스
 *
 * 도메인에 종속되지 않는 공통 예외를 처리합니다.
 * 정적 팩토리 메서드를 통해 가독성 높은 예외 생성을 지원합니다.
 *
 * 사용 예시:
 * throw CommonException.unauthorized();
 * throw CommonException.notFound("사용자");
 * throw CommonException.invalidInput("이메일 형식이 올바르지 않습니다");
 */
public class CommonException extends ServerlessException {

    private CommonException(CommonErrorCode errorCode) {
        super(errorCode);
    }

    private CommonException(CommonErrorCode errorCode, String message) {
        super(errorCode, message);
    }

    private CommonException(CommonErrorCode errorCode, Throwable cause) {
        super(errorCode, cause);
    }

    private CommonException(CommonErrorCode errorCode, String message, Throwable cause) {
        super(errorCode, message, cause);
    }

    // === 인증/인가 예외 팩토리 메서드 ===

    public static CommonException unauthorized() {
        return new CommonException(CommonErrorCode.UNAUTHORIZED);
    }

    public static CommonException unauthorized(String message) {
        return new CommonException(CommonErrorCode.UNAUTHORIZED, message);
    }

    public static CommonException forbidden() {
        return new CommonException(CommonErrorCode.FORBIDDEN);
    }

    public static CommonException forbidden(String resource) {
        return new CommonException(CommonErrorCode.FORBIDDEN,
                String.format("'%s'에 대한 접근 권한이 없습니다", resource));
    }

    public static CommonException invalidToken() {
        return new CommonException(CommonErrorCode.INVALID_TOKEN);
    }

    public static CommonException tokenExpired() {
        return new CommonException(CommonErrorCode.TOKEN_EXPIRED);
    }

    // === 검증 예외 팩토리 메서드 ===

    public static CommonException invalidInput() {
        return new CommonException(CommonErrorCode.INVALID_INPUT);
    }

    public static CommonException invalidInput(String message) {
        return new CommonException(CommonErrorCode.INVALID_INPUT, message);
    }

    public static CommonException requiredFieldMissing(String fieldName) {
        return new CommonException(CommonErrorCode.REQUIRED_FIELD_MISSING,
                String.format("필수 필드 '%s'가 누락되었습니다", fieldName));
    }

    public static CommonException invalidFormat(String fieldName) {
        return new CommonException(CommonErrorCode.INVALID_FORMAT,
                String.format("'%s' 형식이 올바르지 않습니다", fieldName));
    }

    public static CommonException valueOutOfRange(String fieldName, Object min, Object max) {
        return new CommonException(CommonErrorCode.VALUE_OUT_OF_RANGE,
                String.format("'%s' 값은 %s ~ %s 범위여야 합니다", fieldName, min, max));
    }

    // === 리소스 예외 팩토리 메서드 ===

    public static CommonException notFound(String resourceName) {
        return new CommonException(CommonErrorCode.RESOURCE_NOT_FOUND,
                String.format("'%s'를 찾을 수 없습니다", resourceName));
    }

    public static CommonException notFound(String resourceName, String identifier) {
        return new CommonException(CommonErrorCode.RESOURCE_NOT_FOUND,
                String.format("'%s' (ID: %s)를 찾을 수 없습니다", resourceName, identifier));
    }

    public static CommonException alreadyExists(String resourceName) {
        return new CommonException(CommonErrorCode.RESOURCE_ALREADY_EXISTS,
                String.format("'%s'가 이미 존재합니다", resourceName));
    }

    public static CommonException alreadyExists(String resourceName, String identifier) {
        return new CommonException(CommonErrorCode.RESOURCE_ALREADY_EXISTS,
                String.format("'%s' (ID: %s)가 이미 존재합니다", resourceName, identifier));
    }

    // === 시스템 예외 팩토리 메서드 ===

    public static CommonException internalError() {
        return new CommonException(CommonErrorCode.INTERNAL_SERVER_ERROR);
    }

    public static CommonException internalError(Throwable cause) {
        return new CommonException(CommonErrorCode.INTERNAL_SERVER_ERROR, cause);
    }

    public static CommonException internalError(String message) {
        return new CommonException(CommonErrorCode.INTERNAL_SERVER_ERROR, message);
    }

    public static CommonException databaseError(Throwable cause) {
        return new CommonException(CommonErrorCode.DATABASE_ERROR, cause);
    }

    public static CommonException externalApiError(String apiName) {
        return new CommonException(CommonErrorCode.EXTERNAL_API_ERROR,
                String.format("'%s' API 호출 중 오류가 발생했습니다", apiName));
    }

    public static CommonException externalApiError(String apiName, Throwable cause) {
        return new CommonException(CommonErrorCode.EXTERNAL_API_ERROR,
                String.format("'%s' API 호출 중 오류가 발생했습니다", apiName), cause);
    }

    public static CommonException serviceUnavailable() {
        return new CommonException(CommonErrorCode.SERVICE_UNAVAILABLE);
    }
}
