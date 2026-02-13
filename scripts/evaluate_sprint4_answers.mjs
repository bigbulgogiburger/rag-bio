#!/usr/bin/env node
import fs from 'node:fs/promises';
import path from 'node:path';

const API_BASE = process.env.API_BASE_URL ?? 'http://localhost:8080';
const inquiryId = process.env.INQUIRY_ID;
const evalPath = process.env.EVALSET_PATH ?? 'backend/app-api/src/main/resources/evaluation/sprint3_evalset_v1.json';
const outDir = process.env.REPORT_DIR ?? 'docs/workflow/reports';
const tone = process.env.ANSWER_TONE ?? 'professional';
const channel = process.env.ANSWER_CHANNEL ?? 'email';

if (!inquiryId) {
  console.error('INQUIRY_ID 환경변수가 필요합니다.');
  process.exit(1);
}

const evalset = JSON.parse(await fs.readFile(evalPath, 'utf-8'));

let total = 0;
let verdictCorrect = 0;
let citationIncluded = 0;
let lowConfidenceCases = 0;
let lowConfidenceGuardrail = 0;
let riskFlagCases = 0;
let riskGuardrail = 0;
let channelFormatChecks = 0;
let channelFormatPassed = 0;
const channelFormatFailureReasons = new Map();

const rows = [];

for (const item of evalset) {
  total += 1;

  const res = await fetch(`${API_BASE}/api/v1/inquiries/${inquiryId}/answers/draft`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ question: item.question, tone, channel })
  });

  if (!res.ok) {
    rows.push({ id: item.id, ok: false, status: `HTTP_${res.status}` });
    continue;
  }

  const data = await res.json();
  const verdictOk = data.verdict === item.expectedVerdict;
  if (verdictOk) verdictCorrect += 1;

  const citations = Array.isArray(data.citations) ? data.citations : [];
  const hasCitation = citations.length > 0;
  if (hasCitation) citationIncluded += 1;

  const confidence = Number(data.confidence ?? 0);
  const draft = String(data.draft ?? '');
  const riskFlags = Array.isArray(data.riskFlags) ? data.riskFlags : [];

  const hasLowConfCase = confidence < 0.75;
  const hasLowConfNotice = draft.includes('추가 확인이 필요합니다');
  if (hasLowConfCase) {
    lowConfidenceCases += 1;
    if (hasLowConfNotice) lowConfidenceGuardrail += 1;
  }

  const hasRiskCase = riskFlags.length > 0;
  const hasRiskNotice = draft.includes('보수적 안내') || draft.includes('주의:');
  if (hasRiskCase) {
    riskFlagCases += 1;
    if (hasRiskNotice) riskGuardrail += 1;
  }

  const formatCheck = checkChannelFormat(draft, channel);
  channelFormatChecks += 1;
  if (formatCheck.ok) {
    channelFormatPassed += 1;
  } else {
    channelFormatFailureReasons.set(
      formatCheck.reason,
      (channelFormatFailureReasons.get(formatCheck.reason) ?? 0) + 1
    );
  }

  rows.push({
    id: item.id,
    expected: item.expectedVerdict,
    actual: data.verdict,
    verdictOk,
    confidence,
    citationCount: citations.length,
    riskFlags,
    lowConfidenceGuardrail: hasLowConfCase ? hasLowConfNotice : null,
    riskGuardrail: hasRiskCase ? hasRiskNotice : null,
    channelFormatOk: formatCheck.ok,
    channelFormatReason: formatCheck.reason,
    answerId: data.answerId,
    version: data.version,
    status: data.status
  });
}

function checkChannelFormat(draft, channel) {
  const text = String(draft ?? '');
  if (channel === 'email') {
    const hasGreeting = text.includes('안녕하세요');
    const hasClosing = text.includes('감사합니다');
    if (hasGreeting && hasClosing) {
      return { ok: true, reason: 'ok' };
    }
    if (!hasGreeting && !hasClosing) {
      return { ok: false, reason: 'email: greeting+closing missing' };
    }
    if (!hasGreeting) {
      return { ok: false, reason: 'email: greeting missing' };
    }
    return { ok: false, reason: 'email: closing missing' };
  }

  if (channel === 'messenger') {
    const hasSummaryTag = text.includes('[요약]');
    const maxLengthOk = text.length <= 260;
    if (hasSummaryTag && maxLengthOk) {
      return { ok: true, reason: 'ok' };
    }
    if (!hasSummaryTag && !maxLengthOk) {
      return { ok: false, reason: 'messenger: summary tag missing + length overflow' };
    }
    if (!hasSummaryTag) {
      return { ok: false, reason: 'messenger: summary tag missing' };
    }
    return { ok: false, reason: 'messenger: length overflow' };
  }

  return { ok: true, reason: 'channel not checked' };
}

const pct = (n, d) => (d ? ((n / d) * 100).toFixed(2) : '0.00');

const ts = new Date().toISOString().replace(/[:.]/g, '-');
await fs.mkdir(outDir, { recursive: true });
const reportPath = path.join(outDir, `sprint4_answer_eval_report_${ts}.md`);

const lines = [];
lines.push('# Sprint4 Answer Quality Evaluation Report');
lines.push('');
lines.push(`- API_BASE: ${API_BASE}`);
lines.push(`- INQUIRY_ID: ${inquiryId}`);
lines.push(`- Evalset: ${evalPath}`);
lines.push(`- Tone/Channel: ${tone}/${channel}`);
lines.push(`- Total: ${total}`);
lines.push('');
lines.push('## Summary Metrics');
lines.push(`- Verdict Accuracy: ${verdictCorrect}/${total} (${pct(verdictCorrect, total)}%)`);
lines.push(`- Citation Inclusion Rate: ${citationIncluded}/${total} (${pct(citationIncluded, total)}%)`);
lines.push(`- Low-Confidence Guardrail Coverage: ${lowConfidenceGuardrail}/${lowConfidenceCases} (${pct(lowConfidenceGuardrail, lowConfidenceCases)}%)`);
lines.push(`- Risk Guardrail Coverage: ${riskGuardrail}/${riskFlagCases} (${pct(riskGuardrail, riskFlagCases)}%)`);
lines.push(`- Channel Format Pass Rate: ${channelFormatPassed}/${channelFormatChecks} (${pct(channelFormatPassed, channelFormatChecks)}%)`);
lines.push('');
lines.push('## Channel Format Failure Breakdown');
if (channelFormatFailureReasons.size === 0) {
  lines.push('- none');
} else {
  for (const [reason, count] of [...channelFormatFailureReasons.entries()].sort((a, b) => b[1] - a[1])) {
    lines.push(`- ${reason}: ${count}`);
  }
}
lines.push('');
lines.push('## Case Results');
for (const r of rows) {
  if (!r.expected) {
    lines.push(`- ❌ ${r.id} ${r.status}`);
    continue;
  }
  lines.push(`- ${r.verdictOk ? '✅' : '❌'} ${r.id} expected=${r.expected}, actual=${r.actual}, conf=${r.confidence}, citations=${r.citationCount}, format=${r.channelFormatOk ? 'OK' : 'FAIL'}(${r.channelFormatReason}), status=${r.status}, v=${r.version}`);
}

await fs.writeFile(reportPath, lines.join('\n'), 'utf-8');

console.log('=== Sprint4 Answer Evaluation Report ===');
console.log(`Verdict Accuracy: ${verdictCorrect}/${total} (${pct(verdictCorrect, total)}%)`);
console.log(`Citation Inclusion Rate: ${citationIncluded}/${total} (${pct(citationIncluded, total)}%)`);
console.log(`Low-Confidence Guardrail Coverage: ${lowConfidenceGuardrail}/${lowConfidenceCases} (${pct(lowConfidenceGuardrail, lowConfidenceCases)}%)`);
console.log(`Risk Guardrail Coverage: ${riskGuardrail}/${riskFlagCases} (${pct(riskGuardrail, riskFlagCases)}%)`);
console.log(`Channel Format Pass Rate: ${channelFormatPassed}/${channelFormatChecks} (${pct(channelFormatPassed, channelFormatChecks)}%)`);
if (channelFormatFailureReasons.size === 0) {
  console.log('Channel Format Failure Breakdown: none');
} else {
  const summary = [...channelFormatFailureReasons.entries()]
    .sort((a, b) => b[1] - a[1])
    .map(([k, v]) => `${k}=${v}`)
    .join(', ');
  console.log(`Channel Format Failure Breakdown: ${summary}`);
}
console.log(`Report saved: ${reportPath}`);
