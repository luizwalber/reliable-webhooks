'use strict';

/**
 * PROTOTYPE — throwaway. Answers wayfinder ticket #2 (State-machine & retry-topology
 * sketch, part of map #1) by letting a human drive an event through three candidate
 * retry-topic topologies and state machines, watching for edge cases that don't feel
 * right on paper. This module is pure: no I/O, no console. See README.md.
 */

// ---- Topology definitions --------------------------------------------------

const TOPOLOGIES = {
  A_FIXED_BANDS: {
    name: 'A — Fixed delay-band topics',
    description:
      'A small fixed set of retry topics, one per delay band. Attempt N routes to a ' +
      'band by lookup table, regardless of exact attempt count beyond the table length.',
    topics: [
      'webhook.delivery.main',
      'webhook.delivery.retry.30s',
      'webhook.delivery.retry.5m',
      'webhook.delivery.retry.30m',
      'webhook.delivery.dlq',
    ],
    maxAttempts: 5,
    // attempt 1 -> main, attempt 2 -> retry.30s, attempt 3 -> retry.5m, attempt 4 -> retry.30m, attempt 5 -> last try then DLQ
    topicForAttempt(attemptNumber) {
      const bands = ['webhook.delivery.main', 'webhook.delivery.retry.30s', 'webhook.delivery.retry.5m', 'webhook.delivery.retry.30m'];
      if (attemptNumber <= bands.length) return bands[attemptNumber - 1];
      return null; // exhausted -> DLQ
    },
  },

  B_PER_ATTEMPT: {
    name: 'B — Per-attempt-number topics',
    description:
      'One retry topic per attempt number (dynamically named), delay computed by the ' +
      'consumer as base * 2^attempt + jitter. More topics, but exact per-attempt delay ' +
      'instead of banding.',
    topics: ['webhook.delivery.main', 'webhook.delivery.retry.attempt-1', 'webhook.delivery.retry.attempt-2', 'webhook.delivery.retry.attempt-3', 'webhook.delivery.dlq'],
    maxAttempts: 4,
    topicForAttempt(attemptNumber) {
      if (attemptNumber === 1) return 'webhook.delivery.main';
      if (attemptNumber <= 4) return `webhook.delivery.retry.attempt-${attemptNumber - 1}`;
      return null;
    },
  },

  C_SINGLE_DELAYED: {
    name: 'C — Single delayed-retry topic',
    description:
      'Only two live topics: main + one shared delayed-retry topic. Every retry ' +
      'republishes to the same topic carrying a `not_before` timestamp header; the ' +
      'worker re-queues (skips, does not process) messages read before their time. ' +
      'Fewest topics, but delivery-worker logic owns the delay instead of Kafka layout.',
    topics: ['webhook.delivery.main', 'webhook.delivery.retry.delayed', 'webhook.delivery.dlq'],
    maxAttempts: 5,
    topicForAttempt(attemptNumber) {
      if (attemptNumber === 1) return 'webhook.delivery.main';
      if (attemptNumber <= 5) return 'webhook.delivery.retry.delayed';
      return null;
    },
  },
};

// ---- Event state machine ---------------------------------------------------

const EVENT_STATES = ['RECEIVED', 'OUTBOXED', 'PUBLISHED', 'DELIVERING', 'AWAITING_RETRY', 'DELIVERED', 'DEAD'];
const ATTEMPT_OUTCOMES = ['SUCCESS', 'TIMEOUT', 'HTTP_5XX', 'HTTP_4XX', 'CIRCUIT_OPEN'];
const CIRCUIT_STATES = ['CLOSED', 'OPEN', 'HALF_OPEN'];

function initialState(topologyKey) {
  return {
    topologyKey,
    eventState: 'RECEIVED',
    attemptNumber: 0,
    attempts: [], // { number, outcome, topic }
    currentTopic: null,
    circuitState: 'CLOSED',
    log: ['Event received (idempotency key checked, not a duplicate).'],
  };
}

function topology(state) {
  return TOPOLOGIES[state.topologyKey];
}

function ingest(state) {
  if (state.eventState !== 'RECEIVED') return state; // no-op if already past ingest
  return {
    ...state,
    eventState: 'OUTBOXED',
    log: [...state.log, 'Persisted to outbox in same DB transaction as business op.'],
  };
}

function publish(state) {
  if (state.eventState !== 'OUTBOXED') return state;
  const topic = topology(state).topicForAttempt(1);
  return {
    ...state,
    eventState: 'PUBLISHED',
    currentTopic: topic,
    log: [...state.log, `Outbox poller published to ${topic}.`],
  };
}

function beginDelivery(state) {
  if (!['PUBLISHED', 'AWAITING_RETRY'].includes(state.eventState)) return state;
  return {
    ...state,
    eventState: 'DELIVERING',
    attemptNumber: state.attemptNumber + 1,
    log: [...state.log, `Worker picked up attempt ${state.attemptNumber + 1} from ${state.currentTopic}.`],
  };
}

function attemptOutcome(state, outcome) {
  if (state.eventState !== 'DELIVERING') return state;
  const attemptRecord = { number: state.attemptNumber, outcome, topic: state.currentTopic };
  const attempts = [...state.attempts, attemptRecord];

  if (outcome === 'SUCCESS') {
    return {
      ...state,
      attempts,
      eventState: 'DELIVERED',
      log: [...state.log, `Attempt ${state.attemptNumber} succeeded. Event DELIVERED (terminal).`],
    };
  }

  // failure of some kind — decide next topic
  const nextTopic = topology(state).topicForAttempt(state.attemptNumber + 1);
  if (nextTopic === null) {
    return {
      ...state,
      attempts,
      eventState: 'DEAD',
      currentTopic: topology(state).topics[topology(state).topics.length - 1], // dlq
      log: [...state.log, `Attempt ${state.attemptNumber} failed (${outcome}). Attempts exhausted -> routed to DLQ.`],
    };
  }
  return {
    ...state,
    attempts,
    eventState: 'AWAITING_RETRY',
    currentTopic: nextTopic,
    log: [...state.log, `Attempt ${state.attemptNumber} failed (${outcome}). Republished to ${nextTopic}.`],
  };
}

function manualRetryFromDlq(state, resetAttempts) {
  if (state.eventState !== 'DEAD') return state;
  const topic = topology(state).topicForAttempt(1);
  return {
    ...state,
    eventState: 'AWAITING_RETRY',
    attemptNumber: resetAttempts ? 0 : state.attemptNumber,
    currentTopic: topic,
    log: [
      ...state.log,
      `Manual retry from DLQ -> re-enters at ${topic}` + (resetAttempts ? ' (attempt count RESET to 0).' : ' (attempt count PRESERVED — next attempt continues numbering).'),
    ],
  };
}

function toggleCircuit(state) {
  const idx = CIRCUIT_STATES.indexOf(state.circuitState);
  const next = CIRCUIT_STATES[(idx + 1) % CIRCUIT_STATES.length];
  return {
    ...state,
    circuitState: next,
    log: [...state.log, `Circuit breaker for this endpoint manually set to ${next}.`],
  };
}

// if circuit is OPEN, a delivery attempt short-circuits to CIRCUIT_OPEN outcome
// without an actual HTTP call — caller (TUI) checks state.circuitState before
// offering SUCCESS/TIMEOUT/HTTP_5XX/HTTP_4XX as attempt outcomes.

module.exports = {
  TOPOLOGIES,
  EVENT_STATES,
  ATTEMPT_OUTCOMES,
  CIRCUIT_STATES,
  initialState,
  ingest,
  publish,
  beginDelivery,
  attemptOutcome,
  manualRetryFromDlq,
  toggleCircuit,
};
