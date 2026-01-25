# 영어 끝말잇기(쿵쿵따) 프론트엔드 통합 가이드

## 개요
영어 끝말잇기 게임 - 이전 단어의 마지막 글자로 시작하는 단어를 제출하는 게임

## REST API 엔드포인트

### 1. 게임 시작
```
POST /chat/rooms/{roomId}/wordchain/start
Authorization: Bearer {token}
```

**Response (성공):**
```json
{
  "success": true,
  "message": "Word Chain game started",
  "data": {
    "sessionId": "uuid",
    "gameStatus": "PLAYING",
    "currentRound": 1,
    "currentPlayerId": "user-id",
    "currentWord": "apple",
    "nextLetter": "e",
    "timeLimit": 15,
    "turnStartTime": 1706000000000,
    "serverTime": 1706000000000,
    "activePlayers": ["user1", "user2", "user3"],
    "eliminatedPlayers": [],
    "scores": {},
    "usedWords": ["apple"]
  }
}
```

### 2. 단어 제출
```
POST /chat/rooms/{roomId}/wordchain/submit
Authorization: Bearer {token}
Content-Type: application/json

{
  "word": "elephant"
}
```

**Response (정답):**
```json
{
  "success": true,
  "message": "Correct!",
  "data": {
    "resultType": "CORRECT",
    "word": "elephant",
    "definition": "(noun) A large mammal with a trunk",
    "phonetic": "/ˈɛləfənt/",
    "score": 23,
    "nextLetter": "t",
    "nextPlayerId": "user2",
    "nextTimeLimit": 15
  }
}
```

**Response (오답 - 첫 글자 틀림):**
```json
{
  "success": true,
  "message": "Wrong answer",
  "data": {
    "resultType": "WRONG_LETTER",
    "error": "'e'로 시작하는 단어를 입력하세요."
  }
}
```

**Response (오답 - 사전에 없음):**
```json
{
  "success": true,
  "message": "Wrong answer",
  "data": {
    "resultType": "INVALID_WORD",
    "error": "사전에 없는 단어입니다: xyz"
  }
}
```

### 3. 타임아웃 처리
```
POST /chat/rooms/{roomId}/wordchain/timeout
Authorization: Bearer {token}
```

### 4. 게임 종료 (시작자만)
```
POST /chat/rooms/{roomId}/wordchain/stop
Authorization: Bearer {token}
```

### 5. 게임 상태 조회
```
GET /chat/rooms/{roomId}/wordchain/status
Authorization: Bearer {token}
```

---

## WebSocket 메시지

### Domain
```javascript
domain: "wordchain"
```

### 메시지 타입

| messageType | 설명 |
|-------------|------|
| `wordchain_start` | 게임 시작 |
| `wordchain_correct` | 정답 |
| `wordchain_wrong` | 오답 |
| `wordchain_timeout` | 시간 초과 (탈락) |
| `wordchain_end` | 게임 종료 |

---

## WebSocket 메시지 상세

### 1. 게임 시작 (wordchain_start)
```json
{
  "domain": "wordchain",
  "messageType": "wordchain_start",
  "messageId": "uuid",
  "roomId": "room-id",
  "userId": "SYSTEM",
  "content": "🎮 끝말잇기 시작!\n시작 단어: apple\n다음 글자: 'e'\n\n첫 번째 차례: user1\n제한 시간: 15초",
  "createdAt": "2026-01-24T12:00:00Z",
  "timestamp": 1706000000000,
  "sessionId": "session-uuid",
  "starterWord": "apple",
  "nextLetter": "e",
  "currentPlayerId": "user1",
  "timeLimit": 15,
  "turnStartTime": 1706000000000,
  "serverTime": 1706000000000,
  "players": ["user1", "user2", "user3"],
  "activePlayers": ["user1", "user2", "user3"]
}
```

### 2. 정답 (wordchain_correct)
```json
{
  "domain": "wordchain",
  "messageType": "wordchain_correct",
  "messageId": "uuid",
  "roomId": "room-id",
  "userId": "SYSTEM",
  "content": "✅ 닉네임: \"elephant\" (+23점)\n뜻: (noun) A large mammal\n다음 글자: 't'",
  "createdAt": "2026-01-24T12:00:05Z",
  "timestamp": 1706000005000,
  "serverTime": 1706000005000,
  "resultType": "CORRECT",
  "word": "elephant",
  "definition": "(noun) A large mammal with a trunk",
  "phonetic": "/ˈɛləfənt/",
  "score": 23,
  "nextLetter": "t",
  "nextPlayerId": "user2",
  "nextTimeLimit": 15,
  "playerNickname": "닉네임",
  "turnStartTime": 1706000005000,
  "scores": {
    "user1": 23
  }
}
```

### 3. 오답 (wordchain_wrong)
```json
{
  "domain": "wordchain",
  "messageType": "wordchain_wrong",
  "messageId": "uuid",
  "roomId": "room-id",
  "userId": "SYSTEM",
  "content": "❌ 사전에 없는 단어입니다: xyz",
  "resultType": "INVALID_WORD",
  "error": "사전에 없는 단어입니다: xyz"
}
```

### 4. 시간 초과 (wordchain_timeout)
```json
{
  "domain": "wordchain",
  "messageType": "wordchain_timeout",
  "messageId": "uuid",
  "roomId": "room-id",
  "userId": "SYSTEM",
  "content": "⏰ 닉네임 시간 초과! 탈락!",
  "resultType": "TIMEOUT",
  "eliminatedPlayerId": "user1",
  "eliminatedNickname": "닉네임",
  "nextPlayerId": "user2",
  "nextTimeLimit": 13,
  "nextLetter": "e",
  "turnStartTime": 1706000015000,
  "activePlayers": ["user2", "user3"]
}
```

### 5. 게임 종료 (wordchain_end)
```json
{
  "domain": "wordchain",
  "messageType": "wordchain_end",
  "messageId": "uuid",
  "roomId": "room-id",
  "userId": "SYSTEM",
  "content": "🏆 승자: 닉네임!",
  "resultType": "GAME_END",
  "winnerId": "user2",
  "winnerNickname": "닉네임",
  "ranking": [
    { "playerId": "user2", "nickname": "닉네임2", "score": 45, "eliminated": false },
    { "playerId": "user3", "nickname": "닉네임3", "score": 30, "eliminated": true },
    { "playerId": "user1", "nickname": "닉네임1", "score": 23, "eliminated": true }
  ],
  "usedWords": ["apple", "elephant", "tiger", "rainbow"],
  "wordDefinitions": {
    "apple": "(noun) A fruit",
    "elephant": "(noun) A large mammal",
    "tiger": "(noun) A large cat",
    "rainbow": "(noun) An arc of colors"
  },
  "scores": {
    "user1": 23,
    "user2": 45,
    "user3": 30
  }
}
```

---

## 게임 규칙

### 시간 제한 (라운드별 감소)
| 라운드 | 시간 제한 |
|--------|----------|
| 1-2 | 15초 |
| 3-4 | 13초 |
| 5-6 | 11초 |
| 7-8 | 9초 |
| 9+ | 8초 |

### 점수 계산
```
점수 = 기본점수(10) + 시간보너스 + 길이보너스

시간보너스 = 남은시간(초)
길이보너스 = (단어길이 - 4) × 2  (5글자 이상부터)
```

**예시:**
- 15초 제한에서 5초 만에 "elephant"(8글자) 제출
- 점수 = 10 + 10 + 8 = 28점

### 게임 종료 조건
- 1명만 남으면 게임 종료
- 시작자가 `/stop` 호출

---

## 프론트엔드 구현 가이드

### 1. 타이머 동기화
```javascript
// 서버 시간과 클라이언트 시간 차이 계산
const serverTimeDiff = message.serverTime - Date.now();

// 남은 시간 계산
const elapsed = Date.now() + serverTimeDiff - message.turnStartTime;
const remaining = (message.timeLimit * 1000) - elapsed;
```

### 2. WebSocket 메시지 핸들러
```javascript
socket.onmessage = (event) => {
  const message = JSON.parse(event.data);

  if (message.domain !== 'wordchain') return;

  switch (message.messageType) {
    case 'wordchain_start':
      handleGameStart(message);
      break;
    case 'wordchain_correct':
      handleCorrectAnswer(message);
      break;
    case 'wordchain_wrong':
      handleWrongAnswer(message);
      break;
    case 'wordchain_timeout':
      handleTimeout(message);
      break;
    case 'wordchain_end':
      handleGameEnd(message);
      break;
  }
};
```

### 3. 타임아웃 자동 전송
```javascript
// 내 턴일 때 타이머 만료 시 자동으로 타임아웃 API 호출
if (isMyTurn && remaining <= 0) {
  fetch(`/chat/rooms/${roomId}/wordchain/timeout`, {
    method: 'POST',
    headers: { 'Authorization': `Bearer ${token}` }
  });
}
```

### 4. UI 구성 요소
- 현재 단어 표시
- 다음 시작 글자 강조
- 타이머 (남은 시간)
- 현재 차례 플레이어 표시
- 활성/탈락 플레이어 목록
- 점수판
- 사용된 단어 목록
- 단어 입력 필드 (본인 차례일 때만 활성화)

### 5. 게임 종료 후 학습 화면
```javascript
// 게임 종료 시 사용된 단어와 뜻 표시
message.usedWords.forEach(word => {
  const definition = message.wordDefinitions[word];
  console.log(`${word}: ${definition}`);
});
```

---

## 에러 코드

| 코드 | 메시지 |
|------|--------|
| GAME_001 | 게임 시작에 실패했습니다 |
| GAME_002 | 게임 중단에 실패했습니다 |
| GAME_010 | 게임 액션 처리에 실패했습니다 |
| INPUT_001 | 유효하지 않은 입력입니다 |

---

## 참고

- Dictionary API: [Free Dictionary API](https://dictionaryapi.dev/)
- 최소 인원: 2명
- 시작 단어: 서버에서 랜덤 선택 (apple, house, water 등)
