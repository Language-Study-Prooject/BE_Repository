package com.mzc.secondproject.serverless.domain.chatting.model

import spock.lang.Specification

class PollSpec extends Specification {

    def "addVote: 정상적인 투표 추가"() {
        given:
        def poll = Poll.builder()
                .pollId("poll-123")
                .options(["옵션1", "옵션2", "옵션3"])
                .votes(["0": 0, "1": 0, "2": 0])
                .userVotes([:])
                .build()

        when:
        def result = poll.addVote("user1", 0)

        then:
        result == true
        poll.votes["0"] == 1
        poll.userVotes["user1"] == 0
    }

    def "addVote: 이미 투표한 사용자는 재투표 불가"() {
        given:
        def poll = Poll.builder()
                .options(["옵션1", "옵션2"])
                .votes(["0": 1, "1": 0])
                .userVotes(["user1": 0])
                .build()

        when:
        def result = poll.addVote("user1", 1)

        then:
        result == false
        poll.votes["0"] == 1
        poll.votes["1"] == 0
    }

    def "addVote: 유효하지 않은 옵션 인덱스"() {
        given:
        def poll = Poll.builder()
                .options(["옵션1", "옵션2"])
                .votes(["0": 0, "1": 0])
                .userVotes([:])
                .build()

        when:
        def result = poll.addVote("user1", 5)

        then:
        result == false
    }

    def "addVote: 음수 옵션 인덱스"() {
        given:
        def poll = Poll.builder()
                .options(["옵션1", "옵션2"])
                .votes(["0": 0, "1": 0])
                .userVotes([:])
                .build()

        when:
        def result = poll.addVote("user1", -1)

        then:
        result == false
    }

    def "hasVoted: 투표한 사용자 확인"() {
        given:
        def poll = Poll.builder()
                .userVotes(["user1": 0])
                .build()

        expect:
        poll.hasVoted("user1") == true
        poll.hasVoted("user2") == false
    }

    def "hasVoted: userVotes가 null인 경우"() {
        given:
        def poll = Poll.builder()
                .userVotes(null)
                .build()

        expect:
        poll.hasVoted("user1") == false
    }

    def "getTotalVotes: 총 투표 수 계산"() {
        given:
        def poll = Poll.builder()
                .votes(["0": 3, "1": 2, "2": 5])
                .build()

        expect:
        poll.getTotalVotes() == 10
    }

    def "getTotalVotes: 투표가 없는 경우"() {
        given:
        def poll = Poll.builder()
                .votes(["0": 0, "1": 0])
                .build()

        expect:
        poll.getTotalVotes() == 0
    }

    def "getTotalVotes: votes가 null인 경우"() {
        given:
        def poll = Poll.builder()
                .votes(null)
                .build()

        expect:
        poll.getTotalVotes() == 0
    }

    def "여러 사용자 투표 시나리오"() {
        given:
        def poll = Poll.builder()
                .options(["A", "B", "C"])
                .votes(["0": 0, "1": 0, "2": 0])
                .userVotes([:])
                .build()

        when:
        poll.addVote("user1", 0)
        poll.addVote("user2", 0)
        poll.addVote("user3", 1)
        poll.addVote("user4", 2)

        then:
        poll.votes["0"] == 2
        poll.votes["1"] == 1
        poll.votes["2"] == 1
        poll.getTotalVotes() == 4
        poll.hasVoted("user1")
        poll.hasVoted("user2")
        poll.hasVoted("user3")
        poll.hasVoted("user4")
        !poll.hasVoted("user5")
    }
}
