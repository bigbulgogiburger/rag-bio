package com.biorad.csrag.interfaces.rest.document;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * PDFBox 기반 PDF 표 추출 서비스.
 * 텍스트 위치 정보를 분석하여 열(column) 구조를 감지하고 Markdown 표로 변환한다.
 */
@Service
public class TableExtractorService {

    private static final Logger log = LoggerFactory.getLogger(TableExtractorService.class);

    /** 같은 행으로 간주할 Y좌표 허용 오차 (pt) */
    private static final float ROW_TOLERANCE = 3.0f;

    /** 열 구분을 위한 최소 X좌표 간격 (pt) */
    private static final float COLUMN_GAP_THRESHOLD = 15.0f;

    /** 표로 인정하기 위한 최소 행 수 */
    private static final int MIN_TABLE_ROWS = 2;

    /** 표로 인정하기 위한 최소 열 수 */
    private static final int MIN_TABLE_COLS = 2;

    public List<ExtractedTable> extractTables(Path pdfPath) {
        try (PDDocument document = Loader.loadPDF(pdfPath.toFile())) {
            List<ExtractedTable> allTables = new ArrayList<>();

            for (int pageIdx = 0; pageIdx < document.getNumberOfPages(); pageIdx++) {
                int pageNumber = pageIdx + 1;
                List<ExtractedTable> pageTables = extractTablesFromPage(document, pageNumber);
                allTables.addAll(pageTables);
            }

            log.info("table.extract.complete path={} tables={}", pdfPath.getFileName(), allTables.size());
            return allTables;
        } catch (Exception e) {
            log.warn("Table extraction failed for {}: {}", pdfPath, e.getMessage());
            return List.of();
        }
    }

    private List<ExtractedTable> extractTablesFromPage(PDDocument document, int pageNumber) throws IOException {
        List<List<TextPosition>> textPositions = new ArrayList<>();
        textPositions.add(new ArrayList<>());

        PDFTextStripper stripper = new PDFTextStripper() {
            @Override
            protected void writeString(String string, List<TextPosition> positions) throws IOException {
                if (positions != null) {
                    textPositions.get(0).addAll(positions);
                }
                super.writeString(string, positions);
            }
        };
        stripper.setStartPage(pageNumber);
        stripper.setEndPage(pageNumber);
        stripper.getText(document);

        List<TextPosition> positions = textPositions.get(0);
        if (positions.isEmpty()) {
            return List.of();
        }

        return detectTablesFromPositions(positions, pageNumber);
    }

    private List<ExtractedTable> detectTablesFromPositions(List<TextPosition> positions, int pageNumber) {
        // Group text positions by Y coordinate (rows)
        Map<Float, List<TextPosition>> rowGroups = positions.stream()
                .collect(Collectors.groupingBy(
                        tp -> Math.round(tp.getY() / ROW_TOLERANCE) * ROW_TOLERANCE,
                        Collectors.toList()
                ));

        // Sort rows by Y coordinate
        List<Float> sortedYs = rowGroups.keySet().stream().sorted().toList();

        // Detect column boundaries from rows with multiple text clusters
        List<Float> columnBoundaries = detectColumnBoundaries(rowGroups, sortedYs);

        if (columnBoundaries.size() < MIN_TABLE_COLS) {
            return List.of();
        }

        // Find consecutive rows that match the column structure (table region)
        List<List<String>> tableRows = new ArrayList<>();

        for (Float y : sortedYs) {
            List<TextPosition> rowPositions = rowGroups.get(y);
            List<String> cells = assignToCells(rowPositions, columnBoundaries);

            long nonEmptyCount = cells.stream().filter(c -> !c.isBlank()).count();
            if (nonEmptyCount >= MIN_TABLE_COLS) {
                tableRows.add(cells);
            } else if (!tableRows.isEmpty()) {
                // Gap in table structure - finalize current table if valid
                break;
            }
        }

        if (tableRows.size() < MIN_TABLE_ROWS) {
            return List.of();
        }

        String markdown = convertToMarkdown(tableRows);
        return List.of(new ExtractedTable(pageNumber, markdown, tableRows.size(), columnBoundaries.size()));
    }

    private List<Float> detectColumnBoundaries(Map<Float, List<TextPosition>> rowGroups, List<Float> sortedYs) {
        // Collect all X start positions across rows
        List<Float> allXStarts = new ArrayList<>();
        for (Float y : sortedYs) {
            List<TextPosition> rowPositions = rowGroups.get(y);
            if (rowPositions.isEmpty()) continue;

            // Sort by X position
            List<TextPosition> sorted = rowPositions.stream()
                    .sorted((a, b) -> Float.compare(a.getX(), b.getX()))
                    .toList();

            // Find gap-separated clusters in this row
            float lastEnd = sorted.get(0).getX();
            allXStarts.add(sorted.get(0).getX());
            for (int i = 1; i < sorted.size(); i++) {
                float gap = sorted.get(i).getX() - lastEnd;
                if (gap > COLUMN_GAP_THRESHOLD) {
                    allXStarts.add(sorted.get(i).getX());
                }
                lastEnd = sorted.get(i).getX() + sorted.get(i).getWidth();
            }
        }

        if (allXStarts.isEmpty()) {
            return List.of();
        }

        // Cluster the X start positions to find consistent column boundaries
        allXStarts.sort(Float::compare);
        List<Float> boundaries = new ArrayList<>();
        float clusterStart = allXStarts.get(0);
        float clusterSum = clusterStart;
        int clusterCount = 1;

        for (int i = 1; i < allXStarts.size(); i++) {
            if (allXStarts.get(i) - (clusterSum / clusterCount) < COLUMN_GAP_THRESHOLD) {
                clusterSum += allXStarts.get(i);
                clusterCount++;
            } else {
                // Check if this cluster appears frequently enough (at least in 30% of rows)
                if (clusterCount >= Math.max(2, sortedYs.size() * 0.3)) {
                    boundaries.add(clusterSum / clusterCount);
                }
                clusterStart = allXStarts.get(i);
                clusterSum = clusterStart;
                clusterCount = 1;
            }
        }
        if (clusterCount >= Math.max(2, sortedYs.size() * 0.3)) {
            boundaries.add(clusterSum / clusterCount);
        }

        return boundaries;
    }

    private List<String> assignToCells(List<TextPosition> rowPositions, List<Float> columnBoundaries) {
        List<StringBuilder> cells = new ArrayList<>();
        for (int i = 0; i < columnBoundaries.size(); i++) {
            cells.add(new StringBuilder());
        }

        for (TextPosition tp : rowPositions) {
            int colIdx = findClosestColumn(tp.getX(), columnBoundaries);
            if (colIdx >= 0 && colIdx < cells.size()) {
                if (!cells.get(colIdx).isEmpty()) {
                    cells.get(colIdx).append(" ");
                }
                cells.get(colIdx).append(tp.getUnicode());
            }
        }

        return cells.stream().map(sb -> sb.toString().trim()).toList();
    }

    private int findClosestColumn(float x, List<Float> columnBoundaries) {
        int closest = 0;
        float minDist = Float.MAX_VALUE;
        for (int i = 0; i < columnBoundaries.size(); i++) {
            float dist = Math.abs(x - columnBoundaries.get(i));
            if (dist < minDist) {
                minDist = dist;
                closest = i;
            }
        }
        // Allow some tolerance - if x is way past the last column, assign to last
        if (x > columnBoundaries.get(columnBoundaries.size() - 1) + COLUMN_GAP_THRESHOLD * 3) {
            return columnBoundaries.size() - 1;
        }
        return closest;
    }

    private String convertToMarkdown(List<List<String>> tableRows) {
        if (tableRows.isEmpty()) return "";

        int numCols = tableRows.stream().mapToInt(List::size).max().orElse(0);

        StringBuilder sb = new StringBuilder();

        // Header row
        List<String> header = tableRows.get(0);
        sb.append("| ");
        for (int i = 0; i < numCols; i++) {
            sb.append(i < header.size() ? escapeMarkdown(header.get(i)) : "");
            sb.append(" | ");
        }
        sb.append("\n");

        // Separator
        sb.append("| ");
        for (int i = 0; i < numCols; i++) {
            sb.append("---");
            sb.append(" | ");
        }
        sb.append("\n");

        // Data rows
        for (int r = 1; r < tableRows.size(); r++) {
            List<String> row = tableRows.get(r);
            sb.append("| ");
            for (int i = 0; i < numCols; i++) {
                sb.append(i < row.size() ? escapeMarkdown(row.get(i)) : "");
                sb.append(" | ");
            }
            sb.append("\n");
        }

        return sb.toString().trim();
    }

    private String escapeMarkdown(String text) {
        if (text == null || text.isEmpty()) return "";
        return text.replace("|", "\\|").replace("\n", " ");
    }

    public record ExtractedTable(
            int pageNumber,
            String markdownTable,
            int rowCount,
            int colCount
    ) {}

    /**
     * 추출된 표를 페이지 텍스트에 병합한다.
     */
    public static List<DocumentTextExtractor.PageText> mergeTablesIntoPages(
            List<DocumentTextExtractor.PageText> pages, List<ExtractedTable> tables) {
        if (tables.isEmpty()) return pages;

        Map<Integer, List<ExtractedTable>> tablesByPage = tables.stream()
                .collect(Collectors.groupingBy(ExtractedTable::pageNumber));

        return pages.stream().map(page -> {
            List<ExtractedTable> pageTables = tablesByPage.getOrDefault(page.pageNumber(), List.of());
            if (pageTables.isEmpty()) return page;

            StringBuilder sb = new StringBuilder(page.text());
            for (ExtractedTable t : pageTables) {
                sb.append("\n\n[표]\n").append(t.markdownTable());
            }
            return new DocumentTextExtractor.PageText(
                    page.pageNumber(), sb.toString(), page.startOffset(), page.endOffset());
        }).toList();
    }
}
