# k6 성능 테스트

위키 "성능 테스트" 문서의 시나리오 1~4. 부하 도구가 서버 자원을 같이 쓰지 않도록 **클러스터 밖 PC에서** 실행한다.

| 파일 | 시나리오 | 부하 | 목표 (실패하면 k6 종료 코드 99) |
|---|---|---|---|
| `scenario1-read.js` | 1. 조회 (홈 → 분야) | 0→200명 2분, 200명 5분, →0 1분 | p95 < 300ms, 실패율 < 1% |
| `scenario2-question.js` | 2. 질문 (로그인 → 질문 → 2초마다 확인) | 0→50명 1분, 50명 5분 | 접수 p95 < 500ms, 접수→완료 p95 < mock 지연 + 5초, FAILED·타임아웃 0 |
| `scenario3-spike.js` | 3. 스파이크 | 20명 1분 → 10초 만에 500명 → 3분 → 20명 2분 | 실패율 < 5% (HPA는 Grafana로 함께 관찰) |
| `scenario4-rolling.js` | 4. 롤링 배포 중 | 조회 40명 + 질문 10명, `DURATION`(기본 10분) | 실패 요청 0건 |

## 실행

k6를 설치하지 않고 Docker로 돌린다. `backend` 폴더에서:

```bash
docker run --rm -v "%cd%/perf:/perf" grafana/k6:2.2.0 run -e BASE_URL=http://<대상 주소> /perf/scenario1-read.js
```

- PowerShell은 `%cd%` 대신 `${PWD}`
- 같은 PC의 로컬 compose에 돌릴 때 `BASE_URL=http://host.docker.internal:8080`
- 온프렘·AWS: Nginx나 ALB 주소 (예: `http://<온프렘 주소>` — `/api/...`가 백엔드로 가는 주소)

## 환경 변수

| 이름 | 기본값 | 설명 |
|---|---|---|
| `BASE_URL` | `http://localhost:8080` | 대상 주소. 온프렘·AWS 공통 |
| `MOCK_DELAY_MS` | `0` | 서버의 `AI_MOCK_DELAY`와 같은 값(ms). 시나리오 2·4의 접수→완료 기준값이 이 값 + 5초 |
| `DURATION` | `10m` | 시나리오 4의 유지 시간 |

## 테스트 계정

질문은 사용자당 하루 10회 제한이 있다. 고정 계정을 쓰면 같은 날 두 번째 실행부터 429가 나서, 시나리오 2·4는 실행할 때마다 `setup()`에서 **새 계정 100명**(`perf-<실행 시각>-001@test.com` …)을 만들고 질문을 100명에게 고르게 나눈다 (시나리오 2 기준 사용자당 6~7회). 테스트 DB에 계정이 쌓이므로 운영 데이터와 섞이지 않는 환경에서 돌린다.

## 짧게 확인만 할 때

`--vus`, `--duration`을 주면 위 단계 대신 그 값으로 돈다.

```bash
docker run --rm -v "%cd%/perf:/perf" grafana/k6:2.2.0 run -e BASE_URL=http://host.docker.internal:8080 --vus 5 --duration 45s /perf/scenario2-question.js
```

결과를 파일로 남기려면 `--summary-export /perf/result.json`을 붙인다.
