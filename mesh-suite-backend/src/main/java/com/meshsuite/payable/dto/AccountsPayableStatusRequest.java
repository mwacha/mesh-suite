package com.meshsuite.payable.dto;

import com.meshsuite.payable.domain.enums.AccountsPayableStatus;
import jakarta.validation.constraints.NotNull;

public record AccountsPayableStatusRequest(@NotNull AccountsPayableStatus status) {
}
