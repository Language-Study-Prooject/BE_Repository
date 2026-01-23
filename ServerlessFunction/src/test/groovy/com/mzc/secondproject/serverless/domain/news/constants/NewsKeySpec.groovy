package com.mzc.secondproject.serverless.domain.news.constants

import spock.lang.Specification
import spock.lang.Unroll

class NewsKeySpec extends Specification {

    // ==================== Prefix Constants Tests ====================

    def "NEWS prefix 확인"() {
        expect:
        NewsKey.NEWS == "NEWS#"
    }

    def "ARTICLE prefix 확인"() {
        expect:
        NewsKey.ARTICLE == "ARTICLE#"
    }

    def "LEVEL prefix 확인"() {
        expect:
        NewsKey.LEVEL == "LEVEL#"
    }

    def "CATEGORY prefix 확인"() {
        expect:
        NewsKey.CATEGORY == "CATEGORY#"
    }

    def "READ prefix 확인"() {
        expect:
        NewsKey.READ == "READ#"
    }

    def "QUIZ prefix 확인"() {
        expect:
        NewsKey.QUIZ == "QUIZ#"
    }

    def "WORD prefix 확인"() {
        expect:
        NewsKey.WORD == "WORD#"
    }

    def "BOOKMARK prefix 확인"() {
        expect:
        NewsKey.BOOKMARK == "BOOKMARK#"
    }

    // ==================== Key Builder Tests ====================

    @Unroll
    def "newsPk: '#date' -> 'NEWS##date'"() {
        expect:
        NewsKey.newsPk(date) == expectedPk

        where:
        date         | expectedPk
        "2024-01-15" | "NEWS#2024-01-15"
        "2025-12-31" | "NEWS#2025-12-31"
        "2024-02-29" | "NEWS#2024-02-29"
    }

    @Unroll
    def "articleSk: '#articleId' -> 'ARTICLE##articleId'"() {
        expect:
        NewsKey.articleSk(articleId) == expectedSk

        where:
        articleId      | expectedSk
        "abc123"       | "ARTICLE#abc123"
        "news-001"     | "ARTICLE#news-001"
        "uuid-abcd1234"| "ARTICLE#uuid-abcd1234"
    }

    @Unroll
    def "levelPk: '#level' -> 'LEVEL##level'"() {
        expect:
        NewsKey.levelPk(level) == expectedPk

        where:
        level        | expectedPk
        "BEGINNER"   | "LEVEL#BEGINNER"
        "INTERMEDIATE"| "LEVEL#INTERMEDIATE"
        "ADVANCED"   | "LEVEL#ADVANCED"
    }

    @Unroll
    def "categoryPk: '#category' -> 'CATEGORY##category'"() {
        expect:
        NewsKey.categoryPk(category) == expectedPk

        where:
        category   | expectedPk
        "TECH"     | "CATEGORY#TECH"
        "BUSINESS" | "CATEGORY#BUSINESS"
        "HEALTH"   | "CATEGORY#HEALTH"
    }

    def "userNewsPk: userId로 사용자 뉴스 PK 생성"() {
        expect:
        NewsKey.userNewsPk("user-123") == "USER#user-123#NEWS"
    }

    def "readSk: articleId로 읽기 기록 SK 생성"() {
        expect:
        NewsKey.readSk("article-001") == "READ#article-001"
    }

    def "quizSk: articleId로 퀴즈 결과 SK 생성"() {
        expect:
        NewsKey.quizSk("article-001") == "QUIZ#article-001"
    }

    def "wordSk: word와 articleId로 단어 수집 SK 생성"() {
        expect:
        NewsKey.wordSk("hello", "article-001") == "WORD#hello#article-001"
    }

    def "bookmarkSk: articleId로 북마크 SK 생성"() {
        expect:
        NewsKey.bookmarkSk("article-001") == "BOOKMARK#article-001"
    }

    def "userNewsWordsPk: userId로 수집 단어 GSI1 PK 생성"() {
        expect:
        NewsKey.userNewsWordsPk("user-123") == "USER#user-123#NEWS_WORDS"
    }

    def "commentPk: articleId로 댓글 PK 생성"() {
        expect:
        NewsKey.commentPk("article-001") == "NEWS_COMMENT#article-001"
    }

    def "commentSk: commentId로 댓글 SK 생성"() {
        expect:
        NewsKey.commentSk("comment-001") == "COMMENT#comment-001"
    }

    def "userNewsCommentsPk: userId로 사용자 댓글 GSI1 PK 생성"() {
        expect:
        NewsKey.userNewsCommentsPk("user-123") == "USER#user-123#NEWS_COMMENTS"
    }

    def "userNewsStatPk: userId로 사용자 뉴스 통계 GSI1 PK 생성"() {
        expect:
        NewsKey.userNewsStatPk("user-123") == "USER_NEWS_STAT#user-123"
    }

    // ==================== extractDateFromPk Tests ====================

    @Unroll
    def "extractDateFromPk: '#pk' -> '#expectedDate'"() {
        expect:
        NewsKey.extractDateFromPk(pk) == expectedDate

        where:
        pk                  | expectedDate
        "NEWS#2024-01-15"   | "2024-01-15"
        "NEWS#2025-12-31"   | "2025-12-31"
        "NEWS#2024-02-29"   | "2024-02-29"
        null                | null
        ""                  | null
        "INVALID#2024-01-15"| null
        "NEWS"              | null  // NEWS#로 시작하지 않음
        "news#2024-01-15"   | null  // 대소문자 구분
    }

    def "extractDateFromPk: null 입력 시 null 반환"() {
        expect:
        NewsKey.extractDateFromPk(null) == null
    }

    def "extractDateFromPk: NEWS# prefix가 없으면 null 반환"() {
        expect:
        NewsKey.extractDateFromPk("ARTICLE#2024-01-15") == null
        NewsKey.extractDateFromPk("2024-01-15") == null
    }

    def "extractDateFromPk: 유효한 PK에서 날짜 추출"() {
        given:
        def date = "2024-01-15"
        def pk = NewsKey.newsPk(date)

        expect:
        NewsKey.extractDateFromPk(pk) == date
    }

    // ==================== Key Composition Tests ====================

    def "newsPk와 extractDateFromPk는 역함수 관계"() {
        given:
        def originalDate = "2024-06-15"

        when:
        def pk = NewsKey.newsPk(originalDate)
        def extractedDate = NewsKey.extractDateFromPk(pk)

        then:
        extractedDate == originalDate
    }
}
