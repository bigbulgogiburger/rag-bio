package com.biorad.csrag.interfaces.rest.vector;

import com.biorad.csrag.interfaces.rest.search.SearchFilter;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.locks.ReentrantLock;

@Component
@Primary
@ConditionalOnProperty(prefix = "vector", name = "provider", havingValue = "qdrant")
public class QdrantVectorStore implements VectorStore {

    private static final Logger log = LoggerFactory.getLogger(QdrantVectorStore.class);

    private static final int UPSERT_MAX_RETRIES = 3;
    private static final long UPSERT_INITIAL_DELAY_MS = 1000L;

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final String collection;
    private final VectorStoreCircuitBreaker circuitBreaker;
    private volatile boolean collectionReady = false;
    private final ReentrantLock collectionLock = new ReentrantLock();

    public QdrantVectorStore(
            @Value("${vector.qdrant.url}") String qdrantUrl,
            @Value("${vector.qdrant.api-key:}") String apiKey,
            @Value("${vector.qdrant.collection:csrag_chunks}") String collection,
            ObjectMapper objectMapper,
            VectorStoreCircuitBreaker circuitBreaker
    ) {
        RestClient.Builder builder = RestClient.builder()
                .baseUrl(qdrantUrl)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);

        if (apiKey != null && !apiKey.isBlank()) {
            builder.defaultHeader("api-key", apiKey);
        }

        this.restClient = builder.build();
        this.objectMapper = objectMapper;
        this.collection = collection;
        this.circuitBreaker = circuitBreaker;
    }

    @Override
    public void upsert(UUID chunkId, UUID documentId, List<Double> vector, String content) {
        upsert(chunkId, documentId, vector, content, "INQUIRY");
    }

    @Override
    public void upsert(UUID chunkId, UUID documentId, List<Double> vector, String content, String sourceType) {
        upsert(chunkId, documentId, vector, content, sourceType, null);
    }

    @Override
    public void upsert(UUID chunkId, UUID documentId, List<Double> vector, String content, String sourceType, String productFamily) {
        ensureCollection(vector.size());

        Map<String, Object> payload = new HashMap<>();
        payload.put("chunkId", chunkId.toString());
        payload.put("documentId", documentId.toString());
        payload.put("content", content == null ? "" : content);
        payload.put("sourceType", sourceType == null ? "INQUIRY" : sourceType);
        payload.put("productFamily", productFamily == null ? "" : productFamily);

        Map<String, Object> point = new HashMap<>();
        point.put("id", chunkId.toString());
        point.put("vector", vector);
        point.put("payload", payload);

        Map<String, Object> body = Map.of("points", List.of(point));

        upsertWithRetry(body, chunkId, documentId, sourceType, productFamily, vector.size());
    }

    /**
     * Upsert에 지수 백오프 재시도를 적용한다.
     * 연결/타임아웃 오류만 재시도하고, 4xx 클라이언트 오류는 즉시 전파한다.
     */
    private void upsertWithRetry(Map<String, Object> body, UUID chunkId, UUID documentId,
                                  String sourceType, String productFamily, int dim) {
        long delay = UPSERT_INITIAL_DELAY_MS;
        for (int attempt = 1; attempt <= UPSERT_MAX_RETRIES; attempt++) {
            try {
                restClient.put()
                        .uri("/collections/{collection}/points?wait=true", collection)
                        .body(body)
                        .retrieve()
                        .toBodilessEntity();
                log.info("qdrant.upsert.success chunkId={} documentId={} sourceType={} productFamily={} dim={} attempt={}",
                        chunkId, documentId, sourceType, productFamily, dim, attempt);
                return;
            } catch (Exception ex) {
                if (isClientError(ex)) {
                    log.error("qdrant.upsert.client_error chunkId={} reason={}", chunkId, ex.getMessage());
                    throw new RuntimeException("Qdrant upsert client error for chunkId=" + chunkId, ex);
                }
                if (attempt == UPSERT_MAX_RETRIES) {
                    log.error("qdrant.upsert.failed chunkId={} after {} retries: {}", chunkId, UPSERT_MAX_RETRIES, ex.getMessage());
                    throw new RuntimeException("Qdrant upsert failed after " + UPSERT_MAX_RETRIES + " retries for chunkId=" + chunkId, ex);
                }
                log.warn("qdrant.upsert.retry chunkId={} attempt={}/{} delay={}ms reason={}",
                        chunkId, attempt, UPSERT_MAX_RETRIES, delay, ex.getMessage());
                try {
                    Thread.sleep(delay);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("Qdrant upsert interrupted", ie);
                }
                delay *= 2;
            }
        }
    }

    /**
     * HTTP 4xx 클라이언트 오류 여부를 판별한다.
     * 4xx 오류는 재시도 대상이 아니다.
     */
    private boolean isClientError(Exception ex) {
        String msg = ex.getMessage();
        if (msg == null) return false;
        // Spring RestClient의 HttpClientErrorException은 4xx 상태 코드를 포함한다
        return msg.contains("4") && (msg.contains("400") || msg.contains("401")
                || msg.contains("403") || msg.contains("404") || msg.contains("409")
                || msg.contains("422"));
    }

    @Override
    public List<VectorSearchResult> search(List<Double> queryVector, int topK) {
        return search(queryVector, topK, null);
    }

    @Override
    public List<VectorSearchResult> search(List<Double> queryVector, int topK, SearchFilter filter) {
        return circuitBreaker.execute(
                () -> doSearch(queryVector, topK, filter),
                Collections::emptyList
        );
    }

    /**
     * Qdrant HTTP 검색 — circuit breaker에 의해 래핑됨.
     */
    private List<VectorSearchResult> doSearch(List<Double> queryVector, int topK, SearchFilter filter) {
        ensureCollection(queryVector.size());

        Map<String, Object> body = new HashMap<>();
        body.put("vector", queryVector);
        body.put("limit", Math.max(1, topK));
        body.put("with_payload", true);

        if (filter != null && !filter.isEmpty()) {
            List<Map<String, Object>> mustClauses = new ArrayList<>();
            List<Map<String, Object>> shouldClauses = new ArrayList<>();

            // inquiryId 스코핑: documentIds OR sourceTypes (OR 로직)
            if (filter.hasDocumentFilter() && filter.hasSourceTypeFilter() && filter.inquiryId() != null) {
                List<String> docIdStrings = filter.documentIds().stream()
                        .map(UUID::toString)
                        .toList();
                shouldClauses.add(Map.of(
                        "key", "documentId",
                        "match", Map.of("any", docIdStrings)
                ));
                List<String> sourceTypeList = new ArrayList<>(filter.sourceTypes());
                shouldClauses.add(Map.of(
                        "key", "sourceType",
                        "match", Map.of("any", sourceTypeList)
                ));
            } else {
                if (filter.hasDocumentFilter()) {
                    List<String> docIdStrings = filter.documentIds().stream()
                            .map(UUID::toString)
                            .toList();
                    mustClauses.add(Map.of(
                            "key", "documentId",
                            "match", Map.of("any", docIdStrings)
                    ));
                }

                if (filter.hasSourceTypeFilter()) {
                    List<String> sourceTypeList = new ArrayList<>(filter.sourceTypes());
                    mustClauses.add(Map.of(
                            "key", "sourceType",
                            "match", Map.of("any", sourceTypeList)
                    ));
                }
            }

            if (filter.hasProductFilter()) {
                List<String> productFamilyList = new ArrayList<>(filter.productFamilies());
                mustClauses.add(Map.of(
                        "key", "productFamily",
                        "match", Map.of("any", productFamilyList)
                ));
            }

            Map<String, Object> filterMap = new HashMap<>();
            if (!mustClauses.isEmpty()) {
                filterMap.put("must", mustClauses);
            }
            if (!shouldClauses.isEmpty()) {
                filterMap.put("should", shouldClauses);
            }
            if (!filterMap.isEmpty()) {
                body.put("filter", filterMap);
            }
        }

        String response = restClient.post()
                .uri("/collections/{collection}/points/search", collection)
                .body(body)
                .retrieve()
                .body(String.class);

        try {
            JsonNode root = objectMapper.readTree(response == null ? "{}" : response);
            JsonNode result = root.path("result");
            if (!result.isArray()) {
                return List.of();
            }

            List<VectorSearchResult> hits = new ArrayList<>();
            for (JsonNode node : result) {
                JsonNode payload = node.path("payload");

                String chunkIdRaw = payload.path("chunkId").asText(node.path("id").asText());
                String documentIdRaw = payload.path("documentId").asText();
                String content = payload.path("content").asText("");
                String sourceType = payload.path("sourceType").asText("INQUIRY");
                double score = node.path("score").asDouble(0d);

                if (chunkIdRaw == null || chunkIdRaw.isBlank() || documentIdRaw == null || documentIdRaw.isBlank()) {
                    continue;
                }

                hits.add(new VectorSearchResult(
                        UUID.fromString(chunkIdRaw),
                        UUID.fromString(documentIdRaw),
                        content,
                        score,
                        sourceType
                ));
            }
            return hits;
        } catch (Exception ex) {
            log.warn("qdrant.search.parse.failed: {}", ex.getMessage());
            return List.of();
        }
    }

    @Override
    public void deleteByDocumentId(UUID documentId) {
        Map<String, Object> filter = Map.of(
                "must", List.of(
                        Map.of(
                                "key", "documentId",
                                "match", Map.of("value", documentId.toString())
                        )
                )
        );

        Map<String, Object> body = Map.of("filter", filter);

        try {
            restClient.post()
                    .uri("/collections/{collection}/points/delete?wait=true", collection)
                    .body(body)
                    .retrieve()
                    .toBodilessEntity();
            log.info("qdrant.deleteByDocumentId.success documentId={}", documentId);
        } catch (Exception ex) {
            String msg = ex.getMessage() != null ? ex.getMessage() : "";
            if (msg.contains("doesn't exist") || msg.contains("Index required but not found")) {
                log.info("qdrant.deleteByDocumentId.skipped documentId={} reason=collection_or_index_not_ready", documentId);
            } else {
                log.error("qdrant.deleteByDocumentId.failed documentId={} reason={}", documentId, msg);
                throw new RuntimeException("Failed to delete vectors for documentId=" + documentId, ex);
            }
        }
    }

    private void ensureCollection(int vectorSize) {
        if (collectionReady) {
            return;
        }

        collectionLock.lock();
        try {
            if (collectionReady) {
                return;
            }

            // 먼저 컬렉션 존재 여부를 GET으로 확인
            boolean exists = false;
            try {
                restClient.get()
                        .uri("/collections/{collection}", collection)
                        .retrieve()
                        .toBodilessEntity();
                exists = true;
                log.info("qdrant.collection.exists name={}", collection);
            } catch (Exception ex) {
                log.info("qdrant.collection.not_found name={}, creating...", collection);
            }

            if (!exists) {
                Map<String, Object> vectors = Map.of(
                        "size", Math.max(1, vectorSize),
                        "distance", "Cosine"
                );
                Map<String, Object> body = Map.of("vectors", vectors);
                try {
                    restClient.put()
                            .uri("/collections/{collection}", collection)
                            .body(body)
                            .retrieve()
                            .toBodilessEntity();
                    log.info("qdrant.collection.created name={} dim={}", collection, vectorSize);
                } catch (Exception ex) {
                    log.error("qdrant.collection.create.failed name={} reason={}", collection, ex.getMessage());
                    return; // 컬렉션 생성 실패 시 collectionReady를 true로 설정하지 않음
                }
            }

            // payload 인덱스 생성 (필터 검색에 필요)
            for (String field : List.of("documentId", "sourceType", "productFamily")) {
                try {
                    restClient.put()
                            .uri("/collections/{collection}/index", collection)
                            .body(Map.of("field_name", field, "field_schema", "keyword"))
                            .retrieve()
                            .toBodilessEntity();
                    log.info("qdrant.index.ready field={}", field);
                } catch (Exception ex) {
                    log.info("qdrant.index.ensure skipped/exists field={} reason={}", field, ex.getMessage());
                }
            }

            collectionReady = true;
        } finally {
            collectionLock.unlock();
        }
    }
}
