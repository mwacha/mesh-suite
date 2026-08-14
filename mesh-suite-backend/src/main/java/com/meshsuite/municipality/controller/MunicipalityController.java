package com.meshsuite.municipality.controller;

import com.meshsuite.municipality.repository.MunicipalityRepository;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/municipalities")
public class MunicipalityController {

    private final MunicipalityRepository municipalityRepository;

    public MunicipalityController(MunicipalityRepository municipalityRepository) {
        this.municipalityRepository = municipalityRepository;
    }

    @GetMapping
    public List<String> listar(@RequestParam(required = false) String uf) {
        return municipalityRepository.findNamesByStateOptional(uf);
    }
}
