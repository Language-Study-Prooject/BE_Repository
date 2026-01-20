package com.mzc.secondproject.serverless.common.util

import com.google.gson.JsonArray
import com.google.gson.JsonParser
import spock.lang.Specification

class JsonUtilSpec extends Specification {

    // ==================== extractJson Tests ====================

    def "extractJson: 정상적인 JSON 객체 추출"() {
        given:
        def response = 'Some text before {"key": "value"} and after'

        when:
        def result = JsonUtil.extractJson(response)

        then:
        result == '{"key": "value"}'
    }

    def "extractJson: 중첩된 JSON 객체 추출"() {
        given:
        def response = 'Prefix {"outer": {"inner": "value"}} Suffix'

        when:
        def result = JsonUtil.extractJson(response)

        then:
        result == '{"outer": {"inner": "value"}}'
    }

    def "extractJson: 순수 JSON 문자열"() {
        given:
        def response = '{"name": "test", "count": 10}'

        when:
        def result = JsonUtil.extractJson(response)

        then:
        result == '{"name": "test", "count": 10}'
    }

    def "extractJson: null 입력"() {
        when:
        def result = JsonUtil.extractJson(null)

        then:
        result == null
    }

    def "extractJson: 빈 문자열 입력"() {
        when:
        def result = JsonUtil.extractJson("")

        then:
        result == null
    }

    def "extractJson: 공백만 있는 문자열 입력"() {
        when:
        def result = JsonUtil.extractJson("   ")

        then:
        result == null
    }

    def "extractJson: JSON이 없는 문자열"() {
        given:
        def response = "This is plain text without JSON"

        when:
        def result = JsonUtil.extractJson(response)

        then:
        result == response  // 원본 반환
    }

    // ==================== toStringList Tests ====================

    def "toStringList: 정상적인 JsonArray 변환"() {
        given:
        def jsonArray = JsonParser.parseString('["apple", "banana", "cherry"]').getAsJsonArray()

        when:
        def result = JsonUtil.toStringList(jsonArray)

        then:
        result == ["apple", "banana", "cherry"]
    }

    def "toStringList: 빈 JsonArray 변환"() {
        given:
        def jsonArray = new JsonArray()

        when:
        def result = JsonUtil.toStringList(jsonArray)

        then:
        result == []
    }

    def "toStringList: null 입력"() {
        when:
        def result = JsonUtil.toStringList(null)

        then:
        result == []
    }

    def "toStringList: 단일 요소 JsonArray"() {
        given:
        def jsonArray = JsonParser.parseString('["single"]').getAsJsonArray()

        when:
        def result = JsonUtil.toStringList(jsonArray)

        then:
        result == ["single"]
    }
}
