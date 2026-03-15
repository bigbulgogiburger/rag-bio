package com.biorad.csrag.infrastructure.rag.cache;

import com.biorad.csrag.infrastructure.persistence.cache.SemanticCacheEntity;
import com.biorad.csrag.infrastructure.persistence.cache.SemanticCacheRepository;
import com.biorad.csrag.interfaces.rest.vector.EmbeddingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SemanticCacheServiceTest {

    @Mock
    private SemanticCacheRepository repository;

    @Mock
    private EmbeddingService embeddingService;

    private SemanticCacheService service;

    private static final List<Double> EMBEDDING_A = List.of(0.1, 0.2, 0.3);
    private static final List<Double> EMBEDDING_B = List.of(0.9, 0.8, 0.7);

    @BeforeEach
    void setUp() {
        service = new SemanticCacheService(repository, embeddingService);
        ReflectionTestUtils.setField(service, "cacheEnabled", true);
        ReflectionTestUtils.setField(service, "ttlHours", 24);
        ReflectionTestUtils.setField(service, "similarityThreshold", 0.95);
    }

    @Test
    void get_returnsCachedAnswer_whenHashMatches() {
        // given
        when(embeddingService.embed("test question")).thenReturn(EMBEDDING_A);
        String hash = service.computeEmbeddingHash(EMBEDDING_A);

        SemanticCacheEntity cached = new SemanticCacheEntity();
        ReflectionTestUtils.setField(cached, "id", 1L);
        cached.setAnswerText("cached answer");
        cached.setAnswerMetadata("{\"verdict\":\"SUPPORTED\"}");
        cached.setHitCount(5);
        cached.setExpiresAt(Instant.now().plus(1, ChronoUnit.HOURS));

        when(repository.findByQueryEmbeddingHashAndInvalidatedFalse(hash))
                .thenReturn(Optional.of(cached));

        // when
        Optional<SemanticCacheService.CachedAnswer> result = service.get("test question");

        // then
        assertThat(result).isPresent();
        assertThat(result.get().answerText()).isEqualTo("cached answer");
        assertThat(result.get().metadata()).isEqualTo("{\"verdict\":\"SUPPORTED\"}");
        verify(repository).incrementHitCount(1L);
    }

    @Test
    void get_returnsEmpty_whenCacheDisabled() {
        // given
        ReflectionTestUtils.setField(service, "cacheEnabled", false);

        // when
        Optional<SemanticCacheService.CachedAnswer> result = service.get("test question");

        // then
        assertThat(result).isEmpty();
        verify(repository, never()).findByQueryEmbeddingHashAndInvalidatedFalse(anyString());
    }

    @Test
    void get_returnsEmpty_whenNoMatch() {
        // given
        when(embeddingService.embed("unknown query")).thenReturn(EMBEDDING_B);
        String hash = service.computeEmbeddingHash(EMBEDDING_B);

        when(repository.findByQueryEmbeddingHashAndInvalidatedFalse(hash))
                .thenReturn(Optional.empty());

        // when
        Optional<SemanticCacheService.CachedAnswer> result = service.get("unknown query");

        // then
        assertThat(result).isEmpty();
    }

    @Test
    void get_returnsEmpty_whenExpired() {
        // given
        when(embeddingService.embed("expired query")).thenReturn(EMBEDDING_A);
        String hash = service.computeEmbeddingHash(EMBEDDING_A);

        SemanticCacheEntity cached = new SemanticCacheEntity();
        ReflectionTestUtils.setField(cached, "id", 2L);
        cached.setAnswerText("expired answer");
        cached.setExpiresAt(Instant.now().minus(1, ChronoUnit.HOURS)); // expired

        when(repository.findByQueryEmbeddingHashAndInvalidatedFalse(hash))
                .thenReturn(Optional.of(cached));

        // when
        Optional<SemanticCacheService.CachedAnswer> result = service.get("expired query");

        // then
        assertThat(result).isEmpty();
        verify(repository, never()).incrementHitCount(anyLong());
    }

    @Test
    void put_storesEntry() {
        // given
        when(embeddingService.embed("new question")).thenReturn(EMBEDDING_A);

        // when
        service.put("new question", "new answer", "{\"confidence\":0.9}");

        // then
        ArgumentCaptor<SemanticCacheEntity> captor = ArgumentCaptor.forClass(SemanticCacheEntity.class);
        verify(repository).save(captor.capture());

        SemanticCacheEntity saved = captor.getValue();
        assertThat(saved.getQueryText()).isEqualTo("new question");
        assertThat(saved.getAnswerText()).isEqualTo("new answer");
        assertThat(saved.getAnswerMetadata()).isEqualTo("{\"confidence\":0.9}");
        assertThat(saved.getExpiresAt()).isAfter(Instant.now());
        assertThat(saved.getQueryEmbeddingHash()).isNotBlank();
    }

    @Test
    void put_doesNothing_whenCacheDisabled() {
        // given
        ReflectionTestUtils.setField(service, "cacheEnabled", false);

        // when
        service.put("question", "answer", null);

        // then
        verify(repository, never()).save(any());
    }

    @Test
    void invalidateByInquiryId_delegatesToRepository() {
        // when
        service.invalidateByInquiryId(42L);

        // then
        verify(repository).invalidateByInquiryId(42L);
    }

    @Test
    void cleanupExpired_delegatesToRepository() {
        // when
        service.cleanupExpired();

        // then
        verify(repository).deleteExpired(any(Instant.class));
    }

    @Test
    void computeEmbeddingHash_isDeterministic() {
        String hash1 = service.computeEmbeddingHash(EMBEDDING_A);
        String hash2 = service.computeEmbeddingHash(EMBEDDING_A);

        assertThat(hash1).isEqualTo(hash2);
        assertThat(hash1).hasSize(64); // SHA-256 hex = 64 chars
    }

    @Test
    void computeEmbeddingHash_differsByEmbedding() {
        String hashA = service.computeEmbeddingHash(EMBEDDING_A);
        String hashB = service.computeEmbeddingHash(EMBEDDING_B);

        assertThat(hashA).isNotEqualTo(hashB);
    }

    @Test
    void putAndGet_roundTrip() {
        // given
        when(embeddingService.embed("round trip query")).thenReturn(EMBEDDING_A);
        String hash = service.computeEmbeddingHash(EMBEDDING_A);

        // put
        service.put("round trip query", "round trip answer", null);

        // simulate repository returning saved entity on next get
        SemanticCacheEntity cached = new SemanticCacheEntity();
        ReflectionTestUtils.setField(cached, "id", 10L);
        cached.setAnswerText("round trip answer");
        cached.setExpiresAt(Instant.now().plus(24, ChronoUnit.HOURS));
        cached.setHitCount(0);

        when(repository.findByQueryEmbeddingHashAndInvalidatedFalse(hash))
                .thenReturn(Optional.of(cached));

        // when
        Optional<SemanticCacheService.CachedAnswer> result = service.get("round trip query");

        // then
        assertThat(result).isPresent();
        assertThat(result.get().answerText()).isEqualTo("round trip answer");
    }
}
