package com.meshsuite.auth.dto;

import java.util.List;
import java.util.UUID;

// `contas` empty means the login completed and the session cookie was set;
// non-empty means credentials were valid but matched more than one account --
// no session yet, the client must call POST /api/auth/select-account with one
// of these tenantIds to finish.
public record LoginResponse(List<AccountOption> contas) {

    public record AccountOption(UUID tenantId, String nomeEmpresa) {
    }
}
