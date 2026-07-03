package com.sep.comiverse.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;

@Component
public class ApplicationReadyListener implements ApplicationListener<ApplicationReadyEvent> {

    private static final Logger log = LoggerFactory.getLogger(ApplicationReadyListener.class);

    @Value("${server.port:8080}")
    private String port;

    @Value("${server.servlet.context-path:}")
    private String contextPath;

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        String baseUrl = "http://localhost:" + port + contextPath;

        String swaggerUrl = baseUrl + "/swagger-ui/index.html";

        log.info("----------------------------------------------------------");
        log.info("Application '{}' is running successfully!", event.getApplicationContext().getApplicationName());
        log.info("Local URL:       {}", baseUrl);
        log.info("Swagger UI:      {}", swaggerUrl);
        log.info("----------------------------------------------------------");
    }
}
