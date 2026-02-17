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
import java.util.List;
import java.util.stream.Collectors;

@Component
public class DocumentTextExtractor {

    private static final Logger log = LoggerFactory.getLogger(DocumentTextExtractor.class);

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

            log.info("pdf.extractByPage.success path={} pages={}", filePath.getFileName(), pages.size());
            return pages;
        }
    }

    private String cleanText(String text) {
        if (text == null) {
            return "";
        }
        return text.replaceAll("\\p{Cntrl}", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }
}
