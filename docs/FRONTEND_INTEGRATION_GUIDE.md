# Frontend Integration Guide

영어 학습 플랫폼 백엔드 API 연동 가이드

## 1. 환경 설정

### 1.1 엔드포인트 정보

| 서비스 | URL |
|--------|-----|
| REST API | `https://gc8l9ijhzc.execute-api.ap-northeast-2.amazonaws.com/dev` |
| Chat WebSocket | `wss://t378dif43l.execute-api.ap-northeast-2.amazonaws.com/dev` |
| Grammar WebSocket | `wss://ltrccmteo8.execute-api.ap-northeast-2.amazonaws.com/dev` |

### 1.2 Cognito 설정

| 항목 | 값 |
|------|-----|
| User Pool ID | `ap-northeast-2_ezDwzFCzR` |
| Client ID | `4ns077jcr1pkue2vvisr6qdpu5` |
| Region | `ap-northeast-2` |

### 1.3 환경변수 예시 (.env)

```env
VITE_API_URL=https://gc8l9ijhzc.execute-api.ap-northeast-2.amazonaws.com/dev
VITE_WS_URL=wss://t378dif43l.execute-api.ap-northeast-2.amazonaws.com/dev
VITE_GRAMMAR_WS_URL=wss://ltrccmteo8.execute-api.ap-northeast-2.amazonaws.com/dev
VITE_COGNITO_USER_POOL_ID=ap-northeast-2_ezDwzFCzR
VITE_COGNITO_CLIENT_ID=4ns077jcr1pkue2vvisr6qdpu5
VITE_COGNITO_REGION=ap-northeast-2
```

---

## 2. 인증 (Cognito)

### 2.1 AWS Amplify 설정

```typescript
// src/config/amplify.ts
import { Amplify } from 'aws-amplify';

Amplify.configure({
  Auth: {
    Cognito: {
      userPoolId: import.meta.env.VITE_COGNITO_USER_POOL_ID,
      userPoolClientId: import.meta.env.VITE_COGNITO_CLIENT_ID,
      signUpVerificationMethod: 'code',
    }
  }
});
```

### 2.2 회원가입

```typescript
import { signUp, confirmSignUp } from 'aws-amplify/auth';

// 1. 회원가입 요청
const { isSignUpComplete, userId, nextStep } = await signUp({
  username: email,
  password: password,
  options: {
    userAttributes: {
      email: email,
    }
  }
});

// 2. 이메일 인증 코드 확인
await confirmSignUp({
  username: email,
  confirmationCode: code
});
```

### 2.3 로그인

```typescript
import { signIn, fetchAuthSession } from 'aws-amplify/auth';

// 로그인
const { isSignedIn, nextStep } = await signIn({
  username: email,
  password: password
});

// 토큰 가져오기
const session = await fetchAuthSession();
const idToken = session.tokens?.idToken?.toString();
```

### 2.4 API 요청 시 토큰 사용

```typescript
// axios 인터셉터 설정
import axios from 'axios';
import { fetchAuthSession } from 'aws-amplify/auth';

const api = axios.create({
  baseURL: import.meta.env.VITE_API_URL
});

api.interceptors.request.use(async (config) => {
  const session = await fetchAuthSession();
  const token = session.tokens?.idToken?.toString();
  if (token) {
    config.headers.Authorization = token;
  }
  return config;
});
```

---

## 3. API 엔드포인트

### 3.1 공통 응답 형식

```typescript
interface ApiResponse<T> {
  statusCode: number;
  body: {
    success: boolean;
    message: string;
    data: T;
  }
}
```

### 3.2 사용자 프로필 API

| Method | Endpoint | Auth | Description |
|--------|----------|------|-------------|
| GET | `/users/profile/me` | Required | 내 프로필 조회 |
| PUT | `/users/profile/me` | Required | 내 프로필 수정 |
| POST | `/users/profile/me/image` | Required | 프로필 이미지 업로드 |

```typescript
// 프로필 조회
const response = await api.get('/users/profile/me');

// 프로필 수정
await api.put('/users/profile/me', {
  nickname: 'newNickname',
  level: 'INTERMEDIATE'
});

// 프로필 이미지 업로드 (Base64)
await api.post('/users/profile/me/image', {
  imageData: base64ImageData,
  contentType: 'image/png'
});
```

---

### 3.3 단어 API (공개)

| Method | Endpoint | Auth | Description |
|--------|----------|------|-------------|
| GET | `/vocab/words` | None | 단어 목록 조회 |
| GET | `/vocab/words/{wordId}` | None | 단어 상세 조회 |
| GET | `/vocab/words/search?keyword=xxx` | None | 단어 검색 |
| POST | `/vocab/words/batch/get` | None | 단어 일괄 조회 |

```typescript
// 단어 목록 조회
const words = await api.get('/vocab/words', {
  params: { level: 'BEGINNER', limit: 20 }
});

// 단어 검색
const results = await api.get('/vocab/words/search', {
  params: { keyword: 'apple' }
});

// 단어 일괄 조회
const batchWords = await api.post('/vocab/words/batch/get', {
  wordIds: ['word1', 'word2', 'word3']
});
```

---

### 3.4 사용자 단어 학습 API

| Method | Endpoint | Auth | Description |
|--------|----------|------|-------------|
| GET | `/vocab/user-words` | Required | 내 단어 목록 |
| GET | `/vocab/user-words/{wordId}` | Required | 내 단어 상세 |
| PUT | `/vocab/user-words/{wordId}` | Required | 학습 상태 업데이트 |
| PUT | `/vocab/user-words/{wordId}/status` | Required | 상태 변경 (LEARNING/MASTERED) |
| PUT | `/vocab/user-words/{wordId}/tag` | Required | 태그 추가 |
| GET | `/vocab/wrong-answers` | Required | 오답 노트 |

```typescript
// 내 단어 목록
const myWords = await api.get('/vocab/user-words', {
  params: { status: 'LEARNING' }
});

// 단어 마스터 처리
await api.put('/vocab/user-words/word123/status', {
  status: 'MASTERED'
});

// 오답 노트
const wrongAnswers = await api.get('/vocab/wrong-answers');
```

---

### 3.5 일일 학습 API

| Method | Endpoint | Auth | Description |
|--------|----------|------|-------------|
| GET | `/vocab/daily` | Required | 오늘의 학습 단어 |
| POST | `/vocab/daily/words/{wordId}/learned` | Required | 학습 완료 표시 |

```typescript
// 오늘의 단어 가져오기
const dailyWords = await api.get('/vocab/daily');

// 단어 학습 완료
await api.post('/vocab/daily/words/word123/learned');
```

---

### 3.6 단어 그룹 API

| Method | Endpoint | Auth | Description |
|--------|----------|------|-------------|
| GET | `/vocab/groups` | Required | 내 그룹 목록 |
| POST | `/vocab/groups` | Required | 그룹 생성 |
| GET | `/vocab/groups/{groupId}` | Required | 그룹 상세 |
| PUT | `/vocab/groups/{groupId}` | Required | 그룹 수정 |
| DELETE | `/vocab/groups/{groupId}` | Required | 그룹 삭제 |
| POST | `/vocab/groups/{groupId}/words/{wordId}` | Required | 그룹에 단어 추가 |
| DELETE | `/vocab/groups/{groupId}/words/{wordId}` | Required | 그룹에서 단어 제거 |

```typescript
// 그룹 생성
await api.post('/vocab/groups', {
  name: '토익 필수 단어',
  description: '토익 시험 대비 단어장'
});

// 그룹에 단어 추가
await api.post('/vocab/groups/group123/words/word456');
```

---

### 3.7 시험 API

| Method | Endpoint | Auth | Description |
|--------|----------|------|-------------|
| POST | `/vocab/test/start` | Required | 시험 시작 |
| POST | `/vocab/test/submit` | Required | 답안 제출 |
| GET | `/vocab/test/results` | Required | 시험 결과 목록 |
| GET | `/vocab/test/results/{testId}` | Required | 시험 상세 결과 |
| GET | `/vocab/test/tested-words` | Required | 시험 본 단어 목록 |

```typescript
// 시험 시작
const test = await api.post('/vocab/test/start', {
  wordCount: 10,
  testType: 'MULTIPLE_CHOICE', // MULTIPLE_CHOICE, WRITING
  source: 'DAILY' // DAILY, GROUP, WRONG_ANSWERS
});

// 답안 제출
const result = await api.post('/vocab/test/submit', {
  testId: 'test123',
  answers: [
    { wordId: 'word1', answer: 'apple' },
    { wordId: 'word2', answer: 'banana' }
  ]
});

// 시험 결과 조회
const results = await api.get('/vocab/test/results');
```

---

### 3.8 통계 API

| Method | Endpoint | Auth | Description |
|--------|----------|------|-------------|
| GET | `/vocab/stats` | Required | 전체 학습 통계 |
| GET | `/vocab/stats/daily` | Required | 일별 통계 |
| GET | `/vocab/stats/weakness` | Required | 취약점 분석 |
| GET | `/stats/daily` | Required | 일간 상세 통계 |
| GET | `/stats/weekly` | Required | 주간 통계 |
| GET | `/stats/monthly` | Required | 월간 통계 |
| GET | `/stats/total` | Required | 전체 통계 |
| GET | `/stats/history` | Required | 통계 히스토리 |

```typescript
// 전체 통계
const stats = await api.get('/vocab/stats');

// 취약점 분석
const weakness = await api.get('/vocab/stats/weakness');

// 주간 통계
const weeklyStats = await api.get('/stats/weekly');
```

---

### 3.9 배지 API

| Method | Endpoint | Auth | Description |
|--------|----------|------|-------------|
| GET | `/badges` | Required | 전체 배지 목록 |
| GET | `/badges/earned` | Required | 획득한 배지 |

```typescript
// 전체 배지
const allBadges = await api.get('/badges');

// 내 배지
const myBadges = await api.get('/badges/earned');
```

---

### 3.10 음성 합성 API

| Method | Endpoint | Auth | Description |
|--------|----------|------|-------------|
| POST | `/vocab/voice/synthesize` | None | 단어 발음 생성 |
| POST | `/chat/voice/synthesize` | Required | 문장 발음 생성 |

```typescript
// 단어 발음 (공개)
const audio = await api.post('/vocab/voice/synthesize', {
  text: 'apple',
  voiceId: 'Joanna' // 또는 Matthew, Amy 등
});
// 응답: { audioUrl: 's3://...' }
```

---

### 3.11 채팅방 API

| Method | Endpoint | Auth | Description |
|--------|----------|------|-------------|
| POST | `/chat/rooms` | Required | 채팅방 생성 |
| GET | `/chat/rooms` | Required | 채팅방 목록 |
| GET | `/chat/rooms/{roomId}` | Required | 채팅방 상세 |
| DELETE | `/chat/rooms/{roomId}` | Required | 채팅방 삭제 |
| POST | `/chat/rooms/{roomId}/join` | Required | 채팅방 참여 |
| POST | `/chat/rooms/{roomId}/leave` | Required | 채팅방 퇴장 |

```typescript
// 채팅방 생성
const room = await api.post('/chat/rooms', {
  name: '영어 스터디',
  description: '함께 영어 공부해요',
  maxParticipants: 10
});

// 채팅방 참여
await api.post(`/chat/rooms/${roomId}/join`);
```

---

### 3.12 채팅 메시지 API

| Method | Endpoint | Auth | Description |
|--------|----------|------|-------------|
| POST | `/chat/rooms/{roomId}/messages` | Required | 메시지 전송 |
| GET | `/chat/rooms/{roomId}/messages` | Required | 메시지 목록 |
| GET | `/chat/rooms/{roomId}/messages/{messageId}` | Required | 메시지 상세 |

```typescript
// 메시지 전송
await api.post(`/chat/rooms/${roomId}/messages`, {
  content: 'Hello everyone!',
  messageType: 'TEXT' // TEXT, IMAGE, VOICE
});

// 메시지 목록 (페이지네이션)
const messages = await api.get(`/chat/rooms/${roomId}/messages`, {
  params: { limit: 50, lastKey: 'xxx' }
});
```

---

### 3.13 게임 API (Catch Mind)

| Method | Endpoint | Auth | Description |
|--------|----------|------|-------------|
| POST | `/chat/rooms/{roomId}/game/start` | Required | 게임 시작 |
| POST | `/chat/rooms/{roomId}/game/stop` | Required | 게임 종료 |
| GET | `/chat/rooms/{roomId}/game/status` | Required | 게임 상태 |
| GET | `/chat/rooms/{roomId}/game/scores` | Required | 점수 조회 |

```typescript
// 게임 시작
const game = await api.post(`/chat/rooms/${roomId}/game/start`, {
  roundCount: 5,
  roundTimeSeconds: 60
});

// 게임 상태 확인
const status = await api.get(`/chat/rooms/${roomId}/game/status`);
```

---

### 3.14 문법 체크 API

| Method | Endpoint | Auth | Description |
|--------|----------|------|-------------|
| POST | `/grammar/check` | Required | 문법 체크 |
| POST | `/grammar/conversation` | Required | AI 대화 |
| GET | `/grammar/sessions` | Required | 세션 목록 |
| GET | `/grammar/sessions/{sessionId}` | Required | 세션 상세 |
| DELETE | `/grammar/sessions/{sessionId}` | Required | 세션 삭제 |

```typescript
// 문법 체크
const result = await api.post('/grammar/check', {
  text: 'I goes to school yesterday.'
});
// 응답: 교정된 문장, 오류 설명, 학습 팁

// AI 대화
const conversation = await api.post('/grammar/conversation', {
  sessionId: 'session123', // 없으면 새 세션 생성
  message: 'How do I use present perfect tense?'
});
```

---

## 4. WebSocket 연동

### 4.1 채팅 WebSocket

```typescript
// 연결
const ws = new WebSocket(
  `${import.meta.env.VITE_WS_URL}?roomId=${roomId}&token=${idToken}`
);

ws.onopen = () => {
  console.log('Connected to chat');
};

ws.onmessage = (event) => {
  const data = JSON.parse(event.data);

  switch (data.type) {
    case 'MESSAGE':
      // 새 메시지
      handleNewMessage(data.payload);
      break;
    case 'USER_JOINED':
      // 사용자 입장
      handleUserJoined(data.payload);
      break;
    case 'USER_LEFT':
      // 사용자 퇴장
      handleUserLeft(data.payload);
      break;
    case 'GAME_START':
      // 게임 시작
      handleGameStart(data.payload);
      break;
    case 'ROUND_START':
      // 라운드 시작
      handleRoundStart(data.payload);
      break;
    case 'CORRECT_ANSWER':
      // 정답
      handleCorrectAnswer(data.payload);
      break;
    case 'ROUND_END':
      // 라운드 종료
      handleRoundEnd(data.payload);
      break;
    case 'GAME_END':
      // 게임 종료
      handleGameEnd(data.payload);
      break;
  }
};

// 메시지 전송
ws.send(JSON.stringify({
  action: 'sendMessage',
  roomId: roomId,
  content: 'Hello!',
  messageType: 'TEXT'
}));
```

### 4.2 문법 스트리밍 WebSocket

```typescript
// 연결 (JWT 토큰 포함)
const grammarWs = new WebSocket(
  `${import.meta.env.VITE_GRAMMAR_WS_URL}?token=${idToken}`
);

grammarWs.onmessage = (event) => {
  const data = JSON.parse(event.data);

  switch (data.type) {
    case 'STREAM_START':
      // 스트리밍 시작
      break;
    case 'STREAM_CHUNK':
      // AI 응답 청크
      appendToResponse(data.content);
      break;
    case 'STREAM_END':
      // 스트리밍 완료
      break;
    case 'ERROR':
      // 에러
      handleError(data.message);
      break;
  }
};

// 문법 스트리밍 요청
grammarWs.send(JSON.stringify({
  action: 'grammarStreaming',
  sessionId: 'session123',
  message: 'Explain the difference between "affect" and "effect"'
}));
```

---

## 5. 에러 처리

### 5.1 HTTP 상태 코드

| 코드 | 의미 |
|------|------|
| 200 | 성공 |
| 400 | 잘못된 요청 |
| 401 | 인증 실패 (토큰 만료/무효) |
| 403 | 권한 없음 |
| 404 | 리소스 없음 |
| 500 | 서버 오류 |

### 5.2 토큰 갱신

```typescript
import { fetchAuthSession } from 'aws-amplify/auth';

api.interceptors.response.use(
  (response) => response,
  async (error) => {
    if (error.response?.status === 401) {
      // 토큰 갱신 시도
      try {
        const session = await fetchAuthSession({ forceRefresh: true });
        const newToken = session.tokens?.idToken?.toString();

        // 원래 요청 재시도
        error.config.headers.Authorization = newToken;
        return api.request(error.config);
      } catch (refreshError) {
        // 로그아웃 처리
        window.location.href = '/login';
      }
    }
    return Promise.reject(error);
  }
);
```

---

## 6. 타입 정의

```typescript
// types/api.ts

// 사용자
interface User {
  userId: string;
  email: string;
  nickname: string;
  level: 'BEGINNER' | 'INTERMEDIATE' | 'ADVANCED';
  profileUrl: string;
  createdAt: string;
}

// 단어
interface Word {
  wordId: string;
  word: string;
  meaning: string;
  pronunciation: string;
  partOfSpeech: string;
  level: string;
  examples: string[];
}

// 사용자 단어
interface UserWord {
  wordId: string;
  word: Word;
  status: 'NEW' | 'LEARNING' | 'MASTERED';
  correctCount: number;
  wrongCount: number;
  lastStudiedAt: string;
  tags: string[];
}

// 시험
interface Test {
  testId: string;
  testType: 'MULTIPLE_CHOICE' | 'WRITING';
  wordCount: number;
  questions: TestQuestion[];
  startedAt: string;
}

interface TestQuestion {
  wordId: string;
  word: string;
  options?: string[]; // 객관식일 경우
}

interface TestResult {
  testId: string;
  score: number;
  correctCount: number;
  totalCount: number;
  answers: TestAnswer[];
  completedAt: string;
}

// 채팅
interface ChatRoom {
  roomId: string;
  name: string;
  description: string;
  ownerId: string;
  participants: Participant[];
  maxParticipants: number;
  createdAt: string;
}

interface ChatMessage {
  messageId: string;
  roomId: string;
  senderId: string;
  senderNickname: string;
  content: string;
  messageType: 'TEXT' | 'IMAGE' | 'VOICE' | 'SYSTEM';
  createdAt: string;
}

// 게임
interface GameState {
  roomId: string;
  status: 'WAITING' | 'PLAYING' | 'FINISHED';
  currentRound: number;
  totalRounds: number;
  currentWord?: string;
  drawerId?: string;
  scores: Record<string, number>;
}

// 배지
interface Badge {
  badgeId: string;
  name: string;
  description: string;
  iconUrl: string;
  condition: string;
}

interface EarnedBadge extends Badge {
  earnedAt: string;
}
```

---

## 7. 주의사항

1. **인증 토큰**: 대부분의 API는 `Authorization` 헤더에 Cognito ID Token이 필요합니다.

2. **CORS**: 백엔드에서 `*` origin을 허용하므로 로컬 개발 시 별도 설정 불필요합니다.

3. **WebSocket 연결**: 연결 시 쿼리 파라미터로 `token`을 전달해야 합니다.

4. **페이지네이션**: 목록 API는 `limit`과 `lastKey`를 사용합니다.

5. **파일 업로드**: Base64 인코딩하여 전송합니다.

---

## 8. 연락처

문의사항이 있으시면 백엔드 팀에 연락해주세요.
