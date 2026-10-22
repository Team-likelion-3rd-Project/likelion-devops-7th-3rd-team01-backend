// 시나리오 1. 조회 부하 — 홈·분야 페이지 (비로그인)
// 목표: p95 < 300ms, 실패율 < 1%
import { browse } from './lib.js';

export const options = {
  stages: [
    { duration: '2m', target: 200 },
    { duration: '5m', target: 200 },
    { duration: '1m', target: 0 },
  ],
  thresholds: {
    http_req_duration: ['p(95)<300'],
    http_req_failed: ['rate<0.01'],
  },
};

export default function () {
  browse();
}
