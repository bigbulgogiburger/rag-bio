package com.biorad.csrag.interfaces.rest.answer.orchestration;

import com.biorad.csrag.interfaces.rest.analysis.AnalysisService;
import com.biorad.csrag.interfaces.rest.analysis.AnalyzeResponse;
import com.biorad.csrag.interfaces.rest.analysis.EvidenceItem;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class DefaultStepsTest {

    @Test
    void defaultRetrieveStep_delegatesToAnalysisService() {
        AnalysisService analysisService = mock(AnalysisService.class);
        UUID inquiryId = UUID.randomUUID();
        List<EvidenceItem> expected = List.of(
                new EvidenceItem("c1", "d1", 0.9, "text", "INQUIRY", "file.pdf", 1, 2)
        );
        when(analysisService.retrieve(inquiryId, "question", 5)).thenReturn(expected);

        DefaultRetrieveStep step = new DefaultRetrieveStep(analysisService);
        List<EvidenceItem> result = step.execute(inquiryId, "question", 5);

        assertThat(result).isEqualTo(expected);
        verify(analysisService).retrieve(inquiryId, "question", 5);
    }

    @Test
    void defaultVerifyStep_delegatesToAnalysisService() {
        AnalysisService analysisService = mock(AnalysisService.class);
        UUID inquiryId = UUID.randomUUID();
        List<EvidenceItem> evidences = List.of();
        AnalyzeResponse expected = new AnalyzeResponse(
                inquiryId.toString(), "SUPPORTED", 0.85, "reason", List.of(), evidences, null
        );
        when(analysisService.verify(inquiryId, "question", evidences)).thenReturn(expected);

        DefaultVerifyStep step = new DefaultVerifyStep(analysisService);
        AnalyzeResponse result = step.execute(inquiryId, "question", evidences);

        assertThat(result).isEqualTo(expected);
        verify(analysisService).verify(inquiryId, "question", evidences);
    }
}
