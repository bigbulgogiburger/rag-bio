package com.biorad.csrag.interfaces.rest.vector;

import java.util.List;

public interface EmbeddingService {

    List<Double> embed(String text);

    /** 문서 인덱싱용 임베딩 (향후 비대칭 임베딩 지원) */
    default List<Double> embedDocument(String text) {
        return embed(text);
    }

    /** 검색 쿼리용 임베딩 (향후 비대칭 임베딩 지원) */
    default List<Double> embedQuery(String text) {
        return embed(text);
    }

    /** 배치 임베딩 (기본: 순차 호출) */
    default List<List<Double>> embedBatch(List<String> texts) {
        return texts.stream().map(this::embed).toList();
    }
}
