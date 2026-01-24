package com.mzc.secondproject.serverless.domain.chatting.service

import spock.lang.Specification

class DictionaryServiceSpec extends Specification {

    def "DictionaryResult.valid: 유효한 결과 생성"() {
        when:
        def result = DictionaryService.DictionaryResult.valid("apple", "(noun) A fruit", "/ˈæpəl/")

        then:
        result.isValid()
        result.word() == "apple"
        result.getDefinition().isPresent()
        result.getDefinition().get() == "(noun) A fruit"
        result.getPhonetic().isPresent()
        result.getPhonetic().get() == "/ˈæpəl/"
        result.errorMessage() == null
    }

    def "DictionaryResult.validWithoutDefinition: 정의 없이 유효한 결과"() {
        when:
        def result = DictionaryService.DictionaryResult.validWithoutDefinition("apple")

        then:
        result.isValid()
        result.word() == "apple"
        result.getDefinition().isEmpty()
        result.getPhonetic().isEmpty()
    }

    def "DictionaryResult.invalid: 유효하지 않은 결과"() {
        when:
        def result = DictionaryService.DictionaryResult.invalid("사전에 없는 단어입니다.")

        then:
        !result.isValid()
        result.word() == null
        result.getDefinition().isEmpty()
        result.errorMessage() == "사전에 없는 단어입니다."
    }

    def "DictionaryResult.getDefinition: Optional 반환"() {
        expect:
        DictionaryService.DictionaryResult.valid("test", "def", null).getDefinition().isPresent()
        DictionaryService.DictionaryResult.valid("test", null, null).getDefinition().isEmpty()
    }

    def "DictionaryResult.getPhonetic: Optional 반환"() {
        expect:
        DictionaryService.DictionaryResult.valid("test", null, "/test/").getPhonetic().isPresent()
        DictionaryService.DictionaryResult.valid("test", null, null).getPhonetic().isEmpty()
    }
}
