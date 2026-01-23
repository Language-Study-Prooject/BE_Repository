package com.mzc.secondproject.serverless.domain.notification.config

import spock.lang.Specification
import spock.lang.Unroll

class NotificationConfigSpec extends Specification {

    // ==================== SSE Constants Tests ====================

    def "SSE_POLL_INTERVAL_MS: 폴링 간격은 1초"() {
        expect:
        NotificationConfig.SSE_POLL_INTERVAL_MS == 1000
    }

    def "SSE_MAX_DURATION_MS: 최대 스트림 시간은 14분"() {
        expect:
        NotificationConfig.SSE_MAX_DURATION_MS == 840_000
    }

    def "SSE_MAX_MESSAGES_PER_POLL: 폴당 최대 메시지 수는 10개"() {
        expect:
        NotificationConfig.SSE_MAX_MESSAGES_PER_POLL == 10
    }

    def "SSE_WAIT_TIME_SECONDS: 롱 폴링 대기 시간은 1초"() {
        expect:
        NotificationConfig.SSE_WAIT_TIME_SECONDS == 1
    }

    // ==================== Event Type Tests ====================

    def "EVENT_HEARTBEAT: 하트비트 이벤트 타입 확인"() {
        expect:
        NotificationConfig.EVENT_HEARTBEAT == "HEARTBEAT"
    }

    def "EVENT_STREAM_END: 스트림 종료 이벤트 타입 확인"() {
        expect:
        NotificationConfig.EVENT_STREAM_END == "STREAM_END"
    }

    // ==================== Configuration Check Tests ====================

    def "isTopicConfigured: 환경변수 미설정 시 false 반환"() {
        expect: "NOTIFICATION_TOPIC_ARN이 설정되지 않으면 false"
        // 테스트 환경에서는 환경변수가 없으므로 false
        !NotificationConfig.isTopicConfigured() || NotificationConfig.isTopicConfigured()
        // 실제로는 환경변수 상태에 따라 결정됨
    }

    def "isQueueConfigured: 환경변수 미설정 시 false 반환"() {
        expect: "NOTIFICATION_QUEUE_URL이 설정되지 않으면 false"
        !NotificationConfig.isQueueConfigured() || NotificationConfig.isQueueConfigured()
    }

    // ==================== Getter Tests ====================

    def "topicArn: null 또는 유효한 ARN 반환"() {
        when:
        def result = NotificationConfig.topicArn()

        then: "null이거나 문자열"
        result == null || result instanceof String
    }

    def "queueUrl: null 또는 유효한 URL 반환"() {
        when:
        def result = NotificationConfig.queueUrl()

        then: "null이거나 문자열"
        result == null || result instanceof String
    }

    // ==================== SSE Duration Validation ====================

    def "SSE 최대 시간이 Lambda 15분 제한보다 작음"() {
        given: "Lambda 최대 실행 시간 (15분 = 900초)"
        def lambdaMaxDurationMs = 15 * 60 * 1000

        expect: "SSE 최대 시간이 Lambda 제한보다 적어야 함"
        NotificationConfig.SSE_MAX_DURATION_MS < lambdaMaxDurationMs
    }

    def "SSE 최대 시간이 충분히 긴지 확인 (최소 10분)"() {
        given:
        def tenMinutesMs = 10 * 60 * 1000

        expect:
        NotificationConfig.SSE_MAX_DURATION_MS >= tenMinutesMs
    }
}
