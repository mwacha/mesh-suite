package com.meshsuite.venda.repository.specification;

import com.meshsuite.venda.domain.Venda;
import org.springframework.data.jpa.domain.Specification;

public final class VendaSpecifications {

    private VendaSpecifications() {
    }

    public static Specification<Venda> comBusca(String busca) {
        if (busca == null || busca.isBlank()) {
            return null;
        }
        String termo = "%" + busca.toLowerCase() + "%";
        Integer numero = tryParseInt(busca.trim());
        return (root, query, cb) -> {
            var porCliente = cb.like(cb.lower(root.get("cliente").get("nomeFantasia")), termo);
            if (numero != null) {
                return cb.or(porCliente, cb.equal(root.get("numero"), numero));
            }
            return porCliente;
        };
    }

    private static Integer tryParseInt(String valor) {
        try {
            return Integer.parseInt(valor);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
