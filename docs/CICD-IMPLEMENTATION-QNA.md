# CI/CD 파이프라인 구현 설명 및 면접 Q&A

## 1. CI/CD 아키텍처 개요

```
┌─────────────┐    ┌─────────────┐    ┌─────────────┐    ┌─────────────┐
│   GitHub    │───▶│ CodePipeline│───▶│  CodeBuild  │───▶│CloudFormation│
│  (Source)   │    │  (Pipeline) │    │   (Build)   │    │  (Deploy)   │
└─────────────┘    └─────────────┘    └─────────────┘    └─────────────┘
      │                   │                  │                  │
      │                   ▼                  ▼                  ▼
      │            ┌─────────────┐    ┌─────────────┐    ┌─────────────┐
      │            │     SNS     │    │     S3      │    │   Lambda    │
      │            │(Notification)│    │ (Artifacts) │    │  Functions  │
      │            └─────────────┘    └─────────────┘    └─────────────┘
      │
      ▼
┌─────────────────────────────────────────────────────────────────────┐
│                        prod 브랜치 Push/Merge                         │
└─────────────────────────────────────────────────────────────────────┘
```

## 2. 구성 요소 상세 설명

### 2.1 Source Stage (GitHub)

- **트리거**: prod 브랜치에 Push 또는 PR Merge 시 자동 실행
- **연결 방식**: AWS CodeConnections (구 CodeStar Connections)
- **아티팩트**: 소스 코드를 ZIP으로 압축하여 다음 스테이지로 전달

### 2.2 Build Stage (CodeBuild)

- **런타임**: Amazon Linux 2, Java Corretto 21
- **빌드 단계**:
	1. **Install**: SAM CLI 설치
	2. **Pre-build**: Gradle 테스트 실행 (`./gradlew clean test`)
	3. **Build**: SAM build & package
	4. **Post-build**: 완료 로그
- **캐싱**: Gradle 캐시를 S3에 저장하여 빌드 시간 단축
- **리포트**: JUnit 테스트 결과, JaCoCo 코드 커버리지 리포트

### 2.3 Deploy Stage (CloudFormation)

- **배포 방식**: CloudFormation CREATE_UPDATE
- **템플릿**: SAM으로 패키징된 `packaged-template.yaml`
- **기능**: CAPABILITY_IAM, CAPABILITY_AUTO_EXPAND

### 2.4 Notification (SNS)

- **이벤트**: 파이프라인 시작, 성공, 실패 시 이메일 알림
- **구현**: CodeStar Notifications + SNS Topic

## 3. 주요 파일 구조

```
BE_Repository/
├── cicd/
│   └── pipeline.yaml          # CloudFormation 파이프라인 템플릿
├── ServerlessFunction/
│   ├── buildspec.yml          # CodeBuild 빌드 명세
│   ├── samconfig.toml         # SAM 배포 설정
│   └── template.yaml          # SAM 애플리케이션 템플릿
```

## 4. IAM 역할 구성

| 역할                 | 목적                  | 주요 권한                                  |
|--------------------|---------------------|----------------------------------------|
| PipelineRole       | CodePipeline 서비스 역할 | S3, CodeBuild, CloudFormation, SNS     |
| CodeBuildRole      | CodeBuild 서비스 역할    | S3, CloudWatch Logs, CodeBuild Reports |
| CloudFormationRole | 리소스 배포 역할           | AdministratorAccess (SAM 리소스 생성용)      |

---

## 5. 면접 예상 질문 및 답변

### Q1. CI/CD 파이프라인을 구축한 이유는 무엇인가요?

**A1:**
수동 배포의 문제점을 해결하기 위해 CI/CD를 도입했습니다.

1. **일관성**: 수동 배포 시 발생할 수 있는 휴먼 에러 방지
2. **자동화**: 코드 푸시만으로 테스트-빌드-배포가 자동 실행
3. **품질 보장**: 테스트 실패 시 배포가 중단되어 결함 있는 코드가 프로덕션에 배포되는 것을 방지
4. **추적성**: 모든 배포 이력이 CodePipeline에 기록되어 문제 발생 시 원인 추적 용이
5. **속도**: 반복적인 배포 작업 시간을 단축하여 개발 생산성 향상

---

### Q2. GitHub과 AWS CodePipeline을 어떻게 연동했나요?

**A2:**
AWS CodeConnections(구 CodeStar Connections)를 사용하여 연동했습니다.

```yaml
# pipeline.yaml의 Source Stage 설정
- Name: Source
  Actions:
    - Name: GitHub
      ActionTypeId:
        Category: Source
        Owner: AWS
        Provider: CodeStarSourceConnection
        Version: '1'
      Configuration:
        ConnectionArn: !Ref GitHubConnectionArn
        FullRepositoryId: "Language-Study-Prooject/BE_Repository"
        BranchName: "prod"
        DetectChanges: true
```

**연동 과정:**

1. AWS Console에서 CodeConnections 생성
2. GitHub OAuth 앱 승인
3. Connection ARN을 파이프라인에 설정
4. `DetectChanges: true`로 설정하여 자동 트리거 활성화

---

### Q3. CodeBuild의 buildspec.yml에서 각 phase의 역할은 무엇인가요?

**A3:**

```yaml
phases:
  install: # 빌드 환경 설정
    runtime-versions:
      java: corretto21
    commands:
      - pip3 install aws-sam-cli

  pre_build: # 테스트 실행 (품질 게이트)
    commands:
      - cd ServerlessFunction
      - ./gradlew clean test

  build: # 실제 빌드 및 패키징
    commands:
      - sam build
      - sam package --s3-bucket ... --output-template-file packaged-template.yaml

  post_build: # 후처리 (로깅, 정리)
    commands:
      - echo "Build completed"
```

- **install**: 빌드에 필요한 런타임과 도구 설치
- **pre_build**: 테스트 실행 - 실패 시 빌드 중단 (품질 게이트 역할)
- **build**: SAM 애플리케이션 빌드 및 S3에 패키징
- **post_build**: 완료 로그 기록, 정리 작업

---

### Q4. 테스트가 실패하면 배포가 어떻게 되나요?

**A4:**
테스트 실패 시 배포가 자동으로 중단됩니다.

**작동 원리:**

1. `pre_build` 단계에서 `./gradlew clean test` 실행
2. 테스트 실패 시 Gradle이 exit code 1 반환
3. CodeBuild가 비정상 종료로 판단하여 빌드 실패 처리
4. CodePipeline의 Build Stage가 실패 상태가 됨
5. Deploy Stage로 진행되지 않음
6. SNS를 통해 실패 알림 이메일 발송

```
Pipeline Flow:
Source ──▶ Build (테스트 실패) ──✗ Deploy
                    │
                    ▼
              SNS 알림 발송
```

---

### Q5. SAM과 CloudFormation의 관계는 무엇인가요?

**A5:**
SAM(Serverless Application Model)은 CloudFormation의 확장입니다.

**관계:**

- SAM 템플릿은 CloudFormation 템플릿의 상위 집합
- `sam build`/`sam package` 실행 시 SAM 템플릿이 표준 CloudFormation 템플릿으로 변환
- 변환된 템플릿(`packaged-template.yaml`)을 CloudFormation이 배포

**SAM의 장점:**

1. 간결한 문법: `AWS::Serverless::Function`으로 Lambda + API Gateway + IAM 역할 한번에 정의
2. 로컬 테스트: `sam local invoke`로 Lambda 로컬 실행 가능
3. 자동 패키징: 코드를 S3에 업로드하고 참조 자동 생성

```yaml
# SAM 템플릿 (간결)
Type: AWS::Serverless::Function
Properties:
  Handler: handler.main
  Runtime: java21
  Events:
    Api:
      Type: Api
      Properties:
        Path: /hello
        Method: get

# 변환된 CloudFormation (복잡)
# Lambda Function + API Gateway + IAM Role + Permission 등 여러 리소스로 확장
```

---

### Q6. 배포 중 롤백은 어떻게 처리되나요?

**A6:**
CloudFormation의 기본 롤백 기능을 활용합니다.

**설정:**

```yaml
# samconfig.toml
disable_rollback = false  # 롤백 활성화
```

**롤백 시나리오:**

1. **배포 실패 시**: CloudFormation이 자동으로 이전 상태로 롤백
2. **Lambda 오류 시**:
	- 현재는 기본 롤백만 사용
	- 추가로 Canary/Linear 배포 설정 가능 (AWS CodeDeploy 연동)

```yaml
# 점진적 배포 예시 (선택적 구현)
DeploymentPreference:
  Type: Canary10Percent5Minutes  # 10%에 5분간 배포 후 문제없으면 전체 배포
```

---

### Q7. 파이프라인의 아티팩트는 어떻게 관리되나요?

**A7:**
S3 버킷을 사용하여 아티팩트를 관리합니다.

```yaml
ArtifactBucket:
  Type: AWS::S3::Bucket
  Properties:
    BucketName: group2-englishstudy-pipeline-artifacts
    VersioningConfiguration:
      Status: Enabled          # 버전 관리 활성화
    BucketEncryption:
      ServerSideEncryptionConfiguration:
        - ServerSideEncryptionByDefault:
            SSEAlgorithm: AES256  # 암호화
```

**아티팩트 종류:**

1. **SourceArtifact**: GitHub에서 가져온 소스 코드 ZIP
2. **BuildArtifact**: 빌드된 `packaged-template.yaml`
3. **Cache**: Gradle 캐시 (빌드 시간 단축용)

---

### Q8. 파이프라인 알림은 어떻게 구현했나요?

**A8:**
AWS CodeStar Notifications와 SNS를 연동하여 구현했습니다.

```yaml
# SNS Topic 생성
NotificationTopic:
  Type: AWS::SNS::Topic
  Properties:
    TopicName: cicd-pipeline-notifications

# 이메일 구독
EmailSubscription:
  Type: AWS::SNS::Subscription
  Properties:
    TopicArn: !Ref NotificationTopic
    Protocol: email
    Endpoint: !Ref NotificationEmail

# 알림 규칙
PipelineNotificationRule:
  Type: AWS::CodeStarNotifications::NotificationRule
  Properties:
    EventTypeIds:
      - codepipeline-pipeline-pipeline-execution-started
      - codepipeline-pipeline-pipeline-execution-succeeded
      - codepipeline-pipeline-pipeline-execution-failed
    Targets:
      - TargetType: SNS
        TargetAddress: !Ref NotificationTopic
```

---

### Q9. CI/CD 구축 중 겪은 문제와 해결 방법은?

**A9:**

**문제 1: Gradle Wrapper를 찾을 수 없음**

- 원인: `.gitignore`에서 `gradle/` 폴더 전체가 제외됨
- 해결: `.gitignore` 수정하여 `!gradle/wrapper/` 예외 추가

**문제 2: JAVA_HOME 환경 변수 오류**

- 원인: CodeBuild에서 JAVA_HOME을 수동 설정했으나 경로 불일치
- 해결: `runtime-versions: java: corretto21`만 사용하고 JAVA_HOME 수동 설정 제거

**문제 3: SAM package S3 버킷 참조 오류**

- 원인: 환경 변수를 사용한 멀티라인 명령어에서 변수 치환 실패
- 해결: 단일 라인으로 버킷 이름 직접 지정

**문제 4: Lambda 환경 변수 누락**

- 원인: WebSocket Connect 함수에 `WEBSOCKET_ENDPOINT` 환경 변수 미설정
- 해결: `template.yaml`에 환경 변수 추가

---

### Q10. 현재 CI/CD의 개선점이 있다면?

**A10:**

1. **테스트 커버리지 게이트**
	- 현재: 테스트 실행만 함
	- 개선: 커버리지 80% 미만 시 빌드 실패 설정

2. **점진적 배포 (Canary/Blue-Green)**
	- 현재: 전체 교체 배포
	- 개선: Lambda Alias + CodeDeploy로 Canary 배포 구현

3. **다중 환경 지원**
	- 현재: prod 단일 환경
	- 개선: dev, staging, prod 분리 및 승인 단계 추가

4. **보안 스캔**
	- 개선: 의존성 취약점 스캔 (OWASP Dependency-Check) 추가

5. **성능 테스트**
	- 개선: 배포 전 부하 테스트 단계 추가

---

### Q11. IaC(Infrastructure as Code)를 사용한 이유는?

**A11:**
파이프라인 자체도 CloudFormation 템플릿(`pipeline.yaml`)으로 정의했습니다.

**장점:**

1. **버전 관리**: 인프라 변경 이력을 Git으로 추적
2. **재현성**: 동일한 파이프라인을 다른 프로젝트/계정에 쉽게 복제
3. **리뷰 가능**: 인프라 변경도 코드 리뷰 프로세스 적용
4. **자동화**: 수동 콘솔 작업 없이 `aws cloudformation deploy`로 생성/업데이트
5. **문서화**: 템플릿 자체가 인프라 문서 역할

---

### Q12. CodeBuild와 Jenkins의 차이점은?

**A12:**

| 항목     | CodeBuild     | Jenkins              |
|--------|---------------|----------------------|
| 관리     | 완전 관리형 (서버리스) | 자체 서버 운영 필요          |
| 비용     | 빌드 시간 기반 과금   | 서버 운영 비용             |
| 확장성    | 자동 확장         | 수동 확장 필요             |
| AWS 통합 | 네이티브 통합       | 플러그인 필요              |
| 커스터마이징 | buildspec.yml | Jenkinsfile (Groovy) |
| 플러그인   | 제한적           | 풍부한 생태계              |

**선택 이유:**

- AWS 서비스 중심 아키텍처에서 네이티브 통합의 이점
- 서버 관리 부담 없음
- SAM/CloudFormation과의 원활한 연동

---

## 6. 핵심 용어 정리

| 용어                                  | 설명                                             |
|-------------------------------------|------------------------------------------------|
| CI (Continuous Integration)         | 코드 변경을 자주 통합하고 자동 테스트하는 방식                     |
| CD (Continuous Delivery/Deployment) | 자동으로 프로덕션까지 배포하는 방식                            |
| Pipeline                            | 소스-빌드-배포로 이어지는 자동화된 워크플로우                      |
| Artifact                            | 빌드 결과물 (패키징된 코드, 템플릿 등)                        |
| buildspec.yml                       | CodeBuild의 빌드 명세 파일                            |
| SAM                                 | Serverless Application Model - 서버리스 앱 정의 프레임워크 |
| IaC                                 | Infrastructure as Code - 코드로 인프라 관리            |

---

## 7. 참고 명령어

```bash
# 파이프라인 생성
aws cloudformation deploy \
  --template-file cicd/pipeline.yaml \
  --stack-name group2-cicd-pipeline \
  --capabilities CAPABILITY_NAMED_IAM \
  --parameter-overrides NotificationEmail=your@email.com

# 파이프라인 상태 확인
aws codepipeline get-pipeline-state --name group2-englishstudy-pipeline

# 수동 파이프라인 실행
aws codepipeline start-pipeline-execution --name group2-englishstudy-pipeline

# 빌드 로그 확인
aws logs tail /aws/codebuild/group2-englishstudy-build --follow
```
