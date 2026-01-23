# Chatting Domain 세부 보고서

## 1. 개요

Chatting 도메인은 실시간 채팅과 캐치마인드 게임 기능을 제공하는 WebSocket 기반 시스템입니다. AWS API Gateway WebSocket과 Lambda를 활용하여 실시간 양방향 통신을 구현했습니다.

---

## 2. 전체 아키텍처

```mermaid
flowchart TB
    subgraph Client["클라이언트"]
        APP[Mobile/Web App]
    end

    subgraph Gateway["API Gateway"]
        REST[REST API]
        WS[WebSocket API]
    end

    subgraph Lambda["Lambda Handlers"]
        direction TB
        ROOM[ChatRoomHandler]
        MSG[ChatMessageHandler]
        GAME[GameHandler]
        VOICE[ChatVoiceHandler]
        CONNECT[WebSocketConnectHandler]
        DISCONNECT[WebSocketDisconnectHandler]
        MESSAGE[WebSocketMessageHandler]
    end

    subgraph Storage["데이터 저장소"]
        DDB[(DynamoDB)]
        S3[(S3 - 음성 캐시)]
    end

    APP --> REST
    APP <--> WS
    REST --> ROOM
    REST --> MSG
    REST --> GAME
    REST --> VOICE
    WS --> CONNECT
    WS --> DISCONNECT
    WS --> MESSAGE
    ROOM --> DDB
    MSG --> DDB
    GAME --> DDB
    MESSAGE --> DDB
    VOICE --> S3
```

---

## 3. 채팅방 시스템

### 3.1 채팅방 입장 흐름

```mermaid
sequenceDiagram
    participant Client
    participant REST as REST API
    participant WS as WebSocket API
    participant DB as DynamoDB
    Note over Client, DB: Phase 1 - 방 입장 및 토큰 발급
    Client ->> REST: POST /rooms/{roomId}/join
    REST ->> DB: 비밀번호 검증 (비밀방인 경우)
    REST ->> DB: RoomToken 저장 (TTL 5분)
    REST -->> Client: roomToken 반환
    Note over Client, DB: Phase 2 - WebSocket 연결
    Client ->> WS: $connect?roomToken={token}
    WS ->> DB: 토큰 검증
    WS ->> DB: Connection 저장 (TTL 10분)
    WS -->> Client: 연결 성공
    Note over Client, DB: Phase 3 - 실시간 메시지
    Client ->> WS: sendMessage (채팅)
    WS ->> DB: 메시지 저장
    WS -->> Client: 브로드캐스트 (같은 방 전체)
```

### 3.2 REST API 엔드포인트

| Method | Endpoint                      | 설명                        | 인증 |
|--------|-------------------------------|---------------------------|----|
| POST   | /chat/rooms                   | 채팅방 생성                    | O  |
| GET    | /chat/rooms                   | 채팅방 목록 (level, joined 필터) | O  |
| GET    | /chat/rooms/{roomId}          | 채팅방 상세                    | O  |
| POST   | /chat/rooms/{roomId}/join     | 채팅방 입장 (토큰 발급)            | O  |
| POST   | /chat/rooms/{roomId}/leave    | 채팅방 퇴장                    | O  |
| DELETE | /chat/rooms/{roomId}          | 채팅방 삭제 (방장만)              | O  |
| GET    | /chat/rooms/{roomId}/messages | 메시지 히스토리                  | O  |

### 3.3 WebSocket 이벤트

| Route       | 설명         | Payload                                  |
|-------------|------------|------------------------------------------|
| $connect    | 연결 (토큰 검증) | ?roomToken={token}                       |
| $disconnect | 연결 해제      | -                                        |
| sendMessage | 메시지 전송     | { roomId, userId, content, messageType } |

---

## 4. 캐치마인드 게임 시스템

### 4.1 게임 흐름

```mermaid
flowchart TB
    subgraph GameFlow["캐치마인드 게임 흐름"]
        START["/game 명령어"] --> INIT["게임 초기화<br/>출제자 순서 셔플"]
        INIT --> ROUND["라운드 시작<br/>출제자 + 단어 선정"]
        ROUND --> DRAW["출제자 그림 그리기<br/>(DRAWING 메시지)"]
        DRAW --> GUESS["참가자 정답 입력"]
        GUESS --> CHECK{정답?}
        CHECK -->|Yes| SCORE["점수 계산<br/>시간보너스 + 연속보너스"]
        CHECK -->|No| GUESS
        SCORE --> ALLCORRECT{전원 정답?}
        ALLCORRECT -->|Yes| NEXTROUND
        ALLCORRECT -->|No| TIMEOUT{시간 초과?}
        TIMEOUT -->|Yes| NEXTROUND["다음 라운드"]
        TIMEOUT -->|No| GUESS
        NEXTROUND --> LASTROUND{마지막 라운드?}
        LASTROUND -->|Yes| END["게임 종료<br/>순위 발표"]
        LASTROUND -->|No| ROUND
    end
```

### 4.2 게임 API

| Method | Endpoint                         | 설명          |
|--------|----------------------------------|-------------|
| POST   | /chat/rooms/{roomId}/game/start  | 게임 시작 (방장만) |
| POST   | /chat/rooms/{roomId}/game/stop   | 게임 중지       |
| GET    | /chat/rooms/{roomId}/game/status | 게임 상태 조회    |
| GET    | /chat/rooms/{roomId}/game/scores | 점수판 조회      |

### 4.3 슬래시 명령어

| 명령어     | 설명             | 사용 가능  |
|---------|----------------|--------|
| /start  | 게임 시작          | 방장     |
| /stop   | 게임 중지          | 방장/시작자 |
| /score  | 점수판 보기         | 전체     |
| /member | 접속자 수          | 전체     |
| /hint   | 힌트 제공 (첫글자○○○) | 출제자    |
| /skip   | 라운드 스킵         | 출제자    |
| /help   | 명령어 도움말        | 전체     |

### 4.4 점수 계산 공식

```
점수 = 기본점수(10) + 시간보너스 + 연속보너스 + 출제자보너스

- 시간보너스: (60 - 경과초) × 0.5
- 연속보너스: streak × 2
- 출제자보너스: 정답자당 5점
```

**예시:**

- 30초에 정답 + 연속 3회: 10 + 15 + 6 = 31점
- 출제자가 3명 맞출 경우: 5 × 3 = 15점

### 4.5 게임 상태

```mermaid
stateDiagram-v2
    [*] --> NONE: 대기
    NONE --> PLAYING: /start 명령어
    PLAYING --> ROUND_END: 시간초과/전원정답
    ROUND_END --> PLAYING: 다음 라운드
    ROUND_END --> FINISHED: 마지막 라운드
    PLAYING --> FINISHED: /stop 명령어
    FINISHED --> [*]: 게임 종료
```

---

## 5. WebSocket 메시지 타입

### 5.1 채팅 메시지

| Type        | 설명    | 저장 |
|-------------|-------|----|
| TEXT        | 일반 채팅 | O  |
| IMAGE       | 이미지   | O  |
| VOICE       | 음성    | O  |
| AI_RESPONSE | AI 응답 | O  |

### 5.2 게임 메시지

| Type           | 설명           | 저장 |
|----------------|--------------|----|
| DRAWING        | 그림 데이터 (실시간) | X  |
| DRAWING_CLEAR  | 그림 지우기       | X  |
| GUESS          | 오답 추측        | X  |
| CORRECT_ANSWER | 정답 알림        | X  |
| SCORE_UPDATE   | 점수 갱신        | X  |
| GAME_START     | 게임 시작        | X  |
| ROUND_START    | 라운드 시작       | X  |
| ROUND_END      | 라운드 종료       | X  |
| GAME_END       | 게임 종료        | X  |
| HINT           | 힌트           | X  |

### 5.3 실시간 점수 업데이트 메시지

```json
{
  "messageType": "SCORE_UPDATE",
  "roomId": "uuid",
  "scorerId": "user123",
  "scoreGained": 25,
  "ranking": [
    {
      "rank": 1,
      "userId": "user123",
      "score": 85,
      "change": 25
    },
    {
      "rank": 2,
      "userId": "user456",
      "score": 60,
      "change": 0
    }
  ],
  "currentRound": 3,
  "totalRounds": 5
}
```

---

## 6. 데이터 모델

### 6.1 ChatRoom

```java

@DynamoDbBean
public class ChatRoom {
	// 기본 정보
	String roomId, name, description;
	String level;              // beginner, intermediate, advanced
	Integer currentMembers, maxMembers;
	Boolean isPrivate;
	String password;           // BCrypt 암호화
	String createdBy;          // 방장
	List<String> memberIds;
	
	// 게임 상태
	String gameStatus;         // NONE, PLAYING, ROUND_END, FINISHED
	Integer currentRound, totalRounds;
	String currentDrawerId, currentWord;
	Long roundStartTime;
	Integer roundTimeLimit;    // 60초
	List<String> drawerOrder;
	Map<String, Integer> scores;
	Map<String, Integer> streaks;
	List<String> correctGuessers;
	Boolean hintUsed;
}
```

**DynamoDB Keys:**

- PK: `ROOM#{roomId}` | SK: `METADATA`
- GSI1: `ROOMS` | `{level}#{createdAt}` (레벨별 최신순)

### 6.2 Connection

```java

@DynamoDbBean
public class Connection {
	String connectionId;       // API Gateway 연결 ID
	String userId;
	String roomId;
	Long ttl;                  // 10분 (자동 삭제)
}
```

**DynamoDB Keys:**

- PK: `CONN#{connectionId}` | SK: `METADATA`
- GSI1: `ROOM#{roomId}` | `CONN#{connectionId}` (방별 연결)
- GSI2: `USER#{userId}` | `CONN#{connectionId}` (사용자별 연결)

### 6.3 GameRound

```java

@DynamoDbBean
public class GameRound {
	Integer roundNumber;
	String drawerId, word, wordEnglish;
	List<String> correctGuessers;
	Map<String, Long> guessTimes;      // 정답까지 걸린 시간
	Map<String, Integer> roundScores;
	Long startTime, endTime;
	String endReason;                   // TIME_UP, ALL_CORRECT, SKIP
	Long ttl;                           // 7일
}
```

### 6.4 RoomToken

```java

@DynamoDbBean
public class RoomToken {
	String token;              // UUID
	String roomId;
	String userId;
	Long ttl;                  // 5분
}
```

---

## 7. 서비스 레이어

### 7.1 CQRS 패턴

| Service                | 역할                   |
|------------------------|----------------------|
| ChatRoomCommandService | 채팅방 생성, 입장, 퇴장, 삭제   |
| ChatRoomQueryService   | 채팅방 조회, 목록           |
| GameService            | 게임 시작, 정답 체크, 라운드 종료 |
| GameStatsService       | 게임 종료 후 통계, 배지 처리    |
| CommandService         | 슬래시 명령어 처리           |
| RoomTokenService       | 토큰 발급 및 검증           |

### 7.2 게임 정답 체크 로직

```mermaid
flowchart TB
    INPUT[정답 입력] --> NORMALIZE["정규화<br/>(소문자, 공백제거)"]
    NORMALIZE --> VALIDATE{유효성 검사}
    VALIDATE -->|게임 미진행| REJECT1[거부: 게임 없음]
    VALIDATE -->|출제자 본인| REJECT2[거부: 출제자]
    VALIDATE -->|이미 정답| REJECT3[거부: 중복]
    VALIDATE -->|통과| COMPARE{정답 비교}
    COMPARE -->|일치| CORRECT["정답 처리<br/>점수 계산"]
    COMPARE -->|불일치| WRONG["오답 처리<br/>GUESS 메시지 전송"]
    CORRECT --> BROADCAST["브로드캐스트<br/>CORRECT_ANSWER + SCORE_UPDATE"]
    WRONG --> GUESSBROADCAST["브로드캐스트<br/>GUESS 메시지"]
    BROADCAST --> ALLCHECK{전원 정답?}
    ALLCHECK -->|Yes| ROUNDEND[라운드 자동 종료]
    ALLCHECK -->|No| CONTINUE[게임 계속]
```

---

## 8. 브로드캐스트 시스템

### 8.1 WebSocketBroadcaster

```java
public class WebSocketBroadcaster {
	public List<String> broadcast(
			List<Connection> connections,
			String payload
	) {
		// 1. 같은 방 모든 연결에 메시지 전송
		// 2. 실패한 연결 ID 반환 (Stale 정리용)
	}
}
```

### 8.2 브로드캐스트 유형

| 유형     | 대상     | 예시        |
|--------|--------|-----------|
| 전체     | 방 전체   | 채팅, 정답 알림 |
| 본인 제외  | 발신자 제외 | 그림 데이터    |
| 출제자 전용 | 출제자만   | 단어 정보     |

---

## 9. 파일 구조

```
domain/chatting/
├── handler/
│   ├── ChatRoomHandler.java
│   ├── ChatMessageHandler.java
│   ├── ChatVoiceHandler.java
│   ├── GameHandler.java
│   └── websocket/
│       ├── WebSocketConnectHandler.java
│       ├── WebSocketDisconnectHandler.java
│       └── WebSocketMessageHandler.java
├── service/
│   ├── ChatRoomCommandService.java
│   ├── ChatRoomQueryService.java
│   ├── ChatMessageService.java
│   ├── GameService.java
│   ├── GameStatsService.java
│   ├── CommandService.java
│   └── RoomTokenService.java
├── repository/
│   ├── ChatRoomRepository.java
│   ├── ChatMessageRepository.java
│   ├── ConnectionRepository.java
│   ├── GameRoundRepository.java
│   └── RoomTokenRepository.java
├── model/
│   ├── ChatRoom.java
│   ├── ChatMessage.java
│   ├── Connection.java
│   ├── GameRound.java
│   └── RoomToken.java
├── dto/
│   ├── request/
│   └── response/
│       └── ScoreUpdateMessage.java
└── enums/
    ├── GameStatus.java
    └── MessageType.java
```

---

## 10. 기술 스택

- **Runtime:** AWS Lambda (Java 21)
- **API:** API Gateway REST + WebSocket
- **Database:** DynamoDB (Single Table Design)
- **Auth:** Cognito + RoomToken
- **Encryption:** BCrypt (비밀방 암호)
- **TTS:** AWS Polly + S3 캐시
- **Pattern:** CQRS, Repository, Factory
