package com.meshsuite.parceiro.exception;

import com.meshsuite.parceiro.domain.Parceiro;

public class ParceiroNaoEncontradoException extends RuntimeException {
    public ParceiroNaoEncontradoException() {
        super("Parceiro não encontrado");
    }
}
