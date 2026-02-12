#!/usr/bin/env node
import fs from 'node:fs/promises';

const API_BASE = process.env.API_BASE_URL ?? 'http://localhost:8080';
const inquiryId = process.env.INQUIRY_ID;
const evalPath = process.env.EVALSET_PATH ?? 'backend/app-api/src/main/resources/evaluation/sprint3_evalset_v1.json';

if (!inquiryId) {
  console.error('INQUIRY_ID 환경변수가 필요합니다.');
  process.exit(1);
}

const raw = await fs.readFile(evalPath, 'utf-8');
const evalset = JSON.parse(raw);

let correct = 0;
const rows = [];

for (const item of evalset) {
  const res = await fetch(`${API_BASE}/api/v1/inquiries/${inquiryId}/analysis`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ question: item.question, topK: 5 })
  });

  if (!res.ok) {
    rows.push({ id: item.id, expected: item.expectedVerdict, actual: `HTTP_${res.status}`, ok: false });
    continue;
  }

  const data = await res.json();
  const ok = data.verdict === item.expectedVerdict;
  if (ok) correct++;

  rows.push({
    id: item.id,
    expected: item.expectedVerdict,
    actual: data.verdict,
    confidence: data.confidence,
    ok
  });
}

const accuracy = evalset.length === 0 ? 0 : (correct / evalset.length) * 100;

console.log('=== Sprint3 Evaluation Report ===');
console.log(`API_BASE: ${API_BASE}`);
console.log(`INQUIRY_ID: ${inquiryId}`);
console.log(`Total: ${evalset.length}`);
console.log(`Correct: ${correct}`);
console.log(`Accuracy: ${accuracy.toFixed(2)}%`);
console.log('');

for (const r of rows) {
  console.log(`${r.ok ? '✅' : '❌'} ${r.id} expected=${r.expected} actual=${r.actual}${r.confidence != null ? ` conf=${r.confidence}` : ''}`);
}
