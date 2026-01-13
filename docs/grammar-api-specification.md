# Grammar API 명세서

## 서비스 개요

영어 문법 체크 및 AI 대화 연습 서비스입니다.

### 주요 기능
| 기능 | 설명 |
|------|------|
| **문법 체크** | 사용자가 입력한 영어 문장의 문법 오류를 분석하고 교정 |
| **AI 대화 연습** | AI와 1:1 영어 대화 연습 (문법 체크 + 대화 응답 + 학습 팁) |
| **세션 관리** | 대화 세션 목록 조회, 상세 조회, 삭제 |

### 레벨 시스템
| 레벨 | 설명 |
|------|------|
| `BEGINNER` | 초급 - 한국어 번역 포함, 쉬운 설명 |
| `INTERMEDIATE` | 중급 - 영어 위주 설명 |
| `ADVANCED` | 고급 - 상세한 문법 규칙 설명 |

---

## Base URL

```
https://gc8l9ijhzc.execute-api.ap-northeast-2.amazonaws.com/dev
```

## 인증

모든 API는 **Cognito 인증**이 필요합니다.

```
Authorization: Bearer {ID_TOKEN}
```

---

## API 목록

| Method | Endpoint | 설명 |
|--------|----------|------|
| POST | `/grammar/check` | 문법 체크 |
| POST | `/grammar/conversation` | AI 대화 연습 |
| GET | `/grammar/sessions` | 세션 목록 조회 |
| GET | `/grammar/sessions/{sessionId}` | 세션 상세 조회 |
| DELETE | `/grammar/sessions/{sessionId}` | 세션 삭제 |

---

## 1. 문법 체크 API

영어 문장의 문법 오류를 분석하고 교정합니다.

### Request

```
POST /grammar/check
Content-Type: application/json
Authorization: Bearer {TOKEN}
```

**Body:**
```json
{
  "sentence": "I goed to school yesterday.",
  "level": "BEGINNER"
}
```

| 필드 | 타입 | 필수 | 설명 |
|------|------|------|------|
| sentence | string | ✅ | 검사할 영어 문장 |
| level | string | ❌ | 레벨 (BEGINNER/INTERMEDIATE/ADVANCED), 기본값: BEGINNER |

### Response (성공)

```json
{
  "isSuccess": true,
  "message": "Grammar checked successfully",
  "data": {
    "originalSentence": "I goed to school yesterday.",
    "correctedSentence": "I went to school yesterday.",
    "score": 80,
    "isCorrect": false,
    "errors": [
      {
        "type": "VERB_TENSE",
        "original": "goed",
        "corrected": "went",
        "explanation": "The verb 'go' in the past tense should be 'went'. In Korean, this would be '갔어'.",
        "startIndex": 2,
        "endIndex": 6
      }
    ],
    "feedback": "Good try! The past tense of 'go' is 'went'. Keep practicing!"
  }
}
```

| 필드 | 타입 | 설명 |
|------|------|------|
| originalSentence | string | 원본 문장 |
| correctedSentence | string | 교정된 문장 |
| score | number | 문법 점수 (0-100) |
| isCorrect | boolean | 문법 오류 없음 여부 |
| errors | array | 오류 목록 |
| feedback | string | 전체 피드백 메시지 |

### Error Types (오류 타입)

| 타입 | 한국어 | 설명 |
|------|--------|------|
| VERB_TENSE | 동사 시제 | 시제 오류 |
| SUBJECT_VERB_AGREEMENT | 주어-동사 일치 | 주어와 동사 수 일치 오류 |
| ARTICLE | 관사 | a/an/the 오류 |
| PREPOSITION | 전치사 | 전치사 오류 |
| WORD_ORDER | 어순 | 단어 순서 오류 |
| PLURAL_SINGULAR | 단/복수 | 단수/복수 오류 |
| PRONOUN | 대명사 | 대명사 오류 |
| SPELLING | 철자 | 철자 오류 |
| PUNCTUATION | 구두점 | 구두점 오류 |
| WORD_CHOICE | 어휘 선택 | 단어 선택 오류 |
| SENTENCE_STRUCTURE | 문장 구조 | 문장 구조 오류 |
| OTHER | 기타 | 기타 오류 |

---

## 2. AI 대화 연습 API

AI와 대화하면서 영어를 연습합니다. 사용자의 메시지에 대해 문법 체크 + AI 응답 + 학습 팁을 제공합니다.

### Request

```
POST /grammar/conversation
Content-Type: application/json
Authorization: Bearer {TOKEN}
```

**Body:**
```json
{
  "sessionId": "550e8400-e29b-41d4-a716-446655440000",
  "message": "I wants to learn English. Can you help me?",
  "level": "BEGINNER"
}
```

| 필드 | 타입 | 필수 | 설명 |
|------|------|------|------|
| sessionId | string | ❌ | 세션 ID (없으면 새 세션 생성) |
| message | string | ✅ | 사용자 메시지 |
| level | string | ❌ | 레벨, 기본값: BEGINNER |

### Response (성공)

```json
{
  "isSuccess": true,
  "message": "Conversation generated successfully",
  "data": {
    "sessionId": "550e8400-e29b-41d4-a716-446655440000",
    "grammarCheck": {
      "originalSentence": "I wants to learn English. Can you help me?",
      "correctedSentence": "I want to learn English. Can you help me?",
      "score": 90,
      "isCorrect": false,
      "errors": [
        {
          "type": "SUBJECT_VERB_AGREEMENT",
          "original": "wants",
          "corrected": "want",
          "explanation": "With 'I', use 'want' not 'wants'. 'Wants' is for he/she/it."
        }
      ],
      "feedback": "Small mistake with subject-verb agreement. Keep going!"
    },
    "aiResponse": "Of course! I'd be happy to help you learn English. What would you like to practice today? We can talk about any topic you're interested in.",
    "conversationTip": "Try to use simple sentences first. For example: 'I like music.' or 'I want to travel.'"
  }
}
```

| 필드 | 타입 | 설명 |
|------|------|------|
| sessionId | string | 세션 ID (다음 요청에 포함하면 대화 이어가기) |
| grammarCheck | object | 문법 체크 결과 (위 문법 체크 API 응답과 동일) |
| aiResponse | string | AI의 대화 응답 |
| conversationTip | string | 학습 팁 |

### 대화 이어가기

세션 ID를 포함하면 이전 대화 컨텍스트를 유지하며 대화를 이어갑니다.

```json
{
  "sessionId": "550e8400-e29b-41d4-a716-446655440000",
  "message": "I like to watch movies.",
  "level": "BEGINNER"
}
```

---

## 3. 세션 목록 조회 API

사용자의 대화 세션 목록을 조회합니다.

### Request

```
GET /grammar/sessions?limit=10&cursor={cursor}
Authorization: Bearer {TOKEN}
```

| 파라미터 | 타입 | 필수 | 설명 |
|----------|------|------|------|
| limit | number | ❌ | 조회 개수 (기본: 10, 최대: 50) |
| cursor | string | ❌ | 페이지네이션 커서 |

### Response (성공)

```json
{
  "isSuccess": true,
  "message": "Sessions retrieved successfully",
  "data": {
    "sessions": [
      {
        "sessionId": "550e8400-e29b-41d4-a716-446655440000",
        "level": "BEGINNER",
        "topic": null,
        "messageCount": 5,
        "lastMessage": "I like to watch movies.",
        "createdAt": "2026-01-13T10:30:00Z",
        "updatedAt": "2026-01-13T11:00:00Z"
      }
    ],
    "nextCursor": "eyJQSyI6IkdTRVNT...",
    "hasMore": true
  }
}
```

---

## 4. 세션 상세 조회 API

특정 세션의 상세 정보와 대화 기록을 조회합니다.

### Request

```
GET /grammar/sessions/{sessionId}?messageLimit=50
Authorization: Bearer {TOKEN}
```

| 파라미터 | 타입 | 필수 | 설명 |
|----------|------|------|------|
| sessionId | path | ✅ | 세션 ID |
| messageLimit | query | ❌ | 메시지 조회 개수 (기본: 50, 최대: 100) |

### Response (성공)

```json
{
  "isSuccess": true,
  "message": "Session detail retrieved successfully",
  "data": {
    "session": {
      "sessionId": "550e8400-e29b-41d4-a716-446655440000",
      "level": "BEGINNER",
      "messageCount": 5,
      "createdAt": "2026-01-13T10:30:00Z",
      "updatedAt": "2026-01-13T11:00:00Z"
    },
    "messages": [
      {
        "messageId": "msg-001",
        "role": "USER",
        "content": "I wants to learn English.",
        "correctedContent": "I want to learn English.",
        "grammarScore": 90,
        "createdAt": "2026-01-13T10:30:00Z"
      },
      {
        "messageId": "msg-002",
        "role": "ASSISTANT",
        "content": "Of course! I'd be happy to help you learn English.",
        "createdAt": "2026-01-13T10:30:05Z"
      }
    ]
  }
}
```

---

## 5. 세션 삭제 API

특정 세션을 삭제합니다.

### Request

```
DELETE /grammar/sessions/{sessionId}
Authorization: Bearer {TOKEN}
```

### Response (성공)

```json
{
  "isSuccess": true,
  "message": "Session deleted successfully",
  "data": null
}
```

---

## 에러 응답

### 공통 에러 형식

```json
{
  "code": "GRAMMAR.GRAMMAR_001",
  "message": "유효하지 않은 문장입니다",
  "status": 400,
  "details": {
    "sentence": ""
  }
}
```

### 에러 코드 목록

| 코드 | 메시지 | HTTP Status |
|------|--------|-------------|
| GRAMMAR_001 | 유효하지 않은 문장입니다 | 400 |
| GRAMMAR_002 | 문법 체크에 실패했습니다 | 500 |
| GRAMMAR_003 | 유효하지 않은 레벨입니다 | 400 |
| GRAMMAR_004 | AI 서비스 호출에 실패했습니다 | 502 |
| GRAMMAR_005 | AI 응답 파싱에 실패했습니다 | 500 |
| GRAMMAR_006 | 세션을 찾을 수 없습니다 | 404 |
| GRAMMAR_007 | 세션이 만료되었습니다 | 410 |

---

## 사용 시나리오

### 시나리오 1: 문법 체크만 사용

```
1. POST /grammar/check - 문장 검사
2. 결과 표시 (오류, 교정, 피드백)
```

### 시나리오 2: AI 대화 연습

```
1. POST /grammar/conversation (sessionId 없이) - 새 대화 시작
2. 응답에서 sessionId 저장
3. POST /grammar/conversation (sessionId 포함) - 대화 이어가기
4. 반복...
```

### 시나리오 3: 대화 기록 관리

```
1. GET /grammar/sessions - 세션 목록 조회
2. GET /grammar/sessions/{id} - 특정 세션 대화 기록 조회
3. DELETE /grammar/sessions/{id} - 세션 삭제
```

---

## UI 구현 참고사항

### 문법 체크 결과 표시
- `errors` 배열의 `startIndex`, `endIndex`를 사용하여 원문에서 오류 부분 하이라이트
- `score`로 점수 표시 (프로그레스 바 등)
- `isCorrect`가 true면 "Perfect!" 메시지 표시

### 대화 UI
- 채팅 형식 UI 권장
- 사용자 메시지 위에 문법 체크 결과 표시 (말풍선 위 작은 배지 등)
- AI 응답 아래에 `conversationTip` 표시

### 레벨 선택
- 처음 사용자는 BEGINNER로 시작
- 설정에서 레벨 변경 가능하게 구현

---

## 연락처

백엔드 관련 문의: [담당자 연락처]
