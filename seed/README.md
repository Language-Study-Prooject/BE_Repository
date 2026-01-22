# Seed Data

DynamoDB 초기 데이터 시드 파일

## 구조

```
seed/
├── opic/
│   └── question-homes.json    # OPIc 질문 데이터
└── vocabulary/
    └── words.json             # 단어 학습 데이터
```

## 사용법

AWS CLI를 사용하여 DynamoDB에 데이터 업로드:

```bash
# Vocabulary words
aws dynamodb batch-write-item --request-items file://seed/vocabulary/words.json

# OPIc questions
aws dynamodb batch-write-item --request-items file://seed/opic/question-homes.json
```
