package com.meshsuite.purchaseorder.dto;

import com.meshsuite.purchaseorder.PurchaseOrderStatus;
import jakarta.validation.constraints.NotNull;

public record PurchaseOrderStatusRequest(@NotNull PurchaseOrderStatus status) {
}
