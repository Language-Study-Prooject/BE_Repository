package com.mzc.secondproject.serverless.common.util

import software.amazon.awssdk.services.dynamodb.model.AttributeValue
import spock.lang.Specification

class CursorUtilSpec extends Specification {

    // ==================== encode Tests ====================

    def "encode: 정상적인 lastEvaluatedKey 인코딩"() {
        given: "PK, SK가 있는 lastEvaluatedKey"
        def lastEvaluatedKey = [
                "PK": AttributeValue.builder().s("USER#user123").build(),
                "SK": AttributeValue.builder().s("WORD#word456").build()
        ]

        when: "인코딩"
        def cursor = CursorUtil.encode(lastEvaluatedKey)

        then: "Base64 인코딩된 커서 반환"
        cursor != null
        !cursor.isEmpty()
    }

    def "encode: null 입력"() {
        when:
        def cursor = CursorUtil.encode(null)

        then:
        cursor == null
    }

    def "encode: 빈 Map 입력"() {
        when:
        def cursor = CursorUtil.encode([:])

        then:
        cursor == null
    }

    // ==================== decode Tests ====================

    def "encode-decode 왕복 테스트"() {
        given: "원본 lastEvaluatedKey"
        def original = [
                "PK": AttributeValue.builder().s("USER#testUser").build(),
                "SK": AttributeValue.builder().s("DATE#2026-01-20").build()
        ]

        when: "인코딩 후 디코딩"
        def cursor = CursorUtil.encode(original)
        def decoded = CursorUtil.decode(cursor)

        then: "원본과 동일한 키-값 복원"
        decoded != null
        decoded["PK"].s() == "USER#testUser"
        decoded["SK"].s() == "DATE#2026-01-20"
    }

    def "decode: null 입력"() {
        when:
        def result = CursorUtil.decode(null)

        then:
        result == null
    }

    def "decode: 빈 문자열 입력"() {
        when:
        def result = CursorUtil.decode("")

        then:
        result == null
    }

    def "decode: 잘못된 Base64 문자열"() {
        when:
        def result = CursorUtil.decode("not-valid-base64!!!")

        then: "예외 발생하지 않고 null 반환"
        result == null
    }

    def "encode: 단일 키-값 쌍"() {
        given:
        def lastEvaluatedKey = [
                "PK": AttributeValue.builder().s("SINGLE#key").build()
        ]

        when:
        def cursor = CursorUtil.encode(lastEvaluatedKey)
        def decoded = CursorUtil.decode(cursor)

        then:
        decoded != null
        decoded["PK"].s() == "SINGLE#key"
    }
}
