package com.biorad.csrag.ingestion;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;

import static org.assertj.core.api.Assertions.assertThat;

class IngestionContextMarkerTest {

    @Test
    void markerClass_hasPrivateConstructor() throws Exception {
        Constructor<IngestionContextMarker> constructor = IngestionContextMarker.class.getDeclaredConstructor();

        assertThat(Modifier.isPrivate(constructor.getModifiers())).isTrue();

        constructor.setAccessible(true);
        IngestionContextMarker instance = constructor.newInstance();
        assertThat(instance).isNotNull();
    }
}
