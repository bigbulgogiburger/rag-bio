package com.biorad.csrag.interfaces.rest.vector;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class MockVectorStore implements VectorStore {

    private static final Logger log = LoggerFactory.getLogger(MockVectorStore.class);

    private final Map<UUID, VectorRecord> records = new ConcurrentHashMap<>();

    @Override
    public void upsert(UUID chunkId, UUID documentId, List<Double> vector, String content) {
        records.put(chunkId, new VectorRecord(chunkId, documentId, vector, content));
        log.info("vector.upsert.success chunkId={} documentId={} dim={}", chunkId, documentId, vector.size());
    }

    public int size() {
        return records.size();
    }

    private record VectorRecord(UUID chunkId, UUID documentId, List<Double> vector, String content) {
    }
}
