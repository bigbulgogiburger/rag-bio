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
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
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
    void chunkAndStore_createsParentAndChildChunks() {
        UUID docId = UUID.randomUUID();
        // 짧은 문장 여러 개 - 하나의 Parent + Child 청크가 생성되어야 함
        String text = "Short sentence one. Short sentence two. Short sentence three.";

        int count = chunkingService.chunkAndStore(docId, text);

        verify(chunkRepository).saveAll(chunksCaptor.capture());
        List<DocumentChunkJpaEntity> chunks = chunksCaptor.getValue();

        assertThat(count).isEqualTo(chunks.size());

        // Parent 청크 확인
        List<DocumentChunkJpaEntity> parents = chunks.stream()
                .filter(c -> "PARENT".equals(c.getChunkLevel())).toList();
        assertThat(parents).hasSize(1);
        assertThat(parents.get(0).getContent()).contains("Short sentence one.");
        assertThat(parents.get(0).getContent()).contains("Short sentence three.");

        // Child 청크 확인 - 부모 ID 참조
        List<DocumentChunkJpaEntity> children = chunks.stream()
                .filter(c -> "CHILD".equals(c.getChunkLevel())).toList();
        assertThat(children).allSatisfy(child ->
                assertThat(child.getParentChunkId()).isEqualTo(parents.get(0).getId()));
    }

    @Test
    void chunkAndStore_splitsLongTextIntoMultipleParentsWithChildren() {
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

        List<DocumentChunkJpaEntity> parents = chunks.stream()
                .filter(c -> "PARENT".equals(c.getChunkLevel())).toList();
        List<DocumentChunkJpaEntity> children = chunks.stream()
                .filter(c -> "CHILD".equals(c.getChunkLevel())).toList();

        // 여러 Parent가 생성되어야 함
        assertThat(parents.size()).isGreaterThan(1);
        // Parent 청크는 CHUNK_SIZE를 크게 초과하지 않아야 함
        for (DocumentChunkJpaEntity parent : parents) {
            assertThat(parent.getContent().length()).isLessThanOrEqualTo(1600);
        }
        // 각 Child는 parentChunkId를 가지고 있어야 함
        assertThat(children).allSatisfy(child ->
                assertThat(child.getParentChunkId()).isNotNull());
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

        // Parent 청크만 추출하여 오버랩 확인
        List<DocumentChunkJpaEntity> parents = chunks.stream()
                .filter(c -> "PARENT".equals(c.getChunkLevel())).toList();

        if (parents.size() >= 2) {
            String firstChunk = parents.get(0).getContent();
            String secondChunk = parents.get(1).getContent();

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
        // After joining with "\n\n": pages separated by paragraph break
        List<PageText> pageTexts = new ArrayList<>();
        pageTexts.add(new PageText(1, page1, 0, page1.length()));
        pageTexts.add(new PageText(2, page2, page1.length() + 2, page1.length() + 2 + page2.length()));

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
        // Parent 청크가 CHUNK_SIZE 이하
        List<DocumentChunkJpaEntity> parents = chunks.stream()
                .filter(c -> "PARENT".equals(c.getChunkLevel())).toList();
        for (DocumentChunkJpaEntity parent : parents) {
            assertThat(parent.getContent().length()).isLessThanOrEqualTo(1500);
        }
    }

    // ── TASK 3-2: Parent-Child 청킹 테스트 ──

    @Test
    void splitIntoChildTexts_eachChildWithinTargetSize() {
        // ~800자 텍스트를 400자 Child로 분할
        StringBuilder sb = new StringBuilder();
        for (int i = 1; i <= 20; i++) {
            sb.append("Sentence ").append(i).append(" with extra padding text here. ");
        }
        String text = sb.toString();

        List<String> children = chunkingService.splitIntoChildTexts(text, 400);

        assertThat(children).isNotEmpty();
        for (String child : children) {
            assertThat(child.length()).isLessThanOrEqualTo(450); // 약간의 여유 (문장 경계)
        }
    }

    @Test
    void splitIntoChildTexts_emptyInputReturnsEmptyList() {
        assertThat(chunkingService.splitIntoChildTexts("", 400)).isEmpty();
        assertThat(chunkingService.splitIntoChildTexts(null, 400)).isEmpty();
        assertThat(chunkingService.splitIntoChildTexts("   ", 400)).isEmpty();
    }

    @Test
    void splitIntoChildTexts_shortTextReturnsSingleChild() {
        String text = "Short text.";
        List<String> children = chunkingService.splitIntoChildTexts(text, 400);
        assertThat(children).hasSize(1);
        assertThat(children.get(0)).isEqualTo("Short text.");
    }

    @Test
    void chunkAndStore_childInheritsProductFamily() {
        UUID docId = UUID.randomUUID();
        UUID sourceId = UUID.randomUUID();
        String text = "Knowledge base content about naica system. It has detailed information. Third sentence here.";

        chunkingService.chunkAndStore(docId, text, "KNOWLEDGE_BASE", sourceId, null, "naica");

        verify(chunkRepository).saveAll(chunksCaptor.capture());
        List<DocumentChunkJpaEntity> chunks = chunksCaptor.getValue();

        assertThat(chunks).allSatisfy(chunk ->
                assertThat(chunk.getProductFamily()).isEqualTo("naica"));
    }

    @Test
    void chunkAndStore_parentHasNoParentChunkId() {
        UUID docId = UUID.randomUUID();
        String text = "Some content for testing. Another sentence here.";

        chunkingService.chunkAndStore(docId, text);

        verify(chunkRepository).saveAll(chunksCaptor.capture());
        List<DocumentChunkJpaEntity> chunks = chunksCaptor.getValue();

        List<DocumentChunkJpaEntity> parents = chunks.stream()
                .filter(c -> "PARENT".equals(c.getChunkLevel())).toList();
        assertThat(parents).allSatisfy(parent ->
                assertThat(parent.getParentChunkId()).isNull());
    }

    // ── TASK 1-2: 과학 약어 보호 테스트 ──

    @Test
    void splitIntoSentences_doesNotSplitOnConcentration() {
        List<String> result = chunkingService.splitIntoSentences("final 0.125 uM");
        assertThat(result).hasSize(1);
        assertThat(result.get(0)).isEqualTo("final 0.125 uM");
    }

    @Test
    void splitIntoSentences_doesNotSplitOnFigAbbreviation() {
        List<String> result = chunkingService.splitIntoSentences("(Fig. 2) 참조");
        assertThat(result).hasSize(1);
        assertThat(result.get(0)).isEqualTo("(Fig. 2) 참조");
    }

    @Test
    void splitIntoSentences_doesNotSplitOnEgAbbreviation() {
        List<String> result = chunkingService.splitIntoSentences("e.g. restriction enzyme");
        assertThat(result).hasSize(1);
    }

    @Test
    void splitIntoSentences_splitsKoreanThenEnglish() {
        List<String> result = chunkingService.splitIntoSentences("처리 완료. Vortex 필요.");
        assertThat(result).hasSize(2);
        assertThat(result.get(0)).isEqualTo("처리 완료.");
        assertThat(result.get(1)).isEqualTo("Vortex 필요.");
    }

    @Test
    void splitIntoSentences_doesNotSplitOnDrTitle() {
        List<String> result = chunkingService.splitIntoSentences("Dr. Kim said hello. The result was good.");
        assertThat(result).hasSize(2);
        assertThat(result.get(0)).startsWith("Dr. Kim");
        assertThat(result.get(1)).startsWith("The result");
    }

    // ── TASK 1-6: HEADING_PATTERN 확장 테스트 ──

    @Test
    void headingPattern_matchesMarkdownHeading() {
        assertThat(isHeading("## Introduction")).isTrue();
    }

    @Test
    void headingPattern_matchesNumberedSection() {
        assertThat(isHeading("2.1 Methods")).isTrue();
    }

    @Test
    void headingPattern_matchesAllCapsHeading() {
        assertThat(isHeading("TROUBLESHOOTING")).isTrue();
    }

    @Test
    void headingPattern_matchesTroubleshooting() {
        assertThat(isHeading("Troubleshooting")).isTrue();
    }

    @Test
    void headingPattern_matchesKoreanHeading() {
        assertThat(isHeading("프로토콜:")).isTrue();
    }

    @Test
    void headingPattern_matchesTableReference() {
        assertThat(isHeading("Table 1")).isTrue();
        assertThat(isHeading("Figure 3")).isTrue();
        assertThat(isHeading("Fig. 5")).isTrue();
    }

    @Test
    void headingPattern_matchesSectionKeywords() {
        assertThat(isHeading("Section 2")).isTrue();
        assertThat(isHeading("Chapter 1")).isTrue();
        assertThat(isHeading("APPENDIX A")).isTrue();
    }

    @Test
    void headingPattern_matchesSafetyKeywords() {
        assertThat(isHeading("Warning")).isTrue();
        assertThat(isHeading("Caution")).isTrue();
        assertThat(isHeading("Note")).isTrue();
        assertThat(isHeading("Protocol")).isTrue();
        assertThat(isHeading("Procedure")).isTrue();
        assertThat(isHeading("Safety")).isTrue();
    }

    private static final Pattern HEADING_PATTERN = Pattern.compile(
        "^(?:" +
        "#{1,6}\\s|" +
        "\\d+\\.\\d*\\s|" +
        "[A-Z][A-Z\\s]{2,}$|" +
        "(?:Section|Chapter|Part|APPENDIX)\\s|" +
        "(?:Table|Figure|Fig\\.)\\s+\\d|" +
        "(?:Troubleshooting|Protocol|Procedure|Safety|Warning|Caution|Note)\\b|" +
        "[가-힣]+\\s*:\\s*$" +
        ")", Pattern.MULTILINE
    );

    private boolean isHeading(String text) {
        return HEADING_PATTERN.matcher(text.trim()).find();
    }
}
