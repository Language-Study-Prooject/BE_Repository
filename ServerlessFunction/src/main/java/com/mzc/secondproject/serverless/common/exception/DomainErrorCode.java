package com.mzc.secondproject.serverless.common.exception;

/**
 * 도메인별 에러 코드 인터페이스
 *
 * 각 도메인(Vocabulary, Chatting 등)의 비즈니스 로직 관련 에러 코드가 구현하는 인터페이스입니다.
 * ErrorCode를 확장하여 도메인 식별 기능을 추가합니다.
 *
 * 구현체:
 * - VocabularyErrorCode - 단어 학습 도메인
 * - ChattingErrorCode - 채팅 도메인
 */
public non-sealed interface DomainErrorCode extends ErrorCode {

    /**
     * 도메인 이름 반환 (예: "VOCABULARY", "CHATTING")
     */
    String getDomain();

    /**
     * 전체 에러 식별자 반환 (예: VOCABULARY.WORD_001)
     */
    default String getFullCode() {
        return getDomain() + "." + getCode();
    }
}
