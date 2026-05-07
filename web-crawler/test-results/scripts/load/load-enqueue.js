import http from 'k6/http';
import { check } from 'k6';

export const options = {
  scenarios: {
    enqueue: {
      executor: 'constant-arrival-rate',
      rate: 50,
      timeUnit: '1s',
      duration: '60s',
      preAllocatedVUs: 30,
      maxVUs: 60,
    },
  },
  thresholds: {
    'http_req_duration': ['p(95)<300'],
  },
};

export default function () {
  const url = `http://wiremock:8080/p${__VU}-${__ITER}`;
  const res = http.post(
    'http://localhost:8081/urls',
    JSON.stringify({ url }),
    { headers: { 'Content-Type': 'application/json' } }
  );
  check(res, { 'status 200': (r) => r.status === 200 });
}
