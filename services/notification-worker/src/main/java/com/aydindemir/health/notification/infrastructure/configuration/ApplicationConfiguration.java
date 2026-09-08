package com.aydindemir.health.notification.infrastructure.configuration;

import com.aydindemir.health.notification.application.port.in.DeliverNotificationUseCase;
import com.aydindemir.health.notification.application.port.out.NotificationDeliveryRepository;
import com.aydindemir.health.notification.application.port.out.NotificationSender;
import com.aydindemir.health.notification.application.usecase.NotificationDeliveryService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
class ApplicationConfiguration {
    @Bean
    Clock systemClock() {
        return Clock.systemUTC();
    }

    @Bean
    DeliverNotificationUseCase deliverNotificationUseCase(
            NotificationDeliveryRepository deliveries,
            NotificationSender sender,
            Clock clock) {
        var applicationService = new NotificationDeliveryService(deliveries, sender, clock);
        return new TransactionalDeliverNotificationUseCase(applicationService);
    }
}
