package com.biorad.csrag.interfaces.rest.search;

import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 제품군 레지스트리. 제품 패밀리와 카테고리 매핑을 관리한다.
 */
@Component
public class ProductFamilyRegistry {

    public enum Category {
        ddPCR_Systems,
        Real_Time_PCR,
        Multiplex_Immunoassay,
        Imaging,
        Chromatography,
        Electrophoresis
    }

    public record ProductFamilyInfo(String name, Category category) {}

    private static final Map<String, Category> FAMILY_TO_CATEGORY;
    private static final Map<Category, Set<String>> CATEGORY_TO_FAMILIES;

    static {
        Map<String, Category> map = new LinkedHashMap<>();
        map.put("QX200", Category.ddPCR_Systems);
        map.put("QX600", Category.ddPCR_Systems);
        map.put("QXOne", Category.ddPCR_Systems);
        map.put("naica", Category.ddPCR_Systems);
        map.put("CFX96", Category.Real_Time_PCR);
        map.put("CFX384", Category.Real_Time_PCR);
        map.put("CFXOpus", Category.Real_Time_PCR);
        map.put("BioPlex2200", Category.Multiplex_Immunoassay);
        map.put("BioPlex3D", Category.Multiplex_Immunoassay);
        map.put("ChemiDoc", Category.Imaging);
        FAMILY_TO_CATEGORY = Collections.unmodifiableMap(map);

        Map<Category, Set<String>> catMap = new EnumMap<>(Category.class);
        for (Map.Entry<String, Category> entry : FAMILY_TO_CATEGORY.entrySet()) {
            catMap.computeIfAbsent(entry.getValue(), k -> new LinkedHashSet<>()).add(entry.getKey());
        }
        // Chromatography, Electrophoresis have no families yet
        catMap.putIfAbsent(Category.Chromatography, Set.of());
        catMap.putIfAbsent(Category.Electrophoresis, Set.of());
        CATEGORY_TO_FAMILIES = Collections.unmodifiableMap(catMap);
    }

    /** 등록된 모든 제품군 이름 */
    public Set<String> allFamilies() {
        return FAMILY_TO_CATEGORY.keySet();
    }

    /** 제품군이 속한 카테고리 반환 (미등록 시 null) */
    public Category categoryOf(String family) {
        return FAMILY_TO_CATEGORY.get(family);
    }

    /** 같은 카테고리의 모든 제품군 반환 (자기 자신 포함) */
    public Set<String> relatedFamilies(String family) {
        Category cat = FAMILY_TO_CATEGORY.get(family);
        if (cat == null) {
            return Set.of();
        }
        return CATEGORY_TO_FAMILIES.getOrDefault(cat, Set.of());
    }

    /** 입력 제품들의 카테고리를 확장하여 같은 카테고리 제품 모두 포함 */
    public Set<String> expand(Set<String> families) {
        if (families == null || families.isEmpty()) {
            return Set.of();
        }
        return families.stream()
                .flatMap(f -> relatedFamilies(f).stream())
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    /** 제품군 유효성 검사 */
    public boolean isValid(String family) {
        return FAMILY_TO_CATEGORY.containsKey(family);
    }

    /** 모든 제품군 정보 목록 (API 응답용) */
    public List<ProductFamilyInfo> allFamilyInfos() {
        return FAMILY_TO_CATEGORY.entrySet().stream()
                .map(e -> new ProductFamilyInfo(e.getKey(), e.getValue()))
                .toList();
    }
}
