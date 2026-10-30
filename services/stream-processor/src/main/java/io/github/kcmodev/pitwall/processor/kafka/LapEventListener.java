package io.github.kcmodev.pitwall.processor.kafka;

import io.github.kcmodev.pitwall.processor.lap.LapEvent;
import io.github.kcmodev.pitwall.processor.lap.LapTimeService;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class LapEventListener {
    private static final Logger LOGGER = LoggerFactory.getLogger(LapEventListener.class);
    private final LapTimeService lapTimeService;

    public LapEventListener(LapTimeService lapTimeService) {
        this.lapTimeService = lapTimeService;
    }

    @KafkaListener(topics = "${pitwall.kafka.topics.laps}")
    public void onLap(ConsumerRecord<String, LapEvent> record) {
        LOGGER.debug("Received lap event partition={} offset={} key={}",
                record.partition(), record.offset(), record.key());
        lapTimeService.process(record.value());
    }
}
