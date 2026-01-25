package com.mzc.secondproject.serverless.common.config

import spock.lang.Specification

class EnvConfigSpec extends Specification {

    // ==================== getRequired Tests ====================

    def "getRequired: 설정되지 않은 환경 변수에 대해 IllegalStateException 발생"() {
        when: "존재하지 않는 환경 변수 요청"
        EnvConfig.getRequired("NON_EXISTENT_ENV_VAR_FOR_TEST_12345")

        then: "IllegalStateException 발생"
        def e = thrown(IllegalStateException)
        e.message.contains("NON_EXISTENT_ENV_VAR_FOR_TEST_12345")
        e.message.contains("설정되지 않았습니다")
    }

    def "getRequired: 에러 메시지에 SAM template.yaml 언급 포함"() {
        when:
        EnvConfig.getRequired("TEST_MISSING_VAR")

        then:
        def e = thrown(IllegalStateException)
        e.message.contains("SAM template.yaml")
    }

    // ==================== getOrDefault Tests ====================

    def "getOrDefault: 설정되지 않은 환경 변수에 대해 기본값 반환"() {
        given:
        def defaultValue = "defaultTestValue"

        when:
        def result = EnvConfig.getOrDefault("NON_EXISTENT_VAR_TEST", defaultValue)

        then:
        result == defaultValue
    }

    def "getOrDefault: PATH 환경 변수가 존재하면 값 반환 (시스템 환경 변수)"() {
        when: "일반적으로 설정되어 있는 PATH 환경 변수 요청"
        def result = EnvConfig.getOrDefault("PATH", "default")

        then: "PATH가 설정되어 있으면 해당 값 반환, 아니면 기본값"
        result != null
        !result.isEmpty()
    }

    // ==================== getIntOrDefault Tests ====================

    def "getIntOrDefault: 설정되지 않은 환경 변수에 대해 기본값 반환"() {
        given:
        def defaultValue = 42

        when:
        def result = EnvConfig.getIntOrDefault("NON_EXISTENT_INT_VAR", defaultValue)

        then:
        result == defaultValue
    }

    def "getIntOrDefault: 기본값 0 처리"() {
        when:
        def result = EnvConfig.getIntOrDefault("NON_EXISTENT_VAR", 0)

        then:
        result == 0
    }

    def "getIntOrDefault: 음수 기본값 처리"() {
        when:
        def result = EnvConfig.getIntOrDefault("NON_EXISTENT_VAR", -10)

        then:
        result == -10
    }

    // ==================== getLongOrDefault Tests ====================

    def "getLongOrDefault: 설정되지 않은 환경 변수에 대해 기본값 반환"() {
        given:
        def defaultValue = 1000000000000L

        when:
        def result = EnvConfig.getLongOrDefault("NON_EXISTENT_LONG_VAR", defaultValue)

        then:
        result == defaultValue
    }

    def "getLongOrDefault: 기본값 0L 처리"() {
        when:
        def result = EnvConfig.getLongOrDefault("NON_EXISTENT_VAR", 0L)

        then:
        result == 0L
    }

    def "getLongOrDefault: 음수 기본값 처리"() {
        when:
        def result = EnvConfig.getLongOrDefault("NON_EXISTENT_VAR", -5000L)

        then:
        result == -5000L
    }

    // ==================== Edge Cases ====================

    def "getOrDefault: 빈 환경 변수 이름에 대해 기본값 반환"() {
        when:
        def result = EnvConfig.getOrDefault("", "default")

        then:
        result == "default"
    }

    def "getIntOrDefault: 최대 정수값 기본값 처리"() {
        when:
        def result = EnvConfig.getIntOrDefault("NON_EXISTENT", Integer.MAX_VALUE)

        then:
        result == Integer.MAX_VALUE
    }

    def "getLongOrDefault: 최대 long값 기본값 처리"() {
        when:
        def result = EnvConfig.getLongOrDefault("NON_EXISTENT", Long.MAX_VALUE)

        then:
        result == Long.MAX_VALUE
    }
}
