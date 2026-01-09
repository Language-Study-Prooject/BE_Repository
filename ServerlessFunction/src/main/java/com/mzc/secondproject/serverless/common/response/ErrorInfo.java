package com.mzc.secondproject.serverless.common.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.mzc.secondproject.serverless.common.exception.DomainErrorCode;
import com.mzc.secondproject.serverless.common.exception.ErrorCode;
import com.mzc.secondproject.serverless.common.exception.ServerlessException;

import java.util.Map;

/**
 * RFC 7807 스타일 에러 정보
 *
 * Problem Details for HTTP APIs (RFC 7807) 표준을 참고한 에러 응답 형식입니다.
 *
 * 응답 예시:
 * {
 *   "code": "VOCABULARY.WORD_001",
 *   "message": "단어를 찾을 수 없습니다",
 *   "status": 404,
 *   "details": {
 *     "wordId": "abc-123"
 *   }
 * }
 *
 * @param code 에러 코드 (예: AUTH_001, VOCABULARY.WORD_001)
 * @param message 에러 메시지
 * @param status HTTP 상태 코드
 * @param details 추가 상세 정보 (선택)
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorInfo(
        String code,
        String message,
        int status,
        Map<String, Object> details
) {

    /**
     * 기본 에러 정보 생성
     */
    public static ErrorInfo of(String code, String message, int status) {
        return new ErrorInfo(code, message, status, null);
    }

    /**
     * 상세 정보 포함 에러 정보 생성
     */
    public static ErrorInfo of(String code, String message, int status, Map<String, Object> details) {
        return new ErrorInfo(code, message, status, details);
    }

    /**
     * ErrorCode에서 에러 정보 생성
     */
    public static ErrorInfo from(ErrorCode errorCode) {
        String code = errorCode instanceof DomainErrorCode domainCode
                ? domainCode.getFullCode()
                : errorCode.getCode();
        return new ErrorInfo(code, errorCode.getMessage(), errorCode.getStatusCode(), null);
    }

    /**
     * ServerlessException에서 에러 정보 생성
     */
    public static ErrorInfo from(ServerlessException ex) {
        ErrorCode errorCode = ex.getErrorCode();
        String code = errorCode instanceof DomainErrorCode domainCode
                ? domainCode.getFullCode()
                : errorCode.getCode();

        Map<String, Object> details = ex.getDetails().isEmpty() ? null : ex.getDetails();

        return new ErrorInfo(code, ex.getMessage(), ex.getStatusCode(), details);
    }

    /**
     * 클라이언트 에러 여부 (4xx)
     */
    public boolean isClientError() {
        return status >= 400 && status < 500;
    }

    /**
     * 서버 에러 여부 (5xx)
     */
    public boolean isServerError() {
        return status >= 500 && status < 600;
    }
}
