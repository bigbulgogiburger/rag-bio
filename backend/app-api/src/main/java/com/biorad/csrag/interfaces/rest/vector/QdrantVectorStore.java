package com.biorad.csrag.interfaces.rest.vector;

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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
@Primary
@ConditionalOnProperty(prefix = "vector", name = "provider", havingValue = "qdrant")
public class QdrantVectorStore implements VectorStore {

    private static final Logger log = LoggerFactory.getLogger(QdrantVectorStore.class);

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final String collection;
    private volatile boolean collectionReady = false;

    public QdrantVectorStore(
            @Value("${vector.qdrant.url}") String qdrantUrl,
            @Value("${vector.qdrant.api-key:}") String apiKey,
            @Value("${vector.qdrant.collection:csrag_chunks}") String collection,
            ObjectMapper objectMapper
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
    }

    @Override
    public void upsert(UUID chunkId, UUID documentId, List<Double> vector, String content) {
        upsert(chunkId, documentId, vector, content, "INQUIRY");
    }

    @Override
    public void upsert(UUID chunkId, UUID documentId, List<Double> vector, String content, String sourceType) {
        ensureCollection(vector.size());

        Map<String, Object> payload = new HashMap<>();
        payload.put("chunkId", chunkId.toString());
        payload.put("documentId", documentId.toString());
        payload.put("content", content == null ? "" : content);
        payload.put("sourceType", sourceType == null ? "INQUIRY" : sourceType);

        Map<String, Object> point = new HashMap<>();
        point.put("id", chunkId.toString());
        point.put("vector", vector);
        point.put("payload", payload);

        Map<String, Object> body = Map.of("points", List.of(point));

        restClient.put()
                .uri("/collections/{collection}/points?wait=true", collection)
                .body(body)
                .retrieve()
                .toBodilessEntity();

        log.info("qdrant.upsert.success chunkId={} documentId={} sourceType={} dim={}", chunkId, documentId, sourceType, vector.size());
    }

    @Override
    public List<VectorSearchResult> search(List<Double> queryVector, int topK) {
        ensureCollection(queryVector.size());

        Map<String, Object> body = Map.of(
                "vector", queryVector,
                "limit", Math.max(1, topK),
                "with_payload", true
        );

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
            log.warn("qdrant.deleteByDocumentId.failed documentId={} reason={}", documentId, ex.getMessage());
        }
    }

    private void ensureCollection(int vectorSize) {
        if (collectionReady) {
            return;
        }

        synchronized (this) {
            if (collectionReady) {
                return;
            }

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
                log.info("qdrant.collection.ready name={} dim={}", collection, vectorSize);
            } catch (Exception ex) {
                log.info("qdrant.collection.ensure skipped/exists name={} reason={}", collection, ex.getMessage());
            }
            collectionReady = true;
        }
    }
}
