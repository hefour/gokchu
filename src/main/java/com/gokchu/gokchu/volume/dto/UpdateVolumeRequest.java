package com.gokchu.gokchu.volume.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record UpdateVolumeRequest(
        @Min(value = 0, message = "볼륨은 0 이상이어야 합니다.")
        @Max(value = 100, message = "볼륨은 100 이하여야 합니다.")
        int volume,
        boolean muted) {
}
