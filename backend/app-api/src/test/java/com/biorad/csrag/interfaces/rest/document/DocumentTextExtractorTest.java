package com.biorad.csrag.interfaces.rest.document;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class DocumentTextExtractorTest {

    @TempDir
    Path tempDir;

    private DocumentTextExtractor extractor;

    @BeforeEach
    void setUp() {
        extractor = new DocumentTextExtractor();
    }

    @Test
    void extractFromPdf_extractsTextFromValidPdf() throws IOException {
        Path pdfFile = createTestPdf("Bio-Rad CFX96 qPCR System User Guide. This system supports multiplex assays.");

        String result = extractor.extract(pdfFile, "application/pdf");

        assertThat(result).contains("Bio-Rad");
        assertThat(result).contains("CFX96");
        assertThat(result).contains("qPCR");
        assertThat(result).isNotBlank();
    }

    @Test
    void extractFromDocx_extractsTextFromValidDocx() throws IOException {
        Path docxFile = createTestDocx(
                "Bio-Rad Protocol Document",
                "The QX200 ddPCR system provides absolute quantification.",
                "Sample preparation requires 20 uL reaction volume."
        );

        String result = extractor.extract(docxFile, "application/vnd.openxmlformats-officedocument.wordprocessingml.document");

        assertThat(result).contains("Bio-Rad Protocol Document");
        assertThat(result).contains("QX200");
        assertThat(result).contains("absolute quantification");
    }

    @Test
    void extractFromText_extractsPlainTextFile() throws IOException {
        Path textFile = tempDir.resolve("test.txt");
        Files.writeString(textFile, "This is a plain text document with Bio-Rad content.\nSecond line here.");

        String result = extractor.extract(textFile, "text/plain");

        assertThat(result).contains("Bio-Rad");
        assertThat(result).contains("Second line here");
    }

    @Test
    void extract_routesByContentType_pdf() throws IOException {
        Path pdfFile = createTestPdf("PDF routing test content");

        String result = extractor.extract(pdfFile, "application/pdf");

        assertThat(result).contains("PDF routing test");
    }

    @Test
    void extract_routesByContentType_docx() throws IOException {
        Path docxFile = createTestDocx("DOCX routing test content");

        String result = extractor.extract(docxFile, "application/vnd.openxmlformats-officedocument.wordprocessingml.document");

        assertThat(result).contains("DOCX routing test");
    }

    @Test
    void extract_routesByFileExtension_whenContentTypeIsGeneric() throws IOException {
        Path docxFile = createTestDocx("Extension-based routing");
        Path renamed = tempDir.resolve("test.docx");
        Files.move(docxFile, renamed);

        String result = extractor.extract(renamed, "application/octet-stream");

        assertThat(result).contains("Extension-based routing");
    }

    @Test
    void extract_fallsBackToTextForUnknownType() throws IOException {
        Path textFile = tempDir.resolve("test.csv");
        Files.writeString(textFile, "col1,col2\nval1,val2");

        String result = extractor.extract(textFile, "text/csv");

        assertThat(result).contains("col1,col2");
    }

    @Test
    void extractFromPdf_handlesMultiPagePdf() throws IOException {
        Path pdfFile = createMultiPagePdf();

        String result = extractor.extract(pdfFile, "application/pdf");

        assertThat(result).contains("Page 1 content");
        assertThat(result).contains("Page 2 content");
    }

    @Test
    void extract_cleansControlCharacters() throws IOException {
        Path textFile = tempDir.resolve("dirty.txt");
        Files.writeString(textFile, "Hello\u0000World\u0001Test\u0002End");

        String result = extractor.extract(textFile, "text/plain");

        assertThat(result).doesNotContain("\u0000");
        assertThat(result).contains("Hello");
        assertThat(result).contains("World");
    }

    // --- Helper methods ---

    private Path createTestPdf(String text) throws IOException {
        Path pdfFile = tempDir.resolve(UUID.randomUUID() + ".pdf");
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage();
            doc.addPage(page);
            try (PDPageContentStream contentStream = new PDPageContentStream(doc, page)) {
                contentStream.beginText();
                contentStream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                contentStream.newLineAtOffset(50, 700);
                contentStream.showText(text);
                contentStream.endText();
            }
            doc.save(pdfFile.toFile());
        }
        return pdfFile;
    }

    private Path createMultiPagePdf() throws IOException {
        Path pdfFile = tempDir.resolve("multi-page.pdf");
        try (PDDocument doc = new PDDocument()) {
            for (int i = 1; i <= 2; i++) {
                PDPage page = new PDPage();
                doc.addPage(page);
                try (PDPageContentStream contentStream = new PDPageContentStream(doc, page)) {
                    contentStream.beginText();
                    contentStream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                    contentStream.newLineAtOffset(50, 700);
                    contentStream.showText("Page " + i + " content");
                    contentStream.endText();
                }
            }
            doc.save(pdfFile.toFile());
        }
        return pdfFile;
    }

    private Path createTestDocx(String... paragraphs) throws IOException {
        Path docxFile = tempDir.resolve(UUID.randomUUID() + ".docx");
        try (XWPFDocument doc = new XWPFDocument();
             FileOutputStream out = new FileOutputStream(docxFile.toFile())) {
            for (String text : paragraphs) {
                XWPFParagraph paragraph = doc.createParagraph();
                XWPFRun run = paragraph.createRun();
                run.setText(text);
            }
            doc.write(out);
        }
        return docxFile;
    }

    private static final class UUID {
        static String randomUUID() {
            return java.util.UUID.randomUUID().toString();
        }
    }
}
