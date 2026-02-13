package com.biorad.csrag.communication;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;

import static org.assertj.core.api.Assertions.assertThat;

class CommunicationContextMarkerTest {

    @Test
    void markerClass_hasPrivateConstructor() throws Exception {
        Constructor<CommunicationContextMarker> constructor = CommunicationContextMarker.class.getDeclaredConstructor();

        assertThat(Modifier.isPrivate(constructor.getModifiers())).isTrue();

        constructor.setAccessible(true);
        CommunicationContextMarker instance = constructor.newInstance();
        assertThat(instance).isNotNull();
    }
}
