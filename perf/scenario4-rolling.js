// 시나리오 4. 롤링 배포 중 요청 실패율
// 시나리오 1·2를 섞어 가상 사용자 50명으로 유지하는 동안 새 이미지로 롤링 배포한다
// (kubectl rollout restart 또는 develop/main 머지). 목표: 배포 중 실패 요청 0건
import { ask, browse, createUsers, QUESTION_THRESHOLDS } from './lib.js';

const DURATION = __ENV.DURATION || '10m';

export const options = {
  scenarios: {
    read: { executor: 'constant-vus', vus: 40, duration: DURATION, exec: 'read' },
    question: { executor: 'constant-vus', vus: 10, duration: DURATION, exec: 'question' },
  },
  setupTimeout: '2m',
  thresholds: {
    ...QUESTION_THRESHOLDS,
    http_req_failed: ['rate==0'],
  },
};

export function setup() {
  return createUsers(100);
}

export function read() {
  browse();
}

export function question(users) {
  ask(users);
}
