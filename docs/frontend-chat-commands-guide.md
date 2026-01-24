# 채팅 슬래시 명령어 프론트엔드 통합 가이드

## 개요

채팅방에서 사용할 수 있는 슬래시 명령어 시스템이 추가되었습니다.
이 문서는 프론트엔드에서 명령어 기능을 구현하기 위한 가이드입니다.

---

## 1. 새로운 메시지 타입

### MessageType 추가 항목

```typescript
enum MessageType {
  // 기존 타입
  TEXT = 'text',
  IMAGE = 'image',
  VOICE = 'voice',
  SYSTEM_COMMAND = 'system_command',

  // 새로 추가된 타입
  POLL_CREATE = 'poll_create',      // 투표 생성
  POLL_VOTE = 'poll_vote',          // 투표 참여
  POLL_END = 'poll_end',            // 투표 종료
  CLEAR_CHAT = 'clear_chat',        // 채팅 삭제
  LEAVE_ROOM = 'leave_room',        // 퇴장
}
```

---

## 2. 명령어 목록

### 기본 명령어

| 명령어 | 설명 | 응답 타입 |
|--------|------|----------|
| `/help` | 명령어 목록 표시 | `SYSTEM_COMMAND` |
| `/members` | 접속자 목록 조회 | `SYSTEM_COMMAND` |
| `/leave` | 채팅방 퇴장 | `LEAVE_ROOM` |
| `/clear` | 채팅 내역 삭제 | `CLEAR_CHAT` |

### 재미 명령어

| 명령어 | 설명 | 응답 타입 |
|--------|------|----------|
| `/dice` | 주사위 굴리기 (1-6) | `SYSTEM_COMMAND` |
| `/coin` | 동전 던지기 | `SYSTEM_COMMAND` |
| `/random [옵션1] [옵션2] ...` | 랜덤 선택 | `SYSTEM_COMMAND` |

### 투표 명령어

| 명령어 | 설명 | 응답 타입 |
|--------|------|----------|
| `/poll [질문] \| [옵션1] \| [옵션2] \| ...` | 투표 생성 | `POLL_CREATE` |
| `/vote [번호]` | 투표 참여 | `POLL_VOTE` |
| `/endpoll` | 투표 종료 (생성자만) | `POLL_END` |

---

## 3. WebSocket 메시지 응답 구조

### 3.1 기본 응답 구조

```typescript
interface CommandResponse {
  type: MessageType;
  message: string;
  success: boolean;
  data?: any;
  timestamp: string;
  senderId: string;
}
```

### 3.2 /members 응답

```json
{
  "type": "system_command",
  "message": "👥 현재 접속자: 3명\n  • 홍길동\n  • 김철수\n  • 이영희",
  "success": true,
  "data": {
    "count": 3,
    "members": [
      { "userId": "user-123", "nickname": "홍길동" },
      { "userId": "user-456", "nickname": "김철수" },
      { "userId": "user-789", "nickname": "이영희" }
    ]
  }
}
```

### 3.3 /dice 응답

```json
{
  "type": "system_command",
  "message": "🎲 홍길동님이 주사위를 굴렸습니다: ⚃ 4",
  "success": true,
  "data": {
    "userId": "user-123",
    "nickname": "홍길동",
    "result": 4,
    "type": "dice"
  }
}
```

### 3.4 /coin 응답

```json
{
  "type": "system_command",
  "message": "🪙 홍길동님이 동전을 던졌습니다: 앞면 (Heads)",
  "success": true,
  "data": {
    "userId": "user-123",
    "nickname": "홍길동",
    "result": "heads",
    "type": "coin"
  }
}
```

### 3.5 /random 응답

```json
{
  "type": "system_command",
  "message": "🎯 홍길동님의 랜덤 선택: 짬뽕\n(후보: 짜장, 짬뽕, 탕수육)",
  "success": true,
  "data": {
    "userId": "user-123",
    "nickname": "홍길동",
    "options": ["짜장", "짬뽕", "탕수육"],
    "selected": "짬뽕",
    "type": "random"
  }
}
```

### 3.6 /poll 응답 (투표 생성)

```json
{
  "type": "poll_create",
  "message": "📊 홍길동님이 투표를 시작했습니다!\n\n❓ 점심 뭐 먹을까?\n\n  1. 짜장면\n  2. 짬뽕\n  3. 탕수육\n\n💬 /vote [번호]로 투표하세요!",
  "success": true,
  "data": {
    "pollId": "poll-uuid-123",
    "question": "점심 뭐 먹을까?",
    "options": ["짜장면", "짬뽕", "탕수육"],
    "createdBy": "user-123",
    "creatorNickname": "홍길동"
  }
}
```

### 3.7 /vote 응답 (투표 참여)

```json
{
  "type": "poll_vote",
  "message": "✅ 김철수님이 '짬뽕'에 투표했습니다!\n\n📊 현재 현황 (총 3표):\n  1. 짜장면: █ 1표\n  2. 짬뽕: ██ 2표\n  3. 탕수육:  0표",
  "success": true,
  "data": {
    "pollId": "poll-uuid-123",
    "voterId": "user-456",
    "voterNickname": "김철수",
    "selectedOption": 1,
    "selectedOptionText": "짬뽕",
    "votes": { "0": 1, "1": 2, "2": 0 },
    "totalVotes": 3
  }
}
```

### 3.8 /endpoll 응답 (투표 종료)

```json
{
  "type": "poll_end",
  "message": "🏁 홍길동님이 투표를 종료했습니다!\n\n❓ 점심 뭐 먹을까?\n\n📊 최종 결과 (총 5표):\n🏆 1. 짜장면: ███ 3표\n   2. 짬뽕: ██ 2표\n   3. 탕수육:  0표\n\n🎉 우승: 짜장면",
  "success": true,
  "data": {
    "pollId": "poll-uuid-123",
    "question": "점심 뭐 먹을까?",
    "options": ["짜장면", "짬뽕", "탕수육"],
    "votes": { "0": 3, "1": 2, "2": 0 },
    "totalVotes": 5,
    "winners": ["짜장면"]
  }
}
```

### 3.9 /leave 응답

```json
{
  "type": "leave_room",
  "message": "👋 홍길동님이 퇴장합니다.",
  "success": true,
  "data": {
    "userId": "user-123",
    "nickname": "홍길동",
    "action": "leave"
  }
}
```

### 3.10 /clear 응답

```json
{
  "type": "clear_chat",
  "message": "🗑️ 채팅 내역 삭제를 요청했습니다.",
  "success": true,
  "data": {
    "userId": "user-123",
    "action": "clear"
  }
}
```

---

## 4. 프론트엔드 구현 가이드

### 4.1 명령어 자동완성 (추천)

사용자가 `/`를 입력하면 명령어 목록을 표시하는 자동완성 기능 구현:

```typescript
const COMMANDS = [
  { command: '/help', description: '명령어 목록' },
  { command: '/members', description: '접속자 목록' },
  { command: '/leave', description: '채팅방 나가기' },
  { command: '/clear', description: '채팅 내역 삭제' },
  { command: '/dice', description: '주사위 굴리기' },
  { command: '/coin', description: '동전 던지기' },
  { command: '/random', description: '랜덤 선택', usage: '/random [옵션1] [옵션2] ...' },
  { command: '/poll', description: '투표 생성', usage: '/poll [질문] | [옵션1] | [옵션2]' },
  { command: '/vote', description: '투표하기', usage: '/vote [번호]' },
  { command: '/endpoll', description: '투표 종료' },
];

function getCommandSuggestions(input: string) {
  if (!input.startsWith('/')) return [];
  return COMMANDS.filter(c => c.command.startsWith(input));
}
```

### 4.2 메시지 타입별 렌더링

```tsx
function ChatMessage({ message }: { message: CommandResponse }) {
  switch (message.type) {
    case 'poll_create':
      return <PollCreateMessage data={message.data} />;

    case 'poll_vote':
      return <PollVoteMessage data={message.data} />;

    case 'poll_end':
      return <PollEndMessage data={message.data} />;

    case 'clear_chat':
      // 본인 메시지만 삭제 처리
      if (message.data.userId === currentUserId) {
        handleClearMyMessages();
      }
      return null;

    case 'leave_room':
      return <SystemMessage text={message.message} />;

    case 'system_command':
      return <SystemMessage text={message.message} data={message.data} />;

    default:
      return <TextMessage text={message.message} />;
  }
}
```

### 4.3 투표 UI 컴포넌트 (추천)

투표 메시지는 텍스트 대신 인터랙티브 UI로 표시:

```tsx
function PollCreateMessage({ data }) {
  const { pollId, question, options } = data;

  return (
    <div className="poll-card">
      <h4>📊 {question}</h4>
      <div className="options">
        {options.map((option, index) => (
          <button
            key={index}
            onClick={() => sendMessage(`/vote ${index + 1}`)}
            className="poll-option"
          >
            {index + 1}. {option}
          </button>
        ))}
      </div>
    </div>
  );
}
```

### 4.4 투표 결과 시각화

```tsx
function PollVoteMessage({ data }) {
  const { question, options, votes, totalVotes } = data;

  return (
    <div className="poll-results">
      <h4>📊 현재 현황 ({totalVotes}표)</h4>
      {options.map((option, index) => {
        const count = votes[String(index)] || 0;
        const percentage = totalVotes > 0 ? (count / totalVotes) * 100 : 0;

        return (
          <div key={index} className="poll-bar">
            <span>{option}</span>
            <div className="bar" style={{ width: `${percentage}%` }} />
            <span>{count}표</span>
          </div>
        );
      })}
    </div>
  );
}
```

### 4.5 /leave 처리

```typescript
function handleLeaveRoom(message: CommandResponse) {
  if (message.data.userId === currentUserId) {
    // 본인이 퇴장한 경우 → 채팅방 목록으로 이동
    navigate('/chat/rooms');
  } else {
    // 다른 사용자 퇴장 → 알림 표시
    showNotification(`${message.data.nickname}님이 퇴장했습니다.`);
  }
}
```

### 4.6 /clear 처리

```typescript
function handleClearChat(message: CommandResponse) {
  if (message.data.userId === currentUserId) {
    // 본인 메시지만 UI에서 삭제
    setMessages(prev => prev.filter(m => m.senderId !== currentUserId));
  }
  // 다른 사용자의 clear 명령은 무시
}
```

---

## 5. TypeScript 타입 정의

```typescript
// types/chat.ts

export type MessageType =
  | 'text'
  | 'image'
  | 'voice'
  | 'system_command'
  | 'poll_create'
  | 'poll_vote'
  | 'poll_end'
  | 'clear_chat'
  | 'leave_room';

export interface CommandResponse {
  type: MessageType;
  message: string;
  success: boolean;
  data?: CommandData;
  timestamp: string;
  senderId: string;
}

export type CommandData =
  | MembersData
  | DiceData
  | CoinData
  | RandomData
  | PollCreateData
  | PollVoteData
  | PollEndData
  | LeaveData
  | ClearData;

export interface MembersData {
  count: number;
  members: { userId: string; nickname: string }[];
}

export interface DiceData {
  userId: string;
  nickname: string;
  result: number;
  type: 'dice';
}

export interface CoinData {
  userId: string;
  nickname: string;
  result: 'heads' | 'tails';
  type: 'coin';
}

export interface RandomData {
  userId: string;
  nickname: string;
  options: string[];
  selected: string;
  type: 'random';
}

export interface PollCreateData {
  pollId: string;
  question: string;
  options: string[];
  createdBy: string;
  creatorNickname: string;
}

export interface PollVoteData {
  pollId: string;
  voterId: string;
  voterNickname: string;
  selectedOption: number;
  selectedOptionText: string;
  votes: Record<string, number>;
  totalVotes: number;
}

export interface PollEndData {
  pollId: string;
  question: string;
  options: string[];
  votes: Record<string, number>;
  totalVotes: number;
  winners: string[];
}

export interface LeaveData {
  userId: string;
  nickname: string;
  action: 'leave';
}

export interface ClearData {
  userId: string;
  action: 'clear';
}
```

---

## 6. 체크리스트

### 필수 구현
- [ ] 새로운 MessageType 처리 (`poll_create`, `poll_vote`, `poll_end`, `clear_chat`, `leave_room`)
- [ ] `/leave` 명령 시 채팅방 퇴장 처리
- [ ] `/clear` 명령 시 본인 메시지 UI 삭제

### 권장 구현
- [ ] 명령어 자동완성 UI
- [ ] 투표 인터랙티브 UI (버튼으로 투표)
- [ ] 투표 결과 프로그레스 바 시각화
- [ ] 주사위/동전 애니메이션

### 테스트 항목
- [ ] `/help` 명령어 목록 표시
- [ ] `/members` 접속자 목록 표시
- [ ] `/dice`, `/coin`, `/random` 결과가 전체에게 표시
- [ ] `/poll` 생성 후 `/vote`로 투표 가능
- [ ] `/endpoll` 투표 종료 및 결과 표시
- [ ] `/leave` 퇴장 처리
- [ ] `/clear` 본인 메시지만 삭제

---

## 7. 에러 응답

명령어 실행 실패 시:

```json
{
  "type": "system_command",
  "message": "이미 투표하셨습니다.",
  "success": false,
  "data": null
}
```

에러 메시지 예시:
- `"사용법: /random [옵션1] [옵션2] [옵션3] ..."`
- `"최소 2개 이상의 옵션이 필요합니다."`
- `"이미 진행 중인 투표가 있습니다. /endpoll로 종료 후 새 투표를 만드세요."`
- `"진행 중인 투표가 없습니다."`
- `"이미 투표하셨습니다."`
- `"투표 생성자만 종료할 수 있습니다."`

---

## 문의

백엔드 관련 문의: BE 팀
