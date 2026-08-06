package com.meshsuite.stock;

import com.meshsuite.stock.dto.StockMovementResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/stock-movements")
public class StockMovementController {

    private final StockService stockService;

    public StockMovementController(StockService stockService) {
        this.stockService = stockService;
    }

    @GetMapping
    public Page<StockMovementResponse> history(
            @RequestParam UUID productId,
            @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return stockService.history(productId, pageable);
    }
}
