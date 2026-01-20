package com.mzc.secondproject.serverless.common.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 환경 변수 설정 유틸리티
 * - 필수 환경 변수 누락 시 명확한 에러 메시지 제공
 * - Lambda 시작 시 설정 오류를 빠르게 감지
 */
public final class EnvConfig {

	private static final Logger logger = LoggerFactory.getLogger(EnvConfig.class);

	private EnvConfig() {
		// 유틸리티 클래스 - 인스턴스화 방지
	}

	/**
	 * 필수 환경 변수를 가져옵니다.
	 * 환경 변수가 설정되지 않았거나 빈 문자열인 경우 IllegalStateException을 발생시킵니다.
	 *
	 * @param name 환경 변수 이름
	 * @return 환경 변수 값
	 * @throws IllegalStateException 환경 변수가 설정되지 않은 경우
	 */
	public static String getRequired(String name) {
		String value = System.getenv(name);
		if (value == null || value.trim().isEmpty()) {
			String message = String.format("필수 환경 변수 '%s'가 설정되지 않았습니다. SAM template.yaml을 확인하세요.", name);
			logger.error(message);
			throw new IllegalStateException(message);
		}
		return value;
	}

	/**
	 * 선택적 환경 변수를 가져옵니다.
	 * 환경 변수가 설정되지 않은 경우 기본값을 반환합니다.
	 *
	 * @param name 환경 변수 이름
	 * @param defaultValue 기본값
	 * @return 환경 변수 값 또는 기본값
	 */
	public static String getOrDefault(String name, String defaultValue) {
		String value = System.getenv(name);
		if (value == null || value.trim().isEmpty()) {
			logger.debug("환경 변수 '{}'가 설정되지 않아 기본값 '{}'을 사용합니다.", name, defaultValue);
			return defaultValue;
		}
		return value;
	}

	/**
	 * 선택적 환경 변수를 정수로 가져옵니다.
	 * 환경 변수가 설정되지 않거나 파싱에 실패한 경우 기본값을 반환합니다.
	 *
	 * @param name 환경 변수 이름
	 * @param defaultValue 기본값
	 * @return 환경 변수 값 또는 기본값
	 */
	public static int getIntOrDefault(String name, int defaultValue) {
		String value = System.getenv(name);
		if (value == null || value.trim().isEmpty()) {
			return defaultValue;
		}
		try {
			return Integer.parseInt(value.trim());
		} catch (NumberFormatException e) {
			logger.warn("환경 변수 '{}'의 값 '{}'을 정수로 변환할 수 없어 기본값 {}을 사용합니다.", name, value, defaultValue);
			return defaultValue;
		}
	}

	/**
	 * 선택적 환경 변수를 long으로 가져옵니다.
	 *
	 * @param name 환경 변수 이름
	 * @param defaultValue 기본값
	 * @return 환경 변수 값 또는 기본값
	 */
	public static long getLongOrDefault(String name, long defaultValue) {
		String value = System.getenv(name);
		if (value == null || value.trim().isEmpty()) {
			return defaultValue;
		}
		try {
			return Long.parseLong(value.trim());
		} catch (NumberFormatException e) {
			logger.warn("환경 변수 '{}'의 값 '{}'을 long으로 변환할 수 없어 기본값 {}을 사용합니다.", name, value, defaultValue);
			return defaultValue;
		}
	}
}
