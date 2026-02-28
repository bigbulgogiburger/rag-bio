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

    private static final int CHUNK_SIZE = 1500;
    private static final int OVERLAP_CHARS = 300;

    // 과학 약어 보호 패턴
    private static final Pattern ABBREVIATION = Pattern.compile(
        "(?:e\\.g|i\\.e|Fig|Figs|et al|vs|Vol|No|Dr|Mr|Mrs|Prof|approx|" +
        "Inc|Ltd|Corp|Rev|dept|cf|viz|" +
        "p|pp|sec|min|hr|conc|temp|vol|wt|" +
        "\\d)" +
        "\\.(?=\\s)"
    );
    private static final String DOT_PLACEHOLDER = "\uFFF0";

    // 제목 패턴: Markdown heading, 번호 리스트, 전체 대문자 제목, 기술 문서 섹션
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

    private final DocumentChunkJpaRepository chunkRepository;

    public ChunkingService(DocumentChunkJpaRepository chunkRepository) {
        this.chunkRepository = chunkRepository;
    }

    /**
     * 기존 방식 (하위 호환) - INQUIRY 타입으로 청킹
     */
    public int chunkAndStore(UUID documentId, String text) {
        return chunkAndStore(documentId, text, "INQUIRY", documentId, null, null);
    }

    /**
     * source_type과 source_id를 지정하여 문장 경계 기반 청킹 (하위 호환)
     */
    public int chunkAndStore(UUID documentId, String text, String sourceType, UUID sourceId) {
        return chunkAndStore(documentId, text, sourceType, sourceId, null, null);
    }

    /**
     * source_type, source_id, fileName을 지정하여 문장 경계 기반 청킹 (하위 호환)
     * fileName이 지정되면 각 청크 콘텐츠 앞에 "[fileName] " 접두사 추가
     */
    public int chunkAndStore(UUID documentId, String text, String sourceType, UUID sourceId, String fileName) {
        return chunkAndStore(documentId, text, sourceType, sourceId, fileName, null);
    }

    /**
     * source_type, source_id, fileName, productFamily를 지정하여 문장 경계 기반 청킹
     * fileName이 지정되면 각 청크 콘텐츠 앞에 "[fileName] " 접두사 추가
     * productFamily가 지정되면 각 청크에 제품 패밀리 메타데이터 설정
     */
    public int chunkAndStore(UUID documentId, String text, String sourceType, UUID sourceId, String fileName, String productFamily) {
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
                // 제목 감지: 현재 청크에 내용이 있고 다음 문장이 제목이면 새 청크 시작
                if (chunkContent.length() > 0 && isHeading(nextSentence)) {
                    break;
                }
                if (chunkContent.length() > 0) {
                    chunkContent.append(" ");
                }
                chunkContent.append(nextSentence);
                sentenceEnd++;
            }

            String rawContent = chunkContent.toString();

            // 단일 문장이 CHUNK_SIZE를 초과하면 강제 분할
            if (rawContent.length() > CHUNK_SIZE) {
                int pos = 0;
                while (pos < rawContent.length()) {
                    int end = Math.min(pos + CHUNK_SIZE, rawContent.length());
                    String subChunk = rawContent.substring(pos, end);
                    int startOff = globalOffset + pos;
                    int endOff = startOff + subChunk.length();

                    DocumentChunkJpaEntity chunk = new DocumentChunkJpaEntity(
                            UUID.randomUUID(),
                            documentId,
                            chunkIndex,
                            startOff,
                            endOff,
                            applyFileNamePrefix(subChunk, fileName),
                            sourceType,
                            sourceId,
                            Instant.now()
                    );
                    if (productFamily != null) {
                        chunk.setProductFamily(productFamily);
                    }
                    chunks.add(chunk);
                    chunkIndex++;
                    pos = end;
                }
                globalOffset += rawContent.length();
                sentenceStart = Math.max(sentenceStart + 1, overlapSentencesForChars(sentences, sentenceStart, sentenceEnd));
                continue;
            }

            int startOffset = globalOffset;
            int endOffset = startOffset + rawContent.length();

            DocumentChunkJpaEntity chunk = new DocumentChunkJpaEntity(
                    UUID.randomUUID(),
                    documentId,
                    chunkIndex,
                    startOffset,
                    endOffset,
                    applyFileNamePrefix(rawContent, fileName),
                    sourceType,
                    sourceId,
                    Instant.now()
            );
            if (productFamily != null) {
                chunk.setProductFamily(productFamily);
            }
            chunks.add(chunk);

            globalOffset = endOffset;
            chunkIndex++;

            if (sentenceEnd >= sentences.size()) {
                break;
            }

            // 오버랩: 끝에서 ~300자에 해당하는 문장들을 다음 청크에 포함
            sentenceStart = Math.max(sentenceStart + 1, overlapSentencesForChars(sentences, sentenceStart, sentenceEnd));
        }

        chunkRepository.saveAll(chunks);
        return chunks.size();
    }

    /**
     * 페이지 정보를 포함하여 청킹한다 (하위 호환).
     */
    public int chunkAndStore(UUID documentId, List<PageText> pageTexts, String sourceType, UUID sourceId) {
        return chunkAndStore(documentId, pageTexts, sourceType, sourceId, null, null);
    }

    /**
     * 페이지 정보를 포함하여 청킹한다 (하위 호환).
     * 각 청크에 pageStart/pageEnd가 매핑된다.
     * fileName이 지정되면 각 청크 콘텐츠 앞에 "[fileName] " 접두사 추가
     */
    public int chunkAndStore(UUID documentId, List<PageText> pageTexts, String sourceType, UUID sourceId, String fileName) {
        return chunkAndStore(documentId, pageTexts, sourceType, sourceId, fileName, null);
    }

    /**
     * 페이지 정보를 포함하여 청킹한다.
     * 각 청크에 pageStart/pageEnd가 매핑된다.
     * fileName이 지정되면 각 청크 콘텐츠 앞에 "[fileName] " 접두사 추가
     * productFamily가 지정되면 각 청크에 제품 패밀리 메타데이터 설정
     */
    public int chunkAndStore(UUID documentId, List<PageText> pageTexts, String sourceType, UUID sourceId, String fileName, String productFamily) {
        // 전체 텍스트 연결 (페이지 사이에 단락 구분자)
        String fullText = pageTexts.stream()
                .map(PageText::text)
                .collect(Collectors.joining("\n\n"));

        // 콘텐츠 매칭용 정규화 페이지 텍스트 사전 계산 (오프셋 드리프트에 영향받지 않음)
        List<String> normalizedPages = pageTexts.stream()
                .map(pt -> pt.text().replaceAll("\\s+", " ").trim())
                .collect(Collectors.toList());

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
                // 제목 감지: 현재 청크에 내용이 있고 다음 문장이 제목이면 새 청크 시작
                if (chunkContent.length() > 0 && isHeading(nextSentence)) {
                    break;
                }
                if (chunkContent.length() > 0) {
                    chunkContent.append(" ");
                }
                chunkContent.append(nextSentence);
                sentenceEnd++;
            }

            String rawContent = chunkContent.toString();

            if (rawContent.length() > CHUNK_SIZE) {
                int pos = 0;
                while (pos < rawContent.length()) {
                    int end = Math.min(pos + CHUNK_SIZE, rawContent.length());
                    String subChunk = rawContent.substring(pos, end);
                    int startOff = globalOffset + pos;
                    int endOff = startOff + subChunk.length();
                    int[] pageRange = resolvePageRange(subChunk, pageTexts, normalizedPages);

                    DocumentChunkJpaEntity chunk = new DocumentChunkJpaEntity(
                            UUID.randomUUID(), documentId, chunkIndex,
                            startOff, endOff, applyFileNamePrefix(subChunk, fileName),
                            sourceType, sourceId,
                            pageRange[0] == 0 ? null : pageRange[0],
                            pageRange[1] == 0 ? null : pageRange[1],
                            Instant.now()
                    );
                    if (productFamily != null) {
                        chunk.setProductFamily(productFamily);
                    }
                    chunks.add(chunk);
                    chunkIndex++;
                    pos = end;
                }
                globalOffset += rawContent.length();
                sentenceStart = Math.max(sentenceStart + 1, overlapSentencesForChars(sentences, sentenceStart, sentenceEnd));
                continue;
            }

            int startOffset = globalOffset;
            int endOffset = startOffset + rawContent.length();
            // 페이지 범위 해결 시 원본 콘텐츠 사용 (프리픽스 없이)
            int[] pageRange = resolvePageRange(rawContent, pageTexts, normalizedPages);

            DocumentChunkJpaEntity chunk = new DocumentChunkJpaEntity(
                    UUID.randomUUID(), documentId, chunkIndex,
                    startOffset, endOffset, applyFileNamePrefix(rawContent, fileName),
                    sourceType, sourceId,
                    pageRange[0] == 0 ? null : pageRange[0],
                    pageRange[1] == 0 ? null : pageRange[1],
                    Instant.now()
            );
            if (productFamily != null) {
                chunk.setProductFamily(productFamily);
            }
            chunks.add(chunk);

            globalOffset = endOffset;
            chunkIndex++;

            if (sentenceEnd >= sentences.size()) {
                break;
            }

            sentenceStart = Math.max(sentenceStart + 1, overlapSentencesForChars(sentences, sentenceStart, sentenceEnd));
        }

        chunkRepository.saveAll(chunks);
        return chunks.size();
    }

    /**
     * 청크의 내용이 어떤 페이지에 걸치는지 텍스트 매칭으로 결정한다.
     * 오프셋 기반 대신 콘텐츠 매칭을 사용하여 문장 분리/재결합 오프셋 드리프트 문제를 해결.
     */
    private static final int MAX_PAGE_SPAN = 3;

    private int[] resolvePageRange(String chunkContent, List<PageText> pageTexts, List<String> normalizedPages) {
        if (chunkContent == null || chunkContent.isBlank() || pageTexts.isEmpty()) {
            return new int[]{0, 0};
        }

        String normalized = chunkContent.replaceAll("\\s+", " ").trim();
        int pageStart = findMatchingPage(normalized, true, pageTexts, normalizedPages, 0);
        // pageEnd는 pageStart 인덱스 이후부터만 검색하여 중복 텍스트 오매칭 방지
        int searchFromIndex = 0;
        if (pageStart != 0) {
            for (int i = 0; i < pageTexts.size(); i++) {
                if (pageTexts.get(i).pageNumber() == pageStart) {
                    searchFromIndex = i;
                    break;
                }
            }
        }
        int pageEnd = findMatchingPage(normalized, false, pageTexts, normalizedPages, searchFromIndex);

        if (pageStart == 0 && pageEnd != 0) pageStart = pageEnd;
        if (pageEnd == 0 && pageStart != 0) pageEnd = pageStart;
        if (pageStart != 0 && pageEnd != 0 && pageEnd < pageStart) pageEnd = pageStart;
        // 1500자 chunk가 MAX_PAGE_SPAN 페이지 이상 걸칠 수 없음 — 중복 텍스트 오매칭 보정
        if (pageStart != 0 && pageEnd != 0 && (pageEnd - pageStart) >= MAX_PAGE_SPAN) {
            pageEnd = pageStart;
        }
        return new int[]{pageStart, pageEnd};
    }

    /**
     * 청크 텍스트의 시작 또는 끝 부분을 프로브로 사용하여 매칭되는 페이지를 찾는다.
     * 프로브 길이를 점진적으로 줄여 페이지 경계를 걸치는 텍스트도 처리.
     * searchFromIndex: 이 인덱스 이후의 페이지부터만 검색 (중복 텍스트 대응)
     */
    private int findMatchingPage(String normalized, boolean fromStart,
                                 List<PageText> pageTexts, List<String> normalizedPages,
                                 int searchFromIndex) {
        int textLen = normalized.length();
        if (textLen < 10) return 0;

        for (int probeLen = Math.min(50, textLen); probeLen >= 15; probeLen -= 5) {
            String probe = fromStart
                    ? normalized.substring(0, probeLen)
                    : normalized.substring(textLen - probeLen);

            for (int i = searchFromIndex; i < normalizedPages.size(); i++) {
                if (normalizedPages.get(i).contains(probe)) {
                    return pageTexts.get(i).pageNumber();
                }
            }
        }
        return 0;
    }

    /**
     * 청크 끝에서 ~OVERLAP_CHARS(300자)에 해당하는 문장 수를 역산하여
     * 다음 청크의 시작 인덱스를 반환한다.
     * 이렇게 하면 인접 청크가 양쪽 300자씩 겹치게 된다.
     */
    private int overlapSentencesForChars(List<String> sentences, int sentenceStart, int sentenceEnd) {
        int overlapLen = 0;
        int newStart = sentenceEnd;
        for (int k = sentenceEnd - 1; k > sentenceStart; k--) {
            overlapLen += sentences.get(k).length() + 1; // +1 for space
            if (overlapLen >= OVERLAP_CHARS) {
                newStart = k;
                break;
            }
            newStart = k;
        }
        return newStart;
    }

    private boolean isHeading(String sentence) {
        return HEADING_PATTERN.matcher(sentence.trim()).find();
    }

    private String applyFileNamePrefix(String content, String fileName) {
        if (fileName == null || fileName.isBlank() || content.isEmpty()) {
            return content;
        }
        return "[" + fileName + "] " + content;
    }

    List<String> splitIntoSentences(String text) {
        if (text == null || text.isBlank()) return List.of();

        // Pass 1: 약어 마침표를 플레이스홀더로 치환
        String protectedText = ABBREVIATION.matcher(text)
            .replaceAll(m -> m.group().replace(".", DOT_PLACEHOLDER));

        // 단락 분리 → 문장 분리
        String[] paragraphs = protectedText.split("\\n\\n+");
        List<String> sentences = new ArrayList<>();

        for (String paragraph : paragraphs) {
            String[] parts = paragraph.trim().split(
                "(?<=[.!?。])\\s+(?=[A-Z가-힣\\d(\\[\"'])"
            );
            for (String part : parts) {
                String trimmed = part.trim();
                if (!trimmed.isEmpty()) {
                    // Pass 2: 플레이스홀더를 원래 마침표로 복원
                    sentences.add(trimmed.replace(DOT_PLACEHOLDER, "."));
                }
            }
        }
        return sentences;
    }
}
