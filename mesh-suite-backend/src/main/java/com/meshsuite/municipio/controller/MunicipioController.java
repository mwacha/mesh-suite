package com.meshsuite.municipio.controller;

import com.meshsuite.municipio.repository.MunicipioRepository;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/municipios")
public class MunicipioController {

    private final MunicipioRepository municipioRepository;

    public MunicipioController(MunicipioRepository municipioRepository) {
        this.municipioRepository = municipioRepository;
    }

    @GetMapping
    public List<String> listar(@RequestParam(required = false) String uf) {
        return municipioRepository.findNomesByUfOptional(uf);
    }
}
