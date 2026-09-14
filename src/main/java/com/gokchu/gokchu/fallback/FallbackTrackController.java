package com.gokchu.gokchu.fallback;

import com.gokchu.gokchu.fallback.dto.AddFallbackTrackRequest;
import com.gokchu.gokchu.fallback.dto.FallbackTrackResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/tables/{tableToken}/fallback-tracks")
@RequiredArgsConstructor
public class FallbackTrackController {

    private final FallbackTrackService fallbackTrackService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public FallbackTrackResponse addTrack(
            @PathVariable UUID tableToken, @Valid @RequestBody AddFallbackTrackRequest request) {
        return fallbackTrackService.addTrack(tableToken, request);
    }

    @GetMapping
    public List<FallbackTrackResponse> getTracks(@PathVariable UUID tableToken) {
        return fallbackTrackService.getTracks(tableToken);
    }
}
