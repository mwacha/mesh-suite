package com.meshsuite.parceiro.dto;

import com.meshsuite.parceiro.domain.enums.StatusParceiro;
import jakarta.validation.constraints.NotNull;

public record ParceiroStatusRequest(@NotNull StatusParceiro status) {
}
