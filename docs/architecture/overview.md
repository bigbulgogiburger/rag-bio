# Architecture Overview

## Contexts

- Ingestion: parsing/OCR/chunking/index input
- Inquiry: receives and tracks customer questions
- Knowledge Retrieval: retrieve/rerank evidence chunks
- Verification: classify correct/incorrect/conditional with risk flags
- Response Composition: compose answer/email/message drafts
- Communication: approve/send outbound communication
- Audit: store traceable execution artifacts

## High-Level Flow

1. Upload question + documents
2. Parse/OCR/chunk/embed/index
3. Run Retriever -> Verifier -> Composer
4. Human review and approval
5. Send communication and store audit trail
