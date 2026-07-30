package com.meshsuite.parceiro.dto;

import com.meshsuite.parceiro.StatusParceiro;
import jakarta.validation.constraints.NotNull;

public record ParceiroStatusRequest(@NotNull StatusParceiro status) {
}
