package com.biorad.csrag.interfaces.rest.vector;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
@Primary
@ConditionalOnProperty(prefix = "vector", name = "provider", havingValue = "mock", matchIfMissing = true)
public class MockVectorStore implements VectorStore {

    private static final Logger log = LoggerFactory.getLogger(MockVectorStore.class);

    private final Map<UUID, VectorRecord> records = new ConcurrentHashMap<>();

    @Override
    public void upsert(UUID chunkId, UUID documentId, List<Double> vector, String content) {
        upsert(chunkId, documentId, vector, content, "INQUIRY");
    }

    @Override
    public void upsert(UUID chunkId, UUID documentId, List<Double> vector, String content, String sourceType) {
        records.put(chunkId, new VectorRecord(chunkId, documentId, vector, content, sourceType));
        log.info("vector.upsert.success chunkId={} documentId={} sourceType={} dim={}", chunkId, documentId, sourceType, vector.size());
    }

    @Override
    public List<VectorSearchResult> search(List<Double> queryVector, int topK) {
        return records.values().stream()
                .map(record -> new VectorSearchResult(
                        record.chunkId(),
                        record.documentId(),
                        record.content(),
                        cosineSimilarity(queryVector, record.vector()),
                        record.sourceType()
                ))
                .sorted(Comparator.comparingDouble(VectorSearchResult::score).reversed())
                .limit(topK)
                .toList();
    }

    @Override
    public void deleteByDocumentId(UUID documentId) {
        List<UUID> toRemove = records.entrySet().stream()
                .filter(e -> e.getValue().documentId().equals(documentId))
                .map(Map.Entry::getKey)
                .toList();
        toRemove.forEach(records::remove);
        log.info("vector.deleteByDocumentId.success documentId={} removed={}", documentId, toRemove.size());
    }

    public int size() {
        return records.size();
    }

    private double cosineSimilarity(List<Double> a, List<Double> b) {
        int n = Math.min(a.size(), b.size());
        if (n == 0) return 0d;

        double dot = 0d;
        double normA = 0d;
        double normB = 0d;
        for (int i = 0; i < n; i++) {
            double av = a.get(i);
            double bv = b.get(i);
            dot += av * bv;
            normA += av * av;
            normB += bv * bv;
        }
        if (normA == 0 || normB == 0) return 0d;
        return dot / (Math.sqrt(normA) * Math.sqrt(normB));
    }

    private record VectorRecord(UUID chunkId, UUID documentId, List<Double> vector, String content, String sourceType) {
    }
}
