package com.meshsuite.parceiro.dto;

import com.meshsuite.parceiro.domain.enums.StatusParceiro;
import com.meshsuite.parceiro.domain.enums.TipoPessoa;

import java.util.UUID;

public record ParceiroSummaryResponse(
        UUID id,
        String nomeFantasia,
        String razaoSocial,
        String documento,
        TipoPessoa tipoPessoa,
        String cidade,
        String uf,
        String whatsapp,
        StatusParceiro status) {
}
