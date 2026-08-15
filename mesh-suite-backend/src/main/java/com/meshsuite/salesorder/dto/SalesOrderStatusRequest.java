package com.meshsuite.salesorder.dto;

import com.meshsuite.salesorder.domain.enums.SalesOrderStatus;
import jakarta.validation.constraints.NotNull;

public record SalesOrderStatusRequest(@NotNull SalesOrderStatus status) {
}
