package com.biorad.csrag.interfaces.rest.answer.orchestration;

import com.biorad.csrag.interfaces.rest.analysis.AnalysisService;
import com.biorad.csrag.interfaces.rest.analysis.AnalyzeResponse;
import com.biorad.csrag.interfaces.rest.analysis.EvidenceItem;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Component
@Primary
public class DefaultVerifyStep implements VerifyStep {

    private final AnalysisService analysisService;

    public DefaultVerifyStep(AnalysisService analysisService) {
        this.analysisService = analysisService;
    }

    @Override
    public AnalyzeResponse execute(UUID inquiryId, String question, List<EvidenceItem> evidences) {
        return analysisService.verify(inquiryId, question, evidences);
    }
}
