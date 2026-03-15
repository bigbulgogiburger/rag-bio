package com.biorad.csrag.infrastructure.rag.cache;

import com.biorad.csrag.infrastructure.persistence.cache.SemanticCacheEntity;
import com.biorad.csrag.infrastructure.persistence.cache.SemanticCacheRepository;
import com.biorad.csrag.interfaces.rest.vector.EmbeddingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;

/**
 * Semantic Cache Service.
 * Caches RAG pipeline answers keyed by embedding hash of the query text.
 * Uses quantized embedding hash for fast exact-match lookup (proxy for high similarity threshold).
 */
@Service
public class SemanticCacheService {

    private static final Logger log = LoggerFactory.getLogger(SemanticCacheService.class);

    @Value("${rag.cache.enabled:true}")
    private boolean cacheEnabled;

    @Value("${rag.cache.ttl-hours:24}")
    private int ttlHours;

    @Value("${rag.cache.similarity-threshold:0.95}")
    private double similarityThreshold;

    private final SemanticCacheRepository repository;
    private final EmbeddingService embeddingService;

    public SemanticCacheService(SemanticCacheRepository repository, EmbeddingService embeddingService) {
        this.repository = repository;
        this.embeddingService = embeddingService;
    }

    /**
     * Try to find a cached answer for the given query.
     * Uses embedding hash for exact match (fast), not cosine similarity (expensive).
     * The high similarity threshold (0.95) means we use hash-based lookup as proxy.
     */
    @Transactional
    public Optional<CachedAnswer> get(String queryText) {
        if (!cacheEnabled) return Optional.empty();

        try {
            List<Double> embedding = embeddingService.embed(queryText);
            String hash = computeEmbeddingHash(embedding);

            Optional<SemanticCacheEntity> cached = repository.findByQueryEmbeddingHashAndInvalidatedFalse(hash);
            if (cached.isPresent()) {
                SemanticCacheEntity entity = cached.get();
                // Check TTL
                if (entity.getExpiresAt() != null && Instant.now().isAfter(entity.getExpiresAt())) {
                    return Optional.empty();
                }
                repository.incrementHitCount(entity.getId());
                log.info("Semantic cache HIT for query: '{}' (hitCount: {})",
                        queryText.substring(0, Math.min(50, queryText.length())), entity.getHitCount() + 1);
                return Optional.of(new CachedAnswer(entity.getAnswerText(), entity.getAnswerMetadata()));
            }
        } catch (Exception e) {
            log.warn("Semantic cache lookup failed: {}", e.getMessage());
        }

        return Optional.empty();
    }

    /**
     * Store an answer in the cache.
     */
    @Transactional
    public void put(String queryText, String answerText, String metadata) {
        if (!cacheEnabled) return;

        try {
            List<Double> embedding = embeddingService.embed(queryText);
            String hash = computeEmbeddingHash(embedding);

            SemanticCacheEntity entity = new SemanticCacheEntity();
            entity.setQueryText(queryText);
            entity.setQueryEmbeddingHash(hash);
            entity.setAnswerText(answerText);
            entity.setAnswerMetadata(metadata);
            entity.setCreatedAt(Instant.now());
            entity.setExpiresAt(Instant.now().plus(ttlHours, ChronoUnit.HOURS));

            repository.save(entity);
            log.info("Semantic cache stored for query: '{}'",
                    queryText.substring(0, Math.min(50, queryText.length())));
        } catch (Exception e) {
            log.warn("Semantic cache store failed: {}", e.getMessage());
        }
    }

    /**
     * Invalidate cache entries related to a specific inquiry/document.
     */
    @Transactional
    public void invalidateByInquiryId(Long inquiryId) {
        repository.invalidateByInquiryId(inquiryId);
    }

    /**
     * Cleanup expired entries. Runs every hour via @Scheduled.
     */
    @Scheduled(fixedRate = 3600000) // every hour
    @Transactional
    public void cleanupExpired() {
        repository.deleteExpired(Instant.now());
    }

    /**
     * Compute SHA-256 hash of embedding vector (quantized to 2 decimal places to reduce sensitivity).
     */
    String computeEmbeddingHash(List<Double> embedding) {
        StringBuilder sb = new StringBuilder();
        for (Double v : embedding) {
            sb.append(Math.round(v * 100));
            sb.append(",");
        }
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(sb.toString().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is always available in JVM
            throw new RuntimeException("SHA-256 not available", e);
        }
    }

    public record CachedAnswer(String answerText, String metadata) {}
}
