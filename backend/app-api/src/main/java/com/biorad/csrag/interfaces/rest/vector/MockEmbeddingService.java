package com.biorad.csrag.interfaces.rest.vector;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class MockEmbeddingService implements EmbeddingService {

    private static final int DIMENSION = 3072;

    @Override
    public List<Double> embed(String text) {
        String safe = text == null ? "" : text;
        List<Double> vector = new ArrayList<>(DIMENSION);
        int base = Math.abs(safe.hashCode());

        for (int i = 0; i < DIMENSION; i++) {
            double value = ((base + (i * 31L)) % 1000) / 1000.0;
            vector.add(value);
        }
        return vector;
    }
}
