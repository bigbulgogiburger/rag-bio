package com.biorad.csrag.interfaces.rest.chunk;

import com.biorad.csrag.infrastructure.persistence.chunk.DocumentChunkJpaEntity;
import com.biorad.csrag.infrastructure.persistence.chunk.DocumentChunkJpaRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class ChunkingService {

    private static final int CHUNK_SIZE = 1000;
    private static final int OVERLAP = 150;

    private final DocumentChunkJpaRepository chunkRepository;

    public ChunkingService(DocumentChunkJpaRepository chunkRepository) {
        this.chunkRepository = chunkRepository;
    }

    /**
     * 기존 방식 (하위 호환) - INQUIRY 타입으로 청킹
     */
    public int chunkAndStore(UUID documentId, String text) {
        return chunkAndStore(documentId, text, "INQUIRY", documentId);
    }

    /**
     * source_type과 source_id를 지정하여 청킹
     *
     * @param documentId chunk와 연관된 문서 ID (vector store 키로 사용)
     * @param text       청킹할 텍스트
     * @param sourceType "INQUIRY" 또는 "KNOWLEDGE_BASE"
     * @param sourceId   원본 문서 ID (INQUIRY 타입이면 documents.id, KNOWLEDGE_BASE 타입이면 knowledge_documents.id)
     * @return 생성된 청크 수
     */
    public int chunkAndStore(UUID documentId, String text, String sourceType, UUID sourceId) {
        chunkRepository.deleteByDocumentId(documentId);

        List<DocumentChunkJpaEntity> chunks = new ArrayList<>();
        int index = 0;
        int start = 0;
        int length = text.length();

        while (start < length) {
            int end = Math.min(start + CHUNK_SIZE, length);
            String content = text.substring(start, end);
            chunks.add(new DocumentChunkJpaEntity(
                    UUID.randomUUID(),
                    documentId,
                    index,
                    start,
                    end,
                    content,
                    sourceType,
                    sourceId,
                    Instant.now()
            ));

            if (end >= length) {
                break;
            }
            start = Math.max(0, end - OVERLAP);
            index++;
        }

        chunkRepository.saveAll(chunks);
        return chunks.size();
    }
}
