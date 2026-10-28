package io.github.kcmodev.pitwall.ingest.replay;

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
    public ResponseEntity<ReplayResponse> replay(@PathVariable int sessionKey) {
        int lapCount = replayService.replay(sessionKey);
        if (lapCount == 0) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.accepted().body(new ReplayResponse(sessionKey, lapCount));
    }

    public record ReplayResponse(int sessionKey, int lapCount) {}
}
