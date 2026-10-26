// 시나리오 3. 스파이크 — 갑작스러운 접속 폭증과 자동 확장
// 목표: 폭증 구간 실패율 < 5%, HPA가 Pod를 늘린 뒤 p95가 시나리오 1 수준으로 회복 (Grafana로 함께 관찰)
import { browse } from './lib.js';

export const options = {
  stages: [
    { duration: '1m', target: 20 },
    { duration: '10s', target: 500 },
    { duration: '3m', target: 500 },
    { duration: '2m', target: 20 },
  ],
  thresholds: {
    http_req_failed: ['rate<0.05'],
  },
};

export default function () {
  browse();
}
