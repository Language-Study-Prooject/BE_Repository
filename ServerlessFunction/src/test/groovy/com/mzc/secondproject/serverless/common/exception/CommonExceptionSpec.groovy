package com.mzc.secondproject.serverless.common.exception

import spock.lang.Specification

class CommonExceptionSpec extends Specification {

    // ==================== 인증/인가 예외 Tests ====================

    def "unauthorized: 기본 메시지와 401 상태 코드"() {
        when:
        def exception = CommonException.unauthorized()

        then:
        exception.getErrorCode() == CommonErrorCode.UNAUTHORIZED
        exception.getStatusCode() == 401
        exception.isClientError()
    }

    def "unauthorized: 커스텀 메시지"() {
        given:
        def message = "로그인이 필요한 서비스입니다"

        when:
        def exception = CommonException.unauthorized(message)

        then:
        exception.getMessage() == message
        exception.getStatusCode() == 401
    }

    def "forbidden: 기본 메시지와 403 상태 코드"() {
        when:
        def exception = CommonException.forbidden()

        then:
        exception.getErrorCode() == CommonErrorCode.FORBIDDEN
        exception.getStatusCode() == 403
    }

    def "forbidden: 리소스 이름 포함"() {
        when:
        def exception = CommonException.forbidden("관리자 페이지")

        then:
        exception.getMessage().contains("관리자 페이지")
        exception.getStatusCode() == 403
    }

    def "invalidToken: 401 상태 코드"() {
        when:
        def exception = CommonException.invalidToken()

        then:
        exception.getErrorCode() == CommonErrorCode.INVALID_TOKEN
        exception.getStatusCode() == 401
    }

    def "tokenExpired: 401 상태 코드"() {
        when:
        def exception = CommonException.tokenExpired()

        then:
        exception.getErrorCode() == CommonErrorCode.TOKEN_EXPIRED
        exception.getStatusCode() == 401
    }

    // ==================== 검증 예외 Tests ====================

    def "invalidInput: 기본 메시지와 400 상태 코드"() {
        when:
        def exception = CommonException.invalidInput()

        then:
        exception.getErrorCode() == CommonErrorCode.INVALID_INPUT
        exception.getStatusCode() == 400
    }

    def "invalidInput: 커스텀 메시지"() {
        given:
        def message = "이메일 형식이 올바르지 않습니다"

        when:
        def exception = CommonException.invalidInput(message)

        then:
        exception.getMessage() == message
    }

    def "requiredFieldMissing: 필드명 포함"() {
        when:
        def exception = CommonException.requiredFieldMissing("email")

        then:
        exception.getMessage().contains("email")
        exception.getStatusCode() == 400
    }

    def "invalidFormat: 필드명 포함"() {
        when:
        def exception = CommonException.invalidFormat("phoneNumber")

        then:
        exception.getMessage().contains("phoneNumber")
        exception.getStatusCode() == 400
    }

    def "valueOutOfRange: 필드명과 범위 포함"() {
        when:
        def exception = CommonException.valueOutOfRange("age", 1, 120)

        then:
        exception.getMessage().contains("age")
        exception.getMessage().contains("1")
        exception.getMessage().contains("120")
        exception.getStatusCode() == 400
    }

    // ==================== 리소스 예외 Tests ====================

    def "notFound: 리소스명 포함"() {
        when:
        def exception = CommonException.notFound("사용자")

        then:
        exception.getMessage().contains("사용자")
        exception.getStatusCode() == 404
    }

    def "notFound: 리소스명과 ID 포함"() {
        when:
        def exception = CommonException.notFound("사용자", "user123")

        then:
        exception.getMessage().contains("사용자")
        exception.getMessage().contains("user123")
        exception.getStatusCode() == 404
    }

    def "alreadyExists: 리소스명 포함"() {
        when:
        def exception = CommonException.alreadyExists("이메일")

        then:
        exception.getMessage().contains("이메일")
        exception.getStatusCode() == 409
    }

    def "alreadyExists: 리소스명과 ID 포함"() {
        when:
        def exception = CommonException.alreadyExists("사용자", "user@test.com")

        then:
        exception.getMessage().contains("사용자")
        exception.getMessage().contains("user@test.com")
        exception.getStatusCode() == 409
    }

    // ==================== 시스템 예외 Tests ====================

    def "internalError: 기본 메시지와 500 상태 코드"() {
        when:
        def exception = CommonException.internalError()

        then:
        exception.getErrorCode() == CommonErrorCode.INTERNAL_SERVER_ERROR
        exception.getStatusCode() == 500
        exception.isServerError()
    }

    def "internalError: Throwable cause 포함"() {
        given:
        def cause = new RuntimeException("Original error")

        when:
        def exception = CommonException.internalError(cause)

        then:
        exception.getCause() == cause
        exception.getStatusCode() == 500
    }

    def "internalError: 커스텀 메시지"() {
        given:
        def message = "데이터 처리 중 오류"

        when:
        def exception = CommonException.internalError(message)

        then:
        exception.getMessage() == message
        exception.getStatusCode() == 500
    }

    def "databaseError: cause 포함"() {
        given:
        def cause = new RuntimeException("DB connection failed")

        when:
        def exception = CommonException.databaseError(cause)

        then:
        exception.getCause() == cause
        exception.getStatusCode() == 500
    }

    def "externalApiError: API 이름 포함"() {
        when:
        def exception = CommonException.externalApiError("OpenAI API")

        then:
        exception.getMessage().contains("OpenAI API")
        exception.getStatusCode() == 502
    }

    def "externalApiError: API 이름과 cause 포함"() {
        given:
        def cause = new RuntimeException("Connection timeout")

        when:
        def exception = CommonException.externalApiError("Bedrock API", cause)

        then:
        exception.getMessage().contains("Bedrock API")
        exception.getCause() == cause
        exception.getStatusCode() == 502
    }

    def "serviceUnavailable: 503 상태 코드"() {
        when:
        def exception = CommonException.serviceUnavailable()

        then:
        exception.getErrorCode() == CommonErrorCode.SERVICE_UNAVAILABLE
        exception.getStatusCode() == 503
    }
}
