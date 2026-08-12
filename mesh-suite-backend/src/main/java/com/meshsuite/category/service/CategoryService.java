package com.meshsuite.category.service;

import com.meshsuite.auth.annotation.RequiresPermission;
import com.meshsuite.auth.domain.enums.Action;
import com.meshsuite.auth.domain.enums.Module;
import com.meshsuite.category.domain.Category;
import com.meshsuite.category.dto.CategoryRequest;
import com.meshsuite.category.dto.CategoryResponse;
import com.meshsuite.category.exception.CategoryInUseException;
import com.meshsuite.category.exception.CategoryNotFoundException;
import com.meshsuite.category.exception.DuplicateCategoryNameException;
import com.meshsuite.category.repository.CategoryRepository;
import com.meshsuite.category.repository.specification.CategorySpecifications;
import com.meshsuite.produto.repository.ProdutoRepository;
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
    private final ProdutoRepository produtoRepository;

    public CategoryService(CategoryRepository categoryRepository, ProdutoRepository produtoRepository) {
        this.categoryRepository = categoryRepository;
        this.produtoRepository = produtoRepository;
    }

    @Transactional(readOnly = true)
    @RequiresPermission(module = Module.PRODUCT, action = Action.VIEW)
    public Page<CategoryResponse> list(String search, Boolean active, Pageable pageable) {
        Specification<Category> spec = Specification.allOf(
                CategorySpecifications.withSearch(search),
                CategorySpecifications.withActive(active));
        Page<Category> page = categoryRepository.findAll(spec, pageable);

        List<UUID> ids = page.getContent().stream().map(Category::getId).toList();
        Map<UUID, Long> counts = ids.isEmpty()
                ? Map.of()
                : produtoRepository.countByCategoriaIdIn(ids).stream()
                        .collect(Collectors.toMap(
                                ProdutoRepository.CategoriaProdutoCount::getCategoriaId,
                                ProdutoRepository.CategoriaProdutoCount::getTotal));

        return page.map(category -> toResponse(category, counts.getOrDefault(category.getId(), 0L)));
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
        long linked = produtoRepository.countByCategoriaId(id);
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
    }

    private CategoryResponse toResponse(Category category) {
        return toResponse(category, produtoRepository.countByCategoriaId(category.getId()));
    }

    private CategoryResponse toResponse(Category category, long linkedProducts) {
        return new CategoryResponse(
                category.getId(), category.getName(), category.getDescription(), category.getActive(),
                linkedProducts, category.getCreatedAt());
    }
}
