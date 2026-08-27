package com.meshsuite.paymentmethod.dto;

public record PaymentMethodCountsResponse(long total, long active, long inactive) {
}
