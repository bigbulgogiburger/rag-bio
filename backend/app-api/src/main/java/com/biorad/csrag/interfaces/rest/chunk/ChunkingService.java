package com.biorad.csrag.interfaces.rest.chunk;

import com.biorad.csrag.infrastructure.persistence.chunk.DocumentChunkJpaEntity;
import com.biorad.csrag.infrastructure.persistence.chunk.DocumentChunkJpaRepository;
import org.springframework.stereotype.Service;

import com.biorad.csrag.interfaces.rest.document.DocumentTextExtractor.PageText;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class ChunkingService {

    private static final int CHUNK_SIZE = 1000;
    private static final int OVERLAP_SENTENCES = 2;

    // 문장 분리: 마침표/느낌표/물음표 + 공백, 또는 줄바꿈
    private static final Pattern SENTENCE_SPLIT = Pattern.compile(
            "(?<=[.!?。\\n])\\s+"
    );

    private final DocumentChunkJpaRepository chunkRepository;

    public ChunkingService(DocumentChunkJpaRepository chunkRepository) {
        this.chunkRepository = chunkRepository;
    }

    /**
     * 기존 방식 (하위 호환) - INQUIRY 타입으로 청킹
     */
    public int chunkAndStore(UUID documentId, String text) {
        return chunkAndStore(documentId, text, "INQUIRY", documentId);
    }

    /**
     * source_type과 source_id를 지정하여 문장 경계 기반 청킹
     */
    public int chunkAndStore(UUID documentId, String text, String sourceType, UUID sourceId) {
        chunkRepository.deleteByDocumentId(documentId);

        List<String> sentences = splitIntoSentences(text);
        List<DocumentChunkJpaEntity> chunks = new ArrayList<>();
        int chunkIndex = 0;
        int globalOffset = 0;

        int sentenceStart = 0;
        while (sentenceStart < sentences.size()) {
            StringBuilder chunkContent = new StringBuilder();
            int sentenceEnd = sentenceStart;

            // 문장을 누적하면서 CHUNK_SIZE 이내로 병합
            while (sentenceEnd < sentences.size()) {
                String nextSentence = sentences.get(sentenceEnd);
                if (chunkContent.length() + nextSentence.length() > CHUNK_SIZE && chunkContent.length() > 0) {
                    break;
                }
                if (chunkContent.length() > 0) {
                    chunkContent.append(" ");
                }
                chunkContent.append(nextSentence);
                sentenceEnd++;
            }

            String content = chunkContent.toString();

            // 단일 문장이 CHUNK_SIZE를 초과하면 강제 분할
            if (content.length() > CHUNK_SIZE) {
                int pos = 0;
                while (pos < content.length()) {
                    int end = Math.min(pos + CHUNK_SIZE, content.length());
                    String subChunk = content.substring(pos, end);
                    int startOff = globalOffset + pos;
                    int endOff = startOff + subChunk.length();

                    chunks.add(new DocumentChunkJpaEntity(
                            UUID.randomUUID(),
                            documentId,
                            chunkIndex,
                            startOff,
                            endOff,
                            subChunk,
                            sourceType,
                            sourceId,
                            Instant.now()
                    ));
                    chunkIndex++;
                    pos = end;
                }
                globalOffset += content.length();
                sentenceStart = Math.max(sentenceStart + 1, sentenceEnd - OVERLAP_SENTENCES);
                continue;
            }

            int startOffset = globalOffset;
            int endOffset = startOffset + content.length();

            chunks.add(new DocumentChunkJpaEntity(
                    UUID.randomUUID(),
                    documentId,
                    chunkIndex,
                    startOffset,
                    endOffset,
                    content,
                    sourceType,
                    sourceId,
                    Instant.now()
            ));

            globalOffset = endOffset;
            chunkIndex++;

            if (sentenceEnd >= sentences.size()) {
                break;
            }

            // 오버랩: 마지막 N개 문장을 다음 청크에 포함
            sentenceStart = Math.max(sentenceStart + 1, sentenceEnd - OVERLAP_SENTENCES);
        }

        chunkRepository.saveAll(chunks);
        return chunks.size();
    }

    /**
     * 페이지 정보를 포함하여 청킹한다.
     * 각 청크에 pageStart/pageEnd가 매핑된다.
     */
    public int chunkAndStore(UUID documentId, List<PageText> pageTexts, String sourceType, UUID sourceId) {
        // 전체 텍스트 연결 (페이지 사이에 공백 구분자)
        String fullText = pageTexts.stream()
                .map(PageText::text)
                .collect(Collectors.joining(" "));

        chunkRepository.deleteByDocumentId(documentId);

        List<String> sentences = splitIntoSentences(fullText);
        List<DocumentChunkJpaEntity> chunks = new ArrayList<>();
        int chunkIndex = 0;
        int globalOffset = 0;

        int sentenceStart = 0;
        while (sentenceStart < sentences.size()) {
            StringBuilder chunkContent = new StringBuilder();
            int sentenceEnd = sentenceStart;

            while (sentenceEnd < sentences.size()) {
                String nextSentence = sentences.get(sentenceEnd);
                if (chunkContent.length() + nextSentence.length() > CHUNK_SIZE && chunkContent.length() > 0) {
                    break;
                }
                if (chunkContent.length() > 0) {
                    chunkContent.append(" ");
                }
                chunkContent.append(nextSentence);
                sentenceEnd++;
            }

            String content = chunkContent.toString();

            if (content.length() > CHUNK_SIZE) {
                int pos = 0;
                while (pos < content.length()) {
                    int end = Math.min(pos + CHUNK_SIZE, content.length());
                    String subChunk = content.substring(pos, end);
                    int startOff = globalOffset + pos;
                    int endOff = startOff + subChunk.length();
                    int[] pageRange = resolvePageRange(startOff, endOff, pageTexts);

                    chunks.add(new DocumentChunkJpaEntity(
                            UUID.randomUUID(), documentId, chunkIndex,
                            startOff, endOff, subChunk,
                            sourceType, sourceId,
                            pageRange[0] == 0 ? null : pageRange[0],
                            pageRange[1] == 0 ? null : pageRange[1],
                            Instant.now()
                    ));
                    chunkIndex++;
                    pos = end;
                }
                globalOffset += content.length();
                sentenceStart = Math.max(sentenceStart + 1, sentenceEnd - OVERLAP_SENTENCES);
                continue;
            }

            int startOffset = globalOffset;
            int endOffset = startOffset + content.length();
            int[] pageRange = resolvePageRange(startOffset, endOffset, pageTexts);

            chunks.add(new DocumentChunkJpaEntity(
                    UUID.randomUUID(), documentId, chunkIndex,
                    startOffset, endOffset, content,
                    sourceType, sourceId,
                    pageRange[0] == 0 ? null : pageRange[0],
                    pageRange[1] == 0 ? null : pageRange[1],
                    Instant.now()
            ));

            globalOffset = endOffset;
            chunkIndex++;

            if (sentenceEnd >= sentences.size()) {
                break;
            }

            sentenceStart = Math.max(sentenceStart + 1, sentenceEnd - OVERLAP_SENTENCES);
        }

        chunkRepository.saveAll(chunks);
        return chunks.size();
    }

    /**
     * 청크의 [chunkStart, chunkEnd) 범위가 어떤 페이지에 걸치는지 계산한다.
     * pageNumber=0(비-PDF)이면 [0,0] 반환 → null로 변환됨.
     */
    private int[] resolvePageRange(int chunkStart, int chunkEnd, List<PageText> pageTexts) {
        int pageStart = 0;
        int pageEnd = 0;
        for (PageText pt : pageTexts) {
            if (pt.startOffset() <= chunkStart && chunkStart < pt.endOffset()) {
                pageStart = pt.pageNumber();
            }
            if (pt.startOffset() < chunkEnd && chunkEnd <= pt.endOffset()) {
                pageEnd = pt.pageNumber();
            }
        }
        // chunkEnd가 정확히 마지막 페이지의 endOffset과 같은 경우
        if (pageEnd == 0 && !pageTexts.isEmpty()) {
            PageText last = pageTexts.get(pageTexts.size() - 1);
            if (chunkEnd == last.endOffset()) {
                pageEnd = last.pageNumber();
            }
        }
        if (pageStart == 0 && pageEnd != 0) {
            pageStart = pageEnd;
        }
        if (pageEnd == 0 && pageStart != 0) {
            pageEnd = pageStart;
        }
        return new int[]{pageStart, pageEnd};
    }

    List<String> splitIntoSentences(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }

        String[] parts = SENTENCE_SPLIT.split(text.trim());
        List<String> sentences = new ArrayList<>();
        for (String part : parts) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                sentences.add(trimmed);
            }
        }
        return sentences;
    }
}
