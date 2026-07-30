package com.meshsuite.parceiro;

public class ParceiroNaoEncontradoException extends RuntimeException {
    public ParceiroNaoEncontradoException() {
        super("Parceiro não encontrado");
    }
}
