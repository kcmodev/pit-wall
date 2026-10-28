package io.github.kcmodev.pitwall.processor.kafka;

import io.github.kcmodev.pitwall.processor.lap.LapEvent;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class LapEventListener {

    private static final Logger LOGGER = LoggerFactory.getLogger(LapEventListener.class);

    @KafkaListener(topics = "${pitwall.kafka.topics.laps}")
    public void onLap(ConsumerRecord<String, LapEvent> record) {
        LapEvent lapEvent = record.value();
        LOGGER.info("Message received from Kafka topic: {}, key: {}, partition: {} with offset: {}, driver: {}, lap: {}, duration: {}",
                record.topic(), record.key(), record.partition(), record.offset(),
                lapEvent.driverNumber(), lapEvent.lapNumber(), lapEvent.lapDurationSeconds());
    }
}
