package com.pagoclaro.reconciliation;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class ReconciliationApplication {

	public static void main(String[] args) {
		SpringApplication.run(ReconciliationApplication.class, args);
	}

}
