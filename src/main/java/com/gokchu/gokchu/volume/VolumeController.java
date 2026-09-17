package com.gokchu.gokchu.volume;

import com.gokchu.gokchu.volume.dto.UpdateVolumeRequest;
import com.gokchu.gokchu.volume.dto.VolumeResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/tables/{tableToken}/volume")
@RequiredArgsConstructor
public class VolumeController {

    private final VolumeService volumeService;

    /** TV가 폴링으로 읽는 공개 엔드포인트. 인증이 필요 없다(TV는 자격증명 없는 공용 기기). */
    @GetMapping
    public VolumeResponse getVolume(@PathVariable UUID tableToken) {
        return volumeService.getVolume(tableToken);
    }

    /**
     * 볼륨을 실제로 바꾸는 엔드포인트 — 관리자 인증 필요.
     * tableToken이 손님용 QR에도 쓰이므로, 조회(GET)와 달리 변경은 반드시 보호해야 함
     * (그렇지 않으면 손님이 매장 TV 볼륨을 임의로 끄거나 최대로 올릴 수 있음).
     */
    @PostMapping("/update")
    public VolumeResponse updateVolume(
            @PathVariable UUID tableToken, @Valid @RequestBody UpdateVolumeRequest request) {
        return volumeService.updateVolume(tableToken, request);
    }
}
