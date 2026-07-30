package com.meshsuite.parceiro.dto;

public record ParceiroContatoDto(
        String nome,
        String email,
        String telefoneComercial,
        String telefoneCelular,
        String cargo) {
}
