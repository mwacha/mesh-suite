package com.meshsuite.colorway.service;

import com.meshsuite.auth.annotation.RequiresPermission;
import com.meshsuite.auth.domain.enums.Action;
import com.meshsuite.auth.domain.enums.Module;
import com.meshsuite.colorway.domain.Colorway;
import com.meshsuite.colorway.dto.ColorwayRequest;
import com.meshsuite.colorway.dto.ColorwayResponse;
import com.meshsuite.colorway.exception.ColorwayInUseException;
import com.meshsuite.colorway.exception.ColorwayNotFoundException;
import com.meshsuite.colorway.exception.DuplicateColorwayNameException;
import com.meshsuite.colorway.repository.ColorwayRepository;
import com.meshsuite.colorway.repository.specification.ColorwaySpecifications;
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
public class ColorwayService {

    private final ColorwayRepository colorwayRepository;
    private final ProductRepository produtoRepository;

    public ColorwayService(ColorwayRepository colorwayRepository, ProductRepository produtoRepository) {
        this.colorwayRepository = colorwayRepository;
        this.produtoRepository = produtoRepository;
    }

    @Transactional(readOnly = true)
    @RequiresPermission(module = Module.PRODUCT, action = Action.VIEW)
    public Page<ColorwayResponse> list(String search, Boolean active, Pageable pageable) {
        Specification<Colorway> spec = Specification.allOf(
                ColorwaySpecifications.withSearch(search),
                ColorwaySpecifications.withActive(active));
        Page<Colorway> page = colorwayRepository.findAll(spec, pageable);

        List<UUID> ids = page.getContent().stream().map(Colorway::getId).toList();
        Map<UUID, Long> counts = ids.isEmpty()
                ? Map.of()
                : produtoRepository.countByColorwayIdIn(ids).stream()
                        .collect(Collectors.toMap(
                                ProductRepository.ColorwayProductCount::getColorwayId,
                                ProductRepository.ColorwayProductCount::getTotal));

        return page.map(colorway -> toResponse(colorway, counts.getOrDefault(colorway.getId(), 0L)));
    }

    @Transactional(readOnly = true)
    @RequiresPermission(module = Module.PRODUCT, action = Action.VIEW)
    public ColorwayResponse findById(UUID id) {
        return toResponse(findEntityById(id));
    }

    @Transactional
    @RequiresPermission(module = Module.PRODUCT, action = Action.CREATE)
    public ColorwayResponse create(UUID tenantId, ColorwayRequest request) {
        validateName(request.name(), null);

        Colorway colorway = new Colorway();
        colorway.setTenantId(tenantId);
        apply(colorway, request);
        return toResponse(colorwayRepository.saveAndFlush(colorway));
    }

    @Transactional
    @RequiresPermission(module = Module.PRODUCT, action = Action.EDIT)
    public ColorwayResponse update(UUID id, ColorwayRequest request) {
        validateName(request.name(), id);

        Colorway colorway = findEntityById(id);
        apply(colorway, request);
        return toResponse(colorwayRepository.saveAndFlush(colorway));
    }

    @Transactional
    @RequiresPermission(module = Module.PRODUCT, action = Action.DELETE)
    public void delete(UUID id) {
        Colorway colorway = findEntityById(id);
        long linked = produtoRepository.countByColorwayId(id);
        if (linked > 0) {
            throw new ColorwayInUseException(linked);
        }
        colorwayRepository.delete(colorway);
    }

    private Colorway findEntityById(UUID id) {
        return colorwayRepository.findById(id).orElseThrow(ColorwayNotFoundException::new);
    }

    private void validateName(String name, UUID currentId) {
        boolean duplicate = currentId == null
                ? colorwayRepository.existsByName(name)
                : colorwayRepository.existsByNameAndIdNot(name, currentId);
        if (duplicate) {
            throw new DuplicateColorwayNameException();
        }
    }

    private void apply(Colorway colorway, ColorwayRequest request) {
        colorway.setName(request.name());
        colorway.setEffectiveDate(request.effectiveDate());
        colorway.setDescription(request.description());
        colorway.setActive(request.active() != null ? request.active() : true);
    }

    private ColorwayResponse toResponse(Colorway colorway) {
        return toResponse(colorway, produtoRepository.countByColorwayId(colorway.getId()));
    }

    private ColorwayResponse toResponse(Colorway colorway, long linkedProducts) {
        return new ColorwayResponse(
                colorway.getId(), colorway.getName(), colorway.getEffectiveDate(), colorway.getDescription(),
                colorway.getActive(), linkedProducts, colorway.getCreatedAt());
    }
}
