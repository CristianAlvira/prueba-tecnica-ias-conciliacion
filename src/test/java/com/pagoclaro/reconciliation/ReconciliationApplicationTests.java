package com.pagoclaro.reconciliation;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
@DisplayName("Arranque de la aplicación")
class ReconciliationApplicationTests {

	@Test
	@DisplayName("Carga el contexto de Spring sin necesitar Oracle")
	void debeCargarElContexto() {
	}

}
