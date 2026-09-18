package com.meshsuite.partner.dto;

import jakarta.validation.constraints.Size;

public record PartnerContactDto(
        @Size(max = 100, message = "Nome do contato deve ter no máximo 100 caracteres") String name,
        @Size(max = 254, message = "E-mail do contato deve ter no máximo 254 caracteres") String email,
        @Size(max = 20, message = "Telefone comercial do contato deve ter no máximo 20 caracteres") String businessPhone,
        @Size(max = 20, message = "Celular do contato deve ter no máximo 20 caracteres") String mobilePhone,
        @Size(max = 60, message = "Cargo do contato deve ter no máximo 60 caracteres") String jobTitle) {
}
