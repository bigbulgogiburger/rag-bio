package com.biorad.csrag.knowledge;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;

import static org.assertj.core.api.Assertions.assertThat;

class KnowledgeRetrievalContextMarkerTest {

    @Test
    void markerClass_hasPrivateConstructor() throws Exception {
        Constructor<KnowledgeRetrievalContextMarker> constructor = KnowledgeRetrievalContextMarker.class.getDeclaredConstructor();

        assertThat(Modifier.isPrivate(constructor.getModifiers())).isTrue();

        constructor.setAccessible(true);
        KnowledgeRetrievalContextMarker instance = constructor.newInstance();
        assertThat(instance).isNotNull();
    }
}
