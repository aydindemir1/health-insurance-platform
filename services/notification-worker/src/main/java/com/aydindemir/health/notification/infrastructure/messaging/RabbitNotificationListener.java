package com.aydindemir.health.notification.infrastructure.messaging;

import com.aydindemir.health.notification.application.port.in.DeliverNotificationUseCase;
import com.rabbitmq.client.Channel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class RabbitNotificationListener {
    private static final Logger LOGGER = LoggerFactory.getLogger(RabbitNotificationListener.class);

    private final DeliverNotificationUseCase deliverNotification;
    private final RetryTemplate deliveryRetry;

    public RabbitNotificationListener(
            DeliverNotificationUseCase deliverNotification,
            RetryTemplate deliveryRetry) {
        this.deliverNotification = deliverNotification;
        this.deliveryRetry = deliveryRetry;
    }

    @RabbitListener(
            queues = NotificationRabbitContract.DELIVERY_QUEUE,
            autoStartup = "${app.messaging.rabbit-listener.enabled:false}")
    public void receive(
            @Payload NotificationTaskMessage task,
            Channel channel,
            @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) throws IOException {
        String correlationId = task.causationId() != null ? task.causationId().toString()
                : task.taskId() != null ? task.taskId().toString() : java.util.UUID.randomUUID().toString();
        MDC.put("correlationId", correlationId);
        MDC.put("taskId", String.valueOf(task.taskId()));
        try {
            var command = task.toCommand();
            deliveryRetry.execute(context -> {
                deliverNotification.deliver(command);
                return null;
            });
        } catch (RuntimeException exception) {
            LOGGER.warn(
                    "Notification task rejected: taskId={}, taskVersion={}, notificationType={}, failureType={}",
                    task.taskId(),
                    task.taskVersion(),
                    task.notificationType(),
                    exception.getClass().getSimpleName());
            channel.basicNack(deliveryTag, false, false);
            return;
        } finally {
            MDC.remove("taskId");
            MDC.remove("correlationId");
        }
        channel.basicAck(deliveryTag, false);
    }
}
