package com.smartinsurance.controller;

import com.smartinsurance.dto.ProductDtos.ProductRequest;
import com.smartinsurance.dto.ProductDtos.ProductResponse;
import com.smartinsurance.service.ProductService;
import com.smartinsurance.entity.AppUser;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/products")
public class ProductController {
    private final ProductService products;
    public ProductController(ProductService products) { this.products = products; }
    @GetMapping
    public List<ProductResponse> active() { return products.active(); }
    @GetMapping("/admin")
    @PreAuthorize("hasRole('ADMIN')")
    public List<ProductResponse> all() { return products.all(); }
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ProductResponse create(@AuthenticationPrincipal AppUser actor, @Valid @RequestBody ProductRequest request) {
        return products.create(actor, request);
    }
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ProductResponse update(@AuthenticationPrincipal AppUser actor, @PathVariable Long id,
                                  @Valid @RequestBody ProductRequest request) {
        return products.update(actor, id, request);
    }
    @PatchMapping("/{id}/availability")
    @PreAuthorize("hasRole('ADMIN')")
    public ProductResponse availability(@AuthenticationPrincipal AppUser actor, @PathVariable Long id, @RequestParam boolean active) {
        return products.setActive(actor, id, active);
    }
}
