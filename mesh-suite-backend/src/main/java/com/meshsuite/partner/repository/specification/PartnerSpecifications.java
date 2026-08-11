package com.meshsuite.partner.repository.specification;

import com.meshsuite.partner.domain.Partner;
import com.meshsuite.partner.domain.enums.PartnerRole;
import com.meshsuite.partner.domain.enums.PartnerStatus;
import com.meshsuite.partner.domain.enums.PersonType;
import java.util.List;
import org.springframework.data.jpa.domain.Specification;

public final class PartnerSpecifications {

    private PartnerSpecifications() {
    }

    public static Specification<Partner> withSearch(String search) {
        if (search == null || search.isBlank()) {
            return null;
        }
        String term = "%" + search.toLowerCase() + "%";
        return (root, query, cb) -> cb.or(
                cb.like(cb.lower(root.get("tradeName")), term),
                cb.like(cb.lower(root.get("legalName")), term));
    }

    public static Specification<Partner> withStatus(List<PartnerStatus> status) {
        if (status == null || status.isEmpty()) {
            return null;
        }
        return (root, query, cb) -> root.get("status").in(status);
    }

    public static Specification<Partner> withPersonType(List<PersonType> personType) {
        if (personType == null || personType.isEmpty()) {
            return null;
        }
        return (root, query, cb) -> root.get("personType").in(personType);
    }

    public static Specification<Partner> withState(List<String> state) {
        if (state == null || state.isEmpty()) {
            return null;
        }
        return (root, query, cb) -> root.get("state").in(state);
    }

    public static Specification<Partner> withCity(List<String> city) {
        if (city == null || city.isEmpty()) {
            return null;
        }
        return (root, query, cb) -> root.get("city").in(city);
    }

    public static Specification<Partner> withDocument(String document) {
        if (document == null || document.isBlank()) {
            return null;
        }
        String digits = document.replaceAll("\\D", "");
        if (digits.isBlank()) {
            return null;
        }
        String term = "%" + digits + "%";
        return (root, query, cb) -> cb.like(root.get("document"), term);
    }

    public static Specification<Partner> withRole(PartnerRole role) {
        if (role == null) {
            return null;
        }
        return (root, query, cb) -> cb.isMember(role, root.get("roles"));
    }
}
