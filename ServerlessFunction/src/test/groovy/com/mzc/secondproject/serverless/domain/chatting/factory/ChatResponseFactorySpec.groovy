package com.mzc.secondproject.serverless.domain.chatting.factory

import com.mzc.secondproject.serverless.domain.chatting.enums.ChatLevel
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll

class ChatResponseFactorySpec extends Specification {

    // ==================== MockChatResponseFactory Tests ====================

    @Subject
    MockChatResponseFactory mockFactory = new MockChatResponseFactory()

    def "MockChatResponseFactory: ChatResponse 객체 반환"() {
        given: "Mock Factory"
        def userMessage = "Hello, how are you?"

        when: "응답 생성"
        def response = mockFactory.create(userMessage, ChatLevel.BEGINNER)

        then: "ChatResponse 객체 반환"
        response != null
        response.content() != null
        response.modelId() == "mock-model-v1"
        response.processingTimeMs() >= 0
    }

    @Unroll
    def "MockChatResponseFactory: #level 레벨에 맞는 응답 생성"() {
        given: "Mock Factory"
        def userMessage = "Test message"

        when: "응답 생성"
        def response = mockFactory.create(userMessage, level)

        then: "레벨에 맞는 응답"
        response.content().contains(expectedKeyword)

        where:
        level                  | expectedKeyword
        ChatLevel.BEGINNER     | "안녕하세요"
        ChatLevel.INTERMEDIATE | "What do you think"
        ChatLevel.ADVANCED     | "nuanced perspective"
    }

    def "MockChatResponseFactory: 대화 내역 포함 가능"() {
        given: "Mock Factory와 대화 내역"
        def userMessage = "Continue our discussion"
        def history = "User: Hello\nAI: Hi there!"

        when: "대화 내역 포함하여 응답 생성"
        def response = mockFactory.create(userMessage, ChatLevel.INTERMEDIATE, history)

        then: "정상 응답"
        response != null
        response.content() != null
    }

    def "MockChatResponseFactory: null 메시지 처리"() {
        given: "Mock Factory"

        when: "null 메시지로 응답 생성"
        def response = mockFactory.create(null, ChatLevel.BEGINNER)

        then: "예외 없이 처리"
        response != null
        response.content() != null
    }

    def "MockChatResponseFactory: 긴 메시지 truncate"() {
        given: "Mock Factory와 긴 메시지"
        def longMessage = "A" * 100

        when: "응답 생성"
        def response = mockFactory.create(longMessage, ChatLevel.BEGINNER)

        then: "메시지가 truncate되어 응답에 포함"
        response.content().contains("...")
    }

    // ==================== ChatResponse Tests ====================

    def "ChatResponse: of 팩토리 메서드"() {
        when: "ChatResponse 생성"
        def response = ChatResponse.of("Hello", "model-v1", 100)

        then: "필드 값 확인"
        response.content() == "Hello"
        response.modelId() == "model-v1"
        response.processingTimeMs() == 100
    }

    def "ChatResponse: 간단한 of 메서드"() {
        when: "content만으로 생성"
        def response = ChatResponse.of("Hello")

        then: "기본값 적용"
        response.content() == "Hello"
        response.modelId() == "unknown"
        response.processingTimeMs() == 0
    }

    // ==================== ChatResponseFactory 인터페이스 테스트 ====================

    def "ChatResponseFactory: default 메서드 동작"() {
        given: "Mock Factory"
        def userMessage = "Test"

        when: "대화 내역 없이 호출"
        def response = mockFactory.create(userMessage, ChatLevel.BEGINNER)

        then: "정상 동작"
        response != null
    }
}
