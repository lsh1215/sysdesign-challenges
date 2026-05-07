import http from 'k6/http';
import { check } from 'k6';

export const options = {
  scenarios: {
    dequeue: {
      executor: 'constant-arrival-rate',
      rate: 100,
      timeUnit: '1s',
      duration: '60s',
      preAllocatedVUs: 50,
      maxVUs: 100,
    },
  },
  thresholds: {
    'http_req_duration': ['p(95)<200'],
    'http_req_failed': ['rate<0.05'],
  },
};

export default function () {
  const res = http.get('http://localhost:8081/urls/next');
  check(res, { 'status 200 or 204': (r) => r.status === 200 || r.status === 204 });
}
