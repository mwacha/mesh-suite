package com.meshsuite.purchaseorder.dto;

public record PurchaseOrderCountsResponse(long total, long open, long received, long cancelled) {
}
