package com.meshsuite.colorway.controller;

import com.meshsuite.auth.service.AuthContextService;
import com.meshsuite.colorway.dto.ColorwayRequest;
import com.meshsuite.colorway.dto.ColorwayResponse;
import com.meshsuite.colorway.service.ColorwayService;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/colorways")
public class ColorwayController {

    private final ColorwayService colorwayService;

    public ColorwayController(ColorwayService colorwayService) {
        this.colorwayService = colorwayService;
    }

    @GetMapping
    public Page<ColorwayResponse> list(
            @RequestParam(required = false) String busca,
            @RequestParam(required = false) Boolean ativo,
            @PageableDefault(size = 10, sort = "name") Pageable pageable) {
        return colorwayService.list(busca, ativo, pageable);
    }

    @GetMapping("/{id}")
    public ColorwayResponse findById(@PathVariable UUID id) {
        return colorwayService.findById(id);
    }

    @PostMapping
    public ResponseEntity<ColorwayResponse> create(@AuthenticationPrincipal AuthContextService.Context principal,
                                                     @Valid @RequestBody ColorwayRequest request) {
        ColorwayResponse response = colorwayService.create(principal.tenantId(), request);
        return ResponseEntity.status(201).body(response);
    }

    @PutMapping("/{id}")
    public ColorwayResponse update(@PathVariable UUID id, @Valid @RequestBody ColorwayRequest request) {
        return colorwayService.update(id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        colorwayService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
