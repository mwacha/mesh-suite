package com.meshsuite.municipality.repository;

import com.meshsuite.municipality.domain.Municipality;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MunicipalityRepository extends JpaRepository<Municipality, Long> {

    @Query("SELECT DISTINCT m.name FROM Municipality m WHERE (:uf IS NULL OR m.state = :uf) ORDER BY m.name")
    List<String> findNamesByStateOptional(@Param("uf") String uf);
}
