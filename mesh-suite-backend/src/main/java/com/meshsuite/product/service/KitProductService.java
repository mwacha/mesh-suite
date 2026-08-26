package com.meshsuite.product.service;

import com.meshsuite.auth.annotation.RequiresPermission;
import com.meshsuite.auth.domain.enums.Action;
import com.meshsuite.auth.domain.enums.Module;
import com.meshsuite.product.domain.Product;
import com.meshsuite.product.domain.ProductKitItem;
import com.meshsuite.product.domain.enums.ProductStatus;
import com.meshsuite.product.domain.enums.ProductType;
import com.meshsuite.product.dto.KitItemInput;
import com.meshsuite.product.dto.KitItemResponse;
import com.meshsuite.product.dto.KitProductRequest;
import com.meshsuite.product.dto.KitProductResponse;
import com.meshsuite.product.exception.DuplicateSkuException;
import com.meshsuite.product.exception.ProductValidationException;
import com.meshsuite.product.repository.ProductRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@link ProductTypeStrategy} for {@link ProductType#PRODUCT_KIT} -- no price or
 * stock of its own. A kit is a list of component products with quantities; its
 * salePrice is always Σ(quantity × component.salePrice), never entered directly
 * (see wireframe: "Valor de Venda do Kit ... calculado automaticamente ... bloqueado").
 */
@Service
public class KitProductService extends AbstractProductTypeService {

    private final ProductRepository productRepository;

    public KitProductService(ProductRepository productRepository) {
        super(productRepository);
        this.productRepository = productRepository;
    }

    @Override
    public ProductType type() {
        return ProductType.PRODUCT_KIT;
    }

    @Transactional(readOnly = true)
    @RequiresPermission(module = Module.PRODUCT, action = Action.VIEW)
    public KitProductResponse findById(UUID id) {
        return toResponse(findEntityByType(id));
    }

    @Transactional
    @RequiresPermission(module = Module.PRODUCT, action = Action.CREATE)
    public KitProductResponse create(UUID tenantId, KitProductRequest request) {
        validateSku(request.sku(), null);

        Product kit = new Product();
        kit.setTenantId(tenantId);
        kit.setType(ProductType.PRODUCT_KIT);
        apply(kit, request);
        return toResponse(productRepository.saveAndFlush(kit));
    }

    @Transactional
    @RequiresPermission(module = Module.PRODUCT, action = Action.EDIT)
    public KitProductResponse update(UUID id, KitProductRequest request) {
        validateSku(request.sku(), id);

        Product kit = findEntityByType(id);
        apply(kit, request);
        return toResponse(productRepository.saveAndFlush(kit));
    }

    private void validateSku(String sku, UUID currentId) {
        boolean duplicate = currentId == null
                ? productRepository.existsBySku(sku)
                : productRepository.existsBySkuAndIdNot(sku, currentId);
        if (duplicate) {
            throw new DuplicateSkuException();
        }
    }

    private void apply(Product kit, KitProductRequest request) {
        kit.setName(request.name());
        kit.setSku(request.sku());
        kit.setBarcode(request.barcode());
        kit.setMeasurementUnit(request.measurementUnit() != null ? request.measurementUnit() : kit.getMeasurementUnit());
        kit.setStatus(request.status() != null ? request.status() : ProductStatus.ACTIVE);
        kit.setDescription(request.description());

        // Regenerate the whole item list on every save -- same convention
        // PriceTableService.apply() uses for its own item collection.
        kit.getKitItems().clear();
        BigDecimal total = BigDecimal.ZERO;
        for (KitItemInput itemInput : request.items()) {
            Product component = productRepository.findById(itemInput.componentProductId())
                    .orElseThrow(() -> new ProductValidationException("Produto componente não encontrado"));
            if (component.getType() == ProductType.PRODUCT_KIT || component.getType() == ProductType.VARIATION_PARENT) {
                throw new ProductValidationException(
                        "Componente \"" + component.getName() + "\" não pode ser um kit ou uma variação-pai");
            }
            ProductKitItem item = new ProductKitItem();
            item.setKitProduct(kit);
            item.setComponentProduct(component);
            item.setQuantity(itemInput.quantity());
            kit.getKitItems().add(item);
            total = total.add(component.getSalePrice().multiply(itemInput.quantity()));
        }
        kit.setSalePrice(total);
    }

    private KitProductResponse toResponse(Product kit) {
        List<KitItemResponse> items = kit.getKitItems().stream()
                .map(i -> new KitItemResponse(i.getComponentProduct().getId(), i.getComponentProduct().getName(),
                        i.getComponentProduct().getSku(), i.getQuantity(), i.getComponentProduct().getSalePrice(),
                        i.getComponentProduct().getSalePrice().multiply(i.getQuantity())))
                .toList();
        return new KitProductResponse(kit.getId(), kit.getName(), kit.getSku(), kit.getBarcode(),
                kit.getMeasurementUnit(), kit.getStatus(), kit.getDescription(), items, kit.getSalePrice());
    }
}
