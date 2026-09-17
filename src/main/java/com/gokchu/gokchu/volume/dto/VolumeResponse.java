package com.gokchu.gokchu.volume.dto;

import com.gokchu.gokchu.store.Store;

public record VolumeResponse(int volume, boolean muted) {

    public static VolumeResponse from(Store store) {
        return new VolumeResponse(store.getVolume(), store.isMuted());
    }
}
