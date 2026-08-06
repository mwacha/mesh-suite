package com.meshsuite.payable;

import com.meshsuite.payable.dto.AccountsPayableResponse;
import com.meshsuite.payable.dto.AccountsPayableStatusRequest;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/accounts-payable")
public class AccountsPayableController {

    private final AccountsPayableService accountsPayableService;

    public AccountsPayableController(AccountsPayableService accountsPayableService) {
        this.accountsPayableService = accountsPayableService;
    }

    @GetMapping
    public Page<AccountsPayableResponse> list(
            @RequestParam(required = false) AccountsPayableStatus status,
            @PageableDefault(size = 10, sort = "dueDate") Pageable pageable) {
        return accountsPayableService.list(status, pageable);
    }

    @PatchMapping("/{id}/status")
    public AccountsPayableResponse updateStatus(@PathVariable UUID id,
                                                 @Valid @RequestBody AccountsPayableStatusRequest request) {
        return accountsPayableService.updateStatus(id, request.status());
    }
}
