package com.biorad.csrag.interfaces.rest.search;

import java.util.List;

public interface KeywordSearchService {

    List<KeywordSearchResult> search(String query, int topK);
}
