package io.github.kcmodev.pitwall.ingest.openf1;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Objects;

@Component
public class OpenF1Client {

    private final RestClient restClient;

    OpenF1Client(RestClient.Builder restClientBuilder,
                 @Value("${openf1.base-url}") String openf1Url) {
        this.restClient = restClientBuilder.baseUrl(openf1Url).build();
    }

    public List<OpenF1Lap> getLaps(int sessionKey) {
        return Objects.requireNonNullElse(restClient.get()
                .uri(uri -> uri.path("/laps").queryParam("session_key", sessionKey).build())
                .retrieve()
                .body(new ParameterizedTypeReference<>() {
                }), List.of());
    }
}
