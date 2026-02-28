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
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DocumentTextExtractorTest {

    @TempDir
    Path tempDir;

    private DocumentTextExtractor extractor;

    @BeforeEach
    void setUp() {
        extractor = new DocumentTextExtractor(new TableExtractorService());
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

    // --- cleanText tests ---

    @Nested
    class CleanTextTest {

        @Test
        void preservesNewlinesBetweenParagraphs() throws IOException {
            Path file = tempDir.resolve("paragraphs.txt");
            Files.writeString(file, "First paragraph\nSecond paragraph\nThird paragraph");

            String result = extractor.extract(file, "text/plain");

            assertThat(result).contains("First paragraph\nSecond paragraph\nThird paragraph");
        }

        @Test
        void convertsWindowsLineEndingsToUnix() throws IOException {
            Path file = tempDir.resolve("crlf.txt");
            Files.writeString(file, "Line one\r\nLine two\r\nLine three");

            String result = extractor.extract(file, "text/plain");

            assertThat(result).contains("Line one\nLine two\nLine three");
            assertThat(result).doesNotContain("\r");
        }

        @Test
        void removesControlCharsButKeepsNewlineAndTab() throws IOException {
            Path file = tempDir.resolve("ctrl.txt");
            Files.writeString(file, "Hello\u0000World\n\tIndented\u0001Line");

            String result = extractor.extract(file, "text/plain");

            assertThat(result).doesNotContain("\u0000");
            assertThat(result).doesNotContain("\u0001");
            assertThat(result).contains("\n");
            assertThat(result).contains("Hello");
            assertThat(result).contains("Indented");
        }

        @Test
        void collapsesThreeOrMoreBlankLinesToTwo() throws IOException {
            Path file = tempDir.resolve("blanks.txt");
            Files.writeString(file, "Section 1\n\n\n\n\nSection 2");

            String result = extractor.extract(file, "text/plain");

            assertThat(result).isEqualTo("Section 1\n\nSection 2");
        }

        @Test
        void handlesNullInput() throws IOException {
            // cleanText is private, test via extractFromText with empty file
            Path file = tempDir.resolve("empty.txt");
            Files.writeString(file, "");

            String result = extractor.extract(file, "text/plain");

            assertThat(result).isEmpty();
        }
    }

    // --- removeHeadersFooters tests ---

    @Nested
    class RemoveHeadersFootersTest {

        @Test
        void removesRepeatedHeadersAndFooters() {
            var pages = List.of(
                    new DocumentTextExtractor.PageText(1, "Bio-Rad Manual\nContent page 1\nPage 1 of 5", 0, 50),
                    new DocumentTextExtractor.PageText(2, "Bio-Rad Manual\nContent page 2\nPage 2 of 5", 50, 100),
                    new DocumentTextExtractor.PageText(3, "Bio-Rad Manual\nContent page 3\nPage 3 of 5", 100, 150),
                    new DocumentTextExtractor.PageText(4, "Bio-Rad Manual\nContent page 4\nPage 4 of 5", 150, 200),
                    new DocumentTextExtractor.PageText(5, "Bio-Rad Manual\nContent page 5\nPage 5 of 5", 200, 250)
            );

            List<DocumentTextExtractor.PageText> result = extractor.removeHeadersFooters(pages);

            assertThat(result).hasSize(5);
            for (var page : result) {
                assertThat(page.text()).doesNotContain("Bio-Rad Manual");
                assertThat(page.text()).contains("Content page");
            }
        }

        @Test
        void skipsWhenLessThanThreePages() {
            var pages = List.of(
                    new DocumentTextExtractor.PageText(1, "Header\nContent\nFooter", 0, 20),
                    new DocumentTextExtractor.PageText(2, "Header\nContent 2\nFooter", 20, 40)
            );

            List<DocumentTextExtractor.PageText> result = extractor.removeHeadersFooters(pages);

            assertThat(result).isEqualTo(pages);
        }

        @Test
        void handlesPagesWithSingleLine() {
            var pages = List.of(
                    new DocumentTextExtractor.PageText(1, "Only line", 0, 10),
                    new DocumentTextExtractor.PageText(2, "Only line", 10, 20),
                    new DocumentTextExtractor.PageText(3, "Only line", 20, 30)
            );

            // Single-line pages: first line matches as header (3/3 >= threshold 1)
            // but last line won't match (lines.length == 1, so lastLineFreq not incremented)
            List<DocumentTextExtractor.PageText> result = extractor.removeHeadersFooters(pages);

            assertThat(result).hasSize(3);
            // Header "Only line" removed from each page
            for (var page : result) {
                assertThat(page.text()).isEmpty();
            }
        }

        @Test
        void handlesEmptyPageText() {
            var pages = List.of(
                    new DocumentTextExtractor.PageText(1, "", 0, 0),
                    new DocumentTextExtractor.PageText(2, "Content", 0, 7),
                    new DocumentTextExtractor.PageText(3, "", 7, 7)
            );

            List<DocumentTextExtractor.PageText> result = extractor.removeHeadersFooters(pages);

            assertThat(result).hasSize(3);
        }

        @Test
        void keepsNonRepeatingHeaders() {
            var pages = List.of(
                    new DocumentTextExtractor.PageText(1, "Chapter 1\nContent A\nFooter", 0, 30),
                    new DocumentTextExtractor.PageText(2, "Chapter 2\nContent B\nFooter", 30, 60),
                    new DocumentTextExtractor.PageText(3, "Chapter 3\nContent C\nFooter", 60, 90),
                    new DocumentTextExtractor.PageText(4, "Chapter 4\nContent D\nFooter", 90, 120)
            );

            List<DocumentTextExtractor.PageText> result = extractor.removeHeadersFooters(pages);

            // Each chapter title is unique, so none should be removed
            assertThat(result.get(0).text()).contains("Chapter 1");
            assertThat(result.get(1).text()).contains("Chapter 2");
            // Footer "Footer" appears 4/4 times >= threshold 2, so it should be removed
            for (var page : result) {
                assertThat(page.text()).doesNotContain("Footer");
            }
        }
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
