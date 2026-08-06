package com.sep.comiverse.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "offline-download")
public class OfflineDownloadProperties {

    private boolean enabled = false;
    private String signingPrivateKey = "";
    private String signingPublicKey = "";
    private String signingKeyId = "offline-ed25519-v1";
    private String issuer = "comiverse-api";
    private String audience = "comiverse-android";
    private Duration licenseDuration = Duration.ofDays(7);
    private Duration challengeTtl = Duration.ofMinutes(5);
    private Duration connectTimeout = Duration.ofSeconds(8);
    private Duration sourceRequestTimeout = Duration.ofSeconds(20);
    private int maxDevicesPerUser = 3;
    private int maxChallengesPerHour = 50;
    private int maxPackagesPerHour = 50;
    private int maxLicensesPerHour = 100;
    private int maxPages = 200;
    private long maxPageBytes = 12L * 1024L * 1024L;
    private long maxPackageBytes = 150L * 1024L * 1024L;
    private List<String> allowedImageHosts = new ArrayList<>(List.of("res.cloudinary.com"));
}
