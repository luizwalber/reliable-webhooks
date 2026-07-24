#!/usr/bin/env node
'use strict';

/**
 * PROTOTYPE TUI — throwaway shell over model.js. Run: node tui.js
 * Drives a single simulated event through one of three candidate topologies.
 */

const readline = require('readline');
const M = require('./model');

const BOLD = '\x1b[1m';
const DIM = '\x1b[2m';
const RESET = '\x1b[0m';
const YELLOW = '\x1b[33m';
const GREEN = '\x1b[32m';
const RED = '\x1b[31m';

let topologyKey = 'A_FIXED_BANDS';
let state = M.initialState(topologyKey);

function color(text, code) {
  return `${code}${text}${RESET}`;
}

function render() {
  console.clear();
  const topo = M.TOPOLOGIES[state.topologyKey];
  console.log(color(`Topology ${state.topologyKey}: ${topo.name}`, BOLD));
  console.log(color(topo.description, DIM));
  console.log(color(`Topics: ${topo.topics.join(', ')}`, DIM));
  console.log();
  console.log(color('Event state:  ', BOLD) + eventStateColor(state.eventState));
  console.log(color('Attempt #:    ', BOLD) + state.attemptNumber);
  console.log(color('Current topic:', BOLD) + ' ' + (state.currentTopic || color('(none yet)', DIM)));
  console.log(color('Circuit:      ', BOLD) + circuitColor(state.circuitState));
  console.log();
  console.log(color('Attempts so far:', BOLD));
  if (state.attempts.length === 0) {
    console.log(color('  (none)', DIM));
  } else {
    for (const a of state.attempts) {
      console.log(`  #${a.number} ${a.outcome.padEnd(12)} ${color('via ' + a.topic, DIM)}`);
    }
  }
  console.log();
  console.log(color('Log:', BOLD));
  for (const line of state.log.slice(-6)) {
    console.log(color('  ' + line, DIM));
  }
  console.log();
  printShortcuts();
}

function eventStateColor(s) {
  if (s === 'DELIVERED') return color(s, GREEN);
  if (s === 'DEAD') return color(s, RED);
  if (s === 'AWAITING_RETRY') return color(s, YELLOW);
  return s;
}

function circuitColor(s) {
  if (s === 'OPEN') return color(s, RED);
  if (s === 'HALF_OPEN') return color(s, YELLOW);
  return color(s, GREEN);
}

function printShortcuts() {
  const shortcuts = [];
  if (state.eventState === 'RECEIVED') shortcuts.push(['i', 'ingest (outbox)']);
  if (state.eventState === 'OUTBOXED') shortcuts.push(['p', 'outbox poller publishes']);
  if (state.eventState === 'PUBLISHED' || state.eventState === 'AWAITING_RETRY') shortcuts.push(['w', 'worker picks up attempt']);
  if (state.eventState === 'DELIVERING') {
    if (state.circuitState === 'OPEN') {
      shortcuts.push(['x', 'attempt short-circuits (CIRCUIT_OPEN)']);
    } else {
      shortcuts.push(['s', 'attempt outcome: SUCCESS']);
      shortcuts.push(['1', 'attempt outcome: TIMEOUT']);
      shortcuts.push(['2', 'attempt outcome: HTTP_5XX']);
      shortcuts.push(['3', 'attempt outcome: HTTP_4XX']);
    }
  }
  if (state.eventState === 'DEAD') {
    shortcuts.push(['r', 'manual DLQ retry (reset attempts)']);
    shortcuts.push(['R', 'manual DLQ retry (preserve attempts)']);
  }
  shortcuts.push(['c', 'toggle circuit breaker state']);
  shortcuts.push(['t', 'switch topology (resets event)']);
  shortcuts.push(['n', 'new event (same topology)']);
  shortcuts.push(['q', 'quit']);

  const line = shortcuts.map(([k, d]) => `${color('[' + k + ']', BOLD)} ${color(d, DIM)}`).join('  ');
  console.log(line);
}

function handle(key) {
  switch (key) {
    case 'i': state = M.ingest(state); break;
    case 'p': state = M.publish(state); break;
    case 'w': state = M.beginDelivery(state); break;
    case 's': state = M.attemptOutcome(state, 'SUCCESS'); break;
    case '1': state = M.attemptOutcome(state, 'TIMEOUT'); break;
    case '2': state = M.attemptOutcome(state, 'HTTP_5XX'); break;
    case '3': state = M.attemptOutcome(state, 'HTTP_4XX'); break;
    case 'x': state = M.attemptOutcome(state, 'CIRCUIT_OPEN'); break;
    case 'r': state = M.manualRetryFromDlq(state, true); break;
    case 'R': state = M.manualRetryFromDlq(state, false); break;
    case 'c': state = M.toggleCircuit(state); break;
    case 't': {
      const keys = Object.keys(M.TOPOLOGIES);
      const idx = keys.indexOf(topologyKey);
      topologyKey = keys[(idx + 1) % keys.length];
      state = M.initialState(topologyKey);
      break;
    }
    case 'n': state = M.initialState(topologyKey); break;
    case 'q': process.exit(0); break;
    default: break;
  }
}

readline.emitKeypressEvents(process.stdin);
if (process.stdin.isTTY) process.stdin.setRawMode(true);
render();
process.stdin.on('keypress', (str, key) => {
  if (key && key.ctrl && key.name === 'c') process.exit(0);
  handle(str);
  render();
});
