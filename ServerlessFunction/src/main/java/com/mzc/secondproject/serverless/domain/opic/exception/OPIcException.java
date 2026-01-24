package com.mzc.secondproject.serverless.domain.opic.exception;

/**
 * OPIc 도메인 공통 예외
 */
public class OPIcException extends RuntimeException {
	public OPIcException(String message) {
		super(message);
	}
	
	public OPIcException(String message, Throwable cause) {
		super(message, cause);
	}
	
	// 응답 truncate (로그용)
	private static String truncate(String text) {
		if (text == null) return "null";
		return text.length() > 200 ? text.substring(0, 200) + "..." : text;
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
	
	// 피드백 파싱 실패
	public static class FeedbackParseException extends OPIcException {
		public FeedbackParseException(String response, Throwable cause) {
			super("피드백 응답 파싱 실패: " + truncate(response), cause);
		}
	}
	
	// 세션 리포트 파싱 실패
	public static class ReportParseException extends OPIcException {
		public ReportParseException(String response, Throwable cause) {
			super("세션 리포트 파싱 실패: " + truncate(response), cause);
		}
	}
	
	// Bedrock API 호출 실패
	public static class BedrockApiException extends OPIcException {
		public BedrockApiException(String message, Throwable cause) {
			super("Bedrock API 호출 실패: " + message, cause);
		}
	}
}
