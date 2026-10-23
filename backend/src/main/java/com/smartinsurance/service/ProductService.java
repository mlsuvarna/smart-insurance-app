package com.smartinsurance.service;

import com.smartinsurance.dto.ProductDtos.ProductRequest;
import com.smartinsurance.dto.ProductDtos.ProductResponse;
import com.smartinsurance.entity.InsuranceProduct;
import com.smartinsurance.entity.AppUser;
import com.smartinsurance.repository.ProductRepository;
import com.smartinsurance.exception.ResourceNotFoundException;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProductService {
    private final ProductRepository products;
    private final AuditLogService audit;
    public ProductService(ProductRepository products, AuditLogService audit) { this.products = products; this.audit = audit; }
    @Transactional(readOnly = true)
    public List<ProductResponse> active() { return products.findByActiveTrue().stream().map(this::toResponse).toList(); }
    @Transactional(readOnly = true)
    public List<ProductResponse> all() { return products.findAll().stream().map(this::toResponse).toList(); }
    @Transactional
    public ProductResponse create(AppUser actor, ProductRequest request) {
        InsuranceProduct product = products.save(new InsuranceProduct(request.name(), request.category(), request.description(),
                request.annualPremium(), request.coverageLimit()));
        audit.record(actor, "PRODUCT_CREATED", "PRODUCT", product.getId().toString());
        return toResponse(product);
    }
    @Transactional
    public ProductResponse update(AppUser actor, Long id, ProductRequest request) {
        InsuranceProduct product = products.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found"));
        product.update(request.name().trim(), request.category().trim(), request.description().trim(),
                request.annualPremium(), request.coverageLimit());
        audit.record(actor, "PRODUCT_UPDATED", "PRODUCT", product.getId().toString());
        return toResponse(product);
    }
    @Transactional
    public ProductResponse setActive(AppUser actor, Long id, boolean active) {
        InsuranceProduct product = products.findById(id).orElseThrow(() ->
                new com.smartinsurance.exception.ResourceNotFoundException("Product not found"));
        product.setActive(active);
        audit.record(actor, active ? "PRODUCT_ACTIVATED" : "PRODUCT_DEACTIVATED", "PRODUCT", product.getId().toString());
        return toResponse(product);
    }
    private ProductResponse toResponse(InsuranceProduct p) {
        return new ProductResponse(p.getId(), p.getName(), p.getCategory(), p.getDescription(),
                p.getAnnualPremium(), p.getCoverageLimit(), p.isActive());
    }
}
