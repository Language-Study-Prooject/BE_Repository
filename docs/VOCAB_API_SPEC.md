# 단어 암기 서비스 API 명세서

## 개요
영어 단어 암기 학습을 위한 백엔드 API입니다.
- **Base URL**: `https://gc8l9ijhzc.execute-api.ap-northeast-2.amazonaws.com/dev`
- **Content-Type**: `application/json`

---

## 핵심 기능

| 기능 | 설명 |
|------|------|
| 일일 학습 | 매일 55개 단어 (새 단어 50개 + 복습 5개) |
| Spaced Repetition | SM-2 알고리즘 기반 최적 복습 주기 |
| 시험 | 학습한 단어 테스트 및 성적 기록 |
| TTS | Polly 기반 발음 듣기 (남성/여성) |
| 약점 분석 | 틀린 단어, 카테고리별 정확도 분석 |

---

## 1. 단어 관리 API

### 1.1 단어 목록 조회
```
GET /vocab/words
```

**Query Parameters**
| 파라미터 | 타입 | 필수 | 설명 |
|---------|------|------|------|
| level | string | N | `BEGINNER`, `INTERMEDIATE`, `ADVANCED` |
| category | string | N | `DAILY`, `BUSINESS`, `ACADEMIC` |
| limit | number | N | 페이지 크기 (기본: 20, 최대: 50) |
| cursor | string | N | 페이지네이션 커서 |

**Response**
```json
{
  "success": true,
  "message": "Words retrieved",
  "data": {
    "words": [
      {
        "wordId": "uuid",
        "english": "apple",
        "korean": "사과",
        "example": "I eat an apple every day.",
        "level": "BEGINNER",
        "category": "DAILY"
      }
    ],
    "nextCursor": "base64-encoded-cursor",
    "hasMore": true
  }
}
```

---

### 1.2 단어 검색
```
GET /vocab/words/search
```

**Query Parameters**
| 파라미터 | 타입 | 필수 | 설명 |
|---------|------|------|------|
| q | string | Y | 검색어 (영어/한국어 모두 가능) |
| limit | number | N | 결과 개수 (기본: 20) |
| cursor | string | N | 페이지네이션 커서 |

**Response**
```json
{
  "success": true,
  "message": "Search completed",
  "data": {
    "words": [...],
    "query": "apple",
    "nextCursor": null,
    "hasMore": false
  }
}
```

---

### 1.3 단어 상세 조회
```
GET /vocab/words/{wordId}
```

**Response**
```json
{
  "success": true,
  "message": "Word retrieved",
  "data": {
    "wordId": "uuid",
    "english": "apple",
    "korean": "사과",
    "example": "I eat an apple every day.",
    "level": "BEGINNER",
    "category": "DAILY",
    "createdAt": "2024-01-07T12:00:00Z"
  }
}
```

---

## 2. 일일 학습 API

### 2.1 오늘의 학습 단어 조회
```
GET /vocab/daily/{userId}
```

**설명**: 오늘 학습할 55개 단어를 반환합니다.
- 새 단어 50개 (아직 학습하지 않은 단어)
- 복습 단어 5개 (Spaced Repetition 기반)

**Response**
```json
{
  "success": true,
  "message": "Daily words retrieved",
  "data": {
    "date": "2024-01-07",
    "userId": "user123",
    "totalWords": 55,
    "learnedCount": 10,
    "isCompleted": false,
    "newWords": [
      {
        "wordId": "uuid",
        "english": "apple",
        "korean": "사과",
        "example": "I eat an apple every day."
      }
    ],
    "reviewWords": [
      {
        "wordId": "uuid",
        "english": "book",
        "korean": "책",
        "lastReviewedAt": "2024-01-05",
        "correctCount": 3,
        "incorrectCount": 1
      }
    ]
  }
}
```

---

### 2.2 단어 학습 완료 표시
```
POST /vocab/daily/{userId}/words/{wordId}/learned
```

**Request Body**
```json
{
  "isCorrect": true
}
```

**Response**
```json
{
  "success": true,
  "message": "Word marked as learned",
  "data": {
    "wordId": "uuid",
    "status": "LEARNING",
    "nextReviewAt": "2024-01-08"
  }
}
```

---

## 3. 사용자 단어 학습 상태 API

### 3.1 학습 상태 조회
```
GET /vocab/users/{userId}/words
```

**Query Parameters**
| 파라미터 | 타입 | 필수 | 설명 |
|---------|------|------|------|
| status | string | N | `NEW`, `LEARNING`, `REVIEWING`, `MASTERED` |
| limit | number | N | 페이지 크기 (기본: 20) |
| cursor | string | N | 페이지네이션 커서 |

**Response**
```json
{
  "success": true,
  "message": "User words retrieved",
  "data": {
    "userWords": [
      {
        "wordId": "uuid",
        "userId": "user123",
        "status": "LEARNING",
        "correctCount": 5,
        "incorrectCount": 2,
        "interval": 6,
        "nextReviewAt": "2024-01-13",
        "lastReviewedAt": "2024-01-07",
        "bookmarked": true,
        "favorite": false,
        "difficulty": "HARD"
      }
    ],
    "nextCursor": null,
    "hasMore": false
  }
}
```

**학습 상태 설명**
| 상태 | 설명 |
|------|------|
| `NEW` | 아직 학습하지 않음 |
| `LEARNING` | 학습 중 (1-2회 정답) |
| `REVIEWING` | 복습 단계 (2-4회 정답) |
| `MASTERED` | 완전 암기 (5회 이상 연속 정답) |

---

### 3.2 학습 결과 업데이트 (정답/오답)
```
PUT /vocab/users/{userId}/words/{wordId}
```

**Request Body**
```json
{
  "isCorrect": true
}
```

**Response**
```json
{
  "success": true,
  "message": "UserWord updated",
  "data": {
    "wordId": "uuid",
    "status": "REVIEWING",
    "interval": 6,
    "easeFactor": 2.5,
    "repetitions": 3,
    "nextReviewAt": "2024-01-13",
    "correctCount": 6,
    "incorrectCount": 2
  }
}
```

**Spaced Repetition 알고리즘 (SM-2)**
- 정답 시: `interval` 증가 (1일 → 6일 → 이전간격 × easeFactor)
- 오답 시: `interval = 1`, `easeFactor` 감소 (최소 1.3)
- 5회 연속 정답 시 `MASTERED` 상태

---

### 3.3 단어 태그 변경 (북마크/즐겨찾기/난이도)
```
PUT /vocab/users/{userId}/words/{wordId}/tag
```

**Request Body**
```json
{
  "bookmarked": true,
  "favorite": false,
  "difficulty": "HARD"
}
```

| 필드 | 타입 | 설명 |
|------|------|------|
| bookmarked | boolean | 북마크 여부 |
| favorite | boolean | 즐겨찾기 여부 |
| difficulty | string | `EASY`, `NORMAL`, `HARD` |

**Response**
```json
{
  "success": true,
  "message": "Tag updated",
  "data": {
    "wordId": "uuid",
    "bookmarked": true,
    "favorite": false,
    "difficulty": "HARD"
  }
}
```

---

## 4. 시험 API

### 4.1 시험 시작
```
POST /vocab/test/{userId}/start
```

**Request Body**
```json
{
  "wordCount": 20,
  "level": "BEGINNER",
  "type": "KOREAN_TO_ENGLISH"
}
```

| 필드 | 타입 | 필수 | 설명 |
|------|------|------|------|
| wordCount | number | N | 문제 수 (기본: 20) |
| level | string | N | 출제 레벨 필터 |
| type | string | N | `KOREAN_TO_ENGLISH`, `ENGLISH_TO_KOREAN` |

**Response**
```json
{
  "success": true,
  "message": "Test started",
  "data": {
    "testId": "uuid",
    "startedAt": "2024-01-07T12:00:00Z",
    "wordCount": 20,
    "questions": [
      {
        "questionId": 1,
        "wordId": "uuid",
        "question": "사과",
        "options": ["apple", "banana", "orange", "grape"],
        "type": "KOREAN_TO_ENGLISH"
      }
    ]
  }
}
```

---

### 4.2 답안 제출
```
POST /vocab/test/{userId}/submit
```

**Request Body**
```json
{
  "testId": "uuid",
  "answers": [
    {"questionId": 1, "wordId": "uuid", "answer": "apple"},
    {"questionId": 2, "wordId": "uuid", "answer": "book"}
  ]
}
```

**Response**
```json
{
  "success": true,
  "message": "Test submitted",
  "data": {
    "testId": "uuid",
    "totalQuestions": 20,
    "correctCount": 18,
    "incorrectCount": 2,
    "successRate": 90.0,
    "results": [
      {
        "questionId": 1,
        "wordId": "uuid",
        "isCorrect": true,
        "userAnswer": "apple",
        "correctAnswer": "apple"
      },
      {
        "questionId": 5,
        "wordId": "uuid",
        "isCorrect": false,
        "userAnswer": "banana",
        "correctAnswer": "apple"
      }
    ],
    "completedAt": "2024-01-07T12:15:00Z"
  }
}
```

---

### 4.3 시험 결과 조회
```
GET /vocab/test/{userId}/results
```

**Query Parameters**
| 파라미터 | 타입 | 필수 | 설명 |
|---------|------|------|------|
| limit | number | N | 결과 개수 (기본: 20) |
| cursor | string | N | 페이지네이션 커서 |

**Response**
```json
{
  "success": true,
  "message": "Test results retrieved",
  "data": {
    "testResults": [
      {
        "testId": "uuid",
        "totalQuestions": 20,
        "correctCount": 18,
        "successRate": 90.0,
        "completedAt": "2024-01-07T12:15:00Z"
      }
    ],
    "nextCursor": null,
    "hasMore": false
  }
}
```

---

## 5. 통계 API

### 5.1 전체 학습 통계
```
GET /vocab/stats/{userId}
```

**Response**
```json
{
  "success": true,
  "message": "Stats retrieved",
  "data": {
    "totalWords": 150,
    "wordStatusCounts": {
      "NEW": 50,
      "LEARNING": 40,
      "REVIEWING": 35,
      "MASTERED": 25
    },
    "totalCorrect": 500,
    "totalIncorrect": 100,
    "accuracy": 83.3,
    "testCount": 15,
    "avgSuccessRate": 85.5,
    "studyDays": 30,
    "completedDays": 25,
    "completionRate": 83.3
  }
}
```

---

### 5.2 일별 학습 통계
```
GET /vocab/stats/{userId}/daily
```

**Query Parameters**
| 파라미터 | 타입 | 필수 | 설명 |
|---------|------|------|------|
| limit | number | N | 조회 일수 (기본: 30, 최대: 90) |

**Response**
```json
{
  "success": true,
  "message": "Daily stats retrieved",
  "data": {
    "dailyStats": [
      {
        "date": "2024-01-07",
        "totalWords": 55,
        "learnedCount": 55,
        "isCompleted": true,
        "progress": 100.0
      },
      {
        "date": "2024-01-06",
        "totalWords": 55,
        "learnedCount": 40,
        "isCompleted": false,
        "progress": 72.7
      }
    ],
    "nextCursor": null,
    "hasMore": false
  }
}
```

---

### 5.3 약점 분석
```
GET /vocab/stats/{userId}/weakness
```

**Response**
```json
{
  "success": true,
  "message": "Weakness analysis completed",
  "data": {
    "weakestWords": [
      {
        "wordId": "uuid",
        "english": "hypothesis",
        "korean": "가설",
        "level": "ADVANCED",
        "category": "ACADEMIC",
        "incorrectCount": 5,
        "correctCount": 2,
        "accuracy": 28.6,
        "status": "LEARNING"
      }
    ],
    "categoryAnalysis": {
      "DAILY": {
        "totalCorrect": 200,
        "totalIncorrect": 30,
        "wordCount": 50,
        "accuracy": 87.0
      },
      "BUSINESS": {
        "totalCorrect": 150,
        "totalIncorrect": 50,
        "wordCount": 40,
        "accuracy": 75.0
      },
      "ACADEMIC": {
        "totalCorrect": 80,
        "totalIncorrect": 40,
        "wordCount": 30,
        "accuracy": 66.7
      }
    },
    "levelAnalysis": {
      "BEGINNER": {"accuracy": 90.0, "wordCount": 50},
      "INTERMEDIATE": {"accuracy": 78.0, "wordCount": 40},
      "ADVANCED": {"accuracy": 65.0, "wordCount": 30}
    },
    "suggestions": [
      "ACADEMIC 카테고리의 정확도가 66.7%로 가장 낮습니다. 집중 학습을 권장합니다.",
      "ADVANCED 레벨의 정확도가 65.0%입니다. 이 레벨의 단어들을 더 복습해보세요.",
      "자주 틀리는 단어 10개가 있습니다. 북마크하여 집중 복습하세요."
    ]
  }
}
```

---

## 6. 음성 API (TTS)

### 6.1 단어 발음 듣기
```
POST /vocab/voice/synthesize
```

**Request Body**
```json
{
  "wordId": "uuid",
  "text": "apple",
  "voice": "FEMALE"
}
```

| 필드 | 타입 | 필수 | 설명 |
|------|------|------|------|
| wordId | string | Y | 단어 ID (캐시 키로 사용) |
| text | string | Y | 발음할 텍스트 |
| voice | string | N | `MALE` (Matthew), `FEMALE` (Joanna, 기본값) |

**Response**
```json
{
  "success": true,
  "message": "Speech synthesized",
  "data": {
    "audioUrl": "https://s3.ap-northeast-2.amazonaws.com/...",
    "s3Key": "vocab/voice/uuid_female.mp3",
    "cached": true
  }
}
```

**참고**:
- `audioUrl`은 1시간 동안 유효한 Pre-signed URL입니다.
- 동일 단어+음성 조합은 S3에 캐시되어 재사용됩니다.

---

## 에러 응답 형식

```json
{
  "success": false,
  "error": "에러 메시지"
}
```

**HTTP 상태 코드**
| 코드 | 설명 |
|------|------|
| 200 | 성공 |
| 201 | 생성 성공 |
| 400 | 잘못된 요청 (필수 파라미터 누락 등) |
| 404 | 리소스 없음 |
| 500 | 서버 에러 |

---

## 프론트엔드 구현 가이드

### 추천 화면 구성

1. **메인 대시보드**
   - 오늘의 학습 진행률 (GET /vocab/daily/{userId})
   - 전체 통계 요약 (GET /vocab/stats/{userId})

2. **일일 학습 화면**
   - 플래시카드 UI
   - 정답/오답 버튼 → PUT /vocab/users/{userId}/words/{wordId}
   - TTS 발음 듣기 버튼 → POST /vocab/voice/synthesize

3. **시험 화면**
   - 시험 시작 → POST /vocab/test/{userId}/start
   - 4지선다 문제 표시
   - 답안 제출 → POST /vocab/test/{userId}/submit
   - 결과 화면 (정답/오답 표시)

4. **단어장 화면**
   - 전체 단어 목록 (GET /vocab/words)
   - 검색 기능 (GET /vocab/words/search)
   - 북마크/즐겨찾기 토글 (PUT /vocab/users/{userId}/words/{wordId}/tag)

5. **통계/분석 화면**
   - 학습 달력 (일별 완료 여부)
   - 약점 분석 차트 (GET /vocab/stats/{userId}/weakness)
   - 레벨/카테고리별 정확도 그래프

### 상태 관리 추천
- userId는 로그인 후 전역 상태로 관리
- 일일 학습 단어 목록은 세션 캐시
- 북마크/즐겨찾기는 낙관적 업데이트

---

## 테스트 데이터

현재 등록된 시드 데이터:
- **BEGINNER + DAILY**: 25개 (apple, book, cat, ...)
- **INTERMEDIATE + BUSINESS**: 25개 (achieve, benefit, ...)
- **ADVANCED + ACADEMIC**: 19개 (abstract, hypothesis, ...)

총 **69개 단어** 등록됨
