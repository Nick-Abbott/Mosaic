import http from 'k6/http';
import { check } from 'k6';
import exec from 'k6/execution';
import { Counter, Trend } from 'k6/metrics';

const route = __ENV.ROUTE;
const field = { light: 'customerId', aggregate: 'customerId', batching: 'catalogId', compute: 'seed' }[route];
if (!field) throw new Error(`Unsupported route: ${route}`);

const successes = new Counter('successful_requests');
const failures = new Counter('http_failures');
const successfulLatency = new Trend('successful_latency', true);
const inputs = [1, 7, 42, 99];

export const options = {
  discardResponseBodies: true,
  scenarios: {
    offered_load: {
      executor: 'constant-arrival-rate',
      rate: Number(__ENV.OFFERED_RPS),
      timeUnit: '1s',
      duration: __ENV.DURATION,
      preAllocatedVUs: Number(__ENV.PREALLOCATED_VUS),
      maxVUs: Number(__ENV.MAX_VUS),
      gracefulStop: '0s',
    },
  },
};

export default function () {
  const input = inputs[exec.scenario.iterationInTest % inputs.length];
  const response = http.post(`${__ENV.BASE_URL}/${route}`, JSON.stringify({ [field]: input }), {
    headers: { 'Content-Type': 'application/json' },
    responseType: 'none',
  });
  const ok = response.status >= 200 && response.status < 300;
  check(response, { 'HTTP success': () => ok });
  if (ok) {
    successes.add(1);
    successfulLatency.add(response.timings.duration);
  } else {
    failures.add(1);
  }
}
