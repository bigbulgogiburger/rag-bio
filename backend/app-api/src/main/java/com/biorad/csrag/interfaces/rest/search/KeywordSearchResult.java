package com.biorad.csrag.interfaces.rest.search;

import java.util.UUID;

public record KeywordSearchResult(
        UUID chunkId,
        UUID documentId,
        String content,
        double score,
        String sourceType
) {}
