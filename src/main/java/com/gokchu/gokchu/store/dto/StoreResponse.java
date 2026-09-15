package com.gokchu.gokchu.store.dto;

import com.gokchu.gokchu.store.Store;

public record StoreResponse(Long id, String name, String regionCode) {

    public static StoreResponse from(Store store) {
        return new StoreResponse(store.getId(), store.getName(), store.getRegionCode());
    }
}
