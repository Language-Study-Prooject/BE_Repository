package com.mzc.secondproject.serverless.domain.chatting.enums

import spock.lang.Specification
import spock.lang.Unroll

class GameStatusSpec extends Specification {

    // ==================== isValid Tests ====================

    @Unroll
    def "isValid: '#value' -> #expected"() {
        expect:
        GameStatus.isValid(value) == expected

        where:
        value       | expected
        "NONE"      | true
        "WAITING"   | true
        "PLAYING"   | true
        "ROUND_END" | true
        "FINISHED"  | true
        "none"      | true
        "waiting"   | true
        "INVALID"   | false
        ""          | false
        null        | false
    }

    // ==================== fromString Tests ====================

    @Unroll
    def "fromString: '#value' -> #expected"() {
        expect:
        GameStatus.fromString(value) == expected

        where:
        value       | expected
        "NONE"      | GameStatus.NONE
        "none"      | GameStatus.NONE
        "WAITING"   | GameStatus.WAITING
        "waiting"   | GameStatus.WAITING
        "PLAYING"   | GameStatus.PLAYING
        "ROUND_END" | GameStatus.ROUND_END
        "FINISHED"  | GameStatus.FINISHED
        null        | GameStatus.NONE
        "INVALID"   | GameStatus.NONE
    }

    // ==================== isGameActive Tests ====================

    def "isGameActive: PLAYING과 ROUND_END만 true"() {
        expect:
        GameStatus.NONE.isGameActive() == false
        GameStatus.WAITING.isGameActive() == false
        GameStatus.PLAYING.isGameActive() == true
        GameStatus.ROUND_END.isGameActive() == true
        GameStatus.FINISHED.isGameActive() == false
    }

    // ==================== canStartGame Tests ====================

    def "canStartGame: NONE과 FINISHED만 true"() {
        expect:
        GameStatus.NONE.canStartGame() == true
        GameStatus.WAITING.canStartGame() == false
        GameStatus.PLAYING.canStartGame() == false
        GameStatus.ROUND_END.canStartGame() == false
        GameStatus.FINISHED.canStartGame() == true
    }

    // ==================== Getter Tests ====================

    def "GameStatus 속성 정상 반환"() {
        expect:
        GameStatus.NONE.getCode() == "none"
        GameStatus.NONE.getDisplayName() == "게임 없음"

        GameStatus.WAITING.getCode() == "waiting"
        GameStatus.WAITING.getDisplayName() == "게임 대기 중"

        GameStatus.PLAYING.getCode() == "playing"
        GameStatus.PLAYING.getDisplayName() == "게임 진행 중"

        GameStatus.ROUND_END.getCode() == "round_end"
        GameStatus.ROUND_END.getDisplayName() == "라운드 종료"

        GameStatus.FINISHED.getCode() == "finished"
        GameStatus.FINISHED.getDisplayName() == "게임 종료"
    }

    def "모든 GameStatus 값 존재 확인"() {
        expect: "5개의 상태 존재"
        GameStatus.values().length == 5
    }
}
