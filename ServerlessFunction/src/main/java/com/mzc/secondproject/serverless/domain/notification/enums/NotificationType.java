package com.mzc.secondproject.serverless.domain.notification.enums;

/**
 * 알림 타입 정의
 * 새로운 알림 타입 추가 시 여기에 enum 추가
 */
public enum NotificationType {
	// 배지 관련
	BADGE_EARNED("배지 획득", "badge"),

	// 학습 관련
	DAILY_COMPLETE("일일 학습 완료", "daily"),
	STREAK_REMINDER("연속 학습 리마인더", "streak"),

	// 테스트/퀴즈 관련
	TEST_COMPLETE("테스트 완료", "test"),
	NEWS_QUIZ_COMPLETE("뉴스 퀴즈 완료", "quiz"),

	// 게임 관련
	GAME_END("게임 종료", "game"),
	GAME_STREAK("게임 연속 정답", "game"),

	// OPIc 관련
	OPIC_COMPLETE("OPIc 세션 완료", "opic");

	private final String description;
	private final String category;

	NotificationType(String description, String category) {
		this.description = description;
		this.category = category;
	}

	public String getDescription() {
		return description;
	}

	public String getCategory() {
		return category;
	}
}
