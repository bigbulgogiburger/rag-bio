#!/usr/bin/env node
import fs from 'node:fs/promises';
import path from 'node:path';

const API_BASE = process.env.API_BASE_URL ?? 'http://localhost:8080';
const inquiryId = process.env.INQUIRY_ID;
const evalPath = process.env.EVALSET_PATH ?? 'backend/app-api/src/main/resources/evaluation/sprint3_evalset_v1.json';
const outDir = process.env.REPORT_DIR ?? 'docs/workflow/reports';

if (!inquiryId) {
  console.error('INQUIRY_ID 환경변수가 필요합니다.');
  process.exit(1);
}

const raw = await fs.readFile(evalPath, 'utf-8');
const evalset = JSON.parse(raw);

const labels = ['SUPPORTED', 'REFUTED', 'CONDITIONAL'];
const matrix = Object.fromEntries(labels.map(a => [a, Object.fromEntries(labels.map(p => [p, 0]))]));

let correct = 0;
const rows = [];

for (const item of evalset) {
  const res = await fetch(`${API_BASE}/api/v1/inquiries/${inquiryId}/analysis`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ question: item.question, topK: 5 })
  });

  if (!res.ok) {
    rows.push({ id: item.id, expected: item.expectedVerdict, actual: `HTTP_${res.status}`, ok: false, domain: item.domain, difficulty: item.difficulty });
    continue;
  }

  const data = await res.json();
  const actual = data.verdict;
  const expected = item.expectedVerdict;
  const ok = actual === expected;
  if (ok) correct++;

  if (labels.includes(expected) && labels.includes(actual)) {
    matrix[expected][actual] += 1;
  }

  rows.push({
    id: item.id,
    expected,
    actual,
    confidence: data.confidence,
    ok,
    domain: item.domain,
    difficulty: item.difficulty,
    riskFlags: data.riskFlags ?? []
  });
}

const accuracy = evalset.length === 0 ? 0 : (correct / evalset.length) * 100;

const byDomain = {};
for (const r of rows) {
  if (!byDomain[r.domain]) byDomain[r.domain] = { total: 0, correct: 0 };
  byDomain[r.domain].total += 1;
  if (r.ok) byDomain[r.domain].correct += 1;
}

const ts = new Date().toISOString().replace(/[:.]/g, '-');
await fs.mkdir(outDir, { recursive: true });
const reportPath = path.join(outDir, `sprint3_eval_report_${ts}.md`);

const lines = [];
lines.push('# Sprint3 Evaluation Report');
lines.push('');
lines.push(`- API_BASE: ${API_BASE}`);
lines.push(`- INQUIRY_ID: ${inquiryId}`);
lines.push(`- Evalset: ${evalPath}`);
lines.push(`- Total: ${evalset.length}`);
lines.push(`- Correct: ${correct}`);
lines.push(`- Accuracy: ${accuracy.toFixed(2)}%`);
lines.push('');
lines.push('## Domain Accuracy');
for (const [domain, stat] of Object.entries(byDomain)) {
  const acc = stat.total ? (stat.correct / stat.total) * 100 : 0;
  lines.push(`- ${domain}: ${stat.correct}/${stat.total} (${acc.toFixed(2)}%)`);
}
lines.push('');
lines.push('## Confusion Matrix (expected -> predicted)');
lines.push('');
lines.push(`| expected\\pred | SUPPORTED | REFUTED | CONDITIONAL |`);
lines.push(`|---|---:|---:|---:|`);
for (const exp of labels) {
  lines.push(`| ${exp} | ${matrix[exp].SUPPORTED} | ${matrix[exp].REFUTED} | ${matrix[exp].CONDITIONAL} |`);
}
lines.push('');
lines.push('## Case Results');
for (const r of rows) {
  lines.push(`- ${r.ok ? '✅' : '❌'} ${r.id} [${r.domain}/${r.difficulty}] expected=${r.expected}, actual=${r.actual}${r.confidence != null ? `, conf=${r.confidence}` : ''}${r.riskFlags?.length ? `, risk=${r.riskFlags.join(',')}` : ''}`);
}

await fs.writeFile(reportPath, lines.join('\n'), 'utf-8');

console.log('=== Sprint3 Evaluation Report ===');
console.log(`Accuracy: ${accuracy.toFixed(2)}% (${correct}/${evalset.length})`);
console.log(`Report saved: ${reportPath}`);
