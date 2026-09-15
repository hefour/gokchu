package com.gokchu.gokchu.tabletoken.dto;

import com.gokchu.gokchu.tabletoken.TableToken;

import java.util.UUID;

public record TableTokenResponse(Long id, UUID token, String label) {

    public static TableTokenResponse from(TableToken tableToken) {
        return new TableTokenResponse(tableToken.getId(), tableToken.getToken(), tableToken.getLabel());
    }
}
