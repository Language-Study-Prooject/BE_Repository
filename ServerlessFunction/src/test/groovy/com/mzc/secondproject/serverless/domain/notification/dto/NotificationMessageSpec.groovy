package com.mzc.secondproject.serverless.domain.notification.dto

import com.mzc.secondproject.serverless.domain.notification.enums.NotificationType
import spock.lang.Specification

class NotificationMessageSpec extends Specification {

    // ==================== Builder Tests ====================

    def "Builder: 기본 메시지 생성"() {
        given:
        def type = NotificationType.BADGE_EARNED
        def userId = "user-123"
        def payload = [badgeType: "STREAK_7", badgeName: "7일 연속 학습"]

        when:
        def message = NotificationMessage.builder()
                .type(type)
                .userId(userId)
                .payload(payload)
                .build()

        then:
        message.type() == type
        message.userId() == userId
        message.payload() == payload
        message.notificationId() != null
        message.notificationId().startsWith("notif-")
        message.createdAt() != null
    }

    def "Builder: notificationId 자동 생성"() {
        when:
        def message1 = NotificationMessage.builder()
                .type(NotificationType.TEST_COMPLETE)
                .userId("user-1")
                .payload([:])
                .build()

        def message2 = NotificationMessage.builder()
                .type(NotificationType.TEST_COMPLETE)
                .userId("user-1")
                .payload([:])
                .build()

        then: "각 메시지는 고유한 ID를 가짐"
        message1.notificationId() != message2.notificationId()
    }

    def "Builder: createdAt 자동 생성"() {
        when:
        def before = java.time.Instant.now().minusSeconds(1).toString()
        def message = NotificationMessage.builder()
                .type(NotificationType.DAILY_COMPLETE)
                .userId("user-1")
                .payload([:])
                .build()
        def after = java.time.Instant.now().plusSeconds(1).toString()

        then: "createdAt이 현재 시간 범위 내"
        message.createdAt() >= before
        message.createdAt() <= after
    }

    // ==================== NotificationId Format Tests ====================

    def "notificationId: 'notif-' 접두사로 시작"() {
        when:
        def message = NotificationMessage.builder()
                .type(NotificationType.GAME_END)
                .userId("user-1")
                .payload([:])
                .build()

        then:
        message.notificationId().startsWith("notif-")
    }

    def "notificationId: 8자리 UUID 부분 포함"() {
        when:
        def message = NotificationMessage.builder()
                .type(NotificationType.STREAK_REMINDER)
                .userId("user-1")
                .payload([:])
                .build()

        then:
        message.notificationId().length() == "notif-".length() + 8
    }

    // ==================== Payload Tests ====================

    def "Payload: 다양한 타입의 값 포함 가능"() {
        given:
        def payload = [
                stringVal: "test",
                intVal: 100,
                boolVal: true,
                listVal: [1, 2, 3],
                mapVal: [nested: "value"]
        ]

        when:
        def message = NotificationMessage.builder()
                .type(NotificationType.TEST_COMPLETE)
                .userId("user-1")
                .payload(payload)
                .build()

        then:
        message.payload().stringVal == "test"
        message.payload().intVal == 100
        message.payload().boolVal == true
        message.payload().listVal == [1, 2, 3]
        message.payload().mapVal.nested == "value"
    }

    def "Payload: 빈 맵도 허용"() {
        when:
        def message = NotificationMessage.builder()
                .type(NotificationType.GAME_STREAK)
                .userId("user-1")
                .payload([:])
                .build()

        then:
        message.payload().isEmpty()
    }

    // ==================== All NotificationType Tests ====================

    def "모든 NotificationType으로 메시지 생성 가능"() {
        expect: "모든 타입으로 메시지 생성 성공"
        NotificationType.values().every { type ->
            def message = NotificationMessage.builder()
                    .type(type)
                    .userId("test-user")
                    .payload([test: "value"])
                    .build()
            message != null && message.type() == type
        }
    }

    // ==================== Record Immutability Tests ====================

    def "Record: 불변성 확인"() {
        given:
        def message = NotificationMessage.builder()
                .type(NotificationType.BADGE_EARNED)
                .userId("user-1")
                .payload([key: "value"])
                .build()

        expect: "Record는 불변"
        message.type() == NotificationType.BADGE_EARNED
        message.userId() == "user-1"
    }
}
