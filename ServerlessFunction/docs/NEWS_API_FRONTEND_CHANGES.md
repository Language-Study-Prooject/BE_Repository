# News API 프론트엔드 변경사항

> 마지막 업데이트: 2025-01-23

## 목차
1. [기사 목록 조회 API 변경](#1-기사-목록-조회-api-변경)
2. [기사 상세 조회 API 변경](#2-기사-상세-조회-api-변경)
3. [키워드 필드 추가](#3-키워드-필드-추가)
4. [인증 필수 엔드포인트](#4-인증-필수-엔드포인트)
5. [API 응답 예시](#5-api-응답-예시)

---

## 1. 기사 목록 조회 API 변경

### 영향받는 엔드포인트
- `GET /news` - 뉴스 목록 조회
- `GET /news/today` - 오늘의 뉴스 조회
- `GET /news/recommended` - 추천 뉴스 조회

### 변경사항
각 기사 객체에 `isBookmarked` 필드가 추가되었습니다.

| 필드 | 타입 | 설명 |
|------|------|------|
| `isBookmarked` | boolean | 현재 사용자가 해당 기사를 북마크했는지 여부 |

### 주의사항
- **로그인한 사용자**: 실제 북마크 상태 반환
- **비로그인 사용자**: 모든 기사에 `false` 반환

### 기존 응답 (변경 전)
```json
{
  "articles": [
    {
      "articleId": "abc123",
      "title": "...",
      "summary": "...",
      "category": "TECH",
      "level": "INTERMEDIATE"
    }
  ]
}
```

### 새 응답 (변경 후)
```json
{
  "articles": [
    {
      "articleId": "abc123",
      "title": "...",
      "summary": "...",
      "category": "TECH",
      "level": "INTERMEDIATE",
      "cefrLevel": "B1",
      "isBookmarked": true
    }
  ]
}
```

---

## 2. 기사 상세 조회 API 변경

### 영향받는 엔드포인트
- `GET /news/{articleId}` - 기사 상세 조회

### 변경사항
응답에 `isBookmarked`와 `isRead` 필드가 추가되었습니다.

| 필드 | 타입 | 설명 |
|------|------|------|
| `isBookmarked` | boolean | 현재 사용자가 해당 기사를 북마크했는지 여부 |
| `isRead` | boolean | 현재 사용자가 해당 기사를 읽었는지 여부 |

### 새 응답 형식
```json
{
  "success": true,
  "message": "뉴스 조회 성공",
  "data": {
    "article": {
      "articleId": "abc123",
      "title": "Tech Giants Report Strong Quarterly Earnings",
      "summary": "Major technology companies...",
      "category": "TECH",
      "level": "INTERMEDIATE",
      "cefrLevel": "B1",
      "keywords": [...],
      "highlightWords": ["earnings", "revenue", "growth"],
      "quiz": [...]
    },
    "isBookmarked": true,
    "isRead": false
  }
}
```

---

## 3. 키워드 필드 추가

### 변경사항
`keywords` 배열의 각 키워드 객체에 `meaningKo` (한국어 뜻) 필드가 추가되었습니다.

### 키워드 객체 구조

| 필드 | 타입 | 설명 |
|------|------|------|
| `word` | string | 영어 단어 |
| `meaning` | string | 영어 정의 (간단한 설명) |
| `meaningKo` | string | **[신규]** 한국어 뜻 |
| `example` | string | 기사에서 발췌한 예문 |

### 키워드 예시
```json
{
  "keywords": [
    {
      "word": "economy",
      "meaning": "the system of trade and industry",
      "meaningKo": "경제",
      "example": "The economy is growing steadily."
    },
    {
      "word": "revenue",
      "meaning": "income, especially of a company",
      "meaningKo": "수익",
      "example": "The company reported record revenue."
    }
  ]
}
```

### 프론트엔드 활용
- 단어장 기능에서 한국어 뜻 표시
- 학습 카드에 영어/한국어 뜻 모두 표시 가능

---

## 4. 인증 필수 엔드포인트

다음 엔드포인트들은 Cognito 인증 토큰이 필요합니다.

### 인증 필수 (Authorization 헤더 필요)
| 메서드 | 엔드포인트 | 설명 |
|--------|------------|------|
| GET | `/news/stats` | 뉴스 학습 통계 조회 |
| GET | `/news/bookmarks` | 북마크 목록 조회 |
| GET | `/news/words` | 수집 단어 목록 조회 |
| GET | `/news/quiz/history` | 퀴즈 기록 조회 |
| POST | `/news/{articleId}/read` | 읽기 완료 기록 |
| POST | `/news/{articleId}/bookmark` | 북마크 토글 |
| GET | `/news/{articleId}/quiz` | 퀴즈 조회 |
| POST | `/news/{articleId}/quiz` | 퀴즈 제출 |
| POST | `/news/{articleId}/words` | 단어 수집 |
| DELETE | `/news/{articleId}/words/{word}` | 단어 삭제 |
| POST | `/news/words/{word}/sync` | 단어 Vocabulary 연동 |

### 인증 선택 (토큰 있으면 개인화된 응답)
| 메서드 | 엔드포인트 | 설명 |
|--------|------------|------|
| GET | `/news` | 뉴스 목록 (북마크 상태 포함) |
| GET | `/news/today` | 오늘의 뉴스 (북마크 상태 포함) |
| GET | `/news/recommended` | 추천 뉴스 (북마크 상태 포함) |
| GET | `/news/{articleId}` | 기사 상세 (북마크/읽기 상태 포함) |

### 요청 헤더 예시
```
Authorization: Bearer eyJraWQiOiJ...
```

---

## 5. API 응답 예시

### 기사 목록 조회 (GET /news)
```json
{
  "success": true,
  "message": "뉴스 목록 조회 성공",
  "data": {
    "articles": [
      {
        "articleId": "news_20250123_001",
        "title": "Global Tech Summit Addresses AI Regulation",
        "summary": "World leaders gathered to discuss...",
        "source": "Reuters",
        "publishedAt": "2025-01-23T09:00:00Z",
        "category": "TECH",
        "level": "INTERMEDIATE",
        "cefrLevel": "B1",
        "imageUrl": "https://...",
        "readCount": 150,
        "keywords": [
          {
            "word": "regulation",
            "meaning": "official rules made by a government",
            "meaningKo": "규제",
            "example": "New AI regulation will take effect next year."
          }
        ],
        "highlightWords": ["regulation", "summit", "artificial intelligence"],
        "isBookmarked": false
      }
    ],
    "nextCursor": "eyJwayI6Ik5FV1MjMjAyNS0wMS0yMyIsInNrIjoiQVJUSUNMRSMxMjM0NSJ9",
    "hasMore": true,
    "count": 10
  }
}
```

### 기사 상세 조회 (GET /news/{articleId})
```json
{
  "success": true,
  "message": "뉴스 조회 성공",
  "data": {
    "article": {
      "articleId": "news_20250123_001",
      "title": "Global Tech Summit Addresses AI Regulation",
      "summary": "World leaders gathered to discuss the future of artificial intelligence...",
      "source": "Reuters",
      "publishedAt": "2025-01-23T09:00:00Z",
      "category": "TECH",
      "level": "INTERMEDIATE",
      "cefrLevel": "B1",
      "imageUrl": "https://...",
      "readCount": 151,
      "keywords": [
        {
          "word": "regulation",
          "meaning": "official rules made by a government",
          "meaningKo": "규제",
          "example": "New AI regulation will take effect next year."
        },
        {
          "word": "summit",
          "meaning": "an important meeting between leaders",
          "meaningKo": "정상회담",
          "example": "The summit brought together leaders from 50 countries."
        }
      ],
      "highlightWords": ["regulation", "summit", "artificial intelligence"],
      "quiz": [
        {
          "questionId": "q1",
          "type": "COMPREHENSION",
          "question": "What is the main topic of this article?",
          "options": ["AI regulation", "Climate change", "Economic policy", "Healthcare"],
          "points": 20
        },
        {
          "questionId": "q2",
          "type": "WORD_MATCH",
          "question": "What does 'regulation' mean in this context?",
          "options": ["Official rules", "Technology", "Meeting", "Country"],
          "points": 15
        },
        {
          "questionId": "q3",
          "type": "FILL_BLANK",
          "question": "World leaders gathered at the _____ to discuss AI.",
          "options": ["summit", "office", "factory", "school"],
          "points": 30
        }
      ]
    },
    "isBookmarked": true,
    "isRead": false
  }
}
```

---

## 프론트엔드 체크리스트

### 기사 목록 화면
- [ ] 각 기사 카드에 북마크 아이콘 표시 (`isBookmarked` 활용)
- [ ] 북마크된 기사는 다른 색상/아이콘으로 구분

### 기사 상세 화면
- [ ] 북마크 버튼 상태 초기화 (`isBookmarked` 활용)
- [ ] 읽기 완료 표시 (`isRead` 활용)
- [ ] 키워드 목록에 한국어 뜻 표시 (`meaningKo` 활용)

### 단어장/학습 카드
- [ ] 한국어 뜻 표시 기능 추가
- [ ] 영어/한국어 토글 기능 (선택사항)

### 인증
- [ ] 필수 인증 엔드포인트에 토큰 전송 확인
- [ ] 401 에러 처리 (로그인 페이지로 리다이렉트)

---

## 질문 및 문의

백엔드 관련 문의사항이 있으면 연락주세요.
