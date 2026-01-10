package com.mzc.secondproject.serverless.common.exception;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * 서버리스 애플리케이션 기본 예외 클래스
 *
 * 모든 비즈니스 예외의 추상 기반 클래스입니다.
 * ErrorCode를 통해 표준화된 에러 정보를 제공합니다.
 *
 * 사용 예시:
 * - CommonException: 공통/시스템 예외
 * - VocabularyException: 단어 학습 도메인 예외
 * - ChattingException: 채팅 도메인 예외
 */
public abstract class ServerlessException extends RuntimeException {

    private final ErrorCode errorCode;
    private final Map<String, Object> details;

    protected ServerlessException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
        this.details = new HashMap<>();
    }

    protected ServerlessException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
        this.details = new HashMap<>();
    }

    protected ServerlessException(ErrorCode errorCode, Throwable cause) {
        super(errorCode.getMessage(), cause);
        this.errorCode = errorCode;
        this.details = new HashMap<>();
    }

    protected ServerlessException(ErrorCode errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
        this.details = new HashMap<>();
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }

    public String getCode() {
        return errorCode.getCode();
    }

    public int getStatusCode() {
        return errorCode.getStatusCode();
    }

    public Map<String, Object> getDetails() {
        return Collections.unmodifiableMap(details);
    }

    /**
     * 에러 상세 정보 추가 (메서드 체이닝 지원)
     */
    public ServerlessException addDetail(String key, Object value) {
        this.details.put(key, value);
        return this;
    }

    /**
     * 여러 상세 정보 일괄 추가
     */
    public ServerlessException addDetails(Map<String, Object> details) {
        this.details.putAll(details);
        return this;
    }

    public boolean isClientError() {
        return errorCode.isClientError();
    }

    public boolean isServerError() {
        return errorCode.isServerError();
    }
}
