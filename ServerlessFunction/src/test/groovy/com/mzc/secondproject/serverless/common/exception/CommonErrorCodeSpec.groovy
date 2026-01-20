package com.mzc.secondproject.serverless.common.exception

import spock.lang.Specification
import spock.lang.Unroll

class CommonErrorCodeSpec extends Specification {

    @Unroll
    def "에러 코드 '#errorCode': code=#expectedCode, statusCode=#expectedStatusCode"() {
        expect:
        errorCode.getCode() == expectedCode
        errorCode.getStatusCode() == expectedStatusCode
        errorCode.getMessage() != null
        !errorCode.getMessage().isEmpty()

        where:
        errorCode                                   | expectedCode       | expectedStatusCode
        CommonErrorCode.UNAUTHORIZED                | "AUTH_001"         | 401
        CommonErrorCode.FORBIDDEN                   | "AUTH_002"         | 403
        CommonErrorCode.INVALID_TOKEN               | "AUTH_003"         | 401
        CommonErrorCode.TOKEN_EXPIRED               | "AUTH_004"         | 401
        CommonErrorCode.INVALID_INPUT               | "VALIDATION_001"   | 400
        CommonErrorCode.REQUIRED_FIELD_MISSING      | "VALIDATION_002"   | 400
        CommonErrorCode.INVALID_FORMAT              | "VALIDATION_003"   | 400
        CommonErrorCode.VALUE_OUT_OF_RANGE          | "VALIDATION_004"   | 400
        CommonErrorCode.RESOURCE_NOT_FOUND          | "RESOURCE_001"     | 404
        CommonErrorCode.METHOD_NOT_ALLOWED          | "RESOURCE_003"     | 405
        CommonErrorCode.RESOURCE_ALREADY_EXISTS     | "RESOURCE_002"     | 409
        CommonErrorCode.INTERNAL_SERVER_ERROR       | "SYSTEM_001"       | 500
        CommonErrorCode.DATABASE_ERROR              | "SYSTEM_002"       | 500
        CommonErrorCode.EXTERNAL_API_ERROR          | "SYSTEM_003"       | 502
        CommonErrorCode.SERVICE_UNAVAILABLE         | "SYSTEM_004"       | 503
    }

    def "인증 관련 에러 코드들은 4xx"() {
        expect:
        CommonErrorCode.UNAUTHORIZED.getStatusCode() == 401
        CommonErrorCode.FORBIDDEN.getStatusCode() == 403
        CommonErrorCode.INVALID_TOKEN.getStatusCode() == 401
        CommonErrorCode.TOKEN_EXPIRED.getStatusCode() == 401
    }

    def "검증 관련 에러 코드들은 400"() {
        expect:
        CommonErrorCode.INVALID_INPUT.getStatusCode() == 400
        CommonErrorCode.REQUIRED_FIELD_MISSING.getStatusCode() == 400
        CommonErrorCode.INVALID_FORMAT.getStatusCode() == 400
        CommonErrorCode.VALUE_OUT_OF_RANGE.getStatusCode() == 400
    }

    def "시스템 에러 코드들은 5xx"() {
        expect:
        CommonErrorCode.INTERNAL_SERVER_ERROR.getStatusCode() == 500
        CommonErrorCode.DATABASE_ERROR.getStatusCode() == 500
        CommonErrorCode.EXTERNAL_API_ERROR.getStatusCode() == 502
        CommonErrorCode.SERVICE_UNAVAILABLE.getStatusCode() == 503
    }

    def "isClientError: 4xx 상태 코드"() {
        expect:
        CommonErrorCode.UNAUTHORIZED.isClientError()
        CommonErrorCode.FORBIDDEN.isClientError()
        CommonErrorCode.INVALID_INPUT.isClientError()
        CommonErrorCode.RESOURCE_NOT_FOUND.isClientError()
    }

    def "isServerError: 5xx 상태 코드"() {
        expect:
        CommonErrorCode.INTERNAL_SERVER_ERROR.isServerError()
        CommonErrorCode.DATABASE_ERROR.isServerError()
        CommonErrorCode.EXTERNAL_API_ERROR.isServerError()
        CommonErrorCode.SERVICE_UNAVAILABLE.isServerError()
    }

    def "모든 에러 코드 개수 확인"() {
        expect: "15개의 에러 코드 존재"
        CommonErrorCode.values().length == 15
    }
}
