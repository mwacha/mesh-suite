package com.meshsuite.salesorder.dto;

public record SalesOrderCountsResponse(long total, long draft, long inPreparation, long invoiced) {
}
