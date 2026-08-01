package com.example.goride.analytics.service;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
        name = {
                "app.analytics.materialized.enabled",
                "app.analytics.materialized.refresh-enabled"
        },
        havingValue = "true"
)
public class MaterializedAnalyticsRefreshScheduler {
    private final MaterializedAnalyticsRefreshService refreshService;

    public MaterializedAnalyticsRefreshScheduler(
            MaterializedAnalyticsRefreshService refreshService
    ) {
        this.refreshService = refreshService;
    }

    @Scheduled(
            fixedDelayString = "${app.analytics.materialized.refresh-fixed-delay-ms:900000}",
            initialDelayString = "${app.analytics.materialized.refresh-initial-delay-ms:60000}"
    )
    public void refresh() {
        refreshService.refresh();
    }
}
