#!/usr/bin/env node
import fs from 'node:fs/promises';
import path from 'node:path';

const fixturePath = process.env.FIXTURE_PATH ?? 'backend/app-api/src/main/resources/evaluation/sprint4_format_breakdown_fixture_v1.json';
const outDir = process.env.REPORT_DIR ?? 'docs/workflow/reports';

const cases = JSON.parse(await fs.readFile(fixturePath, 'utf-8'));

function checkChannelFormat(draft, channel) {
  const text = String(draft ?? '');
  if (channel === 'email') {
    const hasGreeting = text.includes('안녕하세요');
    const hasClosing = text.includes('감사합니다');
    if (hasGreeting && hasClosing) return { ok: true, reason: 'ok' };
    if (!hasGreeting && !hasClosing) return { ok: false, reason: 'email: greeting+closing missing' };
    if (!hasGreeting) return { ok: false, reason: 'email: greeting missing' };
    return { ok: false, reason: 'email: closing missing' };
  }

  if (channel === 'messenger') {
    const hasSummaryTag = text.includes('[요약]');
    const maxLengthOk = text.length <= 260;
    if (hasSummaryTag && maxLengthOk) return { ok: true, reason: 'ok' };
    if (!hasSummaryTag && !maxLengthOk) return { ok: false, reason: 'messenger: summary tag missing + length overflow' };
    if (!hasSummaryTag) return { ok: false, reason: 'messenger: summary tag missing' };
    return { ok: false, reason: 'messenger: length overflow' };
  }

  return { ok: true, reason: 'channel not checked' };
}

let matched = 0;
const reasonCount = new Map();
const rows = [];

for (const c of cases) {
  const got = checkChannelFormat(c.draft, c.channel);
  if (got.reason === c.expectedReason) matched += 1;
  reasonCount.set(got.reason, (reasonCount.get(got.reason) ?? 0) + 1);
  rows.push({ id: c.id, channel: c.channel, expected: c.expectedReason, actual: got.reason, ok: got.reason === c.expectedReason });
}

await fs.mkdir(outDir, { recursive: true });
const ts = new Date().toISOString().replace(/[:.]/g, '-');
const reportPath = path.join(outDir, `sprint4_format_breakdown_report_${ts}.md`);

const lines = [];
lines.push('# Sprint4 Format Breakdown Validation Report');
lines.push('');
lines.push(`- Fixture: ${fixturePath}`);
lines.push(`- Total: ${cases.length}`);
lines.push(`- Matched: ${matched}/${cases.length}`);
lines.push('');
lines.push('## Breakdown');
for (const [reason, count] of [...reasonCount.entries()].sort((a, b) => b[1] - a[1])) {
  lines.push(`- ${reason}: ${count}`);
}
lines.push('');
lines.push('## Case Results');
for (const r of rows) {
  lines.push(`- ${r.ok ? '✅' : '❌'} ${r.id} [${r.channel}] expected=${r.expected} / actual=${r.actual}`);
}

await fs.writeFile(reportPath, lines.join('\n'), 'utf-8');
console.log(`Matched: ${matched}/${cases.length}`);
console.log(`Report saved: ${reportPath}`);
