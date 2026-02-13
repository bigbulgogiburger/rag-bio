package com.biorad.csrag.interfaces.rest.answer.orchestration;

import com.biorad.csrag.interfaces.rest.analysis.EvidenceItem;

import java.util.List;
import java.util.UUID;

public interface RetrieveStep {
    List<EvidenceItem> execute(UUID inquiryId, String question, int topK);
}
