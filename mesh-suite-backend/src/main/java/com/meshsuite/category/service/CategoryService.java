package com.meshsuite.category.service;

import com.meshsuite.auth.annotation.RequiresPermission;
import com.meshsuite.auth.domain.enums.Action;
import com.meshsuite.auth.domain.enums.Module;
import com.meshsuite.category.domain.Category;
import com.meshsuite.category.dto.CategoryCountsResponse;
import com.meshsuite.category.dto.CategoryRequest;
import com.meshsuite.category.dto.CategoryResponse;
import com.meshsuite.category.exception.CategoryInUseException;
import com.meshsuite.category.exception.CategoryNotFoundException;
import com.meshsuite.category.exception.CategoryValidationException;
import com.meshsuite.category.exception.DuplicateCategoryNameException;
import com.meshsuite.category.repository.CategoryRepository;
import com.meshsuite.category.repository.specification.CategorySpecifications;
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
public class CategoryService {

    private final CategoryRepository categoryRepository;
    private final ProductRepository produtoRepository;

    public CategoryService(CategoryRepository categoryRepository, ProductRepository produtoRepository) {
        this.categoryRepository = categoryRepository;
        this.produtoRepository = produtoRepository;
    }

    @Transactional(readOnly = true)
    @RequiresPermission(module = Module.PRODUCT, action = Action.VIEW)
    public Page<CategoryResponse> list(String search, Boolean active, Boolean raiz, Pageable pageable) {
        Specification<Category> spec = Specification.allOf(
                CategorySpecifications.withSearch(search),
                CategorySpecifications.withActive(active),
                CategorySpecifications.onlyRoot(raiz));
        Page<Category> page = categoryRepository.findAll(spec, pageable);

        List<UUID> ids = page.getContent().stream().map(Category::getId).toList();
        Map<UUID, Long> counts = ids.isEmpty()
                ? Map.of()
                : produtoRepository.countByCategoryIdIn(ids).stream()
                        .collect(Collectors.toMap(
                                ProductRepository.CategoryProductCount::getCategoryId,
                                ProductRepository.CategoryProductCount::getTotal));

        return page.map(category -> toResponse(category, counts.getOrDefault(category.getId(), 0L)));
    }

    @Transactional(readOnly = true)
    @RequiresPermission(module = Module.PRODUCT, action = Action.VIEW)
    public CategoryCountsResponse counts() {
        long active = categoryRepository.countByActive(true);
        long inactive = categoryRepository.countByActive(false);
        return new CategoryCountsResponse(active + inactive, active, inactive);
    }

    @Transactional(readOnly = true)
    @RequiresPermission(module = Module.PRODUCT, action = Action.VIEW)
    public CategoryResponse findById(UUID id) {
        return toResponse(findEntityById(id));
    }

    @Transactional
    @RequiresPermission(module = Module.PRODUCT, action = Action.CREATE)
    public CategoryResponse create(UUID tenantId, CategoryRequest request) {
        validateName(request.name(), null);

        Category category = new Category();
        category.setTenantId(tenantId);
        apply(category, request);
        return toResponse(categoryRepository.saveAndFlush(category));
    }

    @Transactional
    @RequiresPermission(module = Module.PRODUCT, action = Action.EDIT)
    public CategoryResponse update(UUID id, CategoryRequest request) {
        validateName(request.name(), id);

        Category category = findEntityById(id);
        apply(category, request);
        return toResponse(categoryRepository.saveAndFlush(category));
    }

    @Transactional
    @RequiresPermission(module = Module.PRODUCT, action = Action.DELETE)
    public void delete(UUID id) {
        Category category = findEntityById(id);
        if (categoryRepository.existsByParentId(id)) {
            throw new CategoryValidationException("Não é possível excluir: esta categoria possui subcategorias vinculadas");
        }
        long linked = produtoRepository.countByCategoryId(id);
        if (linked > 0) {
            throw new CategoryInUseException(linked);
        }
        categoryRepository.delete(category);
    }

    private Category findEntityById(UUID id) {
        return categoryRepository.findById(id).orElseThrow(CategoryNotFoundException::new);
    }

    private void validateName(String name, UUID currentId) {
        boolean duplicate = currentId == null
                ? categoryRepository.existsByName(name)
                : categoryRepository.existsByNameAndIdNot(name, currentId);
        if (duplicate) {
            throw new DuplicateCategoryNameException();
        }
    }

    private void apply(Category category, CategoryRequest request) {
        category.setName(request.name());
        category.setDescription(request.description());
        category.setActive(request.active() != null ? request.active() : true);
        applyParent(category, request.parentId());
    }

    // Only root categories (parent == null) may be picked as a parent, keeping the
    // hierarchy at two levels -- matching the wireframe's "Categoria Pai" picker,
    // which only ever lists root categories.
    private void applyParent(Category category, UUID parentId) {
        if (parentId == null) {
            category.setParent(null);
            return;
        }
        if (parentId.equals(category.getId())) {
            throw new CategoryValidationException("Uma categoria não pode ser pai dela mesma");
        }
        if (category.getId() != null && categoryRepository.existsByParentId(category.getId())) {
            throw new CategoryValidationException("Esta categoria já possui subcategorias e não pode ter uma categoria pai");
        }
        Category parent = categoryRepository.findById(parentId)
                .orElseThrow(() -> new CategoryValidationException("Categoria pai não encontrada"));
        if (parent.getParent() != null) {
            throw new CategoryValidationException(
                    "A categoria selecionada como pai já possui uma categoria pai; escolha uma categoria raiz");
        }
        category.setParent(parent);
    }

    private CategoryResponse toResponse(Category category) {
        return toResponse(category, produtoRepository.countByCategoryId(category.getId()));
    }

    private CategoryResponse toResponse(Category category, long linkedProducts) {
        UUID parentId = category.getParent() != null ? category.getParent().getId() : null;
        String parentName = category.getParent() != null ? category.getParent().getName() : null;
        return new CategoryResponse(
                category.getId(), category.getName(), category.getDescription(), category.getActive(),
                parentId, parentName, linkedProducts, category.getCreatedAt());
    }
}
