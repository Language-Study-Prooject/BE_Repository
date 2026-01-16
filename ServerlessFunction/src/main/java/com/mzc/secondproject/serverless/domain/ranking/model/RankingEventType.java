package com.mzc.secondproject.serverless.domain.ranking.model;

public enum RankingEventType {
	ATTENDANCE(10, "출석 체크"),
	WORD_LEARNED(5, "단어 학습"),
	WORD_MASTERED(20, "단어 마스터"),
	TEST_COMPLETED(15, "시험 완료"),
	GAME_PLAYED(10, "게임 참여"),
	GAME_WON(50, "게임 1등"),
	GRAMMAR_CHECK(3, "문법 체크"),
	STREAK_BONUS(5, "연속 학습 보너스");

	private final int baseScore;
	private final String description;

	RankingEventType(int baseScore, String description) {
		this.baseScore = baseScore;
		this.description = description;
	}

	public int getBaseScore() {
		return baseScore;
	}

	public String getDescription() {
		return description;
	}
}
