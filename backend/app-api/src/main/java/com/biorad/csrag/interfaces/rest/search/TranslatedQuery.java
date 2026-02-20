package com.biorad.csrag.interfaces.rest.search;

public record TranslatedQuery(
        String original,
        String translated,
        boolean wasTranslated
) {}
