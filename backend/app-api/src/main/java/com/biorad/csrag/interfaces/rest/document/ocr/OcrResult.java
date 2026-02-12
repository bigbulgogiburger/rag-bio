package com.biorad.csrag.interfaces.rest.document.ocr;

public record OcrResult(
        String text,
        double confidence
) {
}
