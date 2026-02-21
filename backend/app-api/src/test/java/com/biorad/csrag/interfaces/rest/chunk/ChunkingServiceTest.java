package com.biorad.csrag.interfaces.rest.chunk;

import com.biorad.csrag.infrastructure.persistence.chunk.DocumentChunkJpaEntity;
import com.biorad.csrag.infrastructure.persistence.chunk.DocumentChunkJpaRepository;
import com.biorad.csrag.interfaces.rest.document.DocumentTextExtractor.PageText;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ChunkingServiceTest {

    @Mock
    private DocumentChunkJpaRepository chunkRepository;

    @Captor
    private ArgumentCaptor<List<DocumentChunkJpaEntity>> chunksCaptor;

    private ChunkingService chunkingService;

    @BeforeEach
    void setUp() {
        chunkingService = new ChunkingService(chunkRepository);
    }

    @Test
    void splitIntoSentences_splitsByPeriodAndNewline() {
        String text = "First sentence. Second sentence. Third sentence.";

        List<String> sentences = chunkingService.splitIntoSentences(text);

        assertThat(sentences).hasSize(3);
        assertThat(sentences.get(0)).isEqualTo("First sentence.");
        assertThat(sentences.get(1)).isEqualTo("Second sentence.");
        assertThat(sentences.get(2)).isEqualTo("Third sentence.");
    }

    @Test
    void splitIntoSentences_handlesKoreanSentences() {
        String text = "첫 번째 문장입니다. 두 번째 문장입니다. 세 번째 문장입니다.";

        List<String> sentences = chunkingService.splitIntoSentences(text);

        assertThat(sentences).hasSize(3);
        assertThat(sentences.get(0)).contains("첫 번째");
        assertThat(sentences.get(2)).contains("세 번째");
    }

    @Test
    void splitIntoSentences_handlesNewlineDelimitedText() {
        String text = "Line one.\nLine two.\nLine three.";

        List<String> sentences = chunkingService.splitIntoSentences(text);

        assertThat(sentences).hasSize(3);
    }

    @Test
    void splitIntoSentences_returnsEmptyForBlankInput() {
        assertThat(chunkingService.splitIntoSentences("")).isEmpty();
        assertThat(chunkingService.splitIntoSentences(null)).isEmpty();
        assertThat(chunkingService.splitIntoSentences("   ")).isEmpty();
    }

    @Test
    void chunkAndStore_createsChunksWithSentenceBoundaries() {
        UUID docId = UUID.randomUUID();
        // 짧은 문장 여러 개 - 하나의 청크에 합쳐져야 함
        String text = "Short sentence one. Short sentence two. Short sentence three.";

        int count = chunkingService.chunkAndStore(docId, text);

        verify(chunkRepository).saveAll(chunksCaptor.capture());
        List<DocumentChunkJpaEntity> chunks = chunksCaptor.getValue();

        assertThat(count).isEqualTo(chunks.size());
        // 짧은 문장들은 하나의 청크로 합쳐져야 함
        assertThat(chunks.size()).isEqualTo(1);
        assertThat(chunks.get(0).getContent()).contains("Short sentence one.");
        assertThat(chunks.get(0).getContent()).contains("Short sentence three.");
    }

    @Test
    void chunkAndStore_splitsLongTextIntoMultipleChunks() {
        UUID docId = UUID.randomUUID();
        // CHUNK_SIZE(1500) 초과하는 긴 텍스트 생성
        StringBuilder sb = new StringBuilder();
        for (int i = 1; i <= 50; i++) {
            sb.append("This is sentence number ").append(i).append(" with some padding text to make it longer. ");
        }
        String text = sb.toString();

        int count = chunkingService.chunkAndStore(docId, text);

        verify(chunkRepository).saveAll(chunksCaptor.capture());
        List<DocumentChunkJpaEntity> chunks = chunksCaptor.getValue();

        assertThat(count).isGreaterThan(1);
        // 각 청크가 CHUNK_SIZE를 크게 초과하지 않아야 함
        for (DocumentChunkJpaEntity chunk : chunks) {
            assertThat(chunk.getContent().length()).isLessThanOrEqualTo(1600); // 약간의 여유
        }
    }

    @Test
    void chunkAndStore_preservesSourceTypeAndSourceId() {
        UUID docId = UUID.randomUUID();
        UUID sourceId = UUID.randomUUID();
        String text = "Knowledge base document content. It has multiple sentences. Third sentence here.";

        chunkingService.chunkAndStore(docId, text, "KNOWLEDGE_BASE", sourceId);

        verify(chunkRepository).saveAll(chunksCaptor.capture());
        List<DocumentChunkJpaEntity> chunks = chunksCaptor.getValue();

        assertThat(chunks).allSatisfy(chunk -> {
            assertThat(chunk.getSourceType()).isEqualTo("KNOWLEDGE_BASE");
            assertThat(chunk.getSourceId()).isEqualTo(sourceId);
        });
    }

    @Test
    void chunkAndStore_defaultsToInquiryType() {
        UUID docId = UUID.randomUUID();
        String text = "Simple inquiry document text content.";

        chunkingService.chunkAndStore(docId, text);

        verify(chunkRepository).saveAll(chunksCaptor.capture());
        List<DocumentChunkJpaEntity> chunks = chunksCaptor.getValue();

        assertThat(chunks).allSatisfy(chunk -> {
            assertThat(chunk.getSourceType()).isEqualTo("INQUIRY");
            assertThat(chunk.getSourceId()).isEqualTo(docId);
        });
    }

    @Test
    void chunkAndStore_overlapsLastSentences() {
        UUID docId = UUID.randomUUID();
        // 긴 문장들로 청크가 2개 이상 되도록 구성 (CHUNK_SIZE=1500 기준)
        StringBuilder sb = new StringBuilder();
        for (int i = 1; i <= 40; i++) {
            sb.append("Sentence ").append(i).append(" is about topic ").append(i).append(" and has plenty of detail. ");
        }
        String text = sb.toString();

        chunkingService.chunkAndStore(docId, text);

        verify(chunkRepository).saveAll(chunksCaptor.capture());
        List<DocumentChunkJpaEntity> chunks = chunksCaptor.getValue();

        if (chunks.size() >= 2) {
            // 마지막 청크의 시작 부분이 이전 청크 내용과 겹쳐야 함 (오버랩)
            String firstChunk = chunks.get(0).getContent();
            String secondChunk = chunks.get(1).getContent();

            // 두 번째 청크에 첫 번째 청크의 마지막 문장이 포함되어 있는지 확인
            String[] firstSentences = firstChunk.split("\\. ");
            if (firstSentences.length > 1) {
                String lastSentenceOfFirst = firstSentences[firstSentences.length - 1];
                assertThat(secondChunk).contains(lastSentenceOfFirst.substring(0, Math.min(20, lastSentenceOfFirst.length())));
            }
        }
    }

    @Test
    void chunkAndStore_withPageTexts_createsChunksWithPageInfo() {
        UUID docId = UUID.randomUUID();
        UUID sourceId = UUID.randomUUID();

        String page1 = "Page one content here.";
        String page2 = "Page two content here.";
        // After joining with " ": "Page one content here. Page two content here."
        List<PageText> pageTexts = new ArrayList<>();
        pageTexts.add(new PageText(1, page1, 0, page1.length()));
        pageTexts.add(new PageText(2, page2, page1.length() + 1, page1.length() + 1 + page2.length()));

        int count = chunkingService.chunkAndStore(docId, pageTexts, "KNOWLEDGE_BASE", sourceId);

        verify(chunkRepository).saveAll(chunksCaptor.capture());
        List<DocumentChunkJpaEntity> chunks = chunksCaptor.getValue();

        assertThat(count).isGreaterThanOrEqualTo(1);
        assertThat(chunks).allSatisfy(chunk -> {
            assertThat(chunk.getSourceType()).isEqualTo("KNOWLEDGE_BASE");
            assertThat(chunk.getSourceId()).isEqualTo(sourceId);
        });
    }

    @Test
    void chunkAndStore_emptyText_returnsZero() {
        UUID docId = UUID.randomUUID();
        int count = chunkingService.chunkAndStore(docId, "");
        assertThat(count).isEqualTo(0);
    }

    @Test
    void chunkAndStore_nullText_returnsZero() {
        UUID docId = UUID.randomUUID();
        int count = chunkingService.chunkAndStore(docId, (String) null);
        assertThat(count).isEqualTo(0);
    }

    @Test
    void chunkAndStore_forceSplitsLongSentence() {
        UUID docId = UUID.randomUUID();
        // Single sentence > CHUNK_SIZE(1500) chars
        String longSentence = "A".repeat(2000);
        int count = chunkingService.chunkAndStore(docId, longSentence);

        verify(chunkRepository).saveAll(chunksCaptor.capture());
        List<DocumentChunkJpaEntity> chunks = chunksCaptor.getValue();

        assertThat(count).isGreaterThan(1);
        for (DocumentChunkJpaEntity chunk : chunks) {
            assertThat(chunk.getContent().length()).isLessThanOrEqualTo(1500);
        }
    }
}
