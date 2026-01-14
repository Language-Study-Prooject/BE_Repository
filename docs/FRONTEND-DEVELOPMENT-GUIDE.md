# 프론트엔드 개발 가이드

영어 학습 플랫폼 백엔드 API 통합을 위한 프론트엔드 개발 가이드입니다.

**목차**
1. [개요](#개요)
2. [인증](#인증)
3. [API 공통 규칙](#api-공통-규칙)
4. [TypeScript 인터페이스](#typescript-인터페이스)
5. [API 엔드포인트](#api-엔드포인트)
6. [에러 코드 및 처리](#에러-코드-및-처리)
7. [WebSocket 연동 가이드](#websocket-연동-가이드)

---

## 개요

### API 기본 정보

영어 학습 플랫폼은 AWS Serverless 아키텍처를 기반으로 한 백엔드 API를 제공합니다.

**주요 기능:**
- 단어 학습 및 관리 (Spaced Repetition 알고리즘)
- 단어 테스트 및 통계
- 실시간 채팅 및 게임
- AI 기반 문법 검사 및 대화
- 뱃지 시스템

**API Base URL:**
```
Production: https://{api-id}.execute-api.{region}.amazonaws.com/prod
Development: https://{api-id}.execute-api.{region}.amazonaws.com/dev
```

### 기술 스택

- **Backend**: Java 21, Spring Cloud Functions (AWS Lambda)
- **Database**: DynamoDB
- **Authentication**: AWS Cognito (JWT)
- **Real-time**: API Gateway WebSocket
- **AI**: AWS Bedrock (Claude)
- **Voice**: AWS Polly

---

## 인증

### Cognito JWT 인증 흐름

#### 1. 회원가입 및 로그인

AWS Cognito User Pool을 사용합니다.

```typescript
// 예제: Cognito 초기화 (AWS Amplify 또는 aws-amplify)
import { Auth } from 'aws-amplify';

// Amplify 설정
Auth.configure({
  region: 'ap-northeast-2',
  userPoolId: '{USER_POOL_ID}',
  userPoolWebClientId: '{CLIENT_ID}'
});

// 회원가입
async function signUp(email: string, password: string, name: string) {
  try {
    const response = await Auth.signUp({
      username: email,
      password: password,
      attributes: {
        email: email,
        name: name
      }
    });
    console.log('회원가입 성공:', response);
  } catch (error) {
    console.error('회원가입 실패:', error);
  }
}

// 로그인
async function signIn(email: string, password: string) {
  try {
    const user = await Auth.signIn(email, password);
    console.log('로그인 성공:', user);
    return user;
  } catch (error) {
    console.error('로그인 실패:', error);
  }
}
```

#### 2. 토큰 획득 및 관리

로그인 후 JWT 토큰을 획득하여 API 요청에 사용합니다.

```typescript
// JWT 토큰 획득
async function getAccessToken(): Promise<string> {
  try {
    const session = await Auth.currentSession();
    const accessToken = session.getAccessToken().getJwtToken();
    return accessToken;
  } catch (error) {
    console.error('토큰 획득 실패:', error);
    throw error;
  }
}

// 토큰 갱신
async function refreshToken() {
  try {
    const session = await Auth.currentSession();
    // 토큰이 만료되었을 경우 자동으로 갱신됨
    const idToken = session.getIdToken().getJwtToken();
    return idToken;
  } catch (error) {
    console.error('토큰 갱신 실패:', error);
  }
}
```

#### 3. API 요청에 토큰 포함

모든 인증이 필요한 API 요청에는 Authorization 헤더에 JWT 토큰을 포함합니다.

```typescript
// Fetch API를 사용한 인증 요청
async function authenticatedFetch(
  url: string,
  options: RequestInit = {}
): Promise<Response> {
  const token = await getAccessToken();

  return fetch(url, {
    ...options,
    headers: {
      ...options.headers,
      'Authorization': `Bearer ${token}`,
      'Content-Type': 'application/json'
    }
  });
}

// 사용 예제
async function getUserWords() {
  try {
    const response = await authenticatedFetch(
      'https://api-id.execute-api.region.amazonaws.com/dev/vocab/user-words'
    );
    const data = await response.json();
    console.log('사용자 단어:', data);
  } catch (error) {
    console.error('요청 실패:', error);
  }
}
```

#### 4. Axios를 사용한 예제

```typescript
import axios from 'axios';

// Axios 인스턴스 생성
const apiClient = axios.create({
  baseURL: 'https://api-id.execute-api.region.amazonaws.com/dev'
});

// 인터셉터로 토큰 자동 추가
apiClient.interceptors.request.use(
  async (config) => {
    const token = await getAccessToken();
    config.headers.Authorization = `Bearer ${token}`;
    return config;
  },
  (error) => {
    return Promise.reject(error);
  }
);

// 응답 인터셉터: 토큰 만료 시 갱신
apiClient.interceptors.response.use(
  (response) => response,
  async (error) => {
    if (error.response?.status === 401) {
      try {
        await refreshToken();
        // 원래 요청 재시도
        return apiClient(error.config);
      } catch (refreshError) {
        // 로그인 페이지로 리다이렉트
        window.location.href = '/login';
        return Promise.reject(refreshError);
      }
    }
    return Promise.reject(error);
  }
);

// 사용 예제
async function fetchUserWords() {
  try {
    const response = await apiClient.get('/vocab/user-words');
    console.log('사용자 단어:', response.data);
  } catch (error) {
    console.error('요청 실패:', error);
  }
}
```

---

## API 공통 규칙

### 표준 응답 형식

모든 API 응답은 다음과 같은 표준 형식을 따릅니다.

```typescript
interface ApiResponse<T> {
  isSuccess: boolean;      // 성공 여부
  message: string | null;  // 응답 메시지 (선택사항)
  data: T | null;         // 응답 데이터
  error: string | null;   // 에러 메시지 (실패 시)
}
```

**성공 응답 예제:**

```json
{
  "isSuccess": true,
  "message": "단어 조회 성공",
  "data": {
    "wordId": "word_001",
    "english": "hello",
    "korean": "안녕하세요",
    "level": "BEGINNER"
  },
  "error": null
}
```

**실패 응답 예제:**

```json
{
  "isSuccess": false,
  "message": null,
  "data": null,
  "error": "필수 필드가 누락되었습니다"
}
```

### 페이지네이션

목록 조회 API는 커서 기반의 페이지네이션을 지원합니다.

```typescript
interface PaginatedResponse<T> {
  items: T[];
  nextCursor?: string;
  hasMore: boolean;
}
```

**페이지네이션 요청 예제:**

```typescript
// 첫 번째 페이지
const firstPage = await apiClient.get('/vocab/words', {
  params: {
    limit: 20,
    level: 'BEGINNER',
    category: 'DAILY'
  }
});

// 다음 페이지 (cursor 사용)
if (firstPage.data.data.hasMore) {
  const nextPage = await apiClient.get('/vocab/words', {
    params: {
      limit: 20,
      cursor: firstPage.data.data.nextCursor,
      level: 'BEGINNER',
      category: 'DAILY'
    }
  });
}
```

### 공통 쿼리 파라미터

| 파라미터 | 타입 | 필수 | 설명 |
|---------|------|------|------|
| limit | number | N | 반환할 아이템 개수 (기본값: 20, 최대: 100) |
| cursor | string | N | 커서 기반 페이지네이션 커서 |
| sortBy | string | N | 정렬 기준 (기본값: 'createdAt') |
| sortOrder | string | N | 정렬 순서 ('ASC' 또는 'DESC', 기본값: 'DESC') |

---

## TypeScript 인터페이스

### 공통 인터페이스

```typescript
// API 응답 래퍼
interface ApiResponse<T> {
  isSuccess: boolean;
  message: string | null;
  data: T | null;
  error: string | null;
}

// 페이지네이션 응답
interface PaginatedResponse<T> {
  items: T[];
  nextCursor?: string;
  hasMore: boolean;
}

// 사용자 정보
interface User {
  userId: string;
  email: string;
  name: string;
  level: StudyLevel;
  createdAt: string;
  updatedAt: string;
}
```

### 단어 관련 인터페이스

```typescript
// 단어 레벨 enum
type WordLevel = 'BEGINNER' | 'INTERMEDIATE' | 'ADVANCED';

// 단어 카테고리 enum
type WordCategory = 'DAILY' | 'IDIOM' | 'PHRASAL_VERB' | 'BUSINESS' | 'ACADEMIC';

// 단어 상태 enum
type WordStatus = 'NEW' | 'LEARNING' | 'REVIEWING' | 'MASTERED';

// 사용자 지정 난이도 enum
type Difficulty = 'EASY' | 'NORMAL' | 'HARD';

// 단어 정보
interface Word {
  wordId: string;
  english: string;
  korean: string;
  example?: string;
  level: WordLevel;
  category: WordCategory;
  createdAt: string;
}

// 단어 생성 요청
interface CreateWordRequest {
  english: string;
  korean: string;
  example?: string;
  level?: WordLevel;
  category?: WordCategory;
}

// 단어 수정 요청
interface UpdateWordRequest {
  english?: string;
  korean?: string;
  example?: string;
  level?: WordLevel;
  category?: WordCategory;
}

// 단어 일괄 생성 요청
interface CreateWordsBatchRequest {
  words: CreateWordRequest[];
}

// 단어 일괄 조회 요청
interface BatchGetWordsRequest {
  wordIds: string[];
}

// 사용자 단어 (학습 상태 포함)
interface UserWord {
  wordId: string;
  userId: string;
  status: WordStatus;
  interval: number;           // 복습 간격 (일)
  easeFactor: number;         // 난이도 계수
  repetitions: number;        // 연속 정답 횟수
  nextReviewAt: string;       // 다음 복습 예정일
  lastReviewedAt?: string;    // 마지막 복습일
  correctCount: number;       // 정답 횟수
  incorrectCount: number;     // 오답 횟수
  bookmarked: boolean;        // 북마크 여부
  favorite: boolean;          // 즐겨찾기 여부
  difficulty?: Difficulty;    // 사용자 지정 난이도
  createdAt: string;
  updatedAt: string;
}

// 사용자 단어 업데이트 요청
interface UpdateUserWordRequest {
  isCorrect: boolean;         // 정답 여부
}

// 사용자 단어 태그 업데이트 요청
interface UpdateUserWordTagRequest {
  bookmarked?: boolean;
  favorite?: boolean;
  difficulty?: Difficulty;
}

// 사용자 단어 상태 업데이트 요청
interface UpdateUserWordStatusRequest {
  status: WordStatus;
}

// 단어 그룹
interface WordGroup {
  groupId: string;
  userId: string;
  name: string;
  description?: string;
  wordIds: string[];
  createdAt: string;
  updatedAt: string;
}

// 단어 그룹 생성 요청
interface CreateWordGroupRequest {
  name: string;
  description?: string;
  wordIds?: string[];
}

// 단어 그룹 수정 요청
interface UpdateWordGroupRequest {
  name?: string;
  description?: string;
}
```

### 테스트 관련 인터페이스

```typescript
// 테스트 타입 enum
type TestType = 'DAILY' | 'CUSTOM' | 'QUICK';

// 테스트 시작 요청
interface StartTestRequest {
  testType?: TestType;
}

// 테스트 응답
interface StartTestResponse {
  testId: string;
  words: Word[];
  startedAt: string;
}

// 테스트 답안
interface TestAnswer {
  wordId: string;
  answer?: string;  // 빈 값 허용 (오답 처리)
}

// 테스트 제출 요청
interface SubmitTestRequest {
  testId: string;
  testType?: TestType;
  answers: TestAnswer[];
  startedAt: string;
}

// 테스트 결과
interface TestResult {
  testId: string;
  userId: string;
  testType: TestType;
  totalQuestions: number;
  correctAnswers: number;
  correctRate: number;        // 0 ~ 100
  spentTime: number;          // 밀리초
  completedAt: string;
}

// 테스트된 단어
interface TestedWord {
  wordId: string;
  english: string;
  korean: string;
  isCorrect: boolean;
  userAnswer?: string;
}

// 통계
interface Statistics {
  totalWords: number;
  masteredCount: number;
  learningCount: number;
  reviewingCount: number;
  newCount: number;
  correctRate: number;
  totalTestsTaken: number;
  averageSpentTime: number;
}

// 일별 통계
interface DailyStatistics {
  date: string;
  wordsLearned: number;
  testsTaken: number;
  correctRate: number;
  spentTime: number;
}

// 취약점 분석
interface WeaknessAnalysis {
  wordId: string;
  english: string;
  korean: string;
  incorrectCount: number;
  level: WordLevel;
}
```

### 채팅 관련 인터페이스

```typescript
// 채팅 레벨 enum
type ChatLevel = 'beginner' | 'intermediate' | 'advanced';

// 메시지 타입 enum
type MessageType = 'TEXT' | 'IMAGE' | 'VOICE' | 'GAME_MOVE';

// 채팅 방
interface ChatRoom {
  roomId: string;
  name: string;
  description?: string;
  level: ChatLevel;
  maxMembers: number;
  currentMembers: number;
  isPrivate: boolean;
  createdBy: string;
  createdAt: string;
}

// 채팅 방 생성 요청
interface CreateRoomRequest {
  name: string;
  description?: string;
  level?: ChatLevel;
  maxMembers?: number;
  isPrivate?: boolean;
  password?: string;
}

// 방 참여 요청
interface JoinRoomRequest {
  roomId: string;
  password?: string;
}

// 방 참여 응답
interface JoinRoomResponse {
  roomId: string;
  roomToken: string;
  name: string;
  level: ChatLevel;
  members: {
    userId: string;
    name: string;
  }[];
}

// 방 나가기 요청
interface LeaveRoomRequest {
  roomId: string;
}

// 채팅 메시지
interface ChatMessage {
  messageId: string;
  roomId: string;
  userId: string;
  userName: string;
  content: string;
  messageType: MessageType;
  createdAt: string;
}

// 메시지 전송 요청
interface SendMessageRequest {
  roomId: string;
  content: string;
  messageType?: MessageType;
}

// 음성 합성 요청 (채팅)
interface VoiceSynthesisRequest {
  text: string;
  voiceId?: string;
}

// 게임 상태
interface GameStatusResponse {
  roomId: string;
  isActive: boolean;
  currentPlayer?: string;
  gameData?: {
    wordId: string;
    hint?: string;
  };
}

// 점수판
interface ScoreboardResponse {
  roomId: string;
  scores: {
    userId: string;
    userName: string;
    score: number;
  }[];
}
```

### 문법 관련 인터페이스

```typescript
// 문법 검사 요청
interface GrammarCheckRequest {
  sentence: string;
  level?: WordLevel;
}

// 문법 검사 응답
interface GrammarCheckResponse {
  originalSentence: string;
  correctedSentence: string;
  score: number;             // 0-100 점수
  isCorrect: boolean;
  errors: GrammarError[];
  feedback: string;          // 전체 피드백 메시지
}

// 문법 오류
interface GrammarError {
  type: GrammarErrorType;    // 오류 타입
  original: string;          // 원본 텍스트
  corrected: string;         // 수정된 텍스트
  explanation: string;       // 오류 설명 (레벨별 언어/상세도 다름)
  startIndex?: number;       // 오류 시작 위치 (optional)
  endIndex?: number;         // 오류 끝 위치 (optional)
}

// 문법 오류 타입
type GrammarErrorType =
  | 'VERB_TENSE'
  | 'SUBJECT_VERB_AGREEMENT'
  | 'ARTICLE'
  | 'PREPOSITION'
  | 'WORD_ORDER'
  | 'PLURAL_SINGULAR'
  | 'PRONOUN'
  | 'SPELLING'
  | 'PUNCTUATION'
  | 'WORD_CHOICE'
  | 'SENTENCE_STRUCTURE'
  | 'OTHER';

// AI 대화 요청
interface ConversationRequest {
  message: string;
  sessionId?: string;
  level?: GrammarLevel;      // BEGINNER | INTERMEDIATE | ADVANCED
}

// AI 대화 응답
interface ConversationResponse {
  sessionId: string;
  grammarCheck: GrammarCheckResponse;  // 문법 검사 결과
  aiResponse: string;                   // AI 대화 응답
  conversationTip: string;              // 학습 팁 (선택적으로 표시)
}

// 문법 레벨
type GrammarLevel = 'BEGINNER' | 'INTERMEDIATE' | 'ADVANCED';

// 세션
interface GrammarSession {
  sessionId: string;
  userId: string;
  startedAt: string;
  updatedAt: string;
  messageCount: number;
}
```

### 뱃지 관련 인터페이스

```typescript
// 뱃지
interface Badge {
  badgeId: string;
  name: string;
  description: string;
  imageUrl: string;
  condition: string;
  isEarned: boolean;
  earnedAt?: string;
}

// 사용자 뱃지
interface UserBadge {
  badgeId: string;
  userId: string;
  earnedAt: string;
}
```

### 통계 관련 인터페이스

```typescript
// 일간 통계
interface DailyStats {
  date: string;
  wordsLearned: number;
  testsTaken: number;
  correctRate: number;
  spentTime: number;
}

// 주간 통계
interface WeeklyStats {
  startDate: string;
  endDate: string;
  totalWordsLearned: number;
  totalTestsTaken: number;
  averageCorrectRate: number;
  totalSpentTime: number;
  dailyStats: DailyStats[];
}

// 월간 통계
interface MonthlyStats {
  month: string;
  totalWordsLearned: number;
  totalTestsTaken: number;
  averageCorrectRate: number;
  totalSpentTime: number;
}

// 전체 통계
interface TotalStats {
  totalWordsLearned: number;
  masteredCount: number;
  learningCount: number;
  totalTestsTaken: number;
  averageCorrectRate: number;
  totalSpentTime: number;
  streak: number;
}
```

---

## API 엔드포인트

### 1. 단어 관리 (Vocabulary)

#### 1.1 단어 CRUD

##### POST /vocab/words - 단어 추가

단일 단어를 추가합니다. 공개 API입니다.

**인증:** 불필요

**요청:**

```typescript
const createWordRequest: CreateWordRequest = {
  english: "hello",
  korean: "안녕하세요",
  example: "Hello, my name is John.",
  level: "BEGINNER",
  category: "DAILY"
};

const response = await fetch(
  'https://api-id.execute-api.region.amazonaws.com/dev/vocab/words',
  {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(createWordRequest)
  }
);

const result: ApiResponse<Word> = await response.json();
```

**응답:**

```json
{
  "isSuccess": true,
  "message": "단어가 등록되었습니다",
  "data": {
    "wordId": "word_001",
    "english": "hello",
    "korean": "안녕하세요",
    "example": "Hello, my name is John.",
    "level": "BEGINNER",
    "category": "DAILY",
    "createdAt": "2024-01-14T10:00:00Z"
  },
  "error": null
}
```

**에러 케이스:**

```json
{
  "isSuccess": false,
  "message": null,
  "data": null,
  "error": "필수 필드가 누락되었습니다"
}
```

---

##### POST /vocab/words/batch - 단어 일괄 추가

최대 100개의 단어를 한번에 추가합니다. 공개 API입니다.

**인증:** 불필요

**요청:**

```typescript
const batchRequest: CreateWordsBatchRequest = {
  words: [
    {
      english: "hello",
      korean: "안녕하세요",
      level: "BEGINNER",
      category: "DAILY"
    },
    {
      english: "goodbye",
      korean: "안녕히 가세요",
      level: "BEGINNER",
      category: "DAILY"
    }
  ]
};

const response = await fetch(
  'https://api-id.execute-api.region.amazonaws.com/dev/vocab/words/batch',
  {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(batchRequest)
  }
);

const result: ApiResponse<Word[]> = await response.json();
```

**응답:**

```json
{
  "isSuccess": true,
  "message": "2개의 단어가 등록되었습니다",
  "data": [
    {
      "wordId": "word_001",
      "english": "hello",
      "korean": "안녕하세요",
      "level": "BEGINNER",
      "category": "DAILY",
      "createdAt": "2024-01-14T10:00:00Z"
    },
    {
      "wordId": "word_002",
      "english": "goodbye",
      "korean": "안녕히 가세요",
      "level": "BEGINNER",
      "category": "DAILY",
      "createdAt": "2024-01-14T10:00:01Z"
    }
  ],
  "error": null
}
```

---

##### POST /vocab/words/batch/get - 단어 일괄 조회

최대 100개의 단어를 일괄 조회합니다. 공개 API입니다.

**인증:** 불필요

**요청:**

```typescript
const batchGetRequest: BatchGetWordsRequest = {
  wordIds: ["word_001", "word_002", "word_003"]
};

const response = await fetch(
  'https://api-id.execute-api.region.amazonaws.com/dev/vocab/words/batch/get',
  {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(batchGetRequest)
  }
);

const result: ApiResponse<Word[]> = await response.json();
```

**응답:**

```json
{
  "isSuccess": true,
  "message": "3개의 단어를 조회했습니다",
  "data": [
    {
      "wordId": "word_001",
      "english": "hello",
      "korean": "안녕하세요",
      "level": "BEGINNER",
      "category": "DAILY",
      "createdAt": "2024-01-14T10:00:00Z"
    }
  ],
  "error": null
}
```

---

##### GET /vocab/words - 단어 목록 조회

페이지네이션과 필터를 지원합니다. 공개 API입니다.

**인증:** 불필요

**쿼리 파라미터:**

| 파라미터 | 타입 | 필수 | 설명 |
|---------|------|------|------|
| limit | number | N | 반환할 아이템 개수 (기본값: 20) |
| cursor | string | N | 페이지네이션 커서 |
| level | string | N | 단어 레벨 (BEGINNER, INTERMEDIATE, ADVANCED) |
| category | string | N | 단어 카테고리 (DAILY, IDIOM, etc.) |
| sortBy | string | N | 정렬 기준 (createdAt, english) |
| sortOrder | string | N | 정렬 순서 (ASC, DESC) |

**요청:**

```typescript
// Fetch API
const response = await fetch(
  'https://api-id.execute-api.region.amazonaws.com/dev/vocab/words?limit=20&level=BEGINNER&category=DAILY'
);

const result: ApiResponse<PaginatedResponse<Word>> = await response.json();

// Axios
const result = await apiClient.get('/vocab/words', {
  params: {
    limit: 20,
    level: 'BEGINNER',
    category: 'DAILY'
  }
});
```

**응답:**

```json
{
  "isSuccess": true,
  "message": "20개의 단어를 조회했습니다",
  "data": {
    "items": [
      {
        "wordId": "word_001",
        "english": "hello",
        "korean": "안녕하세요",
        "example": "Hello, my name is John.",
        "level": "BEGINNER",
        "category": "DAILY",
        "createdAt": "2024-01-14T10:00:00Z"
      }
    ],
    "nextCursor": "cursor_abc123",
    "hasMore": true
  },
  "error": null
}
```

---

##### GET /vocab/words/search - 단어 검색

단어를 검색합니다. 공개 API입니다.

**인증:** 불필요

**쿼리 파라미터:**

| 파라미터 | 타입 | 필수 | 설명 |
|---------|------|------|------|
| q | string | Y | 검색어 |
| limit | number | N | 반환할 아이템 개수 (기본값: 20) |

**요청:**

```typescript
const response = await fetch(
  'https://api-id.execute-api.region.amazonaws.com/dev/vocab/words/search?q=hello&limit=10'
);

const result: ApiResponse<Word[]> = await response.json();
```

**응답:**

```json
{
  "isSuccess": true,
  "message": "5개의 단어를 검색했습니다",
  "data": [
    {
      "wordId": "word_001",
      "english": "hello",
      "korean": "안녕하세요",
      "level": "BEGINNER",
      "category": "DAILY",
      "createdAt": "2024-01-14T10:00:00Z"
    }
  ],
  "error": null
}
```

---

##### GET /vocab/words/{wordId} - 단어 상세 조회

특정 단어의 상세 정보를 조회합니다. 공개 API입니다.

**인증:** 불필요

**요청:**

```typescript
const wordId = "word_001";
const response = await authenticatedFetch(
  `https://api-id.execute-api.region.amazonaws.com/dev/vocab/words/${wordId}`
);

const result: ApiResponse<Word> = await response.json();
```

**응답:**

```json
{
  "isSuccess": true,
  "message": "단어 조회 성공",
  "data": {
    "wordId": "word_001",
    "english": "hello",
    "korean": "안녕하세요",
    "example": "Hello, my name is John.",
    "level": "BEGINNER",
    "category": "DAILY",
    "createdAt": "2024-01-14T10:00:00Z"
  },
  "error": null
}
```

---

##### PUT /vocab/words/{wordId} - 단어 수정

단어 정보를 수정합니다. 공개 API입니다.

**인증:** 불필요

**요청:**

```typescript
const wordId = "word_001";
const updateRequest: UpdateWordRequest = {
  korean: "안녕하세요 (인사)",
  example: "Updated example sentence."
};

const response = await fetch(
  `https://api-id.execute-api.region.amazonaws.com/dev/vocab/words/${wordId}`,
  {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(updateRequest)
  }
);

const result: ApiResponse<Word> = await response.json();
```

**응답:**

```json
{
  "isSuccess": true,
  "message": "단어가 수정되었습니다",
  "data": {
    "wordId": "word_001",
    "english": "hello",
    "korean": "안녕하세요 (인사)",
    "example": "Updated example sentence.",
    "level": "BEGINNER",
    "category": "DAILY",
    "createdAt": "2024-01-14T10:00:00Z"
  },
  "error": null
}
```

---

##### DELETE /vocab/words/{wordId} - 단어 삭제

단어를 삭제합니다. 공개 API입니다.

**인증:** 불필요

**요청:**

```typescript
const wordId = "word_001";
const response = await fetch(
  `https://api-id.execute-api.region.amazonaws.com/dev/vocab/words/${wordId}`,
  { method: 'DELETE' }
);

const result: ApiResponse<null> = await response.json();
```

**응답:**

```json
{
  "isSuccess": true,
  "message": "단어가 삭제되었습니다",
  "data": null,
  "error": null
}
```

---

#### 1.2 사용자 단어 관리 (인증 필요)

##### GET /vocab/user-words - 사용자 학습 진행도

사용자의 단어 학습 진행도를 조회합니다.

**인증:** 필수 (JWT)

**쿼리 파라미터:**

| 파라미터 | 타입 | 필수 | 설명 |
|---------|------|------|------|
| status | string | N | 상태 필터 (NEW, LEARNING, REVIEWING, MASTERED) |
| limit | number | N | 반환할 아이템 개수 (기본값: 20) |
| cursor | string | N | 페이지네이션 커서 |

**요청:**

```typescript
const response = await authenticatedFetch(
  'https://api-id.execute-api.region.amazonaws.com/dev/vocab/user-words?limit=20&status=LEARNING'
);

const result: ApiResponse<PaginatedResponse<UserWord>> = await response.json();
```

**응답:**

```json
{
  "isSuccess": true,
  "message": "사용자 단어 조회 성공",
  "data": {
    "items": [
      {
        "wordId": "word_001",
        "userId": "user_123",
        "status": "LEARNING",
        "interval": 3,
        "easeFactor": 2.5,
        "repetitions": 2,
        "nextReviewAt": "2024-01-17T10:00:00Z",
        "correctCount": 5,
        "incorrectCount": 2,
        "bookmarked": false,
        "favorite": true,
        "createdAt": "2024-01-10T10:00:00Z",
        "updatedAt": "2024-01-14T10:00:00Z"
      }
    ],
    "nextCursor": "cursor_xyz789",
    "hasMore": true
  },
  "error": null
}
```

---

##### GET /vocab/user-words/{wordId} - 특정 단어 학습 상태

특정 단어의 사용자 학습 상태를 조회합니다.

**인증:** 필수 (JWT)

**요청:**

```typescript
const wordId = "word_001";
const response = await authenticatedFetch(
  `https://api-id.execute-api.region.amazonaws.com/dev/vocab/user-words/${wordId}`
);

const result: ApiResponse<UserWord> = await response.json();
```

**응답:**

```json
{
  "isSuccess": true,
  "message": "단어 학습 상태 조회 성공",
  "data": {
    "wordId": "word_001",
    "userId": "user_123",
    "status": "LEARNING",
    "interval": 3,
    "easeFactor": 2.5,
    "repetitions": 2,
    "nextReviewAt": "2024-01-17T10:00:00Z",
    "lastReviewedAt": "2024-01-14T10:00:00Z",
    "correctCount": 5,
    "incorrectCount": 2,
    "bookmarked": false,
    "favorite": true,
    "difficulty": "NORMAL",
    "createdAt": "2024-01-10T10:00:00Z",
    "updatedAt": "2024-01-14T10:00:00Z"
  },
  "error": null
}
```

---

##### PUT /vocab/user-words/{wordId} - 정오답 업데이트

단어 학습 진행도를 업데이트합니다. 정답/오답을 기록합니다.

**인증:** 필수 (JWT)

**요청:**

```typescript
const wordId = "word_001";
const updateRequest: UpdateUserWordRequest = {
  isCorrect: true
};

const response = await authenticatedFetch(
  `https://api-id.execute-api.region.amazonaws.com/dev/vocab/user-words/${wordId}`,
  {
    method: 'PUT',
    body: JSON.stringify(updateRequest)
  }
);

const result: ApiResponse<UserWord> = await response.json();
```

**응답:**

```json
{
  "isSuccess": true,
  "message": "단어 학습 상태가 업데이트되었습니다",
  "data": {
    "wordId": "word_001",
    "userId": "user_123",
    "status": "LEARNING",
    "interval": 6,
    "easeFactor": 2.6,
    "repetitions": 3,
    "nextReviewAt": "2024-01-20T10:00:00Z",
    "correctCount": 6,
    "incorrectCount": 2,
    "bookmarked": false,
    "favorite": true,
    "createdAt": "2024-01-10T10:00:00Z",
    "updatedAt": "2024-01-14T10:30:00Z"
  },
  "error": null
}
```

---

##### PUT /vocab/user-words/{wordId}/tag - 태그 업데이트

북마크, 즐겨찾기, 난이도 등의 태그를 업데이트합니다.

**인증:** 필수 (JWT)

**요청:**

```typescript
const wordId = "word_001";
const updateTagRequest: UpdateUserWordTagRequest = {
  bookmarked: true,
  favorite: true,
  difficulty: "HARD"
};

const response = await authenticatedFetch(
  `https://api-id.execute-api.region.amazonaws.com/dev/vocab/user-words/${wordId}/tag`,
  {
    method: 'PUT',
    body: JSON.stringify(updateTagRequest)
  }
);

const result: ApiResponse<UserWord> = await response.json();
```

**응답:**

```json
{
  "isSuccess": true,
  "message": "태그가 업데이트되었습니다",
  "data": {
    "wordId": "word_001",
    "userId": "user_123",
    "status": "LEARNING",
    "interval": 6,
    "easeFactor": 2.6,
    "repetitions": 3,
    "nextReviewAt": "2024-01-20T10:00:00Z",
    "correctCount": 6,
    "incorrectCount": 2,
    "bookmarked": true,
    "favorite": true,
    "difficulty": "HARD",
    "createdAt": "2024-01-10T10:00:00Z",
    "updatedAt": "2024-01-14T10:30:00Z"
  },
  "error": null
}
```

---

##### PUT /vocab/user-words/{wordId}/status - 상태 변경

단어 학습 상태를 변경합니다 (NEW, LEARNING, REVIEWING, MASTERED).

**인증:** 필수 (JWT)

**요청:**

```typescript
const wordId = "word_001";
const statusRequest: UpdateUserWordStatusRequest = {
  status: "MASTERED"
};

const response = await authenticatedFetch(
  `https://api-id.execute-api.region.amazonaws.com/dev/vocab/user-words/${wordId}/status`,
  {
    method: 'PUT',
    body: JSON.stringify(statusRequest)
  }
);

const result: ApiResponse<UserWord> = await response.json();
```

**응답:**

```json
{
  "isSuccess": true,
  "message": "단어 상태가 변경되었습니다",
  "data": {
    "wordId": "word_001",
    "userId": "user_123",
    "status": "MASTERED",
    "interval": 30,
    "easeFactor": 2.6,
    "repetitions": 10,
    "nextReviewAt": "2024-02-13T10:00:00Z",
    "correctCount": 15,
    "incorrectCount": 2,
    "bookmarked": true,
    "favorite": true,
    "createdAt": "2024-01-10T10:00:00Z",
    "updatedAt": "2024-01-14T10:30:00Z"
  },
  "error": null
}
```

---

##### GET /vocab/wrong-answers - 오답 목록

사용자의 오답 목록을 조회합니다.

**인증:** 필수 (JWT)

**쿼리 파라미터:**

| 파라미터 | 타입 | 필수 | 설명 |
|---------|------|------|------|
| limit | number | N | 반환할 아이템 개수 (기본값: 20) |
| cursor | string | N | 페이지네이션 커서 |

**요청:**

```typescript
const response = await authenticatedFetch(
  'https://api-id.execute-api.region.amazonaws.com/dev/vocab/wrong-answers?limit=20'
);

const result: ApiResponse<PaginatedResponse<UserWord>> = await response.json();
```

**응답:**

```json
{
  "isSuccess": true,
  "message": "오답 목록 조회 성공",
  "data": {
    "items": [
      {
        "wordId": "word_005",
        "userId": "user_123",
        "status": "LEARNING",
        "correctCount": 2,
        "incorrectCount": 8,
        "interval": 1,
        "easeFactor": 1.3,
        "repetitions": 0,
        "nextReviewAt": "2024-01-15T10:00:00Z",
        "createdAt": "2024-01-08T10:00:00Z",
        "updatedAt": "2024-01-14T15:00:00Z"
      }
    ],
    "nextCursor": "cursor_wrong123",
    "hasMore": false
  },
  "error": null
}
```

---

#### 1.3 단어 그룹 관리 (인증 필요)

##### POST /vocab/groups - 그룹 생성

새로운 단어 그룹을 생성합니다.

**인증:** 필수 (JWT)

**요청:**

```typescript
const createGroupRequest: CreateWordGroupRequest = {
  name: "일상 회화",
  description: "일상에서 자주 사용하는 표현들",
  wordIds: ["word_001", "word_002", "word_003"]
};

const response = await authenticatedFetch(
  'https://api-id.execute-api.region.amazonaws.com/dev/vocab/groups',
  {
    method: 'POST',
    body: JSON.stringify(createGroupRequest)
  }
);

const result: ApiResponse<WordGroup> = await response.json();
```

**응답:**

```json
{
  "isSuccess": true,
  "message": "단어 그룹이 생성되었습니다",
  "data": {
    "groupId": "group_001",
    "userId": "user_123",
    "name": "일상 회화",
    "description": "일상에서 자주 사용하는 표현들",
    "wordIds": ["word_001", "word_002", "word_003"],
    "createdAt": "2024-01-14T10:00:00Z",
    "updatedAt": "2024-01-14T10:00:00Z"
  },
  "error": null
}
```

---

##### GET /vocab/groups - 그룹 목록

사용자의 단어 그룹 목록을 조회합니다.

**인증:** 필수 (JWT)

**쿼리 파라미터:**

| 파라미터 | 타입 | 필수 | 설명 |
|---------|------|------|------|
| limit | number | N | 반환할 아이템 개수 (기본값: 20) |
| cursor | string | N | 페이지네이션 커서 |

**요청:**

```typescript
const response = await authenticatedFetch(
  'https://api-id.execute-api.region.amazonaws.com/dev/vocab/groups?limit=10'
);

const result: ApiResponse<PaginatedResponse<WordGroup>> = await response.json();
```

**응답:**

```json
{
  "isSuccess": true,
  "message": "그룹 목록 조회 성공",
  "data": {
    "items": [
      {
        "groupId": "group_001",
        "userId": "user_123",
        "name": "일상 회화",
        "description": "일상에서 자주 사용하는 표현들",
        "wordIds": ["word_001", "word_002", "word_003"],
        "createdAt": "2024-01-14T10:00:00Z",
        "updatedAt": "2024-01-14T10:00:00Z"
      }
    ],
    "nextCursor": "cursor_group123",
    "hasMore": false
  },
  "error": null
}
```

---

##### GET /vocab/groups/{groupId} - 그룹 상세 조회

그룹과 포함된 모든 단어를 조회합니다.

**인증:** 필수 (JWT)

**요청:**

```typescript
const groupId = "group_001";
const response = await authenticatedFetch(
  `https://api-id.execute-api.region.amazonaws.com/dev/vocab/groups/${groupId}`
);

const result: ApiResponse<WordGroup & { words: Word[] }> = await response.json();
```

**응답:**

```json
{
  "isSuccess": true,
  "message": "그룹 상세 조회 성공",
  "data": {
    "groupId": "group_001",
    "userId": "user_123",
    "name": "일상 회화",
    "description": "일상에서 자주 사용하는 표현들",
    "wordIds": ["word_001", "word_002", "word_003"],
    "words": [
      {
        "wordId": "word_001",
        "english": "hello",
        "korean": "안녕하세요",
        "level": "BEGINNER",
        "category": "DAILY",
        "createdAt": "2024-01-14T10:00:00Z"
      }
    ],
    "createdAt": "2024-01-14T10:00:00Z",
    "updatedAt": "2024-01-14T10:00:00Z"
  },
  "error": null
}
```

---

##### PUT /vocab/groups/{groupId} - 그룹 수정

단어 그룹의 이름과 설명을 수정합니다.

**인증:** 필수 (JWT)

**요청:**

```typescript
const groupId = "group_001";
const updateRequest: UpdateWordGroupRequest = {
  name: "일상 회화 (수정됨)",
  description: "일상에서 자주 사용하는 인사 표현들"
};

const response = await authenticatedFetch(
  `https://api-id.execute-api.region.amazonaws.com/dev/vocab/groups/${groupId}`,
  {
    method: 'PUT',
    body: JSON.stringify(updateRequest)
  }
);

const result: ApiResponse<WordGroup> = await response.json();
```

**응답:**

```json
{
  "isSuccess": true,
  "message": "그룹이 수정되었습니다",
  "data": {
    "groupId": "group_001",
    "userId": "user_123",
    "name": "일상 회화 (수정됨)",
    "description": "일상에서 자주 사용하는 인사 표현들",
    "wordIds": ["word_001", "word_002", "word_003"],
    "createdAt": "2024-01-14T10:00:00Z",
    "updatedAt": "2024-01-14T10:30:00Z"
  },
  "error": null
}
```

---

##### DELETE /vocab/groups/{groupId} - 그룹 삭제

단어 그룹을 삭제합니다. 포함된 단어는 삭제되지 않습니다.

**인증:** 필수 (JWT)

**요청:**

```typescript
const groupId = "group_001";
const response = await authenticatedFetch(
  `https://api-id.execute-api.region.amazonaws.com/dev/vocab/groups/${groupId}`,
  { method: 'DELETE' }
);

const result: ApiResponse<null> = await response.json();
```

**응답:**

```json
{
  "isSuccess": true,
  "message": "그룹이 삭제되었습니다",
  "data": null,
  "error": null
}
```

---

##### POST /vocab/groups/{groupId}/words/{wordId} - 그룹에 단어 추가

그룹에 단어를 추가합니다.

**인증:** 필수 (JWT)

**요청:**

```typescript
const groupId = "group_001";
const wordId = "word_004";

const response = await authenticatedFetch(
  `https://api-id.execute-api.region.amazonaws.com/dev/vocab/groups/${groupId}/words/${wordId}`,
  { method: 'POST' }
);

const result: ApiResponse<WordGroup> = await response.json();
```

**응답:**

```json
{
  "isSuccess": true,
  "message": "단어가 그룹에 추가되었습니다",
  "data": {
    "groupId": "group_001",
    "userId": "user_123",
    "name": "일상 회화",
    "wordIds": ["word_001", "word_002", "word_003", "word_004"],
    "createdAt": "2024-01-14T10:00:00Z",
    "updatedAt": "2024-01-14T10:30:00Z"
  },
  "error": null
}
```

---

##### DELETE /vocab/groups/{groupId}/words/{wordId} - 그룹에서 단어 제거

그룹에서 단어를 제거합니다.

**인증:** 필수 (JWT)

**요청:**

```typescript
const groupId = "group_001";
const wordId = "word_004";

const response = await authenticatedFetch(
  `https://api-id.execute-api.region.amazonaws.com/dev/vocab/groups/${groupId}/words/${wordId}`,
  { method: 'DELETE' }
);

const result: ApiResponse<WordGroup> = await response.json();
```

**응답:**

```json
{
  "isSuccess": true,
  "message": "단어가 그룹에서 제거되었습니다",
  "data": {
    "groupId": "group_001",
    "userId": "user_123",
    "name": "일상 회화",
    "wordIds": ["word_001", "word_002", "word_003"],
    "createdAt": "2024-01-14T10:00:00Z",
    "updatedAt": "2024-01-14T10:35:00Z"
  },
  "error": null
}
```

---

#### 1.4 일일 학습 (인증 필요)

##### GET /vocab/daily - 오늘의 학습 단어

오늘의 학습 단어 10개를 자동으로 선정하여 반환합니다.

**인증:** 필수 (JWT)

**요청:**

```typescript
const response = await authenticatedFetch(
  'https://api-id.execute-api.region.amazonaws.com/dev/vocab/daily'
);

const result: ApiResponse<DailyStudy & { words: Word[] }> = await response.json();
```

**응답:**

```json
{
  "isSuccess": true,
  "message": "오늘의 학습 단어를 조회했습니다",
  "data": {
    "userId": "user_123",
    "date": "2024-01-14",
    "words": [
      {
        "wordId": "word_001",
        "english": "hello",
        "korean": "안녕하세요",
        "level": "BEGINNER",
        "category": "DAILY",
        "createdAt": "2024-01-14T10:00:00Z"
      }
    ],
    "createdAt": "2024-01-14T00:00:00Z"
  },
  "error": null
}
```

---

##### POST /vocab/daily/words/{wordId}/learned - 학습 완료 표시

오늘의 학습 단어 중 하나를 학습 완료로 표시합니다.

**인증:** 필수 (JWT)

**요청:**

```typescript
const wordId = "word_001";

const response = await authenticatedFetch(
  `https://api-id.execute-api.region.amazonaws.com/dev/vocab/daily/words/${wordId}/learned`,
  { method: 'POST' }
);

const result: ApiResponse<UserWord> = await response.json();
```

**응답:**

```json
{
  "isSuccess": true,
  "message": "학습이 완료되었습니다",
  "data": {
    "wordId": "word_001",
    "userId": "user_123",
    "status": "LEARNING",
    "interval": 1,
    "easeFactor": 2.5,
    "repetitions": 0,
    "nextReviewAt": "2024-01-15T10:00:00Z",
    "correctCount": 1,
    "incorrectCount": 0,
    "createdAt": "2024-01-14T10:00:00Z",
    "updatedAt": "2024-01-14T10:30:00Z"
  },
  "error": null
}
```

---

#### 1.5 음성 합성

##### POST /vocab/voice/synthesize - TTS (텍스트-음성 변환)

텍스트를 음성으로 변환합니다. 공개 API입니다.

**인증:** 불필요

**요청:**

```typescript
interface SynthesizeVoiceRequest {
  text: string;
  voiceId?: string;         // Polly voice ID (Joanna, Kendra, etc.)
  outputFormat?: string;    // mp3, ogg_vorbis, pcm
}

const synthesizeRequest = {
  text: "Hello, my name is John.",
  voiceId: "Joanna",
  outputFormat: "mp3"
};

const response = await fetch(
  'https://api-id.execute-api.region.amazonaws.com/dev/vocab/voice/synthesize',
  {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(synthesizeRequest)
  }
);

const result = await response.blob();
// 결과는 음성 파일 (blob)
```

**응답:**

음성 파일 (audio/mpeg, audio/ogg, audio/pcm)

---

### 2. 테스트 (Vocabulary Tests)

#### 2.1 테스트 관리

##### POST /vocab/test/start - 테스트 시작

새로운 테스트를 시작합니다.

**인증:** 필수 (JWT)

**요청:**

```typescript
const startTestRequest: StartTestRequest = {
  testType: "DAILY"  // DAILY, CUSTOM, QUICK
};

const response = await authenticatedFetch(
  'https://api-id.execute-api.region.amazonaws.com/dev/vocab/test/start',
  {
    method: 'POST',
    body: JSON.stringify(startTestRequest)
  }
);

const result: ApiResponse<StartTestResponse> = await response.json();
```

**응답:**

```json
{
  "isSuccess": true,
  "message": "테스트가 시작되었습니다",
  "data": {
    "testId": "test_abc123",
    "words": [
      {
        "wordId": "word_001",
        "english": "hello",
        "korean": "안녕하세요",
        "level": "BEGINNER",
        "category": "DAILY",
        "createdAt": "2024-01-14T10:00:00Z"
      }
    ],
    "startedAt": "2024-01-14T10:00:00Z"
  },
  "error": null
}
```

---

##### POST /vocab/test/submit - 답안 제출

테스트 답안을 제출합니다.

**인증:** 필수 (JWT)

**요청:**

```typescript
const submitRequest: SubmitTestRequest = {
  testId: "test_abc123",
  testType: "DAILY",
  answers: [
    { wordId: "word_001", answer: "hello" },
    { wordId: "word_002", answer: "goodbye" },
    { wordId: "word_003", answer: "" }  // 빈 답변 (오답)
  ],
  startedAt: "2024-01-14T10:00:00Z"
};

const response = await authenticatedFetch(
  'https://api-id.execute-api.region.amazonaws.com/dev/vocab/test/submit',
  {
    method: 'POST',
    body: JSON.stringify(submitRequest)
  }
);

const result: ApiResponse<TestResult> = await response.json();
```

**응답:**

```json
{
  "isSuccess": true,
  "message": "테스트 결과가 저장되었습니다",
  "data": {
    "testId": "test_abc123",
    "userId": "user_123",
    "testType": "DAILY",
    "totalQuestions": 10,
    "correctAnswers": 7,
    "correctRate": 70.0,
    "spentTime": 180000,
    "completedAt": "2024-01-14T10:03:00Z"
  },
  "error": null
}
```

---

##### GET /vocab/test/results - 테스트 결과 목록

과거 테스트 결과를 조회합니다.

**인증:** 필수 (JWT)

**쿼리 파라미터:**

| 파라미터 | 타입 | 필수 | 설명 |
|---------|------|------|------|
| testType | string | N | 테스트 타입 필터 (DAILY, CUSTOM, QUICK) |
| limit | number | N | 반환할 아이템 개수 (기본값: 20) |
| cursor | string | N | 페이지네이션 커서 |

**요청:**

```typescript
const response = await authenticatedFetch(
  'https://api-id.execute-api.region.amazonaws.com/dev/vocab/test/results?limit=10'
);

const result: ApiResponse<PaginatedResponse<TestResult>> = await response.json();
```

**응답:**

```json
{
  "isSuccess": true,
  "message": "테스트 결과 목록 조회 성공",
  "data": {
    "items": [
      {
        "testId": "test_abc123",
        "userId": "user_123",
        "testType": "DAILY",
        "totalQuestions": 10,
        "correctAnswers": 7,
        "correctRate": 70.0,
        "spentTime": 180000,
        "completedAt": "2024-01-14T10:03:00Z"
      }
    ],
    "nextCursor": "cursor_test123",
    "hasMore": false
  },
  "error": null
}
```

---

##### GET /vocab/test/results/{testId} - 테스트 결과 상세

특정 테스트의 상세 결과를 조회합니다.

**인증:** 필수 (JWT)

**요청:**

```typescript
const testId = "test_abc123";

const response = await authenticatedFetch(
  `https://api-id.execute-api.region.amazonaws.com/dev/vocab/test/results/${testId}`
);

const result: ApiResponse<TestResult & { testedWords: TestedWord[] }> =
  await response.json();
```

**응답:**

```json
{
  "isSuccess": true,
  "message": "테스트 상세 조회 성공",
  "data": {
    "testId": "test_abc123",
    "userId": "user_123",
    "testType": "DAILY",
    "totalQuestions": 10,
    "correctAnswers": 7,
    "correctRate": 70.0,
    "spentTime": 180000,
    "completedAt": "2024-01-14T10:03:00Z",
    "testedWords": [
      {
        "wordId": "word_001",
        "english": "hello",
        "korean": "안녕하세요",
        "isCorrect": true,
        "userAnswer": "hello"
      },
      {
        "wordId": "word_003",
        "english": "goodbye",
        "korean": "안녕히 가세요",
        "isCorrect": false,
        "userAnswer": ""
      }
    ]
  },
  "error": null
}
```

---

##### GET /vocab/test/tested-words - 최근 테스트 단어

최근에 테스트한 단어들을 조회합니다.

**인증:** 필수 (JWT)

**쿼리 파라미터:**

| 파라미터 | 타입 | 필수 | 설명 |
|---------|------|------|------|
| limit | number | N | 반환할 아이템 개수 (기본값: 20) |

**요청:**

```typescript
const response = await authenticatedFetch(
  'https://api-id.execute-api.region.amazonaws.com/dev/vocab/test/tested-words?limit=20'
);

const result: ApiResponse<TestedWord[]> = await response.json();
```

**응답:**

```json
{
  "isSuccess": true,
  "message": "최근 테스트 단어 조회 성공",
  "data": [
    {
      "wordId": "word_001",
      "english": "hello",
      "korean": "안녕하세요",
      "isCorrect": true,
      "userAnswer": "hello"
    },
    {
      "wordId": "word_002",
      "english": "goodbye",
      "korean": "안녕히 가세요",
      "isCorrect": false,
      "userAnswer": ""
    }
  ],
  "error": null
}
```

---

#### 2.2 통계

##### GET /vocab/stats - 전체 통계

사용자의 전체 학습 통계를 조회합니다.

**인증:** 필수 (JWT)

**요청:**

```typescript
const response = await authenticatedFetch(
  'https://api-id.execute-api.region.amazonaws.com/dev/vocab/stats'
);

const result: ApiResponse<Statistics> = await response.json();
```

**응답:**

```json
{
  "isSuccess": true,
  "message": "통계 조회 성공",
  "data": {
    "totalWords": 150,
    "masteredCount": 50,
    "learningCount": 60,
    "reviewingCount": 30,
    "newCount": 10,
    "correctRate": 82.5,
    "totalTestsTaken": 25,
    "averageSpentTime": 240000
  },
  "error": null
}
```

---

##### GET /vocab/stats/daily - 일별 통계

일별 학습 통계를 조회합니다.

**인증:** 필수 (JWT)

**쿼리 파라미터:**

| 파라미터 | 타입 | 필수 | 설명 |
|---------|------|------|------|
| days | number | N | 조회할 일수 (기본값: 30) |

**요청:**

```typescript
const response = await authenticatedFetch(
  'https://api-id.execute-api.region.amazonaws.com/dev/vocab/stats/daily?days=30'
);

const result: ApiResponse<DailyStatistics[]> = await response.json();
```

**응답:**

```json
{
  "isSuccess": true,
  "message": "일별 통계 조회 성공",
  "data": [
    {
      "date": "2024-01-14",
      "wordsLearned": 5,
      "testsTaken": 2,
      "correctRate": 85.0,
      "spentTime": 300000
    },
    {
      "date": "2024-01-13",
      "wordsLearned": 3,
      "testsTaken": 1,
      "correctRate": 80.0,
      "spentTime": 180000
    }
  ],
  "error": null
}
```

---

##### GET /vocab/stats/weakness - 취약점 분석

오답이 많은 단어들을 조회합니다.

**인証:** 필수 (JWT)

**쿼리 파라미터:**

| 파라미터 | 타입 | 필수 | 설명 |
|---------|------|------|------|
| limit | number | N | 반환할 아이템 개수 (기본값: 20) |

**요청:**

```typescript
const response = await authenticatedFetch(
  'https://api-id.execute-api.region.amazonaws.com/dev/vocab/stats/weakness?limit=20'
);

const result: ApiResponse<WeaknessAnalysis[]> = await response.json();
```

**응답:**

```json
{
  "isSuccess": true,
  "message": "취약점 분석 조회 성공",
  "data": [
    {
      "wordId": "word_005",
      "english": "perspective",
      "korean": "관점",
      "incorrectCount": 8,
      "level": "ADVANCED"
    },
    {
      "wordId": "word_012",
      "english": "aggregate",
      "korean": "집계하다",
      "incorrectCount": 6,
      "level": "INTERMEDIATE"
    }
  ],
  "error": null
}
```

---

### 3. 채팅 (Chatting)

#### 3.1 채팅 방 관리

##### POST /chat/rooms - 방 생성

새로운 채팅 방을 생성합니다.

**인증:** 필수 (JWT)

**요청:**

```typescript
const createRoomRequest: CreateRoomRequest = {
  name: "초급 회화 토론",
  description: "초급 레벨 학습자들을 위한 회화 연습방",
  level: "beginner",
  maxMembers: 6,
  isPrivate: false
};

const response = await authenticatedFetch(
  'https://api-id.execute-api.region.amazonaws.com/dev/chat/rooms',
  {
    method: 'POST',
    body: JSON.stringify(createRoomRequest)
  }
);

const result: ApiResponse<ChatRoom> = await response.json();
```

**응답:**

```json
{
  "isSuccess": true,
  "message": "채팅 방이 생성되었습니다",
  "data": {
    "roomId": "room_001",
    "name": "초급 회화 토론",
    "description": "초급 레벨 학습자들을 위한 회화 연습방",
    "level": "beginner",
    "maxMembers": 6,
    "currentMembers": 1,
    "isPrivate": false,
    "createdBy": "user_123",
    "createdAt": "2024-01-14T10:00:00Z"
  },
  "error": null
}
```

---

##### GET /chat/rooms - 방 목록

채팅 방 목록을 조회합니다.

**인증:** 불필요 (공개 목록), 인증 필수 (참여 여부 필터)

**쿼리 파라미터:**

| 파라미터 | 타입 | 필수 | 설명 |
|---------|------|------|------|
| level | string | N | 레벨 필터 (beginner, intermediate, advanced) |
| joined | boolean | N | 참여한 방만 (true) |
| limit | number | N | 반환할 아이템 개수 (기본값: 20) |
| cursor | string | N | 페이지네이션 커서 |

**요청:**

```typescript
const response = await authenticatedFetch(
  'https://api-id.execute-api.region.amazonaws.com/dev/chat/rooms?level=beginner&limit=10'
);

const result: ApiResponse<PaginatedResponse<ChatRoom>> = await response.json();
```

**응답:**

```json
{
  "isSuccess": true,
  "message": "방 목록 조회 성공",
  "data": {
    "items": [
      {
        "roomId": "room_001",
        "name": "초급 회화 토론",
        "description": "초급 레벨 학습자들을 위한 회화 연습방",
        "level": "beginner",
        "maxMembers": 6,
        "currentMembers": 3,
        "isPrivate": false,
        "createdBy": "user_123",
        "createdAt": "2024-01-14T10:00:00Z"
      }
    ],
    "nextCursor": "cursor_room123",
    "hasMore": false
  },
  "error": null
}
```

---

##### GET /chat/rooms/{roomId} - 방 상세 조회

특정 채팅 방의 상세 정보를 조회합니다.

**인증:** 불필요

**요청:**

```typescript
const roomId = "room_001";

const response = await fetch(
  `https://api-id.execute-api.region.amazonaws.com/dev/chat/rooms/${roomId}`
);

const result: ApiResponse<ChatRoom> = await response.json();
```

**응답:**

```json
{
  "isSuccess": true,
  "message": "방 상세 조회 성공",
  "data": {
    "roomId": "room_001",
    "name": "초급 회화 토론",
    "description": "초급 레벨 학습자들을 위한 회화 연습방",
    "level": "beginner",
    "maxMembers": 6,
    "currentMembers": 3,
    "isPrivate": false,
    "createdBy": "user_123",
    "createdAt": "2024-01-14T10:00:00Z"
  },
  "error": null
}
```

---

##### POST /chat/rooms/{roomId}/join - 방 참여

채팅 방에 참여합니다. WebSocket 연결에 필요한 roomToken을 받습니다.

**인증:** 필수 (JWT)

**요청:**

```typescript
const roomId = "room_001";

const joinRequest: JoinRoomRequest = {
  roomId: roomId
};

const response = await authenticatedFetch(
  `https://api-id.execute-api.region.amazonaws.com/dev/chat/rooms/${roomId}/join`,
  {
    method: 'POST',
    body: JSON.stringify(joinRequest)
  }
);

const result: ApiResponse<JoinRoomResponse> = await response.json();
```

**응답:**

```json
{
  "isSuccess": true,
  "message": "방에 입장했습니다",
  "data": {
    "roomId": "room_001",
    "roomToken": "token_xyz789",
    "name": "초급 회화 토론",
    "level": "beginner",
    "members": [
      {
        "userId": "user_123",
        "name": "John"
      },
      {
        "userId": "user_456",
        "name": "Jane"
      }
    ]
  },
  "error": null
}
```

---

##### POST /chat/rooms/{roomId}/leave - 방 나가기

채팅 방을 나갑니다.

**인증:** 필수 (JWT)

**요청:**

```typescript
const roomId = "room_001";

const leaveRequest: LeaveRoomRequest = {
  roomId: roomId
};

const response = await authenticatedFetch(
  `https://api-id.execute-api.region.amazonaws.com/dev/chat/rooms/${roomId}/leave`,
  {
    method: 'POST',
    body: JSON.stringify(leaveRequest)
  }
);

const result: ApiResponse<null> = await response.json();
```

**응답:**

```json
{
  "isSuccess": true,
  "message": "방을 나갔습니다",
  "data": null,
  "error": null
}
```

---

##### DELETE /chat/rooms/{roomId} - 방 삭제

채팅 방을 삭제합니다. 방장만 가능합니다.

**인증:** 필수 (JWT)

**요청:**

```typescript
const roomId = "room_001";

const response = await authenticatedFetch(
  `https://api-id.execute-api.region.amazonaws.com/dev/chat/rooms/${roomId}`,
  { method: 'DELETE' }
);

const result: ApiResponse<null> = await response.json();
```

**응답:**

```json
{
  "isSuccess": true,
  "message": "방이 삭제되었습니다",
  "data": null,
  "error": null
}
```

---

#### 3.2 메시지 관리

##### POST /chat/rooms/{roomId}/messages - 메시지 전송

채팅 방에 메시지를 전송합니다. REST API로도 전송 가능하지만, 실시간 채팅은 WebSocket 사용을 권장합니다.

**인증:** 필수 (JWT)

**요청:**

```typescript
const roomId = "room_001";

const sendMessageRequest: SendMessageRequest = {
  roomId: roomId,
  content: "Hello everyone!",
  messageType: "TEXT"
};

const response = await authenticatedFetch(
  `https://api-id.execute-api.region.amazonaws.com/dev/chat/rooms/${roomId}/messages`,
  {
    method: 'POST',
    body: JSON.stringify(sendMessageRequest)
  }
);

const result: ApiResponse<ChatMessage> = await response.json();
```

**응답:**

```json
{
  "isSuccess": true,
  "message": "메시지가 전송되었습니다",
  "data": {
    "messageId": "msg_001",
    "roomId": "room_001",
    "userId": "user_123",
    "userName": "John",
    "content": "Hello everyone!",
    "messageType": "TEXT",
    "createdAt": "2024-01-14T10:00:00Z"
  },
  "error": null
}
```

---

##### GET /chat/rooms/{roomId}/messages - 메시지 목록

채팅 방의 메시지 목록을 조회합니다.

**인증:** 필수 (JWT)

**쿼리 파라미터:**

| 파라미터 | 타입 | 필수 | 설명 |
|---------|------|------|------|
| limit | number | N | 반환할 아이템 개수 (기본값: 50) |
| cursor | string | N | 페이지네이션 커서 |

**요청:**

```typescript
const roomId = "room_001";

const response = await authenticatedFetch(
  `https://api-id.execute-api.region.amazonaws.com/dev/chat/rooms/${roomId}/messages?limit=50`
);

const result: ApiResponse<PaginatedResponse<ChatMessage>> = await response.json();
```

**응답:**

```json
{
  "isSuccess": true,
  "message": "메시지 목록 조회 성공",
  "data": {
    "items": [
      {
        "messageId": "msg_001",
        "roomId": "room_001",
        "userId": "user_123",
        "userName": "John",
        "content": "Hello everyone!",
        "messageType": "TEXT",
        "createdAt": "2024-01-14T10:00:00Z"
      }
    ],
    "nextCursor": "cursor_msg123",
    "hasMore": false
  },
  "error": null
}
```

---

##### GET /chat/rooms/{roomId}/messages/{messageId} - 메시지 상세

특정 메시지의 상세 정보를 조회합니다.

**인증:** 필수 (JWT)

**요청:**

```typescript
const roomId = "room_001";
const messageId = "msg_001";

const response = await authenticatedFetch(
  `https://api-id.execute-api.region.amazonaws.com/dev/chat/rooms/${roomId}/messages/${messageId}`
);

const result: ApiResponse<ChatMessage> = await response.json();
```

**응답:**

```json
{
  "isSuccess": true,
  "message": "메시지 상세 조회 성공",
  "data": {
    "messageId": "msg_001",
    "roomId": "room_001",
    "userId": "user_123",
    "userName": "John",
    "content": "Hello everyone!",
    "messageType": "TEXT",
    "createdAt": "2024-01-14T10:00:00Z"
  },
  "error": null
}
```

---

#### 3.3 음성 합성 (채팅)

##### POST /chat/voice/synthesize - 음성 합성

텍스트를 음성으로 변환합니다.

**인증:** 필수 (JWT)

**요청:**

```typescript
const synthesizeRequest: VoiceSynthesisRequest = {
  text: "Hello everyone! How are you today?",
  voiceId: "Joanna"
};

const response = await authenticatedFetch(
  'https://api-id.execute-api.region.amazonaws.com/dev/chat/voice/synthesize',
  {
    method: 'POST',
    body: JSON.stringify(synthesizeRequest)
  }
);

const result = await response.blob();
// 결과는 음성 파일 (blob)
```

**응답:**

음성 파일 (audio/mpeg)

---

#### 3.4 게임 (Catch-Mind)

##### POST /chat/rooms/{roomId}/game/start - 게임 시작

채팅 방에서 Catch-Mind 게임을 시작합니다.

**인증:** 필수 (JWT)

**요청:**

```typescript
const roomId = "room_001";

const response = await authenticatedFetch(
  `https://api-id.execute-api.region.amazonaws.com/dev/chat/rooms/${roomId}/game/start`,
  { method: 'POST' }
);

const result: ApiResponse<{ gameId: string }> = await response.json();
```

**응답:**

```json
{
  "isSuccess": true,
  "message": "게임이 시작되었습니다",
  "data": {
    "gameId": "game_001"
  },
  "error": null
}
```

---

##### POST /chat/rooms/{roomId}/game/stop - 게임 종료

게임을 종료합니다.

**인증:** 필수 (JWT)

**요청:**

```typescript
const roomId = "room_001";

const response = await authenticatedFetch(
  `https://api-id.execute-api.region.amazonaws.com/dev/chat/rooms/${roomId}/game/stop`,
  { method: 'POST' }
);

const result: ApiResponse<null> = await response.json();
```

**응답:**

```json
{
  "isSuccess": true,
  "message": "게임이 종료되었습니다",
  "data": null,
  "error": null
}
```

---

##### GET /chat/rooms/{roomId}/game/status - 게임 상태

게임의 현재 상태를 조회합니다.

**인증:** 필수 (JWT)

**요청:**

```typescript
const roomId = "room_001";

const response = await authenticatedFetch(
  `https://api-id.execute-api.region.amazonaws.com/dev/chat/rooms/${roomId}/game/status`
);

const result: ApiResponse<GameStatusResponse> = await response.json();
```

**응답:**

```json
{
  "isSuccess": true,
  "message": "게임 상태 조회 성공",
  "data": {
    "roomId": "room_001",
    "isActive": true,
    "currentPlayer": "user_456",
    "gameData": {
      "wordId": "word_042",
      "hint": "A greeting"
    }
  },
  "error": null
}
```

---

##### GET /chat/rooms/{roomId}/game/scores - 점수판

게임 점수판을 조회합니다.

**인증:** 필수 (JWT)

**요청:**

```typescript
const roomId = "room_001";

const response = await authenticatedFetch(
  `https://api-id.execute-api.region.amazonaws.com/dev/chat/rooms/${roomId}/game/scores`
);

const result: ApiResponse<ScoreboardResponse> = await response.json();
```

**응답:**

```json
{
  "isSuccess": true,
  "message": "점수판 조회 성공",
  "data": {
    "roomId": "room_001",
    "scores": [
      {
        "userId": "user_123",
        "userName": "John",
        "score": 250
      },
      {
        "userId": "user_456",
        "userName": "Jane",
        "score": 180
      }
    ]
  },
  "error": null
}
```

---

### 4. 문법 검사 및 AI 대화 (Grammar)

#### 4.1 문법 검사

##### POST /grammar/check - 문법 검사

문장의 문법을 검사합니다.

**인증:** 필수 (JWT)

**요청:**

```typescript
const grammarCheckRequest: GrammarCheckRequest = {
  sentence: "She go to the school.",
  level: "BEGINNER"
};

const response = await authenticatedFetch(
  'https://api-id.execute-api.region.amazonaws.com/dev/grammar/check',
  {
    method: 'POST',
    body: JSON.stringify(grammarCheckRequest)
  }
);

const result: ApiResponse<GrammarCheckResponse> = await response.json();
```

**응답:**

```json
{
  "isSuccess": true,
  "message": "문법 검사 완료",
  "data": {
    "originalSentence": "She go to the school.",
    "correctedSentence": "She goes to school.",
    "score": 70,
    "isCorrect": false,
    "errors": [
      {
        "type": "SUBJECT_VERB_AGREEMENT",
        "original": "go",
        "corrected": "goes",
        "explanation": "'She'는 3인칭 단수이므로 동사 'go'는 'goes'로 변경해야 합니다. (She goes, He goes)",
        "startIndex": 4,
        "endIndex": 6
      },
      {
        "type": "ARTICLE",
        "original": "the school",
        "corrected": "school",
        "explanation": "일반적인 장소(학교, 교회 등)를 나타낼 때는 관사 'the'를 생략합니다. (go to school, go to church)",
        "startIndex": 10,
        "endIndex": 20
      }
    ],
    "feedback": "주어-동사 일치와 관사 사용에 주의하세요. 3인칭 단수 주어에는 동사에 -s/-es를 붙여야 합니다."
  },
  "error": null
}
```

**레벨별 explanation 예시:**

| 레벨 | explanation 스타일 |
|------|-------------------|
| BEGINNER | `"'go'의 과거형은 'went'입니다. (go → went)"` (한국어 포함) |
| INTERMEDIATE | `"Use 'went' for past tense of 'go'. Irregular verbs don't follow the -ed rule."` |
| ADVANCED | `"The verb 'go' is irregular. Past simple: went, Past participle: gone."` |

---

#### 4.2 AI 대화

##### POST /grammar/conversation - AI 대화

AI와의 회화형 학습을 진행합니다.

**인증:** 필수 (JWT)

**요청:**

```typescript
const conversationRequest: ConversationRequest = {
  message: "Hi, how are you today?",
  level: "BEGINNER"
};

const response = await authenticatedFetch(
  'https://api-id.execute-api.region.amazonaws.com/dev/grammar/conversation',
  {
    method: 'POST',
    body: JSON.stringify(conversationRequest)
  }
);

const result: ApiResponse<ConversationResponse> = await response.json();
```

**응답:**

```json
{
  "isSuccess": true,
  "message": "대화 완료",
  "data": {
    "sessionId": "550e8400-e29b-41d4-a716-446655440000",
    "grammarCheck": {
      "originalSentence": "I go to school yesterday",
      "correctedSentence": "I went to school yesterday",
      "score": 75,
      "isCorrect": false,
      "errors": [
        {
          "type": "VERB_TENSE",
          "original": "go",
          "corrected": "went",
          "explanation": "과거 시제에서는 'go'가 'went'로 변합니다. (go → went)",
          "startIndex": 2,
          "endIndex": 4
        }
      ],
      "feedback": "동사 시제에 주의하세요!"
    },
    "aiResponse": "That sounds like a busy day! (바쁜 하루였겠네요!) What did you do at school?",
    "conversationTip": "Try using past tense when talking about yesterday."
  },
  "error": null
}
```

**레벨별 aiResponse 톤:**

| 레벨 | 스타일 |
|------|--------|
| BEGINNER | 짧은 문장, 한국어 번역 포함. `"That sounds fun! (재미있었겠네요!)"` |
| INTERMEDIATE | 자연스러운 일상 영어. `"That sounds lovely! What did you do there?"` |
| ADVANCED | 고급 어휘, 관용어 사용. `"How delightful! What activities did you engage in?"` |

---

##### GET /grammar/sessions - 세션 목록

과거 대화 세션을 조회합니다.

**인증:** 필수 (JWT)

**요청:**

```typescript
const response = await authenticatedFetch(
  'https://api-id.execute-api.region.amazonaws.com/dev/grammar/sessions'
);

const result: ApiResponse<PaginatedResponse<GrammarSession>> = await response.json();
```

**응답:**

```json
{
  "isSuccess": true,
  "message": "세션 목록 조회 성공",
  "data": {
    "items": [
      {
        "sessionId": "session_001",
        "userId": "user_123",
        "startedAt": "2024-01-14T10:00:00Z",
        "updatedAt": "2024-01-14T10:15:00Z",
        "messageCount": 5
      }
    ],
    "nextCursor": "cursor_session123",
    "hasMore": false
  },
  "error": null
}
```

---

##### GET /grammar/sessions/{sessionId} - 세션 상세

특정 세션의 상세 정보를 조회합니다.

**인증:** 필수 (JWT)

**요청:**

```typescript
const sessionId = "session_001";

const response = await authenticatedFetch(
  `https://api-id.execute-api.region.amazonaws.com/dev/grammar/sessions/${sessionId}`
);

const result: ApiResponse<GrammarSession> = await response.json();
```

**응답:**

```json
{
  "isSuccess": true,
  "message": "세션 상세 조회 성공",
  "data": {
    "sessionId": "session_001",
    "userId": "user_123",
    "startedAt": "2024-01-14T10:00:00Z",
    "updatedAt": "2024-01-14T10:15:00Z",
    "messageCount": 5
  },
  "error": null
}
```

---

##### DELETE /grammar/sessions/{sessionId} - 세션 삭제

세션을 삭제합니다.

**인증:** 필수 (JWT)

**요청:**

```typescript
const sessionId = "session_001";

const response = await authenticatedFetch(
  `https://api-id.execute-api.region.amazonaws.com/dev/grammar/sessions/${sessionId}`,
  { method: 'DELETE' }
);

const result: ApiResponse<null> = await response.json();
```

**응답:**

```json
{
  "isSuccess": true,
  "message": "세션이 삭제되었습니다",
  "data": null,
  "error": null
}
```

---

### 5. 통계 (Statistics)

#### 5.1 일/주/월간 통계

##### GET /stats/daily - 일간 통계

일간 학습 통계를 조회합니다.

**인증:** 필수 (JWT)

**요청:**

```typescript
const response = await authenticatedFetch(
  'https://api-id.execute-api.region.amazonaws.com/dev/stats/daily'
);

const result: ApiResponse<DailyStats> = await response.json();
```

**응답:**

```json
{
  "isSuccess": true,
  "message": "일간 통계 조회 성공",
  "data": {
    "date": "2024-01-14",
    "wordsLearned": 5,
    "testsTaken": 2,
    "correctRate": 85.0,
    "spentTime": 300000
  },
  "error": null
}
```

---

##### GET /stats/weekly - 주간 통계

주간 학습 통계를 조회합니다.

**인증:** 필수 (JWT)

**요청:**

```typescript
const response = await authenticatedFetch(
  'https://api-id.execute-api.region.amazonaws.com/dev/stats/weekly'
);

const result: ApiResponse<WeeklyStats> = await response.json();
```

**응답:**

```json
{
  "isSuccess": true,
  "message": "주간 통계 조회 성공",
  "data": {
    "startDate": "2024-01-08",
    "endDate": "2024-01-14",
    "totalWordsLearned": 35,
    "totalTestsTaken": 12,
    "averageCorrectRate": 82.5,
    "totalSpentTime": 2100000,
    "dailyStats": [
      {
        "date": "2024-01-14",
        "wordsLearned": 5,
        "testsTaken": 2,
        "correctRate": 85.0,
        "spentTime": 300000
      }
    ]
  },
  "error": null
}
```

---

##### GET /stats/monthly - 월간 통계

월간 학습 통계를 조회합니다.

**인증:** 필수 (JWT)

**요청:**

```typescript
const response = await authenticatedFetch(
  'https://api-id.execute-api.region.amazonaws.com/dev/stats/monthly'
);

const result: ApiResponse<MonthlyStats> = await response.json();
```

**응답:**

```json
{
  "isSuccess": true,
  "message": "월간 통계 조회 성공",
  "data": {
    "month": "2024-01",
    "totalWordsLearned": 150,
    "totalTestsTaken": 50,
    "averageCorrectRate": 81.5,
    "totalSpentTime": 8700000
  },
  "error": null
}
```

---

##### GET /stats/total - 전체 통계

누적 학습 통계를 조회합니다.

**인증:** 필수 (JWT)

**요청:**

```typescript
const response = await authenticatedFetch(
  'https://api-id.execute-api.region.amazonaws.com/dev/stats/total'
);

const result: ApiResponse<TotalStats> = await response.json();
```

**응답:**

```json
{
  "isSuccess": true,
  "message": "전체 통계 조회 성공",
  "data": {
    "totalWordsLearned": 450,
    "masteredCount": 150,
    "learningCount": 200,
    "totalTestsTaken": 120,
    "averageCorrectRate": 80.5,
    "totalSpentTime": 32400000,
    "streak": 15
  },
  "error": null
}
```

---

##### GET /stats/history - 통계 히스토리

통계 변화 히스토리를 조회합니다.

**인증:** 필수 (JWT)

**쿼리 파라미터:**

| 파라미터 | 타입 | 필수 | 설명 |
|---------|------|------|------|
| days | number | N | 조회할 일수 (기본값: 30) |

**요청:**

```typescript
const response = await authenticatedFetch(
  'https://api-id.execute-api.region.amazonaws.com/dev/stats/history?days=30'
);

const result: ApiResponse<DailyStats[]> = await response.json();
```

**응답:**

```json
{
  "isSuccess": true,
  "message": "통계 히스토리 조회 성공",
  "data": [
    {
      "date": "2024-01-14",
      "wordsLearned": 5,
      "testsTaken": 2,
      "correctRate": 85.0,
      "spentTime": 300000
    },
    {
      "date": "2024-01-13",
      "wordsLearned": 3,
      "testsTaken": 1,
      "correctRate": 80.0,
      "spentTime": 180000
    }
  ],
  "error": null
}
```

---

### 6. 뱃지 (Badges)

#### 6.1 뱃지 조회

##### GET /badges - 전체 뱃지

모든 뱃지를 조회합니다 (획득 여부 포함).

**인증:** 필수 (JWT)

**요청:**

```typescript
const response = await authenticatedFetch(
  'https://api-id.execute-api.region.amazonaws.com/dev/badges'
);

const result: ApiResponse<Badge[]> = await response.json();
```

**응답:**

```json
{
  "isSuccess": true,
  "message": "뱃지 목록 조회 성공",
  "data": [
    {
      "badgeId": "badge_001",
      "name": "첫 단어",
      "description": "첫 번째 단어를 학습했습니다",
      "imageUrl": "https://s3.amazonaws.com/badges/first_word.png",
      "condition": "1개 단어 학습",
      "isEarned": true,
      "earnedAt": "2024-01-10T10:00:00Z"
    },
    {
      "badgeId": "badge_002",
      "name": "100단어 마스터",
      "description": "100개 단어를 마스터했습니다",
      "imageUrl": "https://s3.amazonaws.com/badges/100_words.png",
      "condition": "100개 단어 MASTERED 상태",
      "isEarned": false
    }
  ],
  "error": null
}
```

---

##### GET /badges/earned - 획득한 뱃지

사용자가 획득한 뱃지만 조회합니다.

**인증:** 필수 (JWT)

**요청:**

```typescript
const response = await authenticatedFetch(
  'https://api-id.execute-api.region.amazonaws.com/dev/badges/earned'
);

const result: ApiResponse<Badge[]> = await response.json();
```

**응답:**

```json
{
  "isSuccess": true,
  "message": "획득한 뱃지 조회 성공",
  "data": [
    {
      "badgeId": "badge_001",
      "name": "첫 단어",
      "description": "첫 번째 단어를 학습했습니다",
      "imageUrl": "https://s3.amazonaws.com/badges/first_word.png",
      "condition": "1개 단어 학습",
      "isEarned": true,
      "earnedAt": "2024-01-10T10:00:00Z"
    }
  ],
  "error": null
}
```

---

## 에러 코드 및 처리

### 표준 에러 코드

| 코드 | HTTP 상태 | 메시지 | 설명 |
|------|---------|--------|------|
| AUTH_001 | 401 | 인증이 필요합니다 | JWT 토큰이 없거나 요청에 Authorization 헤더가 없음 |
| AUTH_002 | 403 | 접근 권한이 없습니다 | 사용자가 리소스에 접근할 권한이 없음 |
| AUTH_003 | 401 | 유효하지 않은 토큰입니다 | JWT 토큰이 유효하지 않음 (서명 오류 등) |
| AUTH_004 | 401 | 토큰이 만료되었습니다 | JWT 토큰의 유효 기간이 만료됨 |
| VALIDATION_001 | 400 | 잘못된 입력입니다 | 요청 데이터의 형식이 올바르지 않음 |
| VALIDATION_002 | 400 | 필수 필드가 누락되었습니다 | 필수 필드가 요청에 포함되지 않음 |
| VALIDATION_003 | 400 | 형식이 올바르지 않습니다 | 데이터 형식이 예상된 형식과 다름 |
| VALIDATION_004 | 400 | 값이 허용 범위를 벗어났습니다 | 값이 최소/최대 범위를 벗어남 |
| RESOURCE_001 | 404 | 리소스를 찾을 수 없습니다 | 요청한 리소스가 존재하지 않음 |
| RESOURCE_002 | 409 | 이미 존재하는 리소스입니다 | 중복 생성 시도 (예: 같은 이메일로 가입) |
| RESOURCE_003 | 405 | 허용되지 않는 메서드입니다 | HTTP 메서드가 지원되지 않음 |
| SYSTEM_001 | 500 | 내부 서버 오류가 발생했습니다 | 서버 내부 오류 |
| SYSTEM_002 | 500 | 데이터베이스 오류가 발생했습니다 | DynamoDB 쿼리 오류 |
| SYSTEM_003 | 502 | 외부 API 호출 오류가 발생했습니다 | AWS Bedrock 또는 Polly 호출 실패 |
| SYSTEM_004 | 503 | 서비스를 일시적으로 사용할 수 없습니다 | 서비스 일시적 불가능 (다시 시도하세요) |

### 에러 처리 예제

```typescript
// 일반적인 에러 처리
interface ErrorResponse {
  isSuccess: false;
  data: null;
  message: null;
  error: string;
}

async function handleApiError(response: Response) {
  const errorData: ErrorResponse = await response.json();

  if (response.status === 401) {
    // 인증 에러 - 로그인 페이지로 이동
    if (errorData.error.includes('토큰')) {
      window.location.href = '/login';
    }
  } else if (response.status === 403) {
    // 권한 에러 - 사용자 알림
    alert('접근 권한이 없습니다');
  } else if (response.status === 404) {
    // 리소스 없음
    console.error('리소스를 찾을 수 없습니다:', errorData.error);
  } else if (response.status === 400) {
    // 검증 에러
    console.error('입력 오류:', errorData.error);
  } else if (response.status >= 500) {
    // 서버 에러
    console.error('서버 에러:', errorData.error);
  }
}

// Axios 인터셉터로 통합 에러 처리
apiClient.interceptors.response.use(
  (response) => response,
  (error) => {
    if (error.response) {
      const { status, data } = error.response;

      if (status === 401) {
        // 재로그인 처리
        Auth.signOut();
        window.location.href = '/login';
      } else if (status === 403) {
        // 권한 없음 처리
        throw new Error('접근 권한이 없습니다');
      } else if (status === 404) {
        // 리소스 없음 처리
        throw new Error('리소스를 찾을 수 없습니다');
      } else if (status >= 500) {
        // 서버 에러 처리
        throw new Error('서버 오류가 발생했습니다. 잠시 후 다시 시도하세요');
      }

      throw new Error(data.error || '요청 실패');
    }

    throw error;
  }
);
```

---

## WebSocket 연동 가이드

### WebSocket 엔드포인트

```
wss://{api-id}.execute-api.{region}.amazonaws.com/dev
```

### 연결 순서

1. REST API로 채팅 방에 참여 (`/chat/rooms/{roomId}/join`)
2. `roomToken` 획득
3. WebSocket에 연결하고 `roomToken` 전송
4. 메시지 수신/전송

### WebSocket 연동 예제

```typescript
import { Auth } from 'aws-amplify';

class ChatWebSocketManager {
  private ws: WebSocket | null = null;
  private roomId: string;
  private roomToken: string;
  private userId: string;
  private reconnectAttempts = 0;
  private maxReconnectAttempts = 5;
  private reconnectDelay = 3000;

  constructor(
    roomId: string,
    roomToken: string,
    userId: string
  ) {
    this.roomId = roomId;
    this.roomToken = roomToken;
    this.userId = userId;
  }

  // WebSocket 연결
  connect(onMessageReceived: (message: ChatMessage) => void): Promise<void> {
    return new Promise((resolve, reject) => {
      try {
        const wsUrl = `wss://{api-id}.execute-api.{region}.amazonaws.com/dev`;

        this.ws = new WebSocket(wsUrl);

        this.ws.onopen = () => {
          console.log('WebSocket 연결됨');
          this.reconnectAttempts = 0;

          // 인증 메시지 전송
          this.sendAuthMessage();
          resolve();
        };

        this.ws.onmessage = (event) => {
          try {
            const message = JSON.parse(event.data);
            console.log('메시지 수신:', message);

            if (message.action === 'sendMessage') {
              onMessageReceived(message.data);
            }
          } catch (error) {
            console.error('메시지 파싱 오류:', error);
          }
        };

        this.ws.onerror = (error) => {
          console.error('WebSocket 에러:', error);
          reject(error);
        };

        this.ws.onclose = () => {
          console.log('WebSocket 연결 종료');
          this.attemptReconnect(onMessageReceived);
        };
      } catch (error) {
        reject(error);
      }
    });
  }

  // 인증 메시지 전송
  private sendAuthMessage() {
    const authMessage = {
      action: 'connect',
      roomId: this.roomId,
      roomToken: this.roomToken,
      userId: this.userId
    };

    this.send(authMessage);
  }

  // 메시지 전송
  sendMessage(content: string, messageType: MessageType = 'TEXT') {
    const message = {
      action: 'sendMessage',
      roomId: this.roomId,
      userId: this.userId,
      content: content,
      messageType: messageType,
      timestamp: new Date().toISOString()
    };

    this.send(message);
  }

  // 내부 메서드: 메시지 전송
  private send(message: any) {
    if (this.ws && this.ws.readyState === WebSocket.OPEN) {
      this.ws.send(JSON.stringify(message));
    } else {
      console.error('WebSocket이 연결되지 않았습니다');
    }
  }

  // 재연결 시도
  private attemptReconnect(onMessageReceived: (message: ChatMessage) => void) {
    if (this.reconnectAttempts < this.maxReconnectAttempts) {
      this.reconnectAttempts++;
      console.log(
        `재연결 시도 ${this.reconnectAttempts}/${this.maxReconnectAttempts}`
      );

      setTimeout(() => {
        this.connect(onMessageReceived).catch((error) => {
          console.error('재연결 실패:', error);
        });
      }, this.reconnectDelay);
    } else {
      console.error('최대 재연결 횟수 초과');
    }
  }

  // 연결 종료
  disconnect() {
    if (this.ws) {
      this.ws.close();
      this.ws = null;
    }
  }

  // 연결 상태 확인
  isConnected(): boolean {
    return this.ws !== null && this.ws.readyState === WebSocket.OPEN;
  }
}

// 사용 예제
async function startChatting() {
  try {
    // 1. 방에 참여하여 roomToken 획득
    const joinResponse = await authenticatedFetch(
      'https://api-id.execute-api.region.amazonaws.com/dev/chat/rooms/room_001/join',
      { method: 'POST', body: JSON.stringify({ roomId: 'room_001' }) }
    );

    const joinData: ApiResponse<JoinRoomResponse> = await joinResponse.json();

    if (!joinData.isSuccess) {
      throw new Error(joinData.error);
    }

    const { roomToken } = joinData.data;

    // 2. 사용자 정보 획득
    const user = await Auth.currentAuthenticatedUser();

    // 3. WebSocket 매니저 생성 및 연결
    const chatManager = new ChatWebSocketManager(
      'room_001',
      roomToken,
      user.username
    );

    await chatManager.connect((message: ChatMessage) => {
      console.log('새 메시지:', message);
      // UI에 메시지 표시
      displayMessage(message);
    });

    // 4. 메시지 전송
    chatManager.sendMessage('Hello everyone!', 'TEXT');

    // 5. 연결 종료
    // chatManager.disconnect();

  } catch (error) {
    console.error('채팅 시작 오류:', error);
  }
}

function displayMessage(message: ChatMessage) {
  const messageElement = document.createElement('div');
  messageElement.className = 'message';
  messageElement.innerHTML = `
    <div class="message-header">
      <strong>${message.userName}</strong>
      <span class="timestamp">${new Date(message.createdAt).toLocaleTimeString()}</span>
    </div>
    <div class="message-content">${escapeHtml(message.content)}</div>
  `;

  document.getElementById('messages-container')?.appendChild(messageElement);
}

function escapeHtml(text: string): string {
  const div = document.createElement('div');
  div.textContent = text;
  return div.innerHTML;
}
```

### WebSocket 이벤트

#### $connect

클라이언트가 WebSocket에 처음 연결할 때 발생합니다.

```json
{
  "action": "connect",
  "roomId": "room_001",
  "roomToken": "token_xyz789",
  "userId": "user_123"
}
```

#### $disconnect

클라이언트가 WebSocket 연결을 종료할 때 발생합니다.

```json
{
  "action": "disconnect",
  "roomId": "room_001",
  "userId": "user_123"
}
```

#### sendMessage

메시지를 전송할 때 발생합니다.

```json
{
  "action": "sendMessage",
  "roomId": "room_001",
  "userId": "user_123",
  "content": "Hello everyone!",
  "messageType": "TEXT",
  "timestamp": "2024-01-14T10:00:00Z"
}
```

서버로부터의 응답:

```json
{
  "action": "sendMessage",
  "data": {
    "messageId": "msg_001",
    "roomId": "room_001",
    "userId": "user_123",
    "userName": "John",
    "content": "Hello everyone!",
    "messageType": "TEXT",
    "createdAt": "2024-01-14T10:00:00Z"
  }
}
```

---

## 추가 리소스

### 관련 문서

- AWS Cognito 인증: https://docs.aws.amazon.com/cognito/
- AWS API Gateway: https://docs.aws.amazon.com/apigateway/
- AWS Lambda: https://docs.aws.amazon.com/lambda/
- AWS Amplify: https://docs.amplify.aws/

### 유용한 라이브러리

- **HTTP 클라이언트**: axios, fetch API
- **인증**: AWS Amplify, AWS SDK
- **실시간**: WebSocket API
- **상태 관리**: Redux, Zustand, Jotai
- **UI**: React, Vue, Angular

### 베스트 프랙티스

1. **토큰 관리**
   - 토큰 만료 시 자동 갱신 구현
   - 로컬 스토리지 대신 httpOnly 쿠키 사용 권장
   - 토큰 검증은 항상 백엔드에서 수행

2. **에러 처리**
   - 모든 API 호출에 try-catch 또는 .catch() 구현
   - 사용자 친화적인 에러 메시지 표시
   - 에러 로깅 시스템 구축

3. **성능**
   - API 응답 캐싱 (적절한 TTL 설정)
   - 페이지네이션으로 대량 데이터 처리
   - 이미지 최적화 및 CDN 사용

4. **보안**
   - HTTPS만 사용
   - CORS 설정 확인
   - 민감한 데이터는 암호화
   - 주기적인 보안 업데이트

---

**최종 수정일:** 2024년 1월 14일
**버전:** 1.0.0