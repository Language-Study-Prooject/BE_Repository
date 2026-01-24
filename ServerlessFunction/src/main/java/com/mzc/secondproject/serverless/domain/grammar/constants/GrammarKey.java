package com.mzc.secondproject.serverless.domain.grammar.constants;

public final class GrammarKey {
	
	public static final String SESSION_PREFIX = "GSESSION#";
	public static final String SESSION_SK_PREFIX = "SESSION#";
	public static final String MSG_PREFIX = "MSG#";
	public static final String ALL_SESSIONS = "GSESSION#ALL";
	public static final String UPDATED_PREFIX = "UPDATED#";
	
	private GrammarKey() {
	}
	
	public static String sessionPk(String userId) {
		return SESSION_PREFIX + userId;
	}
	
	public static String sessionSk(String sessionId) {
		return SESSION_SK_PREFIX + sessionId;
	}
	
	public static String messageSk(String timestamp, String messageId) {
		return MSG_PREFIX + timestamp + "#" + messageId;
	}
	
	public static String messageGsi1Pk(String sessionId) {
		return SESSION_PREFIX + sessionId;
	}
	
	public static String messageGsi1Sk(String timestamp) {
		return MSG_PREFIX + timestamp;
	}
	
	public static String updatedSk(String timestamp) {
		return UPDATED_PREFIX + timestamp;
	}
}
