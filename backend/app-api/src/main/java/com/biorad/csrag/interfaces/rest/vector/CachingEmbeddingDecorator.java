package com.biorad.csrag.interfaces.rest.vector;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * EmbeddingService 캐싱 데코레이터.
 * LRU 캐시 + TTL로 동일 쿼리의 중복 임베딩 API 호출을 방지.
 * embedQuery()만 캐싱 (검색 쿼리는 반복 가능성 높음).
 * embedDocument()는 캐싱하지 않음 (인덱싱은 1회성).
 */
@Service
@Primary
@ConditionalOnProperty(prefix = "openai", name = "enabled", havingValue = "true")
public class CachingEmbeddingDecorator implements EmbeddingService {

    private static final Logger log = LoggerFactory.getLogger(CachingEmbeddingDecorator.class);

    private final EmbeddingService delegate;
    private final Map<String, CacheEntry> cache;
    private final long ttlMs;
    private int hits = 0;
    private int misses = 0;

    public CachingEmbeddingDecorator(
            @Qualifier("openAiEmbeddingService") EmbeddingService delegate,
            @Value("${embedding.cache.max-size:500}") int maxSize,
            @Value("${embedding.cache.ttl-minutes:30}") int ttlMinutes
    ) {
        this.delegate = delegate;
        this.ttlMs = ttlMinutes * 60_000L;
        // Thread-safe LRU cache
        this.cache = java.util.Collections.synchronizedMap(
                new LinkedHashMap<>(maxSize + 1, 0.75f, true) {
                    @Override
                    protected boolean removeEldestEntry(Map.Entry<String, CacheEntry> eldest) {
                        return size() > maxSize;
                    }
                }
        );
        log.info("embedding.cache.initialized maxSize={} ttlMinutes={}", maxSize, ttlMinutes);
    }

    @Override
    public List<Double> embed(String text) {
        return delegate.embed(text);
    }

    @Override
    public List<Double> embedDocument(String text) {
        // Document embedding은 캐싱하지 않음 (1회성 인덱싱)
        return delegate.embedDocument(text);
    }

    @Override
    public List<Double> embedQuery(String text) {
        String key = hashKey(text);
        CacheEntry cached = cache.get(key);

        if (cached != null && !cached.isExpired(ttlMs)) {
            hits++;
            if ((hits + misses) % 100 == 0) {
                log.info("embedding.cache.stats hits={} misses={} hitRate={}%",
                        hits, misses, String.format("%.1f", hits * 100.0 / (hits + misses)));
            }
            return cached.embedding;
        }

        misses++;
        List<Double> embedding = delegate.embedQuery(text);
        cache.put(key, new CacheEntry(embedding, System.currentTimeMillis()));
        return embedding;
    }

    @Override
    public List<List<Double>> embedBatch(List<String> texts) {
        return delegate.embedBatch(texts);
    }

    private static String hashKey(String text) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(text.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is always available
            return text;
        }
    }

    private record CacheEntry(List<Double> embedding, long createdAt) {
        boolean isExpired(long ttlMs) {
            return System.currentTimeMillis() - createdAt > ttlMs;
        }
    }
}
