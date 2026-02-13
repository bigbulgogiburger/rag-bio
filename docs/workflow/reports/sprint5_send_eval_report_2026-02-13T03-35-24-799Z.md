# Sprint5 Send Workflow Evaluation Report

- API_BASE: http://localhost:18080
- Evalset: backend/app-api/src/main/resources/evaluation/sprint3_evalset_v1.json
- Tone/Channel: professional/email
- Sample Size: 5

## Summary Metrics
- RBAC Block Rate (approve without role=403): 5/5 (100.00%)
- Pre-Approval Send Block Rate (409): 5/5 (100.00%)
- Send Ready Rate (approved+sent success): 5/5 (100.00%)
- Duplicate Block Rate (same sendRequestId): 5/5 (100.00%)

## Case Results
- EVAL-001: approve403=true, preSend409=true, approved=true, sent=true, duplicateBlocked=true, messageId=email-8510aad0-12b7-4f77-951f-c13f713b3df6
- EVAL-002: approve403=true, preSend409=true, approved=true, sent=true, duplicateBlocked=true, messageId=email-383af9e4-a9e4-4bd3-94ee-5b55b4c93692
- EVAL-003: approve403=true, preSend409=true, approved=true, sent=true, duplicateBlocked=true, messageId=email-92208e12-a76f-4881-8d5e-af7c941ee7e5
- EVAL-004: approve403=true, preSend409=true, approved=true, sent=true, duplicateBlocked=true, messageId=email-2196e6dc-1447-4b23-8281-567ae6fc4e8f
- EVAL-005: approve403=true, preSend409=true, approved=true, sent=true, duplicateBlocked=true, messageId=email-107d6c04-2ed9-4bba-b6b2-0fc6c764dbee