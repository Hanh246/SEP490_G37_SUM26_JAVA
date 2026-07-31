package com.sep.comiverse.integration;

import com.sep.comiverse.ComiverseApplication;
import com.sep.comiverse.integration.support.ComiverseIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@ComiverseIntegrationTest
@SpringBootTest(classes = ComiverseApplication.class)
class ComiverseApplicationIT {

	@Test
	void contextLoads() {
	}

}
