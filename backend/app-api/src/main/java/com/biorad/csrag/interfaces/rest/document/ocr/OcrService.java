package com.biorad.csrag.interfaces.rest.document.ocr;

import java.nio.file.Path;

public interface OcrService {

    OcrResult extract(Path filePath);
}
