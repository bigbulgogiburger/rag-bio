package com.biorad.csrag.interfaces.rest.vector;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Comparator;
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

    @Override
    public List<VectorSearchResult> search(List<Double> queryVector, int topK) {
        return records.values().stream()
                .map(record -> new VectorSearchResult(
                        record.chunkId(),
                        record.documentId(),
                        record.content(),
                        cosineSimilarity(queryVector, record.vector())
                ))
                .sorted(Comparator.comparingDouble(VectorSearchResult::score).reversed())
                .limit(topK)
                .toList();
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

    private record VectorRecord(UUID chunkId, UUID documentId, List<Double> vector, String content) {
    }
}
