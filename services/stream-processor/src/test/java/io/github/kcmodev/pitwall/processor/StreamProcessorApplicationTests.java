package io.github.kcmodev.pitwall.processor;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = "spring.kafka.listener.auto-startup=false")
class StreamProcessorApplicationTests {

    @Test
    void contextLoads() {
    }

}
