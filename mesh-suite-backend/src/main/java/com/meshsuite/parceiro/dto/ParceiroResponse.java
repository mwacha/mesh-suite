package com.meshsuite.parceiro.dto;

import com.meshsuite.parceiro.domain.enums.IndicadorIe;
import com.meshsuite.parceiro.domain.enums.PapelParceiro;
import com.meshsuite.parceiro.domain.enums.StatusParceiro;
import com.meshsuite.parceiro.domain.enums.TipoPessoa;

import java.util.List;
import java.util.Set;
import java.util.UUID;

public record ParceiroResponse(
        UUID id,
        TipoPessoa tipoPessoa,
        String documento,
        String nomeFantasia,
        String razaoSocial,
        StatusParceiro status,
        Set<PapelParceiro> papeis,
        String emailsCobranca,
        String whatsapp,
        IndicadorIe indicadorIe,
        String inscricaoEstadual,
        String inscricaoMunicipal,
        String inscricaoSuframa,
        String cep,
        String logradouro,
        String numero,
        String bairro,
        String complemento,
        String uf,
        String cidade,
        String observacao,
        List<ParceiroContatoDto> contatos) {
}
