# Catchmind 게임 프론트엔드 연동 가이드

## 목차

1. [개요](#개요)
2. [아키텍처](#아키텍처)
3. [WebSocket 연결](#websocket-연결)
4. [메시지 구조](#메시지-구조)
5. [게임 흐름](#게임-흐름)
6. [REST API](#rest-api)
7. [타이머 동기화](#타이머-동기화)
8. [게임 자동 종료](#게임-자동-종료)
9. [재접속 처리](#재접속-처리)
10. [에러 처리](#에러-처리)

---

## 개요

Catchmind는 실시간 그림 맞추기 게임입니다. WebSocket을 통한 실시간 통신과 REST API를 통한 게임 세션 관리를 지원합니다.

### 주요 특징

- **실시간 통신**: WebSocket 기반 양방향 통신
- **도메인 분리**: `chat` / `game` 도메인으로 메시지 라우팅
- **타이머 동기화**: `serverTime` 필드를 통한 클라이언트-서버 시간 동기화
- **자동 종료**: 게임 시작 7분 후 자동 종료
- **재접속 지원**: 게임 세션 API를 통한 상태 복원

---

## 아키텍처

```
┌─────────────┐     WebSocket      ┌──────────────────┐
│   Frontend  │◄──────────────────►│  API Gateway WS  │
│   (React)   │                    └────────┬─────────┘
│             │                             │
│             │     REST API        ┌───────▼─────────┐
│             │◄───────────────────►│  API Gateway    │
└─────────────┘                    │  REST           │
                                   └────────┬────────┘
                                            │
                              ┌─────────────┼─────────────┐
                              │             │             │
                        ┌─────▼────┐  ┌─────▼────┐  ┌─────▼────┐
                        │ WS Msg   │  │ Game     │  │ Game     │
                        │ Handler  │  │ Handler  │  │ Session  │
                        └──────────┘  └──────────┘  │ Handler  │
                                                    └──────────┘
```

---

## WebSocket 연결

### 연결 URL

```
wss://{api-id}.execute-api.{region}.amazonaws.com/dev?roomToken={token}
```

### 연결 절차

1. REST API로 방 토큰 발급 (`POST /chat/rooms/{roomId}/join`)
2. 토큰으로 WebSocket 연결
3. 연결 성공 시 자동으로 방에 입장

### 연결 예시 (TypeScript)

```typescript
const connectWebSocket = (roomToken: string): WebSocket => {
  const ws = new WebSocket(
    `wss://xxx.execute-api.ap-northeast-2.amazonaws.com/dev?roomToken=${roomToken}`
  );

  ws.onopen = () => console.log('WebSocket connected');
  ws.onmessage = (event) => handleMessage(JSON.parse(event.data));
  ws.onerror = (error) => console.error('WebSocket error:', error);
  ws.onclose = () => console.log('WebSocket closed');

  return ws;
};
```

---

## 메시지 구조

### 공통 메시지 포맷

모든 WebSocket 메시지는 다음 필드를 포함합니다:

```typescript
interface BaseMessage {
  domain: 'chat' | 'game';        // 도메인 구분
  messageType: string;             // 메시지 타입
  messageId: string;               // 고유 메시지 ID
  roomId: string;                  // 방 ID
  userId: string;                  // 발신자 ID (시스템: "SYSTEM")
  content?: string;                // 메시지 내용
  createdAt: string;               // ISO 8601 형식 시간
  timestamp: number;               // Unix timestamp (ms)
}
```

### 도메인 구분

| 도메인    | 설명     | 메시지 타입                                                                                    |
|--------|--------|-------------------------------------------------------------------------------------------|
| `chat` | 채팅 메시지 | text, image, voice, ai_response                                                           |
| `game` | 게임 메시지 | game_start, game_end, round_start, round_end, drawing, correct_answer, score_update, hint |

### 메시지 라우팅 예시

```typescript
const handleMessage = (message: BaseMessage) => {
  if (message.domain === 'chat') {
    handleChatMessage(message);
  } else if (message.domain === 'game') {
    handleGameMessage(message);
  }
};
```

---

## 게임 흐름

### 게임 상태 (GameStatus)

```typescript
type GameStatus = 'NONE' | 'WAITING' | 'PLAYING' | 'ROUND_END' | 'FINISHED';
```

### 전체 흐름

```
[대기] ─── /game 시작 ───► [게임 시작] ─► [라운드 1] ─► [라운드 종료]
                              │                              │
                              │         ◄───────────────────┘
                              │                    (반복)
                              ▼
                         [게임 종료]
                              │
                         ┌────┴────┐
                         │         │
                    수동 종료   자동 종료
                    (7분 경과)
```

### 1. 게임 시작 (game_start)

**수신 메시지:**

```json
{
  "domain": "game",
  "messageType": "game_start",
  "messageId": "uuid",
  "roomId": "room-123",
  "userId": "SYSTEM",
  "content": "🎮 게임 시작!\n총 5 라운드\n\n라운드 1 시작!\n출제자: user-1",
  "createdAt": "2024-01-20T10:00:00Z",
  "timestamp": 1705746000000,
  "serverTime": 1705746000000,
  "gameStatus": "PLAYING",
  "currentRound": 1,
  "totalRounds": 5,
  "currentDrawerId": "user-1",
  "drawerOrder": ["user-1", "user-2", "user-3"],
  "roundStartTime": 1705746000000,
  "roundDuration": 60
}
```

**프론트엔드 처리:**

```typescript
const handleGameStart = (message: GameStartMessage) => {
  setGameStatus('PLAYING');
  setCurrentRound(message.currentRound);
  setTotalRounds(message.totalRounds);
  setCurrentDrawer(message.currentDrawerId);
  setDrawerOrder(message.drawerOrder);

  // 타이머 동기화
  startTimer(message.roundStartTime, message.roundDuration, message.serverTime);

  // 현재 사용자가 출제자인지 확인
  setIsDrawer(message.currentDrawerId === currentUserId);
};
```

### 2. 그림 데이터 전송/수신 (drawing)

**전송 (출제자만):**

```typescript
const sendDrawing = (drawingData: DrawingData) => {
  ws.send(JSON.stringify({
    action: 'sendMessage',
    messageType: 'drawing',
    content: JSON.stringify(drawingData)
  }));
};
```

**수신 메시지:**

```json
{
  "domain": "game",
  "messageType": "drawing",
  "messageId": "uuid",
  "roomId": "room-123",
  "userId": "user-1",
  "content": "{\"type\":\"path\",\"points\":[...],\"color\":\"#000\",\"width\":3}",
  "timestamp": 1705746010000
}
```

### 3. 정답 체크

**채팅 메시지로 자동 체크됩니다:**

```typescript
const sendAnswer = (answer: string) => {
  ws.send(JSON.stringify({
    action: 'sendMessage',
    messageType: 'text',
    content: answer
  }));
};
```

### 4. 정답 알림 (correct_answer)

**수신 메시지:**

```json
{
  "domain": "game",
  "messageType": "correct_answer",
  "roomId": "room-123",
  "userId": "user-2",
  "content": "🎉 user-2님이 정답을 맞혔습니다! (+35점)",
  "timestamp": 1705746030000,
  "serverTime": 1705746030000,
  "score": 35,
  "elapsedTime": 30000,
  "allCorrect": false,
  "scores": {
    "user-1": 5,
    "user-2": 35
  }
}
```

### 5. 점수 업데이트 (score_update)

**수신 메시지:**

```json
{
  "domain": "game",
  "messageType": "score_update",
  "roomId": "room-123",
  "timestamp": 1705746030000,
  "scores": {
    "user-1": 15,
    "user-2": 35,
    "user-3": 20
  },
  "lastScorer": "user-2",
  "lastScore": 35
}
```

### 6. 라운드 종료 (round_end)

**수신 메시지:**

```json
{
  "domain": "game",
  "messageType": "round_end",
  "roomId": "room-123",
  "content": "라운드 1 종료! 정답: 사과\n\n라운드 2 시작! 출제자: user-2",
  "timestamp": 1705746060000,
  "serverTime": 1705746060000,
  "data": {
    "answer": "사과",
    "currentRound": 1,
    "totalRounds": 5,
    "nextRound": 2,
    "nextDrawer": "user-2",
    "nextWord": {
      "wordId": "word-123",
      "korean": "바나나"
    },
    "roundStartTime": 1705746060000,
    "roundDuration": 60,
    "ranking": [
      { "rank": 1, "userId": "user-2", "score": 35 },
      { "rank": 2, "userId": "user-3", "score": 20 },
      { "rank": 3, "userId": "user-1", "score": 15 }
    ]
  }
}
```

**프론트엔드 처리:**

```typescript
const handleRoundEnd = (message: RoundEndMessage) => {
  const { data } = message;

  // 정답 표시
  showAnswer(data.answer);

  // 순위 표시
  showRanking(data.ranking);

  // 다음 라운드 준비
  if (data.nextRound) {
    setCurrentRound(data.nextRound);
    setCurrentDrawer(data.nextDrawer);
    setIsDrawer(data.nextDrawer === currentUserId);

    // 출제자에게만 단어 표시
    if (data.nextDrawer === currentUserId && data.nextWord) {
      setCurrentWord(data.nextWord.korean);
    }

    // 타이머 재시작
    startTimer(data.roundStartTime, data.roundDuration, message.serverTime);

    // 캔버스 초기화
    clearCanvas();
  }
};
```

### 7. 게임 종료 (game_end)

**수신 메시지:**

```json
{
  "domain": "game",
  "messageType": "game_end",
  "roomId": "room-123",
  "content": "🎮 게임 종료!\n\n📊 최종 순위:\n  🥇 user-2: 120점\n  🥈 user-3: 95점\n  🥉 user-1: 80점",
  "timestamp": 1705746300000,
  "reason": "COMPLETED"
}
```

**종료 사유 (reason):**
| 값 | 설명 |
|----|------|
| `COMPLETED` | 모든 라운드 완료 |
| `STOPPED` | 수동 종료 |
| `TIME_EXPIRED` | 7분 시간 초과 |
| `NOT_ENOUGH_PLAYERS` | 인원 부족 |

---

## REST API

### 게임 시작

```http
POST /chat/rooms/{roomId}/game/start
Authorization: Bearer {accessToken}
```

**Response:**

```json
{
  "success": true,
  "message": "Game started",
  "data": {
    "gameSessionId": "session-123",
    "roomId": "room-123",
    "status": "PLAYING",
    "currentRound": 1,
    "totalRounds": 5,
    "currentDrawerId": "user-1",
    "roundStartTime": 1705746000000,
    "serverTime": 1705746000000,
    "roundDuration": 60,
    "drawerOrder": ["user-1", "user-2", "user-3"],
    "currentWord": {
      "wordId": "word-1",
      "word": "사과"
    }
  }
}
```

> **Note:** `currentWord`는 출제자에게만 포함됩니다.

### 게임 종료

```http
POST /chat/rooms/{roomId}/game/stop
Authorization: Bearer {accessToken}
```

### 게임 상태 조회

```http
GET /chat/rooms/{roomId}/game/status
Authorization: Bearer {accessToken}
```

### 게임 세션 조회 (재접속용)

```http
GET /games/{gameSessionId}
Authorization: Bearer {accessToken}
```

**Response:**

```json
{
  "success": true,
  "message": "Game session retrieved",
  "data": {
    "gameSessionId": "session-123",
    "roomId": "room-123",
    "gameType": "catchmind",
    "status": "PLAYING",
    "currentRound": 3,
    "totalRounds": 5,
    "currentDrawerId": "user-2",
    "roundStartTime": 1705746180000,
    "serverTime": 1705746200000,
    "roundDuration": 60,
    "scores": {
      "user-1": 45,
      "user-2": 60,
      "user-3": 30
    },
    "players": ["user-1", "user-2", "user-3"],
    "drawerOrder": ["user-1", "user-2", "user-3"],
    "hintUsed": false,
    "currentWord": {
      "wordId": "word-5",
      "word": "바나나"
    }
  }
}
```

> **Note:** `currentWord`는 출제자에게만 포함됩니다.

---

## 타이머 동기화

### 문제

클라이언트와 서버 시간 차이로 인한 타이머 불일치

### 해결책

`serverTime` 필드를 사용하여 서버 시간 기준 타이머 계산

### 구현 예시

```typescript
interface TimerSync {
  roundStartTime: number;  // 라운드 시작 시간 (서버 기준)
  roundDuration: number;   // 라운드 지속 시간 (초)
  serverTime: number;      // 메시지 발송 시점의 서버 시간
}

const startTimer = (
  roundStartTime: number,
  roundDuration: number,
  serverTime: number
) => {
  // 서버에서 이미 경과한 시간 계산
  const elapsedOnServer = serverTime - roundStartTime;

  // 남은 시간 계산 (밀리초)
  const remainingTime = (roundDuration * 1000) - elapsedOnServer;

  // 음수 방지
  const safeRemainingTime = Math.max(0, remainingTime);

  setRemainingTime(safeRemainingTime);

  // 타이머 시작
  const interval = setInterval(() => {
    setRemainingTime((prev) => {
      if (prev <= 1000) {
        clearInterval(interval);
        return 0;
      }
      return prev - 1000;
    });
  }, 1000);

  return () => clearInterval(interval);
};
```

### React Hook 예시

```typescript
const useGameTimer = (timerSync: TimerSync | null) => {
  const [remainingSeconds, setRemainingSeconds] = useState(0);

  useEffect(() => {
    if (!timerSync) return;

    const { roundStartTime, roundDuration, serverTime } = timerSync;
    const elapsed = (serverTime - roundStartTime) / 1000;
    const remaining = Math.max(0, roundDuration - elapsed);

    setRemainingSeconds(Math.ceil(remaining));

    const interval = setInterval(() => {
      setRemainingSeconds((prev) => Math.max(0, prev - 1));
    }, 1000);

    return () => clearInterval(interval);
  }, [timerSync]);

  return remainingSeconds;
};
```

---

## 게임 자동 종료

### 개요

게임 시작 후 7분(420초)이 경과하면 자동으로 종료됩니다.

### 자동 종료 메시지

```json
{
  "domain": "game",
  "messageType": "game_end",
  "roomId": "room-123",
  "userId": "SYSTEM",
  "content": "⏰ 시간 초과! 🎮 게임 종료!\n\n📊 최종 순위:\n  🥇 user-2: 120점\n  🥈 user-1: 95점",
  "timestamp": 1705746420000,
  "reason": "TIME_EXPIRED"
}
```

### 프론트엔드 처리

```typescript
const handleGameEnd = (message: GameEndMessage) => {
  setGameStatus('FINISHED');

  // 종료 사유에 따른 UI 처리
  if (message.reason === 'TIME_EXPIRED') {
    showNotification('시간 초과로 게임이 종료되었습니다.');
  } else if (message.reason === 'STOPPED') {
    showNotification('게임이 수동으로 종료되었습니다.');
  }

  // 최종 결과 표시
  showFinalResults(message.content);

  // 캔버스 초기화
  clearCanvas();
};
```

---

## 재접속 처리

### 시나리오

사용자가 게임 중 연결이 끊어졌다가 다시 접속하는 경우

### 처리 절차

1. WebSocket 재연결
2. 게임 세션 API로 현재 상태 조회
3. UI 상태 복원
4. 타이머 동기화

### 구현 예시

```typescript
const handleReconnect = async (roomId: string, gameSessionId: string) => {
  // 1. WebSocket 재연결
  const roomToken = await getRoomToken(roomId);
  connectWebSocket(roomToken);

  // 2. 게임 세션 조회
  const session = await fetchGameSession(gameSessionId);

  if (session.status === 'PLAYING') {
    // 3. UI 상태 복원
    setGameStatus('PLAYING');
    setCurrentRound(session.currentRound);
    setScores(session.scores);
    setCurrentDrawer(session.currentDrawerId);
    setIsDrawer(session.currentDrawerId === currentUserId);

    // 출제자인 경우 단어 설정
    if (session.currentWord) {
      setCurrentWord(session.currentWord.word);
    }

    // 4. 타이머 동기화
    startTimer(
      session.roundStartTime,
      session.roundDuration,
      session.serverTime
    );
  } else if (session.status === 'FINISHED') {
    setGameStatus('FINISHED');
  }
};
```

---

## 에러 처리

### WebSocket 에러 코드

| 코드   | 설명     | 처리 방법        |
|------|--------|--------------|
| 1000 | 정상 종료  | -            |
| 1001 | 서버 종료  | 재연결 시도       |
| 1006 | 비정상 종료 | 재연결 시도       |
| 4001 | 인증 실패  | 토큰 재발급 후 재연결 |
| 4003 | 권한 없음  | 에러 표시        |

### REST API 에러 코드

| 코드         | 설명                    |
|------------|-----------------------|
| `GAME_001` | 게임 시작 실패              |
| `GAME_002` | 게임 중단 실패              |
| `GAME_003` | 진행 중인 게임 없음           |
| `GAME_004` | 이미 게임 진행 중            |
| `GAME_005` | 권한 없음 (게임 시작자만 중단 가능) |
| `GAME_006` | 게임 세션을 찾을 수 없음        |

### 에러 처리 예시

```typescript
const handleError = (error: ApiError) => {
  switch (error.code) {
    case 'GAME_001':
      showNotification('게임을 시작할 수 없습니다. 최소 2명이 필요합니다.');
      break;
    case 'GAME_004':
      showNotification('이미 게임이 진행 중입니다.');
      break;
    case 'GAME_006':
      // 게임 세션 만료 - 목록으로 이동
      navigateToRoomList();
      break;
    default:
      showNotification('오류가 발생했습니다.');
  }
};
```

---

## 전체 상태 관리 예시 (React)

```typescript
interface GameState {
  status: GameStatus;
  currentRound: number;
  totalRounds: number;
  currentDrawerId: string | null;
  currentWord: string | null;
  scores: Record<string, number>;
  isDrawer: boolean;
  remainingTime: number;
  drawerOrder: string[];
}

const initialGameState: GameState = {
  status: 'NONE',
  currentRound: 0,
  totalRounds: 0,
  currentDrawerId: null,
  currentWord: null,
  scores: {},
  isDrawer: false,
  remainingTime: 0,
  drawerOrder: [],
};

const gameReducer = (state: GameState, action: GameAction): GameState => {
  switch (action.type) {
    case 'GAME_START':
      return {
        ...state,
        status: 'PLAYING',
        currentRound: action.payload.currentRound,
        totalRounds: action.payload.totalRounds,
        currentDrawerId: action.payload.currentDrawerId,
        drawerOrder: action.payload.drawerOrder,
        isDrawer: action.payload.currentDrawerId === action.payload.currentUserId,
        scores: {},
      };

    case 'ROUND_END':
      return {
        ...state,
        currentRound: action.payload.nextRound,
        currentDrawerId: action.payload.nextDrawer,
        currentWord: action.payload.isDrawer ? action.payload.nextWord : null,
        isDrawer: action.payload.isDrawer,
      };

    case 'SCORE_UPDATE':
      return {
        ...state,
        scores: action.payload.scores,
      };

    case 'GAME_END':
      return {
        ...initialGameState,
        status: 'FINISHED',
        scores: state.scores,
      };

    case 'RESET':
      return initialGameState;

    default:
      return state;
  }
};
```

---

## 버전 이력

| 버전    | 날짜         | 변경 내용               |
|-------|------------|---------------------|
| 1.0.0 | 2024-01-20 | 초기 문서 작성            |
| 1.1.0 | 2024-01-20 | 게임 자동 종료 (7분) 기능 추가 |
