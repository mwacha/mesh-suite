package com.meshsuite.parceiro.dto;

import com.meshsuite.parceiro.IndicadorIe;
import com.meshsuite.parceiro.PapelParceiro;
import com.meshsuite.parceiro.TipoPessoa;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.Set;

public record ParceiroRequest(
        @NotNull TipoPessoa tipoPessoa,
        @NotBlank String documento,
        @NotBlank String nomeFantasia,
        String razaoSocial,
        @NotEmpty Set<PapelParceiro> papeis,
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
