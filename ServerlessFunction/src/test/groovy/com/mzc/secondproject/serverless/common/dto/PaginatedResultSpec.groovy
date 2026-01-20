package com.mzc.secondproject.serverless.common.dto

import spock.lang.Specification

class PaginatedResultSpec extends Specification {

    def "hasMore: nextCursor가 있으면 true"() {
        given:
        def result = new PaginatedResult<String>(["item1", "item2"], "cursor123")

        expect:
        result.hasMore() == true
    }

    def "hasMore: nextCursor가 null이면 false"() {
        given:
        def result = new PaginatedResult<String>(["item1", "item2"], null)

        expect:
        result.hasMore() == false
    }

    def "items: 아이템 목록 반환"() {
        given:
        def items = ["apple", "banana", "cherry"]
        def result = new PaginatedResult<String>(items, "cursor")

        expect:
        result.items() == items
        result.items().size() == 3
    }

    def "nextCursor: 커서 값 반환"() {
        given:
        def cursor = "abc123"
        def result = new PaginatedResult<String>([], cursor)

        expect:
        result.nextCursor() == cursor
    }

    def "빈 아이템 목록 처리"() {
        given:
        def result = new PaginatedResult<String>([], null)

        expect:
        result.items().isEmpty()
        !result.hasMore()
    }

    def "제네릭 타입으로 Integer 사용"() {
        given:
        def items = [1, 2, 3, 4, 5]
        def result = new PaginatedResult<Integer>(items, "next")

        expect:
        result.items() == [1, 2, 3, 4, 5]
        result.hasMore()
    }

    def "제네릭 타입으로 커스텀 객체 사용"() {
        given:
        def items = [[name: "test1"], [name: "test2"]]
        def result = new PaginatedResult<Map>(items, null)

        expect:
        result.items().size() == 2
        !result.hasMore()
    }
}
