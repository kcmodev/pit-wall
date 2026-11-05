package io.github.kcmodev.pitwall.processor;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
        "spring.kafka.listener.auto-startup=false",
        "spring.flyway.enabled=false",
        "spring.kafka.admin.auto-create=false"
})
class StreamProcessorApplicationTests {

    @Test
    void contextLoads() {
    }

}
