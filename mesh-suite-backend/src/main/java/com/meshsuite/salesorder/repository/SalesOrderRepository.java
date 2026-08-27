package com.meshsuite.salesorder.repository;

import com.meshsuite.salesorder.domain.SalesOrder;
import com.meshsuite.salesorder.domain.enums.SalesOrderStatus;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SalesOrderRepository extends JpaRepository<SalesOrder, UUID>, JpaSpecificationExecutor<SalesOrder> {
    long countByStatus(SalesOrderStatus status);

    @Query("select coalesce(sum(s.total), 0) from SalesOrder s "
            + "where s.status = :status and s.orderDate between :start and :end")
    BigDecimal sumTotalByStatusAndOrderDateBetween(
            @Param("status") SalesOrderStatus status, @Param("start") LocalDate start, @Param("end") LocalDate end);

    @Query("select s.orderDate from SalesOrder s where s.orderDate between :start and :end")
    List<LocalDate> findOrderDatesBetween(@Param("start") LocalDate start, @Param("end") LocalDate end);
}
