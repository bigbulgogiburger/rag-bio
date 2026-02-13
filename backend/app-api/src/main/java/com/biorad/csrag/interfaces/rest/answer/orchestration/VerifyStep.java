package com.biorad.csrag.interfaces.rest.answer.orchestration;

import com.biorad.csrag.interfaces.rest.analysis.AnalyzeResponse;
import com.biorad.csrag.interfaces.rest.analysis.EvidenceItem;

import java.util.List;
import java.util.UUID;

public interface VerifyStep {
    AnalyzeResponse execute(UUID inquiryId, String question, List<EvidenceItem> evidences);
}
