package com.biorad.csrag.interfaces.rest.search;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/product-families")
public class ProductFamilyController {

    private final ProductFamilyRegistry registry;

    public ProductFamilyController(ProductFamilyRegistry registry) {
        this.registry = registry;
    }

    @GetMapping
    public List<ProductFamilyRegistry.ProductFamilyInfo> list() {
        return registry.allFamilyInfos();
    }
}
