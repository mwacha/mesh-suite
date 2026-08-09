package com.meshsuite.municipio.repository;

import com.meshsuite.municipio.domain.Municipio;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MunicipioRepository extends JpaRepository<Municipio, Long> {

    @Query("SELECT DISTINCT m.nome FROM Municipio m WHERE (:uf IS NULL OR m.uf = :uf) ORDER BY m.nome")
    List<String> findNomesByUfOptional(@Param("uf") String uf);
}
