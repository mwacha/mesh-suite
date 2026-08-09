package com.meshsuite.pedido.repository.specification;

import com.meshsuite.pedido.domain.Pedido;
import com.meshsuite.pedido.domain.enums.StatusPedido;
import org.springframework.data.jpa.domain.Specification;

public final class PedidoSpecifications {

    private PedidoSpecifications() {
    }

    public static Specification<Pedido> comBusca(String busca) {
        if (busca == null || busca.isBlank()) {
            return null;
        }
        String termo = "%" + busca.toLowerCase() + "%";
        Integer numero = tryParseInt(busca.trim());
        return (root, query, cb) -> {
            var porTexto = cb.or(
                    cb.like(cb.lower(root.get("cliente").get("nomeFantasia")), termo),
                    cb.like(cb.lower(root.get("vendedor").get("name")), termo));
            if (numero != null) {
                return cb.or(porTexto, cb.equal(root.get("numero"), numero));
            }
            return porTexto;
        };
    }

    public static Specification<Pedido> comStatus(StatusPedido status) {
        if (status == null) {
            return null;
        }
        return (root, query, cb) -> cb.equal(root.get("status"), status);
    }

    private static Integer tryParseInt(String valor) {
        try {
            return Integer.parseInt(valor);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
