package com.meshsuite.usuario;

import com.meshsuite.usuario.dto.UsuarioSummaryResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/usuarios")
public class UsuarioController {

    private final UsuarioRepository usuarioRepository;

    public UsuarioController(UsuarioRepository usuarioRepository) {
        this.usuarioRepository = usuarioRepository;
    }

    @GetMapping("/representantes")
    public List<UsuarioSummaryResponse> representantes() {
        return usuarioRepository.findByPapelOrderByNome(Papel.REPRESENTANTE).stream()
                .map(u -> new UsuarioSummaryResponse(u.getId(), u.getNome()))
                .toList();
    }
}
