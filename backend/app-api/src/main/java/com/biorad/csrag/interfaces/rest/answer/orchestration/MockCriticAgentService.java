package com.biorad.csrag.interfaces.rest.answer.orchestration;

import com.biorad.csrag.interfaces.rest.analysis.EvidenceItem;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@ConditionalOnMissingBean(OpenAiCriticAgentService.class)
public class MockCriticAgentService implements CriticAgentService {

    @Override
    public CriticResult critique(String draft, String question, List<EvidenceItem> evidences) {
        return CriticResult.passing(1.0);
    }
}
