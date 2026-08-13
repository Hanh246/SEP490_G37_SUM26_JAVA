package com.sep.comiverse.service.scheduler;

import com.sep.comiverse.service.AuthorLicenseService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AuthorLicenseExpiryScheduler {

    private final AuthorLicenseService authorLicenseService;

    @Scheduled(cron = "${author.license.expiry-cron:0 0 * * * *}")
    public void expireOverdueAuthorLicenses() {
        authorLicenseService.expireOverdueLicenses();
    }
}
