package com.meshsuite.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.annotation.EnableTransactionManagement;

// order=0 makes the transaction advisor the outermost AOP advice, so it
// always starts the transaction before TenantContextAspect (order=1) runs.
// See TenantContextAspect for why the ordering matters.
@Configuration
@EnableTransactionManagement(order = 0)
public class TransactionConfig {
}
