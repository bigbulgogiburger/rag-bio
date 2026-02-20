package com.biorad.csrag.interfaces.rest.search;

import java.util.UUID;

public record HybridSearchResult(
        UUID chunkId,
        UUID documentId,
        String content,
        double vectorScore,
        double keywordScore,
        double fusedScore,
        String sourceType,
        String matchSource
) {}
