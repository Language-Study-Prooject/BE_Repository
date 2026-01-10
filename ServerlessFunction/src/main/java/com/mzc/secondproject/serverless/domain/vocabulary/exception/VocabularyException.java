package com.mzc.secondproject.serverless.domain.vocabulary.exception;

import com.mzc.secondproject.serverless.common.exception.ServerlessException;

/**
 * 단어 학습 도메인 예외 클래스
 *
 * 정적 팩토리 메서드를 통해 가독성 높은 예외 생성을 지원합니다.
 *
 * 사용 예시:
 * throw VocabularyException.wordNotFound(wordId);
 * throw VocabularyException.invalidDifficulty("INVALID");
 */
public class VocabularyException extends ServerlessException {

    private VocabularyException(VocabularyErrorCode errorCode) {
        super(errorCode);
    }

    private VocabularyException(VocabularyErrorCode errorCode, String message) {
        super(errorCode, message);
    }

    private VocabularyException(VocabularyErrorCode errorCode, Throwable cause) {
        super(errorCode, cause);
    }

    // === 단어(Word) 관련 팩토리 메서드 ===

    public static VocabularyException wordNotFound(String wordId) {
        return (VocabularyException) new VocabularyException(VocabularyErrorCode.WORD_NOT_FOUND,
                String.format("단어를 찾을 수 없습니다 (ID: %s)", wordId))
                .addDetail("wordId", wordId);
    }

    public static VocabularyException wordAlreadyExists(String english) {
        return (VocabularyException) new VocabularyException(VocabularyErrorCode.WORD_ALREADY_EXISTS,
                String.format("이미 존재하는 단어입니다: '%s'", english))
                .addDetail("english", english);
    }

    public static VocabularyException invalidWordData(String reason) {
        return new VocabularyException(VocabularyErrorCode.INVALID_WORD_DATA, reason);
    }

    // === 사용자 단어(UserWord) 관련 팩토리 메서드 ===

    public static VocabularyException userWordNotFound(String userId, String wordId) {
        return (VocabularyException) new VocabularyException(VocabularyErrorCode.USER_WORD_NOT_FOUND,
                String.format("사용자 단어 정보를 찾을 수 없습니다 (userId: %s, wordId: %s)", userId, wordId))
                .addDetail("userId", userId)
                .addDetail("wordId", wordId);
    }

    public static VocabularyException invalidDifficulty(String difficulty) {
        return (VocabularyException) new VocabularyException(VocabularyErrorCode.INVALID_DIFFICULTY,
                String.format("유효하지 않은 난이도입니다: '%s'. EASY, NORMAL, HARD 중 하나여야 합니다", difficulty))
                .addDetail("invalidValue", difficulty)
                .addDetail("allowedValues", "EASY, NORMAL, HARD");
    }

    public static VocabularyException invalidWordStatus(String status) {
        return (VocabularyException) new VocabularyException(VocabularyErrorCode.INVALID_WORD_STATUS,
                String.format("유효하지 않은 단어 상태입니다: '%s'", status))
                .addDetail("invalidValue", status);
    }

    // === 학습(Study) 관련 팩토리 메서드 ===

    public static VocabularyException dailyStudyNotFound(String userId, String date) {
        return (VocabularyException) new VocabularyException(VocabularyErrorCode.DAILY_STUDY_NOT_FOUND,
                String.format("일일 학습 정보를 찾을 수 없습니다 (userId: %s, date: %s)", userId, date))
                .addDetail("userId", userId)
                .addDetail("date", date);
    }

    public static VocabularyException studyLimitExceeded(int limit) {
        return (VocabularyException) new VocabularyException(VocabularyErrorCode.STUDY_LIMIT_EXCEEDED,
                String.format("일일 학습 한도(%d개)를 초과했습니다", limit))
                .addDetail("dailyLimit", limit);
    }

    public static VocabularyException invalidStudyLevel(String level) {
        return (VocabularyException) new VocabularyException(VocabularyErrorCode.INVALID_STUDY_LEVEL,
                String.format("유효하지 않은 학습 레벨입니다: '%s'", level))
                .addDetail("invalidValue", level);
    }

    // === 카테고리/레벨 관련 팩토리 메서드 ===

    public static VocabularyException invalidCategory(String category) {
        return (VocabularyException) new VocabularyException(VocabularyErrorCode.INVALID_CATEGORY,
                String.format("유효하지 않은 카테고리입니다: '%s'", category))
                .addDetail("invalidValue", category);
    }

    public static VocabularyException invalidLevel(String level) {
        return (VocabularyException) new VocabularyException(VocabularyErrorCode.INVALID_LEVEL,
                String.format("유효하지 않은 레벨입니다: '%s'", level))
                .addDetail("invalidValue", level);
    }
}
