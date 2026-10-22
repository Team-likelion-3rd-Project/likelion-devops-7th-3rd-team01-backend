// 시나리오 2. 질문 처리 — 접수부터 답변까지 (mock AI)
// 목표: 접수 p95 < 500ms, 접수→완료 p95 < mock 지연 + 5초, 60초 타임아웃(FAILED) 0건
import { ask, createUsers, QUESTION_THRESHOLDS } from './lib.js';

export const options = {
  stages: [
    { duration: '1m', target: 50 },
    { duration: '5m', target: 50 },
  ],
  setupTimeout: '2m',
  thresholds: {
    ...QUESTION_THRESHOLDS,
    http_req_failed: ['rate<0.01'],
  },
};

// 테스트 계정 100명 (실행마다 새로 만든다, lib.js createUsers 참고)
export function setup() {
  return createUsers(100);
}

export default function (users) {
  ask(users);
}
