package com.biorad.csrag.interfaces.rest.search;

import com.biorad.csrag.interfaces.rest.vector.EmbeddingService;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class MockHydeQueryTransformer implements HydeQueryTransformer {

    private final EmbeddingService embeddingService;

    public MockHydeQueryTransformer(EmbeddingService embeddingService) {
        this.embeddingService = embeddingService;
    }

    @Override
    public List<Double> transformAndEmbed(String question, String productContext) {
        return embeddingService.embedQuery(question);
    }

    @Override
    public boolean isEnabled() {
        return false;
    }
}
