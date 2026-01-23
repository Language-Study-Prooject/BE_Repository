# 채팅방 / 캐치마인드 게임 분리 - 종합 솔루션

## 1. 현재 문제점 분석

### 1.1 백엔드 현황

```
ChatRoom.java (현재 - 혼합 모델)
├── 채팅 필드
│   ├── roomId, name, description
│   ├── memberIds, currentMembers
│   └── lastMessageAt
│
└── 게임 필드 (여기에 섞여있음)
    ├── gameStatus, gameStartedBy
    ├── currentRound, totalRounds
    ├── currentDrawerId, currentWord
    ├── roundStartTime, roundTimeLimit  ← serverTime 없음!
    ├── scores, streaks
    └── correctGuessers
```

**문제점:**

1. `roundStartTime`만 전송, `serverTime` 누락 → 클라이언트 타이머 동기화 불가
2. 게임 세션이 채팅방에 종속 → 게임 상태 독립 관리 불가
3. 재접속 시 게임 상태 복구 어려움
4. 게임 종료 후 상태 정리 복잡

### 1.2 WebSocket 메시지 현황

```java
// WebSocketMessageHandler.java - 현재 구조
handleRequest() {
    switch (messageType) {
        case "DRAWING", "DRAWING_CLEAR" -> handleDrawingMessage()  // 게임
        default -> handleRegularMessage() {
            // 1. 슬래시 명령어 처리 (/start, /stop, /score...)
            // 2. 게임 중 정답 체크
            // 3. 일반 채팅 메시지
        }
    }
}
```

**문제점:**

- 채팅/게임 구분 없이 모든 메시지가 동일 핸들러에서 처리
- 메시지에 `domain` 필드 없음

---

## 2. 최적 솔루션

### 2.1 아키텍처 개요

```
┌─────────────────────────────────────────────────────────────────┐
│                      WebSocket (단일 엔드포인트 유지)              │
│                                                                  │
│  ┌──────────────────────┐    ┌────────────────────────────────┐ │
│  │   domain: "chat"     │    │      domain: "game"            │ │
│  │                      │    │                                │ │
│  │  • TEXT              │    │  • GAME_START / GAME_END       │ │
│  │  • USER_JOIN         │    │  • ROUND_START / ROUND_END     │ │
│  │  • USER_LEAVE        │    │  • DRAWING / DRAWING_CLEAR     │ │
│  │  • SYSTEM            │    │  • GUESS / CORRECT_ANSWER      │ │
│  │                      │    │  • SCORE_UPDATE / HINT         │ │
│  └──────────────────────┘    └────────────────────────────────┘ │
│                                                                  │
│                    GameSession (별도 모델)                        │
│                    ├── gameSessionId                            │
│                    ├── roomId (연결용)                           │
│                    ├── status, currentRound                     │
│                    ├── roundStartTime + serverTime ← 핵심!       │
│                    └── scores, players                          │
└─────────────────────────────────────────────────────────────────┘
```

### 2.2 핵심 변경사항

| 구분  | 현재                   | 변경 후                            |
|-----|----------------------|---------------------------------|
| 모델  | `ChatRoom`에 게임 필드 포함 | `ChatRoom` + `GameSession` 분리   |
| 타이머 | `roundStartTime`만 전송 | `roundStartTime` + `serverTime` |
| 메시지 | `messageType`만 존재    | `domain` + `messageType`        |
| API | 채팅방 API만 존재          | 게임 세션 API 추가                    |

---

## 3. 백엔드 변경사항

### 3.1 Phase 1: 타이머 버그 수정 (즉시)

**변경 파일:** `WebSocketMessageHandler.java`

```java
// GAME_START 메시지에 serverTime 추가
private void broadcastGameStart(...) {
    Map<String, Object> message = new HashMap<>();
    // ... 기존 코드 ...

    message.put("roundStartTime", gameResult.room().getRoundStartTime());
    message.put("serverTime", System.currentTimeMillis());  // 추가!
    message.put("roundDuration", gameResult.room().getRoundTimeLimit()); // 명확한 이름

    // ...
}

// ROUND_END → ROUND_START 메시지에도 동일하게 추가
private void broadcastRoundEnd(...) {
    // ...
    messageData.put("roundStartTime", room.getRoundStartTime());
    messageData.put("serverTime", System.currentTimeMillis());  // 추가!
    messageData.put("roundDuration", room.getRoundTimeLimit());
    // ...
}
```

**예상 작업량:** 30분

### 3.2 Phase 2: 메시지 구조 개선 (1일)

**변경 파일:** `WebSocketMessageHandler.java`, 모든 브로드캐스트 메서드

```java
// 모든 메시지에 domain 필드 추가
private Map<String, Object> createMessage(String domain, String messageType, Object data) {
    Map<String, Object> message = new HashMap<>();
    message.put("domain", domain);  // "chat" 또는 "game"
    message.put("messageType", messageType);
    message.put("data", data);
    message.put("timestamp", System.currentTimeMillis());
    return message;
}

// 채팅 메시지
createMessage("chat", "TEXT", chatData);
createMessage("chat", "USER_JOIN", joinData);

// 게임 메시지
createMessage("game", "GAME_START", gameStartData);
createMessage("game", "ROUND_START", roundStartData);
createMessage("game", "DRAWING", drawingData);
```

### 3.3 Phase 3: 게임 세션 분리 (1주)

#### 3.3.1 새 모델: GameSession.java

```java
@DynamoDbBean
public class GameSession {
    private String pk;              // GAME#{gameSessionId}
    private String sk;              // METADATA
    private String gsi1pk;          // ROOM#{roomId}
    private String gsi1sk;          // GAME#{createdAt}

    // 게임 식별
    private String gameSessionId;
    private String roomId;          // 연결된 채팅방
    private String gameType;        // "catchmind"

    // 게임 상태
    private String status;          // WAITING, PLAYING, FINISHED
    private String startedBy;
    private Long startedAt;
    private Long endedAt;

    // 라운드 정보
    private Integer currentRound;
    private Integer totalRounds;
    private String currentDrawerId;
    private String currentWordId;
    private String currentWord;
    private Long roundStartTime;
    private Integer roundDuration;

    // 점수
    private Map<String, Integer> scores;
    private Map<String, Integer> streaks;
    private List<String> players;
    private List<String> drawerOrder;

    // 자동 종료
    private Long gameEndScheduledAt;
    private String scheduleRuleArn;

    // TTL
    private Long ttl;
}
```

#### 3.3.2 ChatRoom에서 게임 필드 제거

```java
@DynamoDbBean
public class ChatRoom {
    // 채팅 필드만 유지
    private String roomId;
    private String name;
    private String description;
    private String level;
    private Integer currentMembers;
    private Integer maxMembers;
    private Boolean isPrivate;
    private String password;
    private String createdBy;
    private String createdAt;
    private String lastMessageAt;
    private List<String> memberIds;

    // 게임 연결 (참조만)
    private String activeGameSessionId;  // 현재 진행중인 게임 세션 ID

    // 게임 필드 모두 제거!
    // - gameStatus, gameStartedBy, currentRound... 전부 GameSession으로 이동
}
```

#### 3.3.3 게임 세션 API

```
# 게임 세션 생성
POST /api/chat/rooms/{roomId}/games
Request:
{
    "gameType": "catchmind",
    "settings": {
        "totalRounds": 5,
        "roundDuration": 60
    }
}

Response:
{
    "gameSessionId": "game-abc123",
    "roomId": "room-xyz",
    "status": "WAITING",
    "createdAt": "2024-01-20T10:00:00Z"
}

# 게임 상태 조회 (재접속 시 필수!)
GET /api/games/{gameSessionId}

Response:
{
    "gameSessionId": "game-abc123",
    "roomId": "room-xyz",
    "status": "PLAYING",
    "currentRound": 2,
    "totalRounds": 5,
    "currentDrawerId": "user123",
    "roundStartTime": 1705744800000,
    "serverTime": 1705744830000,      // 핵심!
    "roundDuration": 60,
    "scores": {
        "user1": 150,
        "user2": 120
    },
    "players": ["user1", "user2", "user3"]
}

# 게임 시작 (기존 /start 명령어 대체)
POST /api/games/{gameSessionId}/start

# 게임 종료
POST /api/games/{gameSessionId}/stop
```

---

## 4. 프론트엔드 변경사항

### 4.1 Phase 1: 타이머 버그 수정 (즉시)

```javascript
// useTimer.js - 독립적인 타이머 훅
export function useTimer(roundStartTime, roundDuration, serverTime) {
    const [remainingTime, setRemainingTime] = useState(roundDuration);

    useEffect(() => {
        if (!roundStartTime || !roundDuration) return;

        // 서버-클라이언트 시간 차이 보정
        const timeOffset = serverTime ? (Date.now() - serverTime) : 0;

        const interval = setInterval(() => {
            const adjustedNow = Date.now() - timeOffset;
            const elapsed = Math.floor((adjustedNow - roundStartTime) / 1000);
            const remaining = Math.max(0, roundDuration - elapsed);
            setRemainingTime(remaining);

            if (remaining <= 0) {
                clearInterval(interval);
            }
        }, 100);

        return () => clearInterval(interval);
    }, [roundStartTime, roundDuration, serverTime]);

    return remainingTime;
}
```

### 4.2 Phase 2: 메시지 핸들러 분리

```javascript
// WebSocket 메시지 핸들러
onMessage(event) {
    const message = JSON.parse(event.data);

    switch (message.domain) {
        case 'chat':
            this.handleChatMessage(message);
            break;
        case 'game':
            this.handleGameMessage(message);
            break;
    }
}

handleChatMessage(message) {
    switch (message.messageType) {
        case 'TEXT': // 채팅 메시지
        case 'USER_JOIN':
        case 'USER_LEAVE':
        case 'SYSTEM':
    }
}

handleGameMessage(message) {
    switch (message.messageType) {
        case 'GAME_START':
        case 'ROUND_START':
        case 'DRAWING':
        case 'CORRECT_ANSWER':
        case 'SCORE_UPDATE':
    }
}
```

### 4.3 Phase 3: 훅 분리

```
src/domains/
├── chat/
│   ├── hooks/
│   │   └── useChatWebSocket.js    # 채팅만 처리
│   └── components/
│       ├── ChatMessages.jsx
│       └── ChatInput.jsx
│
├── catchmind/
│   ├── hooks/
│   │   ├── useGameWebSocket.js    # 게임만 처리
│   │   ├── useGameState.js
│   │   └── useTimer.js
│   └── components/
│       ├── DrawingCanvas.jsx
│       ├── ScoreBoard.jsx
│       └── Timer.jsx
│
└── freetalk/
    └── pages/
        └── FreeTalkPage.jsx       # chat + catchmind 조합
```

---

## 5. 메시지 스펙 (최종)

### 5.1 공통 메시지 구조

```json
{
    "domain": "chat" | "game",
    "messageType": "...",
    "data": { ... },
    "timestamp": 1705744800000
}
```

### 5.2 채팅 메시지

| Type         | 방향  | data 필드                                       |
|--------------|-----|-----------------------------------------------|
| `TEXT`       | 양방향 | `messageId`, `userId`, `content`, `createdAt` |
| `USER_JOIN`  | S→C | `userId`, `memberCount`                       |
| `USER_LEAVE` | S→C | `userId`, `memberCount`                       |
| `SYSTEM`     | S→C | `content`                                     |

### 5.3 게임 메시지

| Type             | 방향  | data 필드                                                                                                       |
|------------------|-----|---------------------------------------------------------------------------------------------------------------|
| `GAME_START`     | S→C | `gameSessionId`, `totalRounds`, `currentDrawerId`, `roundStartTime`, `serverTime`, `roundDuration`, `players` |
| `GAME_END`       | S→C | `gameSessionId`, `reason`, `finalScores`, `winner`                                                            |
| `ROUND_START`    | S→C | `currentRound`, `currentDrawerId`, `roundStartTime`, `serverTime`, `roundDuration`, `currentWord`(출제자만)       |
| `ROUND_END`      | S→C | `currentRound`, `answer`, `scores`                                                                            |
| `DRAWING`        | 양방향 | `drawingData`                                                                                                 |
| `DRAWING_CLEAR`  | 양방향 | -                                                                                                             |
| `GUESS`          | C→S | `content`                                                                                                     |
| `CORRECT_ANSWER` | S→C | `userId`, `score`, `elapsedTime`                                                                              |
| `SCORE_UPDATE`   | S→C | `scores`, `currentRound`, `totalRounds`                                                                       |
| `HINT`           | S→C | `hint`                                                                                                        |

### 5.4 ROUND_START 상세 (핵심!)

```json
{
    "domain": "game",
    "messageType": "ROUND_START",
    "data": {
        "gameSessionId": "game-abc123",
        "currentRound": 2,
        "totalRounds": 5,
        "currentDrawerId": "user123",
        "roundStartTime": 1705744800000,
        "serverTime": 1705744800500,
        "roundDuration": 60,
        "currentWord": {
            "wordId": "word-1",
            "word": "apple"
        }
    },
    "timestamp": 1705744800500
}
```

**중요:** `currentWord`는 출제자에게만 전송!

---

## 6. 구현 일정

```
Week 1: 긴급 버그 수정
├── [BE] serverTime 필드 추가 (0.5일)
├── [FE] useTimer 훅 수정 (0.5일)
├── [BE] 메시지에 domain 필드 추가 (1일)
└── [FE] 메시지 핸들러 domain 분기 (0.5일)

Week 2: 게임 세션 분리 (BE)
├── [BE] GameSession 모델 생성
├── [BE] GameSessionRepository 구현
├── [BE] GameService 리팩토링
└── [BE] 게임 세션 API 구현

Week 3: 프론트엔드 리팩토링
├── [FE] useChatWebSocket 분리
├── [FE] useGameWebSocket 신규
├── [FE] 컴포넌트 분리
└── [FE/BE] 통합 테스트

Week 4: 안정화 및 추가 기능
├── [BE] 게임 자동 종료 (7분) - Issue #417
├── [BE] 재접속 시 게임 상태 복구
└── [FE/BE] E2E 테스트
```

---

## 7. 기대 효과

| 항목      | 현재          | 개선 후             |
|---------|-------------|------------------|
| 타이머 정확도 | 클라이언트 시계 의존 | 서버 시간 기준 동기화     |
| 재접속     | 게임 상태 유실    | 완전 복구 가능         |
| 테스트     | 채팅/게임 분리 불가 | 독립 테스트 가능        |
| 확장성     | 새 게임 추가 어려움 | gameType으로 확장 용이 |
| 유지보수    | 책임 혼재       | 명확한 책임 분리        |

---

## 8. 즉시 적용 (백엔드 변경 전 프론트엔드 임시 조치)

```javascript
// 백엔드 변경 전까지 프론트엔드에서 적용 가능한 임시 코드

onRoundStart: (data) => {
    const roundData = data.data || data;
    const now = Date.now();

    // serverTime이 없으면 클라이언트 시간 사용 (임시)
    const serverTime = roundData.serverTime || now;
    let roundStartTime = roundData.roundStartTime || now;

    // roundStartTime이 미래 시간이면 현재로 보정
    if (roundStartTime > now + 1000) {
        console.warn('Invalid roundStartTime, using current time');
        roundStartTime = now;
    }

    setGameState((prev) => ({
        ...prev,
        currentRound: roundData.currentRound,
        currentDrawerId: roundData.currentDrawerId,
        roundStartTime: roundStartTime,
        serverTime: serverTime,
        roundDuration: roundData.roundDuration || roundData.roundTimeLimit || 60,
    }));
}
```

---

## 9. 결론

**우선순위:**

1. **즉시 (이번 주)**: `serverTime` 추가 + `domain` 필드 추가
2. **단기 (2주)**: GameSession 모델 분리 + API 구현
3. **중기 (3-4주)**: FE/BE 완전 분리 + 자동 종료 + 재접속 복구

**핵심 원칙:**

- 단일 WebSocket 엔드포인트 유지 (비용/복잡도)
- `domain` 필드로 채팅/게임 구분
- `serverTime`으로 정확한 타이머 동기화
- GameSession 독립 모델로 상태 관리 명확화
