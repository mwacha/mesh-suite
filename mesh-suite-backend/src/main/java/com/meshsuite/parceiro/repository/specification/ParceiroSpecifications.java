package com.meshsuite.parceiro.repository.specification;

import com.meshsuite.parceiro.domain.Parceiro;
import com.meshsuite.parceiro.domain.enums.PapelParceiro;
import com.meshsuite.parceiro.domain.enums.StatusParceiro;
import com.meshsuite.parceiro.domain.enums.TipoPessoa;
import java.util.List;
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

    public static Specification<Parceiro> comStatus(List<StatusParceiro> status) {
        if (status == null || status.isEmpty()) {
            return null;
        }
        return (root, query, cb) -> root.get("status").in(status);
    }

    public static Specification<Parceiro> comTipoPessoa(List<TipoPessoa> tipoPessoa) {
        if (tipoPessoa == null || tipoPessoa.isEmpty()) {
            return null;
        }
        return (root, query, cb) -> root.get("tipoPessoa").in(tipoPessoa);
    }

    public static Specification<Parceiro> comUf(List<String> uf) {
        if (uf == null || uf.isEmpty()) {
            return null;
        }
        return (root, query, cb) -> root.get("uf").in(uf);
    }

    public static Specification<Parceiro> comCidade(List<String> cidade) {
        if (cidade == null || cidade.isEmpty()) {
            return null;
        }
        return (root, query, cb) -> root.get("cidade").in(cidade);
    }

    public static Specification<Parceiro> comDocumento(String documento) {
        if (documento == null || documento.isBlank()) {
            return null;
        }
        String digitos = documento.replaceAll("\\D", "");
        if (digitos.isBlank()) {
            return null;
        }
        String termo = "%" + digitos + "%";
        return (root, query, cb) -> cb.like(root.get("documento"), termo);
    }

    public static Specification<Parceiro> comPapel(PapelParceiro papel) {
        if (papel == null) {
            return null;
        }
        return (root, query, cb) -> cb.isMember(papel, root.get("papeis"));
    }
}
