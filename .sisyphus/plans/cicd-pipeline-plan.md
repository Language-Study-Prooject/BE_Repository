# CI/CD Pipeline 기획서

> **프로젝트**: Group2 English Study Backend
> **작성일**: 2026-01-22
> **버전**: 1.0

---

## 1. 요구사항 요약

| 항목      | 선택                               |
|---------|----------------------------------|
| 소스 저장소  | GitHub (유지) + CodePipeline v2 연결 |
| 배포 환경   | prod 단일 환경                       |
| 트리거     | prod 브랜치 push 또는 PR merge        |
| 승인 프로세스 | 완전 자동 (테스트 통과 시 자동 배포)           |
| 알림      | AWS SNS → 이메일                    |

---

## 2. 전체 아키텍처

```
┌─────────────────────────────────────────────────────────────────────────┐
│                        AWS CodePipeline                                  │
├─────────────────────────────────────────────────────────────────────────┤
│                                                                         │
│  ┌──────────┐    ┌──────────────┐    ┌──────────────┐    ┌──────────┐  │
│  │  Source  │───▶│    Build     │───▶│    Deploy    │───▶│  Notify  │  │
│  │ (GitHub) │    │ (CodeBuild)  │    │(CloudFormation)│   │  (SNS)   │  │
│  └──────────┘    └──────────────┘    └──────────────┘    └──────────┘  │
│       │                 │                   │                  │        │
│       ▼                 ▼                   ▼                  ▼        │
│  GitHub v2         ┌────────────┐    SAM Deploy        Email 알림      │
│  Connection        │ buildspec  │    (sam deploy)                      │
│                    │ ──────────  │                                      │
│                    │ - gradle   │                                      │
│                    │ - sam build│                                      │
│                    │ - test     │                                      │
│                    └────────────┘                                      │
│                                                                         │
└─────────────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────────────┐
│                        AWS Resources (prod)                             │
├─────────────────────────────────────────────────────────────────────────┤
│  Lambda (20+) │ API Gateway │ WebSocket │ DynamoDB │ Cognito │ S3     │
└─────────────────────────────────────────────────────────────────────────┘
```

---

## 3. 파이프라인 단계 상세

### 3.1 Source Stage

| 설정              | 값                      |
|-----------------|------------------------|
| Provider        | GitHub (v2 Connection) |
| Repository      | BE_Repository          |
| Branch          | `prod`                 |
| Trigger         | Push / PR Merge        |
| Output Artifact | SourceArtifact         |

**GitHub Connection 설정 필요**:

- AWS Console → CodePipeline → Settings → Connections
- GitHub App 설치 및 Repository 권한 부여

### 3.2 Build Stage

| 설정              | 값                                                |
|-----------------|--------------------------------------------------|
| Provider        | AWS CodeBuild                                    |
| Environment     | `aws/codebuild/amazonlinux2-x86_64-standard:5.0` |
| Compute         | `BUILD_GENERAL1_MEDIUM` (7GB RAM, 4 vCPU)        |
| Timeout         | 30분                                              |
| Input Artifact  | SourceArtifact                                   |
| Output Artifact | BuildArtifact                                    |

**빌드 단계**:

1. Java 21 환경 설정
2. Gradle 빌드 및 테스트
3. SAM 빌드
4. 아티팩트 패키징

### 3.3 Deploy Stage

| 설정           | 값                                      |
|--------------|----------------------------------------|
| Provider     | CloudFormation                         |
| Action Mode  | CREATE_UPDATE                          |
| Stack Name   | `group2-englishstudy-prod`             |
| Template     | packaged-template.yaml                 |
| Capabilities | CAPABILITY_IAM, CAPABILITY_AUTO_EXPAND |
| Role         | CloudFormationExecutionRole            |

### 3.4 Notification Stage

| 설정       | 값                             |
|----------|-------------------------------|
| Provider | AWS SNS                       |
| Topic    | `cicd-pipeline-notifications` |
| Events   | 성공, 실패, 시작                    |

---

## 4. 필요한 AWS 리소스

### 4.1 신규 생성 필요

| 리소스               | 이름                                       | 용도            |
|-------------------|------------------------------------------|---------------|
| CodePipeline      | `group2-englishstudy-pipeline`           | CI/CD 오케스트레이션 |
| CodeBuild Project | `group2-englishstudy-build`              | 빌드 및 테스트      |
| S3 Bucket         | `group2-englishstudy-pipeline-artifacts` | 파이프라인 아티팩트 저장 |
| GitHub Connection | `github-connection`                      | GitHub 연결     |
| SNS Topic         | `cicd-pipeline-notifications`            | 알림            |
| IAM Role          | `CodePipelineServiceRole`                | 파이프라인 실행      |
| IAM Role          | `CodeBuildServiceRole`                   | 빌드 실행         |
| IAM Role          | `CloudFormationExecutionRole`            | 스택 배포         |

### 4.2 기존 활용

| 리소스                      | 용도           |
|--------------------------|--------------|
| S3 `group2-englishstudy` | Lambda 코드 저장 |
| DynamoDB Tables          | 데이터 저장       |
| Cognito User Pool        | 인증           |

---

## 5. buildspec.yml

```yaml
version: 0.2

env:
  variables:
    JAVA_HOME: /usr/lib/jvm/java-21-amazon-corretto
    SAM_CLI_TELEMETRY: 0

phases:
  install:
    runtime-versions:
      java: corretto21
    commands:
      - echo "Installing SAM CLI..."
      - pip3 install aws-sam-cli
      - sam --version

  pre_build:
    commands:
      - echo "Running tests..."
      - cd ServerlessFunction
      - chmod +x gradlew
      - ./gradlew clean test
      - echo "Tests completed"

  build:
    commands:
      - echo "Building SAM application..."
      - cd $CODEBUILD_SRC_DIR/ServerlessFunction
      - sam build
      - echo "Packaging SAM application..."
      - sam package \
        --s3-bucket ${ARTIFACT_BUCKET} \
        --s3-prefix sam-packages \
        --output-template-file packaged-template.yaml

  post_build:
    commands:
      - echo "Build completed on $(date)"

artifacts:
  files:
    - ServerlessFunction/packaged-template.yaml
    - ServerlessFunction/samconfig.toml
  discard-paths: no

cache:
  paths:
    - '/root/.gradle/caches/**/*'
    - '/root/.gradle/wrapper/**/*'

reports:
  junit-reports:
    files:
      - 'ServerlessFunction/build/test-results/test/*.xml'
    file-format: JUNITXML
  jacoco-reports:
    files:
      - 'ServerlessFunction/build/reports/jacoco/test/jacocoTestReport.xml'
    file-format: JACOCOXML
```

---

## 6. samconfig.toml

```toml
version = 0.1

[default.global.parameters]
stack_name = "group2-englishstudy"

[prod]
[prod.deploy]
[prod.deploy.parameters]
stack_name = "group2-englishstudy-prod"
s3_bucket = "group2-englishstudy-pipeline-artifacts"
s3_prefix = "sam-deploy"
region = "ap-northeast-2"
capabilities = "CAPABILITY_IAM CAPABILITY_AUTO_EXPAND"
confirm_changeset = false
disable_rollback = false
fail_on_empty_changeset = false

[prod.build.parameters]
cached = true
parallel = true
```

---

## 7. IAM 역할 및 정책

### 7.1 CodePipeline Service Role

```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Action": [
        "codestar-connections:UseConnection"
      ],
      "Resource": "arn:aws:codestar-connections:*:*:connection/*"
    },
    {
      "Effect": "Allow",
      "Action": [
        "codebuild:BatchGetBuilds",
        "codebuild:StartBuild"
      ],
      "Resource": "*"
    },
    {
      "Effect": "Allow",
      "Action": [
        "cloudformation:CreateStack",
        "cloudformation:DeleteStack",
        "cloudformation:DescribeStacks",
        "cloudformation:UpdateStack",
        "cloudformation:CreateChangeSet",
        "cloudformation:DeleteChangeSet",
        "cloudformation:DescribeChangeSet",
        "cloudformation:ExecuteChangeSet",
        "cloudformation:SetStackPolicy"
      ],
      "Resource": "*"
    },
    {
      "Effect": "Allow",
      "Action": [
        "s3:GetObject",
        "s3:PutObject",
        "s3:GetBucketVersioning"
      ],
      "Resource": [
        "arn:aws:s3:::group2-englishstudy-pipeline-artifacts",
        "arn:aws:s3:::group2-englishstudy-pipeline-artifacts/*"
      ]
    },
    {
      "Effect": "Allow",
      "Action": [
        "iam:PassRole"
      ],
      "Resource": "*",
      "Condition": {
        "StringEqualsIfExists": {
          "iam:PassedToService": "cloudformation.amazonaws.com"
        }
      }
    },
    {
      "Effect": "Allow",
      "Action": [
        "sns:Publish"
      ],
      "Resource": "arn:aws:sns:*:*:cicd-pipeline-notifications"
    }
  ]
}
```

### 7.2 CodeBuild Service Role

```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Action": [
        "logs:CreateLogGroup",
        "logs:CreateLogStream",
        "logs:PutLogEvents"
      ],
      "Resource": "arn:aws:logs:*:*:*"
    },
    {
      "Effect": "Allow",
      "Action": [
        "s3:GetObject",
        "s3:PutObject",
        "s3:GetBucketAcl",
        "s3:GetBucketLocation"
      ],
      "Resource": [
        "arn:aws:s3:::group2-englishstudy-pipeline-artifacts",
        "arn:aws:s3:::group2-englishstudy-pipeline-artifacts/*",
        "arn:aws:s3:::group2-englishstudy",
        "arn:aws:s3:::group2-englishstudy/*"
      ]
    },
    {
      "Effect": "Allow",
      "Action": [
        "codebuild:CreateReportGroup",
        "codebuild:CreateReport",
        "codebuild:UpdateReport",
        "codebuild:BatchPutTestCases",
        "codebuild:BatchPutCodeCoverages"
      ],
      "Resource": "arn:aws:codebuild:*:*:report-group/*"
    }
  ]
}
```

### 7.3 CloudFormation Execution Role

```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Action": [
        "lambda:*",
        "apigateway:*",
        "dynamodb:*",
        "s3:*",
        "cognito-idp:*",
        "sns:*",
        "sqs:*",
        "iam:*",
        "logs:*",
        "events:*",
        "scheduler:*",
        "cloudformation:*"
      ],
      "Resource": "*"
    }
  ]
}
```

> **보안 주의**: 실제 운영 환경에서는 위 정책을 최소 권한 원칙에 맞게 세분화해야 합니다.

---

## 8. SNS 알림 설정

### 8.1 SNS Topic 생성

```bash
aws sns create-topic --name cicd-pipeline-notifications
```

### 8.2 이메일 구독 추가

```bash
aws sns subscribe \
  --topic-arn arn:aws:sns:ap-northeast-2:ACCOUNT_ID:cicd-pipeline-notifications \
  --protocol email \
  --notification-endpoint your-email@example.com
```

### 8.3 CloudWatch Event Rule (파이프라인 상태 변경)

```json
{
  "source": [
    "aws.codepipeline"
  ],
  "detail-type": [
    "CodePipeline Pipeline Execution State Change"
  ],
  "detail": {
    "pipeline": [
      "group2-englishstudy-pipeline"
    ],
    "state": [
      "SUCCEEDED",
      "FAILED",
      "STARTED"
    ]
  }
}
```

---

## 9. 비용 추정 (월간)

| 서비스             | 예상 사용량               | 예상 비용     |
|-----------------|----------------------|-----------|
| CodePipeline    | 1 파이프라인, ~100회 실행    | $1.00     |
| CodeBuild       | ~100회 x 10분 = 1,000분 | $5.00     |
| S3 (아티팩트)       | ~10GB                | $0.25     |
| CloudWatch Logs | ~5GB                 | $2.50     |
| SNS             | ~100 알림              | $0.01     |
| **총 예상 비용**     |                      | **~$9/월** |

> 실제 비용은 배포 빈도와 빌드 시간에 따라 달라질 수 있습니다.

---

## 10. 구현 체크리스트

### Phase 1: 사전 준비

- [ ] AWS 계정 ID 확인
- [ ] ap-northeast-2 리전 선택
- [ ] 필요한 권한 확인 (Admin 또는 필요 권한)

### Phase 2: 기반 리소스 생성

- [ ] S3 버킷 생성: `group2-englishstudy-pipeline-artifacts`
- [ ] SNS Topic 생성: `cicd-pipeline-notifications`
- [ ] SNS 이메일 구독 설정 및 확인
- [ ] GitHub Connection 생성 (AWS Console)

### Phase 3: IAM 역할 생성

- [ ] CodePipelineServiceRole 생성
- [ ] CodeBuildServiceRole 생성
- [ ] CloudFormationExecutionRole 생성

### Phase 4: CodeBuild 프로젝트 생성

- [ ] 프로젝트 생성: `group2-englishstudy-build`
- [ ] 환경 설정 (Amazon Linux 2, Java 21)
- [ ] buildspec.yml 추가

### Phase 5: CodePipeline 생성

- [ ] 파이프라인 생성: `group2-englishstudy-pipeline`
- [ ] Source Stage 설정 (GitHub v2)
- [ ] Build Stage 설정 (CodeBuild)
- [ ] Deploy Stage 설정 (CloudFormation)
- [ ] 알림 설정 (SNS)

### Phase 6: 테스트 및 검증

- [ ] prod 브랜치에 테스트 커밋
- [ ] 파이프라인 자동 트리거 확인
- [ ] 빌드 성공 확인
- [ ] 배포 성공 확인
- [ ] 이메일 알림 수신 확인
- [ ] Lambda 함수 정상 동작 확인

### Phase 7: 문서화

- [ ] README에 CI/CD 섹션 추가
- [ ] 트러블슈팅 가이드 작성
- [ ] 롤백 절차 문서화

---

## 11. 파이프라인 CloudFormation 템플릿 (선택)

아래 템플릿으로 전체 파이프라인을 IaC로 관리할 수 있습니다:

```yaml
AWSTemplateFormatVersion: '2010-09-09'
Description: CI/CD Pipeline for Group2 English Study

Parameters:
  GitHubConnectionArn:
    Type: String
    Description: ARN of the GitHub Connection

  GitHubRepo:
    Type: String
    Default: "your-org/BE_Repository"

  GitHubBranch:
    Type: String
    Default: "prod"

  NotificationEmail:
    Type: String
    Description: Email for pipeline notifications

Resources:
  # S3 Bucket for Artifacts
  ArtifactBucket:
    Type: AWS::S3::Bucket
    Properties:
      BucketName: group2-englishstudy-pipeline-artifacts
      VersioningConfiguration:
        Status: Enabled

  # SNS Topic for Notifications
  NotificationTopic:
    Type: AWS::SNS::Topic
    Properties:
      TopicName: cicd-pipeline-notifications

  EmailSubscription:
    Type: AWS::SNS::Subscription
    Properties:
      TopicArn: !Ref NotificationTopic
      Protocol: email
      Endpoint: !Ref NotificationEmail

  # CodeBuild Project
  CodeBuildProject:
    Type: AWS::CodeBuild::Project
    Properties:
      Name: group2-englishstudy-build
      ServiceRole: !GetAtt CodeBuildRole.Arn
      Artifacts:
        Type: CODEPIPELINE
      Environment:
        Type: LINUX_CONTAINER
        ComputeType: BUILD_GENERAL1_MEDIUM
        Image: aws/codebuild/amazonlinux2-x86_64-standard:5.0
        EnvironmentVariables:
          - Name: ARTIFACT_BUCKET
            Value: !Ref ArtifactBucket
      Source:
        Type: CODEPIPELINE
        BuildSpec: ServerlessFunction/buildspec.yml
      TimeoutInMinutes: 30
      Cache:
        Type: S3
        Location: !Sub "${ArtifactBucket}/cache"

  # CodePipeline
  Pipeline:
    Type: AWS::CodePipeline::Pipeline
    Properties:
      Name: group2-englishstudy-pipeline
      RoleArn: !GetAtt PipelineRole.Arn
      ArtifactStore:
        Type: S3
        Location: !Ref ArtifactBucket
      Stages:
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
                FullRepositoryId: !Ref GitHubRepo
                BranchName: !Ref GitHubBranch
                OutputArtifactFormat: CODE_ZIP
              OutputArtifacts:
                - Name: SourceArtifact
              RunOrder: 1

        - Name: Build
          Actions:
            - Name: Build
              ActionTypeId:
                Category: Build
                Owner: AWS
                Provider: CodeBuild
                Version: '1'
              Configuration:
                ProjectName: !Ref CodeBuildProject
              InputArtifacts:
                - Name: SourceArtifact
              OutputArtifacts:
                - Name: BuildArtifact
              RunOrder: 1

        - Name: Deploy
          Actions:
            - Name: Deploy
              ActionTypeId:
                Category: Deploy
                Owner: AWS
                Provider: CloudFormation
                Version: '1'
              Configuration:
                ActionMode: CREATE_UPDATE
                StackName: group2-englishstudy-prod
                TemplatePath: BuildArtifact::ServerlessFunction/packaged-template.yaml
                Capabilities: CAPABILITY_IAM,CAPABILITY_AUTO_EXPAND
                RoleArn: !GetAtt CloudFormationRole.Arn
              InputArtifacts:
                - Name: BuildArtifact
              RunOrder: 1

  # Pipeline Notification Rule
  PipelineNotificationRule:
    Type: AWS::CodeStarNotifications::NotificationRule
    Properties:
      Name: group2-pipeline-notifications
      DetailType: FULL
      Resource: !Sub "arn:aws:codepipeline:${AWS::Region}:${AWS::AccountId}:${Pipeline}"
      EventTypeIds:
        - codepipeline-pipeline-pipeline-execution-started
        - codepipeline-pipeline-pipeline-execution-succeeded
        - codepipeline-pipeline-pipeline-execution-failed
      Targets:
        - TargetType: SNS
          TargetAddress: !Ref NotificationTopic

  # IAM Roles (simplified - expand as needed)
  PipelineRole:
    Type: AWS::IAM::Role
    Properties:
      AssumeRolePolicyDocument:
        Version: '2012-10-17'
        Statement:
          - Effect: Allow
            Principal:
              Service: codepipeline.amazonaws.com
            Action: sts:AssumeRole
      ManagedPolicyArns:
        - arn:aws:iam::aws:policy/AWSCodePipeline_FullAccess
      Policies:
        - PolicyName: PipelinePolicy
          PolicyDocument:
            Version: '2012-10-17'
            Statement:
              - Effect: Allow
                Action:
                  - codestar-connections:UseConnection
                Resource: !Ref GitHubConnectionArn
              - Effect: Allow
                Action:
                  - s3:*
                Resource:
                  - !GetAtt ArtifactBucket.Arn
                  - !Sub "${ArtifactBucket.Arn}/*"
              - Effect: Allow
                Action:
                  - codebuild:*
                Resource: !GetAtt CodeBuildProject.Arn
              - Effect: Allow
                Action:
                  - cloudformation:*
                Resource: "*"
              - Effect: Allow
                Action:
                  - iam:PassRole
                Resource: !GetAtt CloudFormationRole.Arn

  CodeBuildRole:
    Type: AWS::IAM::Role
    Properties:
      AssumeRolePolicyDocument:
        Version: '2012-10-17'
        Statement:
          - Effect: Allow
            Principal:
              Service: codebuild.amazonaws.com
            Action: sts:AssumeRole
      Policies:
        - PolicyName: CodeBuildPolicy
          PolicyDocument:
            Version: '2012-10-17'
            Statement:
              - Effect: Allow
                Action:
                  - logs:*
                Resource: "*"
              - Effect: Allow
                Action:
                  - s3:*
                Resource:
                  - !GetAtt ArtifactBucket.Arn
                  - !Sub "${ArtifactBucket.Arn}/*"
                  - "arn:aws:s3:::group2-englishstudy"
                  - "arn:aws:s3:::group2-englishstudy/*"
              - Effect: Allow
                Action:
                  - codebuild:CreateReportGroup
                  - codebuild:CreateReport
                  - codebuild:UpdateReport
                  - codebuild:BatchPutTestCases
                  - codebuild:BatchPutCodeCoverages
                Resource: "*"

  CloudFormationRole:
    Type: AWS::IAM::Role
    Properties:
      AssumeRolePolicyDocument:
        Version: '2012-10-17'
        Statement:
          - Effect: Allow
            Principal:
              Service: cloudformation.amazonaws.com
            Action: sts:AssumeRole
      ManagedPolicyArns:
        - arn:aws:iam::aws:policy/AdministratorAccess  # Narrow down in production!

Outputs:
  PipelineUrl:
    Value: !Sub "https://${AWS::Region}.console.aws.amazon.com/codesuite/codepipeline/pipelines/${Pipeline}/view"
```

---

## 12. 트러블슈팅 가이드

### 빌드 실패: Java 버전 문제

```
Error: Unsupported class file major version 65
```

**해결**: CodeBuild 이미지에서 Java 21 (Corretto) 사용 확인

### 배포 실패: IAM 권한 부족

```
User: arn:aws:sts::xxx is not authorized to perform: iam:CreateRole
```

**해결**: CloudFormationExecutionRole에 IAM 권한 추가

### SAM 빌드 실패: 메모리 부족

```
FATAL ERROR: CALL_AND_RETRY_LAST Allocation failed - JavaScript heap out of memory
```

**해결**: CodeBuild compute type을 `BUILD_GENERAL1_LARGE`로 변경

### GitHub Connection 인증 실패

**해결**: AWS Console에서 Connection 상태 확인 → GitHub App 재인증

---

## 13. 다음 단계

1. **기획서 검토 및 승인**
2. **Phase 1-7 순차 실행**
3. **첫 배포 테스트**
4. **모니터링 대시보드 구성 (선택)**

---

> **문의사항**: CI/CD 파이프라인 구현 중 문제가 있으면 문의하세요.
