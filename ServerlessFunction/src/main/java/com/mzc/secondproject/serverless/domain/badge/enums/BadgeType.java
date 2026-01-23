package com.mzc.secondproject.serverless.domain.badge.enums;

/**
 * 뱃지 타입 정의
 */
public enum BadgeType {
	// 첫 걸음
	FIRST_STEP("첫 걸음", "첫 학습을 완료했습니다", "first_step.png", "FIRST_STUDY", 1),
	
	// 연속 학습
	STREAK_3("3일 연속 학습", "3일 연속으로 학습했습니다", "streak_3.png", "STREAK", 3),
	STREAK_7("일주일 연속 학습", "7일 연속으로 학습했습니다", "streak_7.png", "STREAK", 7),
	STREAK_30("한 달 연속 학습", "30일 연속으로 학습했습니다", "streak_30.png", "STREAK", 30),
	
	// 단어 학습량
	WORDS_100("단어 수집가", "100개의 단어를 학습했습니다", "words_100.png", "WORDS_LEARNED", 100),
	WORDS_500("단어 전문가", "500개의 단어를 학습했습니다", "words_500.png", "WORDS_LEARNED", 500),
	WORDS_1000("단어 마스터", "1000개의 단어를 학습했습니다", "words_1000.png", "WORDS_LEARNED", 1000),
	
	// 테스트 관련
	PERFECT_SCORE("완벽주의자", "테스트에서 만점을 받았습니다", "perfect_score.png", "PERFECT_TEST", 1),
	TEST_10("테스트 도전자", "10회의 테스트를 완료했습니다", "test_10.png", "TESTS_COMPLETED", 10),
	
	// 정확도
	ACCURACY_90("정확도 달인", "전체 정확도 90%를 달성했습니다", "accuracy_90.png", "ACCURACY", 90),
	
	// 게임 관련
	GAME_FIRST_PLAY("첫 게임", "첫 게임에 참여했습니다", "game_first.png", "GAMES_PLAYED", 1),
	GAME_10_WINS("게임 10승", "게임에서 10번 1등을 했습니다", "game_10_wins.png", "GAMES_WON", 10),
	QUICK_GUESSER("번개 정답", "5초 내에 정답을 맞췄습니다", "quick_guesser.png", "QUICK_GUESSES", 1),
	PERFECT_DRAWER("완벽한 출제자", "출제 시 전원이 정답을 맞췄습니다", "perfect_drawer.png", "PERFECT_DRAWS", 1),
	
	// 특별
	MASTER("학습 마스터", "모든 업적을 달성했습니다", "master.png", "ALL_BADGES", 1),

	// 뉴스 - 읽기
	NEWS_FIRST_READ("뉴스 첫 발걸음", "첫 번째 뉴스 읽기 완료", "news_first_read.png", "NEWS_READ", 1),
	NEWS_READ_10("뉴스 탐험가", "뉴스 10개 읽기 완료", "news_read_10.png", "NEWS_READ", 10),
	NEWS_READ_50("뉴스 애호가", "뉴스 50개 읽기 완료", "news_read_50.png", "NEWS_READ", 50),
	NEWS_READ_100("뉴스 전문가", "뉴스 100개 읽기 완료", "news_read_100.png", "NEWS_READ", 100),

	// 뉴스 - 퀴즈
	NEWS_QUIZ_FIRST("퀴즈 도전", "첫 뉴스 퀴즈 완료", "news_quiz_first.png", "NEWS_QUIZ", 1),
	NEWS_QUIZ_PERFECT("완벽한 이해", "뉴스 퀴즈에서 만점 달성", "news_quiz_perfect.png", "NEWS_QUIZ_PERFECT", 1),
	NEWS_QUIZ_10("퀴즈 탐험가", "뉴스 퀴즈 10회 완료", "news_quiz_10.png", "NEWS_QUIZ", 10),
	NEWS_QUIZ_50("퀴즈 마스터", "뉴스 퀴즈 50회 완료", "news_quiz_50.png", "NEWS_QUIZ", 50),

	// 뉴스 - 단어 수집
	NEWS_WORD_10("단어 수집가", "뉴스에서 단어 10개 수집", "news_word_10.png", "NEWS_WORD", 10),
	NEWS_WORD_50("단어 사냥꾼", "뉴스에서 단어 50개 수집", "news_word_50.png", "NEWS_WORD", 50),
	NEWS_WORD_100("단어 전문가", "뉴스에서 단어 100개 수집", "news_word_100.png", "NEWS_WORD", 100),

	// 뉴스 - 연속 학습
	NEWS_STREAK_7("일주일 뉴스 습관", "7일 연속 뉴스 읽기", "news_streak_7.png", "NEWS_STREAK", 7),
	NEWS_STREAK_30("한 달 뉴스 습관", "30일 연속 뉴스 읽기", "news_streak_30.png", "NEWS_STREAK", 30),

	// 뉴스 - 종합
	NEWS_MASTER("뉴스 마스터", "읽기100+퀴즈50+단어100 달성", "news_master.png", "NEWS_MASTER", 1);

	private static final String BUCKET_NAME = System.getenv("BUCKET_NAME");
	private static final String BASE_URL = getBaseUrl();

	private static String getBaseUrl() {
		String bucket = BUCKET_NAME != null ? BUCKET_NAME : "group2-englishstudy";
		return String.format("https://%s.s3.ap-northeast-2.amazonaws.com/badges/", bucket);
	}
	
	private final String name;
	private final String description;
	private final String imageFile;
	private final String category;
	private final int threshold;
	
	BadgeType(String name, String description, String imageFile, String category, int threshold) {
		this.name = name;
		this.description = description;
		this.imageFile = imageFile;
		this.category = category;
		this.threshold = threshold;
	}
	
	public static BadgeType fromString(String value) {
		if (value == null) return null;
		try {
			return BadgeType.valueOf(value.toUpperCase());
		} catch (IllegalArgumentException e) {
			return null;
		}
	}
	
	public String getName() {
		return name;
	}
	
	public String getDescription() {
		return description;
	}
	
	public String getImageUrl() {
		return BASE_URL + imageFile;
	}
	
	public String getImageFile() {
		return imageFile;
	}
	
	public String getCategory() {
		return category;
	}
	
	public int getThreshold() {
		return threshold;
	}
}
