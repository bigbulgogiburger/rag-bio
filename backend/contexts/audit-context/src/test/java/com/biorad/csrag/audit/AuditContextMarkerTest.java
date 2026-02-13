package com.biorad.csrag.audit;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;

import static org.assertj.core.api.Assertions.assertThat;

class AuditContextMarkerTest {

    @Test
    void markerClass_hasPrivateConstructor() throws Exception {
        Constructor<AuditContextMarker> constructor = AuditContextMarker.class.getDeclaredConstructor();

        assertThat(Modifier.isPrivate(constructor.getModifiers())).isTrue();

        constructor.setAccessible(true);
        AuditContextMarker instance = constructor.newInstance();
        assertThat(instance).isNotNull();
    }
}
