package com.meshsuite.fiscal;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Service
public class FiscalCalculationService {

    public FiscalCalculationResult calculate(FiscalRegistration registration, BigDecimal quantity, BigDecimal unitPrice) {
        BigDecimal base = quantity.multiply(unitPrice);
        return new FiscalCalculationResult(
                applyRate(base, registration.getIcmsRate()),
                applyRate(base, registration.getIpiRate()),
                applyRate(base, registration.getPisRate()),
                applyRate(base, registration.getCofinsRate())
        );
    }

    private BigDecimal applyRate(BigDecimal base, BigDecimal ratePercent) {
        return base.multiply(ratePercent.movePointLeft(2)).setScale(2, RoundingMode.HALF_UP);
    }
}
