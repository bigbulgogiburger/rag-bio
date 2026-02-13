#!/usr/bin/env node
import fs from 'node:fs/promises';
import path from 'node:path';

const API_BASE = process.env.API_BASE_URL ?? 'http://localhost:8080';
const evalPath = process.env.EVALSET_PATH ?? 'backend/app-api/src/main/resources/evaluation/sprint3_evalset_v1.json';
const outDir = process.env.REPORT_DIR ?? 'docs/workflow/reports';
const tone = process.env.ANSWER_TONE ?? 'professional';
const channel = process.env.ANSWER_CHANNEL ?? 'email';
const sampleSize = Number(process.env.SAMPLE_SIZE ?? 5);

const evalset = JSON.parse(await fs.readFile(evalPath, 'utf-8'));
const cases = evalset.slice(0, Math.max(1, sampleSize));

const rows = [];
let total = 0;
let approveForbiddenPass = 0;
let sendBeforeApproveConflictPass = 0;
let sendSuccess = 0;
let duplicateBlocked = 0;

for (const item of cases) {
  total += 1;

  const inquiryId = await createInquiry(`s5-send-eval: ${item.id}`);
  const draft = await draftAnswer(inquiryId, item.question);

  const approveNoRole = await postJson(
    `/api/v1/inquiries/${inquiryId}/answers/${draft.answerId}/approve`,
    { actor: 'approver-1', comment: 'approve for eval' },
    {}
  );

  const sendBeforeApprove = await postJson(
    `/api/v1/inquiries/${inquiryId}/answers/${draft.answerId}/send`,
    { actor: 'sender-1', channel, sendRequestId: `pre-${item.id}` },
    { 'X-Role': 'SENDER' }
  );

  const approve = await postJson(
    `/api/v1/inquiries/${inquiryId}/answers/${draft.answerId}/approve`,
    { actor: 'approver-1', comment: 'approve for eval' },
    { 'X-Role': 'APPROVER' }
  );

  const requestId = `send-${item.id}`;
  const send1 = await postJson(
    `/api/v1/inquiries/${inquiryId}/answers/${draft.answerId}/send`,
    { actor: 'sender-1', channel, sendRequestId: requestId },
    { 'X-Role': 'SENDER' }
  );

  const send2 = await postJson(
    `/api/v1/inquiries/${inquiryId}/answers/${draft.answerId}/send`,
    { actor: 'sender-1', channel, sendRequestId: requestId },
    { 'X-Role': 'SENDER' }
  );

  const approveForbiddenOk = approveNoRole.status === 403;
  const sendBeforeApproveConflictOk = sendBeforeApprove.status === 409;
  const sendOk = send1.ok && send1.data?.status === 'SENT';
  const duplicateOk = sendOk && send2.ok && send2.data?.sendMessageId === send1.data?.sendMessageId;

  if (approveForbiddenOk) approveForbiddenPass += 1;
  if (sendBeforeApproveConflictOk) sendBeforeApproveConflictPass += 1;
  if (sendOk) sendSuccess += 1;
  if (duplicateOk) duplicateBlocked += 1;

  rows.push({
    id: item.id,
    inquiryId,
    answerId: draft.answerId,
    approveForbiddenOk,
    sendBeforeApproveConflictOk,
    approved: approve.ok,
    sent: sendOk,
    duplicateBlocked: duplicateOk,
    sendMessageId: send1.data?.sendMessageId ?? null
  });
}

const pct = (n, d) => (d ? ((n / d) * 100).toFixed(2) : '0.00');
const ts = new Date().toISOString().replace(/[:.]/g, '-');
await fs.mkdir(outDir, { recursive: true });
const reportPath = path.join(outDir, `sprint5_send_eval_report_${ts}.md`);

const lines = [];
lines.push('# Sprint5 Send Workflow Evaluation Report');
lines.push('');
lines.push(`- API_BASE: ${API_BASE}`);
lines.push(`- Evalset: ${evalPath}`);
lines.push(`- Tone/Channel: ${tone}/${channel}`);
lines.push(`- Sample Size: ${total}`);
lines.push('');
lines.push('## Summary Metrics');
lines.push(`- RBAC Block Rate (approve without role=403): ${approveForbiddenPass}/${total} (${pct(approveForbiddenPass, total)}%)`);
lines.push(`- Pre-Approval Send Block Rate (409): ${sendBeforeApproveConflictPass}/${total} (${pct(sendBeforeApproveConflictPass, total)}%)`);
lines.push(`- Send Ready Rate (approved+sent success): ${sendSuccess}/${total} (${pct(sendSuccess, total)}%)`);
lines.push(`- Duplicate Block Rate (same sendRequestId): ${duplicateBlocked}/${total} (${pct(duplicateBlocked, total)}%)`);
lines.push('');
lines.push('## Case Results');
for (const r of rows) {
  lines.push(`- ${r.id}: approve403=${r.approveForbiddenOk}, preSend409=${r.sendBeforeApproveConflictOk}, approved=${r.approved}, sent=${r.sent}, duplicateBlocked=${r.duplicateBlocked}, messageId=${r.sendMessageId}`);
}

await fs.writeFile(reportPath, lines.join('\n'), 'utf-8');

console.log('=== Sprint5 Send Workflow Evaluation ===');
console.log(`RBAC Block Rate: ${approveForbiddenPass}/${total} (${pct(approveForbiddenPass, total)}%)`);
console.log(`Pre-Approval Send Block Rate: ${sendBeforeApproveConflictPass}/${total} (${pct(sendBeforeApproveConflictPass, total)}%)`);
console.log(`Send Ready Rate: ${sendSuccess}/${total} (${pct(sendSuccess, total)}%)`);
console.log(`Duplicate Block Rate: ${duplicateBlocked}/${total} (${pct(duplicateBlocked, total)}%)`);
console.log(`Report saved: ${reportPath}`);

async function createInquiry(question) {
  const res = await postJson('/api/v1/inquiries', { question, customerChannel: channel });
  if (!res.ok || !res.data?.inquiryId) {
    throw new Error(`Failed to create inquiry: HTTP_${res.status}`);
  }
  return res.data.inquiryId;
}

async function draftAnswer(inquiryId, question) {
  const res = await postJson(`/api/v1/inquiries/${inquiryId}/answers/draft`, { question, tone, channel });
  if (!res.ok || !res.data?.answerId) {
    throw new Error(`Failed to draft answer: HTTP_${res.status}`);
  }
  return res.data;
}

async function postJson(pathname, body, headers = {}) {
  const res = await fetch(`${API_BASE}${pathname}`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', ...headers },
    body: JSON.stringify(body)
  });

  let data = null;
  try {
    data = await res.json();
  } catch {
    data = null;
  }
  return { ok: res.ok, status: res.status, data };
}
