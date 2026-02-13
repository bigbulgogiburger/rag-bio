package com.biorad.csrag.interfaces.rest.answer.orchestration;

import com.biorad.csrag.interfaces.rest.analysis.AnalysisService;
import com.biorad.csrag.interfaces.rest.analysis.EvidenceItem;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Component
public class DefaultRetrieveStep implements RetrieveStep {

    private final AnalysisService analysisService;

    public DefaultRetrieveStep(AnalysisService analysisService) {
        this.analysisService = analysisService;
    }

    @Override
    public List<EvidenceItem> execute(UUID inquiryId, String question, int topK) {
        return analysisService.retrieve(inquiryId, question, topK);
    }
}
