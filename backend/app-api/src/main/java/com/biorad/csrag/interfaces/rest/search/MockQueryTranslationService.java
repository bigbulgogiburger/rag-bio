package com.biorad.csrag.interfaces.rest.search;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class MockQueryTranslationService implements QueryTranslationService {

    private static final Logger log = LoggerFactory.getLogger(MockQueryTranslationService.class);

    @Override
    public TranslatedQuery translate(String question) {
        log.info("mock.query.translation: returning original text");
        return new TranslatedQuery(question, question, false);
    }
}
