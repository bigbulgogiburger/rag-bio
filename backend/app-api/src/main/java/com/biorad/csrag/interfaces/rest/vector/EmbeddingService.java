package com.biorad.csrag.interfaces.rest.vector;

import java.util.List;

public interface EmbeddingService {

    List<Double> embed(String text);
}
