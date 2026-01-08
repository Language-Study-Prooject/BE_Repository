package com.mzc.secondproject.serverless

import spock.lang.Specification
import spock.lang.Subject

/**
 * Spock 테스트 환경 설정 확인을 위한 샘플 테스트
 */
class SampleSpec extends Specification {

    def "Spock 테스트 환경이 정상적으로 설정되었는지 확인"() {
        expect:
        1 + 1 == 2
    }

    def "given-when-then 블록 테스트"() {
        given: "두 개의 숫자"
        def a = 10
        def b = 20

        when: "두 숫자를 더하면"
        def result = a + b

        then: "결과는 30이다"
        result == 30
    }

    def "where 블록을 이용한 파라미터화 테스트"() {
        expect:
        Math.max(a, b) == max

        where:
        a  | b  || max
        1  | 2  || 2
        5  | 3  || 5
        10 | 10 || 10
    }
}
