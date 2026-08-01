package com.sep.comiverse.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.FirebaseMessaging;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.util.StringUtils;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.concurrent.Executor;

@Configuration
public class FirebasePushConfig {

    private static final String APP_NAME = "comiverse-push";

    @Bean(name = "pushNotificationExecutor")
    public Executor pushNotificationExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(500);
        executor.setThreadNamePrefix("comiverse-push-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(10);
        executor.initialize();
        return executor;
    }

    @Bean
    @ConditionalOnProperty(name = "firebase.push.enabled", havingValue = "true")
    public FirebaseMessaging firebaseMessaging(
            @Value("${firebase.push.project-id:}") String projectId,
            @Value("${firebase.push.service-account-json:}") String serviceAccountJson,
            @Value("${firebase.push.service-account-base64:}") String serviceAccountBase64
    ) throws Exception {
        FirebaseApp existing = FirebaseApp.getApps().stream()
                .filter(app -> APP_NAME.equals(app.getName()))
                .findFirst()
                .orElse(null);
        if (existing != null) {
            return FirebaseMessaging.getInstance(existing);
        }

        FirebaseOptions.Builder options = FirebaseOptions.builder()
                .setCredentials(credentials(serviceAccountJson, serviceAccountBase64));
        if (StringUtils.hasText(projectId)) {
            options.setProjectId(projectId.trim());
        }
        FirebaseApp app = FirebaseApp.initializeApp(options.build(), APP_NAME);
        return FirebaseMessaging.getInstance(app);
    }

    private GoogleCredentials credentials(String rawJson, String rawBase64) throws Exception {
        if (StringUtils.hasText(rawBase64)) {
            byte[] decoded = Base64.getDecoder().decode(rawBase64.trim());
            return GoogleCredentials.fromStream(new ByteArrayInputStream(decoded));
        }
        if (StringUtils.hasText(rawJson)) {
            return GoogleCredentials.fromStream(new ByteArrayInputStream(
                    rawJson.trim().getBytes(StandardCharsets.UTF_8)
            ));
        }
        // Supports GOOGLE_APPLICATION_CREDENTIALS without ever putting a
        // service-account key in source control.
        return GoogleCredentials.getApplicationDefault();
    }
}
