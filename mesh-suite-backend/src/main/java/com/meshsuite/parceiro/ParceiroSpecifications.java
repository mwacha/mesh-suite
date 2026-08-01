package com.meshsuite.parceiro;

import org.springframework.data.jpa.domain.Specification;

public final class ParceiroSpecifications {

    private ParceiroSpecifications() {
    }

    public static Specification<Parceiro> comBusca(String busca) {
        if (busca == null || busca.isBlank()) {
            return null;
        }
        String termo = "%" + busca.toLowerCase() + "%";
        return (root, query, cb) -> cb.or(
                cb.like(cb.lower(root.get("nomeFantasia")), termo),
                cb.like(cb.lower(root.get("razaoSocial")), termo));
    }

    public static Specification<Parceiro> comStatus(StatusParceiro status) {
        if (status == null) {
            return null;
        }
        return (root, query, cb) -> cb.equal(root.get("status"), status);
    }

    public static Specification<Parceiro> comTipoPessoa(TipoPessoa tipoPessoa) {
        if (tipoPessoa == null) {
            return null;
        }
        return (root, query, cb) -> cb.equal(root.get("tipoPessoa"), tipoPessoa);
    }

    public static Specification<Parceiro> comUf(String uf) {
        if (uf == null || uf.isBlank()) {
            return null;
        }
        return (root, query, cb) -> cb.equal(root.get("uf"), uf);
    }

    public static Specification<Parceiro> comCidade(String cidade) {
        if (cidade == null || cidade.isBlank()) {
            return null;
        }
        return (root, query, cb) -> cb.equal(root.get("cidade"), cidade);
    }

    public static Specification<Parceiro> comPapel(PapelParceiro papel) {
        if (papel == null) {
            return null;
        }
        return (root, query, cb) -> cb.isMember(papel, root.get("papeis"));
    }
}
