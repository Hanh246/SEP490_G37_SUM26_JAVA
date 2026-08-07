package com.sep.comiverse.config;

import com.sep.comiverse.service.TranslatorPaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@Profile("!integration")
@RequiredArgsConstructor
@Order(2)
public class TranslatorSettlementBackfill implements CommandLineRunner {

    private final TranslatorPaymentService translatorPaymentService;

    @Override
    public void run(String... args) {
        int count = translatorPaymentService.backfillCompletedTasks();
        if (count > 0) {
            log.info("Backfilled {} completed translator chapter settlement(s)", count);
        }
    }
}
