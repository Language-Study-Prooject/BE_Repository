package com.mzc.secondproject.serverless.domain.chatting.model

import spock.lang.Specification
import spock.lang.Unroll

class WordChainSessionSpec extends Specification {

    def "calculateTimeLimit: 라운드별 시간 제한 계산"() {
        expect:
        WordChainSession.calculateTimeLimit(round) == expected

        where:
        round | expected
        1     | 15
        2     | 15
        3     | 13
        4     | 13
        5     | 11
        6     | 11
        7     | 9
        8     | 9
        9     | 8
        10    | 8
        20    | 8
    }

    def "calculateScore: 기본 점수 계산"() {
        when:
        def score = WordChainSession.calculateScore(responseTimeMs, wordLength, timeLimit)

        then:
        score == expected

        where:
        responseTimeMs | wordLength | timeLimit | expected
        0              | 4          | 15        | 25          // base(10) + time(15) + length(0)
        5000           | 4          | 15        | 20          // base(10) + time(10) + length(0)
        10000          | 4          | 15        | 15          // base(10) + time(5) + length(0)
        15000          | 4          | 15        | 10          // base(10) + time(0) + length(0)
        0              | 7          | 15        | 31          // base(10) + time(15) + length(6)
        5000           | 6          | 15        | 24          // base(10) + time(10) + length(4)
    }

    def "isActive: 게임 활성 상태 확인"() {
        given:
        def session = WordChainSession.builder()
                .status(status)
                .build()

        expect:
        session.isActive() == expected

        where:
        status     | expected
        "PLAYING"  | true
        "FINISHED" | false
        "WAITING"  | false
        null       | false
    }

    def "isCurrentTurn: 현재 턴 확인"() {
        given:
        def session = WordChainSession.builder()
                .currentPlayerId("player1")
                .build()

        expect:
        session.isCurrentTurn("player1") == true
        session.isCurrentTurn("player2") == false
        session.isCurrentTurn(null) == false
    }

    def "isWordUsed: 단어 사용 여부 확인"() {
        given:
        def session = WordChainSession.builder()
                .usedWords(["apple", "elephant", "tiger"])
                .build()

        expect:
        session.isWordUsed("apple") == true
        session.isWordUsed("APPLE") == true
        session.isWordUsed("banana") == false
    }

    def "isWordUsed: usedWords가 null인 경우"() {
        given:
        def session = WordChainSession.builder()
                .usedWords(null)
                .build()

        expect:
        session.isWordUsed("apple") == false
    }

    def "addUsedWord: 단어 추가"() {
        given:
        def session = WordChainSession.builder()
                .usedWords(new ArrayList<>())
                .wordDefinitions(new HashMap<>())
                .build()

        when:
        session.addUsedWord("Apple", "(noun) A fruit")

        then:
        session.usedWords.contains("apple")
        session.wordDefinitions["apple"] == "(noun) A fruit"
    }

    def "addUsedWord: null 리스트에서 시작"() {
        given:
        def session = WordChainSession.builder()
                .usedWords(null)
                .wordDefinitions(null)
                .build()

        when:
        session.addUsedWord("apple", "(noun) A fruit")

        then:
        session.usedWords == ["apple"]
        session.wordDefinitions["apple"] == "(noun) A fruit"
    }

    def "addUsedWord: definition이 null인 경우"() {
        given:
        def session = WordChainSession.builder()
                .usedWords(new ArrayList<>())
                .wordDefinitions(new HashMap<>())
                .build()

        when:
        session.addUsedWord("apple", null)

        then:
        session.usedWords.contains("apple")
        !session.wordDefinitions.containsKey("apple")
    }

    def "eliminatePlayer: 플레이어 탈락 처리"() {
        given:
        def session = WordChainSession.builder()
                .activePlayers(new ArrayList<>(["player1", "player2", "player3"]))
                .eliminatedPlayers(new ArrayList<>())
                .build()

        when:
        session.eliminatePlayer("player2")

        then:
        session.activePlayers == ["player1", "player3"]
        session.eliminatedPlayers == ["player2"]
    }

    def "eliminatePlayer: 이미 탈락한 플레이어는 중복 추가되지 않음"() {
        given:
        def session = WordChainSession.builder()
                .activePlayers(new ArrayList<>(["player1"]))
                .eliminatedPlayers(new ArrayList<>(["player2"]))
                .build()

        when:
        session.eliminatePlayer("player2")

        then:
        session.eliminatedPlayers.size() == 1
    }

    def "getNextPlayerId: 다음 플레이어 반환"() {
        given:
        def session = WordChainSession.builder()
                .activePlayers(["player1", "player2", "player3"])
                .currentPlayerId(currentPlayer)
                .build()

        expect:
        session.getNextPlayerId() == expected

        where:
        currentPlayer | expected
        "player1"     | "player2"
        "player2"     | "player3"
        "player3"     | "player1"
        null          | "player1"
        "unknown"     | "player1"
    }

    def "getNextPlayerId: 한 명만 남은 경우"() {
        given:
        def session = WordChainSession.builder()
                .activePlayers(["winner"])
                .currentPlayerId("winner")
                .build()

        expect:
        session.getNextPlayerId() == "winner"
    }

    def "getNextPlayerId: 빈 리스트인 경우"() {
        given:
        def session = WordChainSession.builder()
                .activePlayers([])
                .build()

        expect:
        session.getNextPlayerId() == null
    }

    def "addScore: 점수 추가"() {
        given:
        def session = WordChainSession.builder()
                .scores(new HashMap<>())
                .build()

        when:
        session.addScore("player1", 10)
        session.addScore("player1", 15)
        session.addScore("player2", 20)

        then:
        session.scores["player1"] == 25
        session.scores["player2"] == 20
    }

    def "addScore: scores가 null인 경우"() {
        given:
        def session = WordChainSession.builder()
                .scores(null)
                .build()

        when:
        session.addScore("player1", 10)

        then:
        session.scores["player1"] == 10
    }

    def "isGameOver: 게임 종료 조건 확인"() {
        given:
        def session = WordChainSession.builder()
                .activePlayers(players)
                .build()

        expect:
        session.isGameOver() == expected

        where:
        players                | expected
        null                   | true
        []                     | true
        ["player1"]            | true
        ["player1", "player2"] | false
    }

    def "getWinner: 승자 반환"() {
        given:
        def session = WordChainSession.builder()
                .activePlayers(players)
                .build()

        expect:
        session.getWinner() == expected

        where:
        players                | expected
        ["winner"]             | "winner"
        ["p1", "p2"]           | null
        []                     | null
        null                   | null
    }
}
