package io.github.kcmodev.pitwall.ingest.replay;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/replays")
public class ReplayController {
    private final ReplayService replayService;

    ReplayController(ReplayService replayService) {
        this.replayService = replayService;
    }

    @PostMapping("/{sessionKey}")
    public ResponseEntity<ReplayResult> replay(@PathVariable int sessionKey) {
        ReplayResult result = replayService.replay(sessionKey);
        if (result.totalLaps() == 0) {
            return ResponseEntity.notFound().build();
        }
        if (result.lapsFailed() > 0) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(result);
        }
        return ResponseEntity.ok(result);
    }
}
