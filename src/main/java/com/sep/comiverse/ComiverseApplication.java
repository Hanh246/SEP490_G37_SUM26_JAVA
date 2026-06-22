package com.sep.comiverse;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication
@EntityScan(basePackages = "com.sep.comiverse.entity")
@EnableJpaRepositories(basePackages = "com.sep.comiverse.repository")
public class ComiverseApplication {

	public static void main(String[] args) {
		SpringApplication.run(ComiverseApplication.class, args);
	}

}
