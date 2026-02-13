package com.biorad.csrag.response;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;

import static org.assertj.core.api.Assertions.assertThat;

class ResponseCompositionContextMarkerTest {

    @Test
    void markerClass_hasPrivateConstructor() throws Exception {
        Constructor<ResponseCompositionContextMarker> constructor = ResponseCompositionContextMarker.class.getDeclaredConstructor();

        assertThat(Modifier.isPrivate(constructor.getModifiers())).isTrue();

        constructor.setAccessible(true);
        ResponseCompositionContextMarker instance = constructor.newInstance();
        assertThat(instance).isNotNull();
    }
}
