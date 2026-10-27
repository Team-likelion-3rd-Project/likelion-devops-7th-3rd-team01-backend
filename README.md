# AI 뉴스 브리핑 - backend

IT 뉴스(전자신문 RSS 6개 분야)를 AI가 이슈별로 요약해 보여 주고, 수집한 기사를 근거로 질문에 답하는 서비스의 백엔드입니다.

- 프로젝트 소개·위키·이슈: [likelion-devops-7th-3rd-team01](https://github.com/Team-likelion-3rd-Project/likelion-devops-7th-3rd-team01)
- 배포(Kubernetes 매니페스트, Terraform): [likelion-devops-7th-3rd-team01-infra](https://github.com/Team-likelion-3rd-Project/likelion-devops-7th-3rd-team01-infra)
- API 명세·ERD·환경 변수·성능 테스트·장애 시나리오: 위키

## 기술 구성

| 항목 | 내용 |
|---|---|
| 런타임 | Java 21, Spring Boot 4.1 |
| 저장소 | MySQL 8.4 (Flyway로 스키마 자동 적용), Redis 7 (요약 캐시·질문 대기열) |
| AI | AWS Bedrock Claude Haiku 5.5. 평소·테스트는 mock (`AI_MODE`) |
| 인증 | JWT를 httpOnly 쿠키 `ACCESS_TOKEN`에 저장 |
| 이미지 | Docker 멀티 스테이지, GHCR에 커밋 SHA 태그 |

## 구조

이미지 하나를 `APP_ROLE`로 나눠 실행합니다.

| `APP_ROLE` | 하는 일 | 쿠버네티스 |
|---|---|---|
| `api` | REST API, 오래된 질문 재투입·타임아웃 처리, 지표 | Deployment (최소 2개) |
| `worker` | Redis 대기열에서 질문을 꺼내 AI로 답변 | Deployment |
| `batch` | RSS 수집 → AI 요약 → DB 저장 → 캐시 비우기를 한 번 하고 종료 | CronJob (30분마다) |
| 없음 | api + worker를 한 프로세스로 (로컬·compose) | - |

```
질문:  POST /api/questions → DB에 PENDING 저장 → Redis 대기열 → worker → COMPLETED / REJECTED / FAILED
       (화면은 2초마다 GET /api/questions/{id}로 확인, 60초 넘으면 FAILED, 하루 10회)
요약:  batch(30분) → 분야별 RSS 수집 → 새 기사가 있으면 AI 요약 → news_issues 저장 → Redis 캐시 비우기
조회:  GET /api/news/home, /api/news/categories/{category} → Redis 캐시 (없으면 DB)
```

- Redis가 죽어도 조회는 DB로 응답하고, 질문은 DB에 PENDING으로 남았다가 복구 뒤 다시 처리됩니다.
- 질문 검색은 MySQL FULLTEXT(ngram)로 후보 15개(관련도 3 이상)를 고르고, AI가 답에 실제로 쓴 기사만 출처로 보여 줍니다.

## 로컬 실행

필요: JDK 21, Docker

```bash
docker compose up -d                          # MySQL(3307)·Redis만
./gradlew bootRun                             # 앱은 IDE나 bootRun으로
```

앱까지 컨테이너로 띄우려면:

```bash
docker compose --profile app up -d --build    # MySQL·Redis + 앱(api+worker, 8080) + 요약 배치 1회
docker compose run --rm batch                 # 요약 다시 만들기
```

- 실제 AI로 시험할 때만 `.env`(커밋 금지)에 `AI_MODE=bedrock`과 AWS 키를 넣습니다. `.env.example` 참고
- PC 기본 Java가 21이 아니면 `JAVA_HOME`을 JDK 21로 지정합니다.

## 테스트

```bash
docker compose up -d   # 테스트도 로컬 MySQL·Redis를 씁니다
./gradlew test
```

## 주요 환경 변수

전체 목록은 위키 "환경 변수"에 있습니다.

| 이름 | 필수 | 설명 |
|---|---|---|
| `SPRING_DATASOURCE_URL` / `_USERNAME` / `_PASSWORD` | Y | MySQL. 코드에 기본값이 없어 빠뜨리면 시작하지 않습니다 |
| `SPRING_DATA_REDIS_HOST` | Y | Redis |
| `JWT_SECRET` | Y | JWT 서명 키 (모든 레플리카가 같은 값) |
| `APP_ROLE` | 쿠버네티스에서 Y | `api` / `worker` / `batch` |
| `AI_MODE` | N | `mock`(기본) / `bedrock` / `fail`(장애 시험) |
| `AI_MOCK_DELAY` | N | mock 응답 지연. 부하 테스트는 `5s` |
| `COOKIE_SECURE` | N | http인 온프렘·로컬은 `false` |
| `CORS_ALLOWED_ORIGINS` | N | 화면과 API 출처가 다를 때 (AWS: `https://app.{도메인}`). `*`는 쓸 수 없습니다 |
| `WARMUP_ROUNDS` | N | api 파드가 시작할 때 조회 API를 미리 부르는 횟수 (기본 20바퀴) |

## 운영

| 항목 | 내용 |
|---|---|
| 헬스체크 | `/actuator/health/liveness`, `/actuator/health/readiness` (readiness는 DB만 봄, 예열이 끝난 뒤 UP) |
| 배포 버전 | `/actuator/info`의 `commit` |
| 지표 | `/actuator/prometheus`: `newsbrief_questions_queue_length`, `newsbrief_questions_failed_total`, `newsbrief_batch_last_duration_seconds` 등. 인그레스는 `/api`만 열어 밖에서는 보이지 않습니다 |
| 종료 | graceful shutdown (처리 중 요청 최대 15초). worker는 처리 중 질문을 40초 기다리고, 못 끝내면 대기열로 돌려놓습니다 |

## CI/CD

```
feature/* → develop PR (승인 1명, squash merge)
develop push → 테스트 → GHCR 이미지 → infra deploy-onprem → 온프렘 배포
main push    → 테스트 → GHCR 이미지 → infra deploy-aws    → AWS 배포
```

`main`·`develop`에는 직접 push하지 않습니다.

## 부하 테스트

`perf/`에 k6 시나리오 1~4(조회, 질문, 스파이크, 롤링 배포)가 있습니다. 실행 방법은 [perf/README.md](perf/README.md), 결과는 위키 "성능 테스트".
