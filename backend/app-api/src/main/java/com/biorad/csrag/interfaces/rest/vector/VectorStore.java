package com.biorad.csrag.interfaces.rest.vector;

import java.util.List;
import java.util.UUID;

public interface VectorStore {

    void upsert(UUID chunkId, UUID documentId, List<Double> vector, String content);

    List<VectorSearchResult> search(List<Double> queryVector, int topK);
}
