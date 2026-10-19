// k6 시나리오 공통 코드 (위키 "성능 테스트" 문서의 시나리오 1~4)
import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter, Trend } from 'k6/metrics';

// 대상 주소. 온프렘·AWS·로컬 공통으로 -e BASE_URL=... 로 넣는다
export const BASE_URL = (__ENV.BASE_URL || 'http://localhost:8080').replace(/\/$/, '');

// 서버의 AI_MOCK_DELAY와 같은 값(ms). 질문 접수→완료 기준값 = 이 값 + 5초
export const MOCK_DELAY_MS = Number(__ENV.MOCK_DELAY_MS || 0);

export const CATEGORIES = ['AI', 'IT_GENERAL', 'MOBILE_TELECOM', 'COMPUTER', 'SECURITY', 'SCIENCE'];

const JSON_HEADERS = { 'Content-Type': 'application/json' };

/** 1~3초 쉬기 (사용자가 화면을 보는 시간) */
export function think() {
  sleep(1 + Math.random() * 2);
}

/** 시나리오 1: 비로그인 사용자가 홈을 보고 분야 페이지로 이동 */
export function browse() {
  const home = http.get(`${BASE_URL}/api/news/home`, { tags: { name: 'home' } });
  check(home, { '홈 200': (r) => r.status === 200 });
  think();
  const category = CATEGORIES[Math.floor(Math.random() * CATEGORIES.length)];
  const page = http.get(`${BASE_URL}/api/news/categories/${category}`, { tags: { name: 'category' } });
  check(page, { '분야 200': (r) => r.status === 200 });
  think();
}

/**
 * 테스트 계정 n명을 새로 만든다. 질문은 사용자당 하루 10회 제한이라 고정 계정을 쓰면 같은 날 다시 돌릴 때 429가 난다.
 * 그래서 실행마다 perf-<실행 시각>-001 형식의 새 계정을 만든다 (setup()에서 한 번 실행).
 */
export function createUsers(n) {
  const run = Date.now();
  const users = [];
  for (let i = 1; i <= n; i++) {
    const user = { email: `perf-${run}-${String(i).padStart(3, '0')}@test.com`, password: 'perfpass123' };
    const res = http.post(
      `${BASE_URL}/api/auth/signup`,
      JSON.stringify({ ...user, nickname: `perf${i}` }),
      { headers: JSON_HEADERS, tags: { name: 'signup' } },
    );
    if (res.status !== 201) {
      throw new Error(`테스트 계정 생성 실패 (${res.status}): ${res.body}`);
    }
    users.push(user);
  }
  return users;
}

const acceptTime = new Trend('question_accept', true);
const completeTime = new Trend('question_complete', true);
const questionFailed = new Counter('question_failed');
const questionTimeout = new Counter('question_timeout');

const QUERIES = [
  'AI 기본법 시행령 내용 알려줘',
  '요즘 반도체 시장 어때?',
  '최근 보안 사고 정리해줘',
  '통신사 요금제 변화 알려줘',
  '국산 AI 모델 공개 소식 알려줘',
];

/**
 * 시나리오 2: 로그인 → 질문 → 2초마다 결과 확인 (완료·거절·실패까지).
 * 질문 수는 사용자당 하루 10회를 넘지 않게 반복마다 사용자를 37칸씩 건너뛰며 100명에 고르게 나누고, 질문 사이에 20~30초 쉰다.
 * (시나리오 2: 50 VU × 6분 ≈ 질문 650개 → 사용자당 평균 6~7회)
 */
export function ask(users) {
  const user = users[(__VU - 1 + __ITER * 37) % users.length];
  const login = http.post(`${BASE_URL}/api/auth/login`, JSON.stringify(user), {
    headers: JSON_HEADERS,
    tags: { name: 'login' },
  });
  if (!check(login, { '로그인 200': (r) => r.status === 200 })) {
    return;
  }

  const query = QUERIES[Math.floor(Math.random() * QUERIES.length)];
  const started = Date.now();
  const res = http.post(`${BASE_URL}/api/questions`, JSON.stringify({ query }), {
    headers: JSON_HEADERS,
    tags: { name: 'ask' },
  });
  acceptTime.add(res.timings.duration);
  if (!check(res, { '질문 접수 202': (r) => r.status === 202 })) {
    return;
  }
  const id = res.json('questionId');

  // 서버는 60초가 지나면 FAILED로 바꾼다. 그보다 조금 더 기다린다
  while (Date.now() - started < 70_000) {
    sleep(2);
    const poll = http.get(`${BASE_URL}/api/questions/${id}`, { tags: { name: 'poll' } });
    const status = poll.status === 200 ? poll.json('status') : null;
    if (status === 'COMPLETED' || status === 'REJECTED') {
      completeTime.add(Date.now() - started);
      sleep(20 + Math.random() * 10);
      return;
    }
    if (status === 'FAILED') {
      questionFailed.add(1);
      sleep(20 + Math.random() * 10);
      return;
    }
  }
  questionTimeout.add(1);
}

/** 시나리오 2 기준값 (시나리오 4에서도 쓴다) */
export const QUESTION_THRESHOLDS = {
  question_accept: ['p(95)<500'],
  question_complete: [`p(95)<${MOCK_DELAY_MS + 5000}`],
  question_failed: ['count==0'],
  question_timeout: ['count==0'],
};
