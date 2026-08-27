package com.meshsuite.brand.service;

import com.meshsuite.auth.annotation.RequiresPermission;
import com.meshsuite.auth.domain.enums.Action;
import com.meshsuite.auth.domain.enums.Module;
import com.meshsuite.brand.domain.Brand;
import com.meshsuite.brand.dto.BrandCountsResponse;
import com.meshsuite.brand.dto.BrandRequest;
import com.meshsuite.brand.dto.BrandResponse;
import com.meshsuite.brand.exception.BrandInUseException;
import com.meshsuite.brand.exception.BrandNotFoundException;
import com.meshsuite.brand.exception.DuplicateBrandNameException;
import com.meshsuite.brand.repository.BrandRepository;
import com.meshsuite.brand.repository.specification.BrandSpecifications;
import com.meshsuite.product.repository.ProductRepository;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BrandService {

    private final BrandRepository brandRepository;
    private final ProductRepository produtoRepository;

    public BrandService(BrandRepository brandRepository, ProductRepository produtoRepository) {
        this.brandRepository = brandRepository;
        this.produtoRepository = produtoRepository;
    }

    @Transactional(readOnly = true)
    @RequiresPermission(module = Module.PRODUCT, action = Action.VIEW)
    public Page<BrandResponse> list(String search, Boolean active, Pageable pageable) {
        Specification<Brand> spec = Specification.allOf(
                BrandSpecifications.withSearch(search),
                BrandSpecifications.withActive(active));
        Page<Brand> page = brandRepository.findAll(spec, pageable);

        List<UUID> ids = page.getContent().stream().map(Brand::getId).toList();
        Map<UUID, Long> counts = ids.isEmpty()
                ? Map.of()
                : produtoRepository.countByBrandIdIn(ids).stream()
                        .collect(Collectors.toMap(
                                ProductRepository.BrandProductCount::getBrandId,
                                ProductRepository.BrandProductCount::getTotal));

        return page.map(brand -> toResponse(brand, counts.getOrDefault(brand.getId(), 0L)));
    }

    @Transactional(readOnly = true)
    @RequiresPermission(module = Module.PRODUCT, action = Action.VIEW)
    public BrandCountsResponse counts() {
        long active = brandRepository.countByActive(true);
        long inactive = brandRepository.countByActive(false);
        return new BrandCountsResponse(active + inactive, active, inactive);
    }

    @Transactional(readOnly = true)
    @RequiresPermission(module = Module.PRODUCT, action = Action.VIEW)
    public BrandResponse findById(UUID id) {
        return toResponse(findEntityById(id));
    }

    @Transactional
    @RequiresPermission(module = Module.PRODUCT, action = Action.CREATE)
    public BrandResponse create(UUID tenantId, BrandRequest request) {
        validateName(request.name(), null);

        Brand brand = new Brand();
        brand.setTenantId(tenantId);
        apply(brand, request);
        return toResponse(brandRepository.saveAndFlush(brand));
    }

    @Transactional
    @RequiresPermission(module = Module.PRODUCT, action = Action.EDIT)
    public BrandResponse update(UUID id, BrandRequest request) {
        validateName(request.name(), id);

        Brand brand = findEntityById(id);
        apply(brand, request);
        return toResponse(brandRepository.saveAndFlush(brand));
    }

    @Transactional
    @RequiresPermission(module = Module.PRODUCT, action = Action.DELETE)
    public void delete(UUID id) {
        Brand brand = findEntityById(id);
        long linked = produtoRepository.countByBrandId(id);
        if (linked > 0) {
            throw new BrandInUseException(linked);
        }
        brandRepository.delete(brand);
    }

    private Brand findEntityById(UUID id) {
        return brandRepository.findById(id).orElseThrow(BrandNotFoundException::new);
    }

    private void validateName(String name, UUID currentId) {
        boolean duplicate = currentId == null
                ? brandRepository.existsByName(name)
                : brandRepository.existsByNameAndIdNot(name, currentId);
        if (duplicate) {
            throw new DuplicateBrandNameException();
        }
    }

    private void apply(Brand brand, BrandRequest request) {
        brand.setName(request.name());
        brand.setActive(request.active() != null ? request.active() : true);
    }

    private BrandResponse toResponse(Brand brand) {
        return toResponse(brand, produtoRepository.countByBrandId(brand.getId()));
    }

    private BrandResponse toResponse(Brand brand, long linkedProducts) {
        return new BrandResponse(
                brand.getId(), brand.getName(), brand.getActive(), linkedProducts, brand.getCreatedAt());
    }
}
