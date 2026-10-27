package io.github.kcmodev.pitwall.ingest.openf1;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.github.kcmodev.pitwall.ingest.lap.LapEvent;

import java.time.OffsetDateTime;

@JsonIgnoreProperties(ignoreUnknown = true)
public record OpenF1Lap(@JsonProperty("date_start") OffsetDateTime dateStart,
                        @JsonProperty("driver_number") int driverNumber,
                        @JsonProperty("duration_sector_1") Double durationSector1,
                        @JsonProperty("duration_sector_2") Double durationSector2,
                        @JsonProperty("duration_sector_3") Double durationSector3,
                        @JsonProperty("is_pit_out_lap") boolean isPitOutLap,
                        @JsonProperty("lap_duration") Double lapDuration,
                        @JsonProperty("lap_number") int lapNumber,
                        @JsonProperty("session_key") int sessionKey
                        ) {

    public LapEvent toLapEvent() {
        return new LapEvent(sessionKey, driverNumber, lapNumber,
                dateStart == null ? null : dateStart.toInstant(),
                lapDuration, durationSector1, durationSector2, durationSector3, isPitOutLap);
    }
}
