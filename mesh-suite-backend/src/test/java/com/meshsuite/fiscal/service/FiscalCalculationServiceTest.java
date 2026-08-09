package com.meshsuite.fiscal.service;

import static org.assertj.core.api.Assertions.assertThat;
import com.meshsuite.fiscal.domain.FiscalRegistration;
import com.meshsuite.fiscal.dto.FiscalCalculationResult;
import com.meshsuite.fiscal.service.FiscalCalculationService;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class FiscalCalculationServiceTest {

    private final FiscalCalculationService service = new FiscalCalculationService();

    private FiscalRegistration registration(String icms, String ipi, String pis, String cofins) {
        FiscalRegistration registration = new FiscalRegistration();
        registration.setIcmsRate(new BigDecimal(icms));
        registration.setIpiRate(new BigDecimal(ipi));
        registration.setPisRate(new BigDecimal(pis));
        registration.setCofinsRate(new BigDecimal(cofins));
        return registration;
    }

    @Test
    void calculatesEachTaxAsPercentOfQuantityTimesUnitPrice() {
        FiscalRegistration registration = registration("18.00", "5.00", "1.65", "7.60");

        FiscalCalculationResult result = service.calculate(registration, new BigDecimal("10"), new BigDecimal("50.00"));

        assertThat(result.icmsValue()).isEqualByComparingTo("90.00");
        assertThat(result.ipiValue()).isEqualByComparingTo("25.00");
        assertThat(result.pisValue()).isEqualByComparingTo("8.25");
        assertThat(result.cofinsValue()).isEqualByComparingTo("38.00");
    }

    @Test
    void zeroRateProducesZeroTax() {
        FiscalRegistration registration = registration("0.00", "0.00", "0.00", "0.00");

        FiscalCalculationResult result = service.calculate(registration, new BigDecimal("10"), new BigDecimal("50.00"));

        assertThat(result.icmsValue()).isEqualByComparingTo("0.00");
        assertThat(result.ipiValue()).isEqualByComparingTo("0.00");
        assertThat(result.pisValue()).isEqualByComparingTo("0.00");
        assertThat(result.cofinsValue()).isEqualByComparingTo("0.00");
    }

    @Test
    void roundsHalfUpToTwoDecimalPlaces() {
        FiscalRegistration registration = registration("33.33", "0.00", "0.00", "0.00");

        FiscalCalculationResult result = service.calculate(registration, new BigDecimal("1"), new BigDecimal("10.00"));

        // base = 10.00; 10.00 * 0.3333 = 3.3330 -> rounds HALF_UP to 3.33
        assertThat(result.icmsValue()).isEqualByComparingTo("3.33");
    }
}
