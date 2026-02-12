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

    public int chunkAndStore(UUID documentId, String text) {
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
