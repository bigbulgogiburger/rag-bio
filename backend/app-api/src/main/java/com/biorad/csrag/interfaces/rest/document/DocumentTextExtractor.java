package com.biorad.csrag.interfaces.rest.document;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class DocumentTextExtractor {

    private static final Logger log = LoggerFactory.getLogger(DocumentTextExtractor.class);

    private final TableExtractorService tableExtractor;

    public DocumentTextExtractor(TableExtractorService tableExtractor) {
        this.tableExtractor = tableExtractor;
    }

    public String extract(Path filePath, String contentType) throws IOException {
        if (contentType == null) {
            contentType = "";
        }

        String normalized = contentType.toLowerCase().trim();

        if (normalized.contains("pdf")) {
            return extractFromPdf(filePath);
        }

        if (normalized.contains("wordprocessingml")
                || normalized.contains("msword")
                || filePath.toString().endsWith(".docx")
                || filePath.toString().endsWith(".doc")) {
            return extractFromDocx(filePath);
        }

        return extractFromText(filePath);
    }

    String extractFromPdf(Path filePath) throws IOException {
        try (PDDocument document = Loader.loadPDF(filePath.toFile())) {
            PDFTextStripper stripper = new PDFTextStripper();
            String text = stripper.getText(document);
            log.info("pdf.extract.success path={} pages={} length={}",
                    filePath.getFileName(), document.getNumberOfPages(), text.length());
            return cleanText(text);
        }
    }

    String extractFromDocx(Path filePath) throws IOException {
        try (InputStream is = Files.newInputStream(filePath);
             XWPFDocument document = new XWPFDocument(is)) {

            String text = document.getParagraphs().stream()
                    .map(XWPFParagraph::getText)
                    .filter(t -> t != null && !t.isBlank())
                    .collect(Collectors.joining("\n"));

            log.info("docx.extract.success path={} paragraphs={} length={}",
                    filePath.getFileName(), document.getParagraphs().size(), text.length());
            return cleanText(text);
        }
    }

    String extractFromText(Path filePath) throws IOException {
        byte[] bytes = Files.readAllBytes(filePath);
        return cleanText(new String(bytes, StandardCharsets.UTF_8));
    }

    /**
     * 페이지별 텍스트와 오프셋을 포함하는 레코드.
     * PDF: pageNumber = 1-based 페이지 번호
     * 비-PDF: pageNumber = 0 (전체 파일)
     */
    public record PageText(int pageNumber, String text, int startOffset, int endOffset) {}

    /**
     * 페이지별 텍스트를 추출한다.
     * PDF: 각 페이지별로 텍스트와 globalOffset 누적
     * 비-PDF: 단일 PageText(pageNumber=0) 반환
     */
    public List<PageText> extractByPage(Path filePath, String contentType) throws IOException {
        String normalized = (contentType == null ? "" : contentType).toLowerCase().trim();

        if (normalized.contains("pdf")) {
            return extractPdfByPage(filePath);
        }

        // 비-PDF: 전체 텍스트를 단일 PageText로
        String text = extract(filePath, contentType);
        return List.of(new PageText(0, text, 0, text.length()));
    }

    private List<PageText> extractPdfByPage(Path filePath) throws IOException {
        try (PDDocument document = Loader.loadPDF(filePath.toFile())) {
            List<PageText> pages = new ArrayList<>();
            PDFTextStripper stripper = new PDFTextStripper();
            int globalOffset = 0;

            for (int i = 1; i <= document.getNumberOfPages(); i++) {
                stripper.setStartPage(i);
                stripper.setEndPage(i);
                String pageText = cleanText(stripper.getText(document));

                int startOffset = globalOffset;
                int endOffset = startOffset + pageText.length();
                pages.add(new PageText(i, pageText, startOffset, endOffset));
                globalOffset = endOffset;
            }

            pages = removeHeadersFooters(pages);

            List<TableExtractorService.ExtractedTable> tables = tableExtractor.extractTables(filePath);
            pages = TableExtractorService.mergeTablesIntoPages(pages, tables);

            log.info("pdf.extractByPage.success path={} pages={}", filePath.getFileName(), pages.size());
            return pages;
        }
    }

    List<PageText> removeHeadersFooters(List<PageText> pages) {
        if (pages.size() < 3) return pages;

        Map<String, Integer> firstLineFreq = new HashMap<>();
        Map<String, Integer> lastLineFreq = new HashMap<>();

        for (PageText page : pages) {
            String[] lines = page.text().split("\\n");
            if (lines.length > 0) firstLineFreq.merge(lines[0].trim(), 1, Integer::sum);
            if (lines.length > 1) lastLineFreq.merge(lines[lines.length - 1].trim(), 1, Integer::sum);
        }

        int threshold = pages.size() / 2;
        Set<String> headerLines = firstLineFreq.entrySet().stream()
                .filter(e -> e.getValue() >= threshold)
                .map(Map.Entry::getKey).collect(Collectors.toSet());
        Set<String> footerLines = lastLineFreq.entrySet().stream()
                .filter(e -> e.getValue() >= threshold)
                .map(Map.Entry::getKey).collect(Collectors.toSet());

        return pages.stream().map(page -> {
            String[] lines = page.text().split("\\n");
            List<String> filtered = new ArrayList<>();
            for (int i = 0; i < lines.length; i++) {
                String trimmed = lines[i].trim();
                if (i == 0 && headerLines.contains(trimmed)) continue;
                if (i == lines.length - 1 && footerLines.contains(trimmed)) continue;
                filtered.add(lines[i]);
            }
            return new PageText(page.pageNumber(), String.join("\n", filtered), page.startOffset(), page.endOffset());
        }).toList();
    }

    private String cleanText(String text) {
        if (text == null) {
            return "";
        }
        return text
                .replaceAll("[\\p{Cntrl}&&[^\\n\\r\\t]]", " ")
                .replaceAll("\\r\\n", "\n")
                .replaceAll("\\r", "\n")
                .replaceAll("[\\t ]+", " ")
                .replaceAll("\\n{3,}", "\n\n")
                .trim();
    }
}
