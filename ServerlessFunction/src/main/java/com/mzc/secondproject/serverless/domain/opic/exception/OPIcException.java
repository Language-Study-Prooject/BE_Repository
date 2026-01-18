package com.mzc.secondproject.serverless.domain.opic.exception;

/**
 * OPIc 도메인 공통 예외
 */
public class OPIcException extends RuntimeException{
    public OPIcException(String message) {
        super(message);
    }

    public OPIcException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * Transcribe 관련 예외
     */
    public static class TranscribeException extends OPIcException {
        public TranscribeException(String message) {
            super(message);
        }

        public TranscribeException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /**
     * 세션 관련 예외
     */
    public static class SessionException extends OPIcException {
        public SessionException(String message) {
            super(message);
        }
    }

    /**
     * 피드백 생성 예외
     */
    public static class FeedbackException extends OPIcException {
        public FeedbackException(String message) {
            super(message);
        }

        public FeedbackException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
