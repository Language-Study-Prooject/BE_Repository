# 프론트엔드 개발 가이드

## 개요

이 문서는 영어 학습 플랫폼 백엔드 API의 프론트엔드 개발 가이드입니다.

## 기본 정보

### Base URL
```
https://gc8l9ijhzc.execute-api.ap-northeast-2.amazonaws.com/dev
```

### 인증

모든 인증이 필요한 API는 Authorization 헤더에 JWT 토큰을 포함해야 합니다.

```typescript
const headers = {
  'Content-Type': 'application/json',
  'Authorization': `Bearer ${idToken}`
};
```

---

## Grammar API

### 1. 문법 검사 (Grammar Check)

단일 문장에 대한 문법 검사를 수행합니다.

**Endpoint:** `POST /grammar/check`

**Request:**
```typescript
interface GrammarCheckRequest {
  sentence: string;  // 검사할 문장
  level: 'BEGINNER' | 'INTERMEDIATE' | 'ADVANCED';  // 학습 레벨
}
```

**Response:**
```typescript
interface GrammarCheckResponse {
  originalSentence: string;    // 원본 문장
  correctedSentence: string;   // 교정된 문장
  score: number;               // 0-100 점수
  isCorrect: boolean;          // 문법 정확 여부
  errors: GrammarError[];      // 오류 목록
  feedback: string;            // 전체 피드백
}

interface GrammarError {
  type: GrammarErrorType;      // 오류 유형
  original: string;            // 원본 표현
  corrected: string;           // 교정된 표현
  explanation: string;         // 설명
  startIndex?: number;         // 원문에서의 시작 위치
  endIndex?: number;           // 원문에서의 끝 위치
}

type GrammarErrorType =
  | 'VERB_TENSE'           // 시제 오류
  | 'SUBJECT_VERB_AGREEMENT' // 주어-동사 일치
  | 'ARTICLE'              // 관사
  | 'PREPOSITION'          // 전치사
  | 'WORD_ORDER'           // 어순
  | 'PLURAL_SINGULAR'      // 단복수
  | 'PRONOUN'              // 대명사
  | 'SPELLING'             // 철자
  | 'PUNCTUATION'          // 구두점
  | 'WORD_CHOICE'          // 어휘 선택
  | 'SENTENCE_STRUCTURE'   // 문장 구조
  | 'OTHER';               // 기타
```

**예시:**
```typescript
const response = await fetch(`${BASE_URL}/grammar/check`, {
  method: 'POST',
  headers: {
    'Content-Type': 'application/json',
    'Authorization': `Bearer ${token}`
  },
  body: JSON.stringify({
    sentence: 'I go to school yesterday',
    level: 'BEGINNER'
  })
});

const result = await response.json();
// result.data:
// {
//   originalSentence: "I go to school yesterday",
//   correctedSentence: "I went to school yesterday",
//   score: 80,
//   isCorrect: false,
//   errors: [{
//     type: "VERB_TENSE",
//     original: "go",
//     corrected: "went",
//     explanation: "'yesterday'는 과거를 나타내므로 'go'를 과거형 'went'로 바꿔야 합니다.",
//     startIndex: 2,
//     endIndex: 4
//   }],
//   feedback: "시제에 주의하세요. 과거를 나타내는 'yesterday'와 함께 사용할 때는 과거형을 사용합니다."
// }
```

---

### 2. 대화 (Conversation) - 동기 방식

AI와 대화하면서 문법을 교정받습니다.

**Endpoint:** `POST /grammar/conversation`

**Request:**
```typescript
interface ConversationRequest {
  sessionId?: string;  // 세션 ID (없으면 새 세션 생성)
  message: string;     // 사용자 메시지
  level?: string;      // 'BEGINNER' | 'INTERMEDIATE' | 'ADVANCED' (기본값: BEGINNER)
}
```

**Response:**
```typescript
interface ConversationResponse {
  sessionId: string;               // 세션 ID
  grammarCheck: GrammarCheckResponse;  // 문법 검사 결과
  aiResponse: string;              // AI의 대화 응답
  conversationTip: string;         // 학습 팁
}
```

---

### 3. 대화 스트리밍 (Conversation Streaming) - WebSocket 방식

실시간으로 AI 응답을 받습니다. 동기 API보다 빠른 사용자 경험을 제공합니다.

**WebSocket Endpoint:** `wss://{websocket-api-id}.execute-api.ap-northeast-2.amazonaws.com/dev`

#### 인증

WebSocket 연결 시 JWT 토큰을 query parameter로 전달해야 합니다.

```
wss://{api-id}.execute-api.ap-northeast-2.amazonaws.com/dev?token={JWT_TOKEN}
```

- 토큰이 없거나 만료된 경우 연결이 거부됩니다 (401)
- 연결 성공 후에는 메시지에 userId를 포함할 필요 없음 (서버에서 자동 조회)

#### 연결 및 사용법

```typescript
// 1. WebSocket 연결 (JWT 토큰 포함)
const ws = new WebSocket(
  `wss://${WEBSOCKET_API_ID}.execute-api.ap-northeast-2.amazonaws.com/dev?token=${jwtToken}`
);

// 2. 연결 완료 시 메시지 전송 (userId 불필요)
ws.onopen = () => {
  const request = {
    action: 'grammarStreaming',  // 라우트 키
    sessionId: 'optional-session-id',
    message: 'I go to school yesterday',
    level: 'BEGINNER'
  };
  ws.send(JSON.stringify(request));
};

// 3. 스트리밍 이벤트 수신
ws.onmessage = (event) => {
  const data = JSON.parse(event.data);

  switch (data.type) {
    case 'start':
      // 스트리밍 시작
      console.log('Streaming started, sessionId:', data.sessionId);
      break;

    case 'token':
      // 실시간 토큰 수신 - UI에 점진적으로 표시
      appendToResponse(data.token);
      break;

    case 'complete':
      // 스트리밍 완료 - 최종 결과
      handleComplete(data);
      break;

    case 'error':
      // 에러 처리
      console.error('Streaming error:', data.message);
      break;
  }
};

ws.onerror = (error) => {
  console.error('WebSocket error:', error);
};

ws.onclose = () => {
  console.log('WebSocket closed');
};
```

#### 스트리밍 이벤트 타입

```typescript
// 시작 이벤트
interface StreamingStartEvent {
  type: 'start';
  sessionId: string;
}

// 토큰 이벤트 (실시간 텍스트 조각)
interface StreamingTokenEvent {
  type: 'token';
  token: string;  // 텍스트 조각
}

// 완료 이벤트
interface StreamingCompleteEvent {
  type: 'complete';
  sessionId: string;
  grammarCheck: GrammarCheckResponse;
  aiResponse: string;
  conversationTip: string;
}

// 에러 이벤트
interface StreamingErrorEvent {
  type: 'error';
  message: string;
}
```

#### 스트리밍 UI 구현 예시

```typescript
function GrammarChat() {
  const [response, setResponse] = useState('');
  const [isStreaming, setIsStreaming] = useState(false);
  const [result, setResult] = useState<StreamingCompleteEvent | null>(null);

  const handleSubmit = (message: string) => {
    setIsStreaming(true);
    setResponse('');
    setResult(null);

    const ws = new WebSocket(WEBSOCKET_URL);

    ws.onopen = () => {
      ws.send(JSON.stringify({
        action: 'grammarStreaming',
        message,
        userId,
        level: 'BEGINNER'
      }));
    };

    ws.onmessage = (event) => {
      const data = JSON.parse(event.data);

      if (data.type === 'token') {
        // 토큰을 점진적으로 추가하여 타이핑 효과
        setResponse(prev => prev + data.token);
      } else if (data.type === 'complete') {
        setResult(data);
        setIsStreaming(false);
        ws.close();
      } else if (data.type === 'error') {
        console.error(data.message);
        setIsStreaming(false);
        ws.close();
      }
    };
  };

  return (
    <div>
      {/* 실시간 응답 표시 */}
      <div className="response-container">
        {response}
        {isStreaming && <span className="cursor">|</span>}
      </div>

      {/* 최종 결과 표시 */}
      {result && (
        <div className="result">
          <GrammarCheckResult grammarCheck={result.grammarCheck} />
          <Tip>{result.conversationTip}</Tip>
        </div>
      )}
    </div>
  );
}
```

---

### 4. 세션 목록 조회

**Endpoint:** `GET /grammar/sessions`

**Query Parameters:**
- `limit`: 조회할 개수 (기본값: 10, 최대: 50)
- `cursor`: 페이지네이션 커서

**Response:**
```typescript
interface SessionListResponse {
  sessions: GrammarSession[];
  nextCursor: string | null;
  hasMore: boolean;
}

interface GrammarSession {
  sessionId: string;
  level: string;
  topic: string;
  messageCount: number;
  lastMessage: string;
  createdAt: string;
  updatedAt: string;
}
```

---

### 5. 세션 상세 조회

**Endpoint:** `GET /grammar/sessions/{sessionId}`

**Query Parameters:**
- `messageLimit`: 메시지 조회 개수 (기본값: 50, 최대: 100)

**Response:**
```typescript
interface SessionDetailResponse {
  session: GrammarSession;
  messages: GrammarMessage[];
}

interface GrammarMessage {
  messageId: string;
  role: 'USER' | 'ASSISTANT';
  content: string;
  correctedContent?: string;
  grammarScore?: number;
  createdAt: string;
}
```

---

### 6. 세션 삭제

**Endpoint:** `DELETE /grammar/sessions/{sessionId}`

**Response:**
```typescript
{
  status: 'success',
  message: 'Session deleted successfully'
}
```

---

## 에러 하이라이팅 구현

`startIndex`와 `endIndex`를 사용하여 원문에서 오류 위치를 하이라이팅할 수 있습니다.

```typescript
function highlightErrors(
  sentence: string,
  errors: GrammarError[]
): React.ReactNode[] {
  if (!errors.length) return [sentence];

  // 위치 정보가 있는 오류만 필터링
  const positionedErrors = errors
    .filter(e => e.startIndex != null && e.endIndex != null)
    .sort((a, b) => a.startIndex! - b.startIndex!);

  if (!positionedErrors.length) return [sentence];

  const parts: React.ReactNode[] = [];
  let lastIndex = 0;

  positionedErrors.forEach((error, i) => {
    // 오류 전 텍스트
    if (error.startIndex! > lastIndex) {
      parts.push(sentence.slice(lastIndex, error.startIndex!));
    }

    // 오류 부분 하이라이트
    parts.push(
      <span
        key={i}
        className="grammar-error"
        title={`${error.explanation}\n수정: ${error.corrected}`}
        style={{
          backgroundColor: '#ffebee',
          borderBottom: '2px wavy red',
          cursor: 'help'
        }}
      >
        {sentence.slice(error.startIndex!, error.endIndex!)}
      </span>
    );

    lastIndex = error.endIndex!;
  });

  // 마지막 텍스트
  if (lastIndex < sentence.length) {
    parts.push(sentence.slice(lastIndex));
  }

  return parts;
}

// 사용 예시
function SentenceWithErrors({ sentence, errors }: Props) {
  return (
    <p className="sentence">
      {highlightErrors(sentence, errors)}
    </p>
  );
}
```

---

## 응답 공통 형식

모든 API 응답은 다음 형식을 따릅니다:

```typescript
interface ApiResponse<T> {
  status: 'success' | 'error';
  message: string;
  data?: T;
  error?: {
    code: string;
    message: string;
  };
}
```

---

## 에러 코드

| 코드 | 설명 |
|------|------|
| `INVALID_SENTENCE` | 유효하지 않은 문장 |
| `INVALID_LEVEL` | 유효하지 않은 레벨 |
| `SESSION_NOT_FOUND` | 세션을 찾을 수 없음 |
| `BEDROCK_API_ERROR` | AI API 호출 오류 |
| `BEDROCK_RESPONSE_PARSE_ERROR` | AI 응답 파싱 오류 |

---

## 레벨별 특성

| 레벨 | 특성 |
|------|------|
| `BEGINNER` | 쉬운 어휘, 한국어 번역 포함, 격려 메시지 |
| `INTERMEDIATE` | 일상 영어, 자연스러운 표현 |
| `ADVANCED` | 고급 어휘, 관용구, 스타일 개선 |
