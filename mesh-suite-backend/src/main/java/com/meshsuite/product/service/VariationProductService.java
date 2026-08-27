package com.meshsuite.product.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.meshsuite.auth.annotation.RequiresPermission;
import com.meshsuite.auth.domain.enums.Action;
import com.meshsuite.auth.domain.enums.Module;
import com.meshsuite.brand.exception.BrandNotFoundException;
import com.meshsuite.brand.repository.BrandRepository;
import com.meshsuite.category.exception.CategoryNotFoundException;
import com.meshsuite.category.repository.CategoryRepository;
import com.meshsuite.colorway.exception.ColorwayNotFoundException;
import com.meshsuite.colorway.repository.ColorwayRepository;
import com.meshsuite.product.domain.Product;
import com.meshsuite.product.domain.enums.ProductStatus;
import com.meshsuite.product.domain.enums.ProductType;
import com.meshsuite.product.dto.VariationAxisInput;
import com.meshsuite.product.dto.VariationAxisResponse;
import com.meshsuite.product.dto.VariationChildInput;
import com.meshsuite.product.dto.VariationChildResponse;
import com.meshsuite.product.dto.VariationParentRequest;
import com.meshsuite.product.dto.VariationParentResponse;
import com.meshsuite.product.exception.DuplicateSkuException;
import com.meshsuite.product.exception.ProductValidationException;
import com.meshsuite.product.repository.ProductRepository;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@link ProductTypeStrategy} for {@link ProductType#VARIATION_PARENT} -- MVP scope:
 * the client sends the parent plus the final, already-priced list of children (own
 * SKU/price/stock/size/colorway each). The wireframe's dynamic Tamanho×Cor combinator
 * that auto-generates children isn't built here (it lives entirely on the frontend,
 * driven by variationAxes below); this service just persists whatever the client sends.
 *
 * Barcode and costPrice are deliberately absent from the parent DTO -- the wireframe
 * shows them locked ("por variante") at the parent level, defined per child instead.
 */
@Service
public class VariationProductService extends AbstractProductTypeService {

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final ColorwayRepository colorwayRepository;
    private final BrandRepository brandRepository;
    private final ObjectMapper objectMapper;

    public VariationProductService(ProductRepository productRepository, CategoryRepository categoryRepository,
                                    ColorwayRepository colorwayRepository, BrandRepository brandRepository,
                                    ObjectMapper objectMapper) {
        super(productRepository);
        this.productRepository = productRepository;
        this.categoryRepository = categoryRepository;
        this.colorwayRepository = colorwayRepository;
        this.brandRepository = brandRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    public ProductType type() {
        return ProductType.VARIATION_PARENT;
    }

    @Transactional(readOnly = true)
    @RequiresPermission(module = Module.PRODUCT, action = Action.VIEW)
    public VariationParentResponse findById(UUID id) {
        Product parent = findEntityByType(id);
        return toResponse(parent, productRepository.findByParentProductIdOrderByCreatedAtAscIdAsc(id));
    }

    @Transactional
    @RequiresPermission(module = Module.PRODUCT, action = Action.CREATE)
    public VariationParentResponse create(UUID tenantId, VariationParentRequest request) {
        validateSku(request.sku(), null);

        Product parent = new Product();
        parent.setTenantId(tenantId);
        parent.setType(ProductType.VARIATION_PARENT);
        applyParent(parent, request);
        productRepository.saveAndFlush(parent);

        List<Product> children = createChildren(parent, request.children());
        return toResponse(parent, children);
    }

    @Transactional
    @RequiresPermission(module = Module.PRODUCT, action = Action.EDIT)
    public VariationParentResponse update(UUID id, VariationParentRequest request) {
        validateSku(request.sku(), id);

        Product parent = findEntityByType(id);
        applyParent(parent, request);

        // Wholesale replace, same convention as Kit/PriceTable items: the Tipos de
        // Variação matrix is the single source of truth for a Variação's composition,
        // so any edit to it (add/remove a value or axis) can reshape the whole children
        // set -- delete every existing child and recreate from the request rather than
        // trying to merge by id. flush() first so the deletes actually reach Postgres
        // before the inserts below reuse the same SKUs.
        productRepository.deleteAll(productRepository.findByParentProductIdOrderByCreatedAtAscIdAsc(id));
        productRepository.flush();

        List<Product> children = createChildren(parent, request.children());
        return toResponse(parent, children);
    }

    private List<Product> createChildren(Product parent, List<VariationChildInput> childInputs) {
        List<Product> children = new ArrayList<>();
        Set<String> skusInRequest = new HashSet<>();
        for (VariationChildInput childInput : childInputs) {
            requireSkuNotRepeatedInRequest(skusInRequest, childInput.sku());
            validateSku(childInput.sku(), null);

            Product child = new Product();
            child.setTenantId(parent.getTenantId());
            child.setType(ProductType.VARIATION_CHILD);
            child.setParentProduct(parent);
            applyChild(child, childInput);
            children.add(productRepository.saveAndFlush(child));
        }
        return children;
    }

    private void requireSkuNotRepeatedInRequest(Set<String> skusInRequest, String sku) {
        if (!skusInRequest.add(sku)) {
            throw new ProductValidationException("SKU \"" + sku + "\" repetido entre as variantes");
        }
    }

    private void validateSku(String sku, UUID currentId) {
        boolean duplicate = currentId == null
                ? productRepository.existsBySku(sku)
                : productRepository.existsBySkuAndIdNot(sku, currentId);
        if (duplicate) {
            throw new DuplicateSkuException();
        }
    }

    private void applyParent(Product parent, VariationParentRequest request) {
        parent.setName(request.name());
        parent.setSku(request.sku());
        parent.setBrand(request.brandId() != null
                ? brandRepository.findById(request.brandId()).orElseThrow(BrandNotFoundException::new)
                : null);
        parent.setCategory(request.categoryId() != null
                ? categoryRepository.findById(request.categoryId()).orElseThrow(CategoryNotFoundException::new)
                : null);
        parent.setSalePrice(request.salePrice());
        parent.setStatus(request.status() != null ? request.status() : ProductStatus.ACTIVE);
        parent.setDescription(request.description());
        parent.setMeasurementUnit(request.measurementUnit() != null ? request.measurementUnit() : parent.getMeasurementUnit());
        parent.setSaleMultiple(request.saleMultiple() != null ? request.saleMultiple() : java.math.BigDecimal.ONE);
        parent.setVariationAxesJson(serializeAxes(request.variationAxes()));
    }

    private String serializeAxes(List<VariationAxisInput> axes) {
        if (axes == null || axes.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(axes);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Falha ao serializar os tipos de variação", e);
        }
    }

    private List<VariationAxisResponse> deserializeAxes(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<VariationAxisResponse>>() {
            });
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Falha ao interpretar os tipos de variação salvos", e);
        }
    }

    private String serializeValues(List<String> values) {
        if (values == null || values.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(values);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Falha ao serializar a combinação da variante", e);
        }
    }

    private List<String> deserializeValues(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {
            });
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Falha ao interpretar a combinação salva da variante", e);
        }
    }

    private void applyChild(Product child, VariationChildInput input) {
        child.setSku(input.sku());
        child.setBarcode(input.barcode());
        child.setSalePrice(input.salePrice());
        child.setCostPrice(input.costPrice());
        child.setStockQuantity(input.stockQuantity() != null ? input.stockQuantity() : java.math.BigDecimal.ZERO);
        child.setMinStock(input.minStock());
        child.setMaxStock(input.maxStock());
        child.setSize(input.size());
        child.setSaleMultiple(input.saleMultiple() != null ? input.saleMultiple() : java.math.BigDecimal.ONE);
        child.setVariationValuesJson(serializeValues(input.variationValues()));
        child.setColorway(input.colorwayId() != null
                ? colorwayRepository.findById(input.colorwayId()).orElseThrow(ColorwayNotFoundException::new)
                : null);
        // Children inherit name/status/measurementUnit conceptually from the parent --
        // the wireframe shows no separate name/status field per variant row.
        child.setName(child.getParentProduct().getName());
        child.setStatus(child.getParentProduct().getStatus());
        child.setMeasurementUnit(child.getParentProduct().getMeasurementUnit());
    }

    private VariationParentResponse toResponse(Product parent, List<Product> children) {
        List<VariationChildResponse> childResponses = children.stream()
                .map(c -> new VariationChildResponse(c.getId(), c.getSku(), c.getBarcode(), c.getSalePrice(),
                        c.getCostPrice(), c.getStockQuantity(), c.getMinStock(), c.getMaxStock(), c.getSize(),
                        c.getColorway() != null ? c.getColorway().getId() : null,
                        c.getColorway() != null ? c.getColorway().getName() : null, c.getSaleMultiple(),
                        deserializeValues(c.getVariationValuesJson())))
                .toList();
        return new VariationParentResponse(parent.getId(), parent.getName(), parent.getSku(),
                parent.getBrand() != null ? parent.getBrand().getId() : null,
                parent.getBrand() != null ? parent.getBrand().getName() : null,
                parent.getCategory() != null ? parent.getCategory().getId() : null,
                parent.getCategory() != null ? parent.getCategory().getName() : null,
                parent.getSalePrice(), parent.getStatus(), parent.getDescription(), parent.getMeasurementUnit(),
                childResponses, parent.getSaleMultiple(), deserializeAxes(parent.getVariationAxesJson()));
    }
}
