package com.biorad.csrag.interfaces.rest.search;

import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class SearchFilterTest {

    @Test
    void forProducts_multipleValues() {
        UUID inquiryId = UUID.randomUUID();
        SearchFilter filter = SearchFilter.forProducts(inquiryId, Set.of("naica", "CFX96"));

        assertThat(filter.productFamilies()).containsExactlyInAnyOrder("naica", "CFX96");
        assertThat(filter.inquiryId()).isEqualTo(inquiryId);
        assertThat(filter.hasProductFilter()).isTrue();
    }

    @Test
    void hasProductFilter_emptySet_false() {
        SearchFilter filter = new SearchFilter(null, null, Set.of(), null);

        assertThat(filter.hasProductFilter()).isFalse();
    }

    @Test
    void hasProductFilter_nullSet_false() {
        SearchFilter filter = new SearchFilter(null, null, null, null);

        assertThat(filter.hasProductFilter()).isFalse();
    }

    @Test
    void forProduct_backwardCompatible() {
        UUID inquiryId = UUID.randomUUID();
        SearchFilter filter = SearchFilter.forProduct(inquiryId, "naica");

        assertThat(filter.productFamilies()).containsExactly("naica");
        assertThat(filter.hasProductFilter()).isTrue();
        assertThat(filter.inquiryId()).isEqualTo(inquiryId);
    }

    @Test
    void none_hasNoFilters() {
        SearchFilter filter = SearchFilter.none();

        assertThat(filter.isEmpty()).isTrue();
        assertThat(filter.hasProductFilter()).isFalse();
        assertThat(filter.hasDocumentFilter()).isFalse();
        assertThat(filter.hasSourceTypeFilter()).isFalse();
        assertThat(filter.inquiryId()).isNull();
    }

    @Test
    void forInquiry_hasOnlyInquiryId() {
        UUID inquiryId = UUID.randomUUID();
        SearchFilter filter = SearchFilter.forInquiry(inquiryId);

        assertThat(filter.inquiryId()).isEqualTo(inquiryId);
        assertThat(filter.hasProductFilter()).isFalse();
        assertThat(filter.hasDocumentFilter()).isFalse();
        assertThat(filter.isEmpty()).isFalse();
    }

    @Test
    void forDocuments_hasDocumentFilter() {
        UUID docId = UUID.randomUUID();
        SearchFilter filter = SearchFilter.forDocuments(Set.of(docId));

        assertThat(filter.hasDocumentFilter()).isTrue();
        assertThat(filter.documentIds()).containsExactly(docId);
        assertThat(filter.isEmpty()).isFalse();
    }

    @Test
    void hasSourceTypeFilter_withValues_true() {
        SearchFilter filter = new SearchFilter(null, null, null, Set.of("KNOWLEDGE_BASE"));

        assertThat(filter.hasSourceTypeFilter()).isTrue();
    }

    @Test
    void isEmpty_allFieldsPresent_false() {
        UUID inquiryId = UUID.randomUUID();
        SearchFilter filter = new SearchFilter(
                inquiryId, Set.of(UUID.randomUUID()), Set.of("naica"), Set.of("INQUIRY"));

        assertThat(filter.isEmpty()).isFalse();
    }
}
