package com.biorad.csrag.interfaces.rest.search;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@ConditionalOnMissingBean(OpenAiMultiHopRetriever.class)
public class MockMultiHopRetriever implements MultiHopRetriever {

    private final AdaptiveRetrievalAgent adaptiveAgent;

    public MockMultiHopRetriever(AdaptiveRetrievalAgent adaptiveAgent) {
        this.adaptiveAgent = adaptiveAgent;
    }

    @Override
    public MultiHopResult retrieve(String question, UUID inquiryId) {
        // Mock: 단순히 AdaptiveAgent 1회 호출
        AdaptiveRetrievalAgent.AdaptiveResult result = adaptiveAgent.retrieve(question, "", inquiryId);
        return MultiHopResult.singleHop(result.evidences());
    }
}
