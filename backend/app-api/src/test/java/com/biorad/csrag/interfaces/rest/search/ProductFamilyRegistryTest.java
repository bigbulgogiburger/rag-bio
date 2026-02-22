package com.biorad.csrag.interfaces.rest.search;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ProductFamilyRegistryTest {

    private ProductFamilyRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new ProductFamilyRegistry();
    }

    @Test
    void allFamilies_returns10() {
        Set<String> families = registry.allFamilies();

        assertThat(families).hasSize(10);
        assertThat(families).contains("QX200", "QX600", "QXOne", "naica",
                "CFX96", "CFX384", "CFXOpus",
                "BioPlex2200", "BioPlex3D", "ChemiDoc");
    }

    @Test
    void categoryOf_naica_returnsDdpcr() {
        ProductFamilyRegistry.Category category = registry.categoryOf("naica");

        assertThat(category).isEqualTo(ProductFamilyRegistry.Category.ddPCR_Systems);
    }

    @Test
    void categoryOf_unknown_returnsNull() {
        assertThat(registry.categoryOf("unknown")).isNull();
    }

    @Test
    void relatedFamilies_naica_returnsDdpcrCategory() {
        Set<String> related = registry.relatedFamilies("naica");

        assertThat(related).containsExactlyInAnyOrder("QX200", "QX600", "QXOne", "naica");
    }

    @Test
    void relatedFamilies_CFX96_returnsRealTimePcr() {
        Set<String> related = registry.relatedFamilies("CFX96");

        assertThat(related).containsExactlyInAnyOrder("CFX96", "CFX384", "CFXOpus");
    }

    @Test
    void relatedFamilies_unknown_returnsEmpty() {
        Set<String> related = registry.relatedFamilies("unknown");

        assertThat(related).isEmpty();
    }

    @Test
    void expand_multipleInputs() {
        Set<String> expanded = registry.expand(Set.of("naica", "CFX96"));

        // naica -> ddPCR_Systems: {QX200, QX600, QXOne, naica}
        // CFX96 -> Real_Time_PCR: {CFX96, CFX384, CFXOpus}
        assertThat(expanded).containsExactlyInAnyOrder(
                "QX200", "QX600", "QXOne", "naica",
                "CFX96", "CFX384", "CFXOpus");
    }

    @Test
    void expand_nullInput_returnsEmpty() {
        assertThat(registry.expand(null)).isEmpty();
    }

    @Test
    void expand_emptyInput_returnsEmpty() {
        assertThat(registry.expand(Set.of())).isEmpty();
    }

    @Test
    void isValid_knownFamily_true() {
        assertThat(registry.isValid("naica")).isTrue();
        assertThat(registry.isValid("QX200")).isTrue();
        assertThat(registry.isValid("ChemiDoc")).isTrue();
    }

    @Test
    void isValid_unknown_false() {
        assertThat(registry.isValid("unknown")).isFalse();
    }

    @Test
    void allFamilyInfos_returnsCorrectSize() {
        var infos = registry.allFamilyInfos();

        assertThat(infos).hasSize(10);
        assertThat(infos.stream().map(ProductFamilyRegistry.ProductFamilyInfo::name).toList())
                .contains("naica", "QX200", "CFX96");
    }
}
