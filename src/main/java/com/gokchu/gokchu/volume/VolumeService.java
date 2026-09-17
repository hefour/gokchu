package com.gokchu.gokchu.volume;

import com.gokchu.gokchu.common.exception.ErrorCode;
import com.gokchu.gokchu.common.exception.GokchuException;
import com.gokchu.gokchu.store.Store;
import com.gokchu.gokchu.tabletoken.TableToken;
import com.gokchu.gokchu.tabletoken.TableTokenRepository;
import com.gokchu.gokchu.volume.dto.UpdateVolumeRequest;
import com.gokchu.gokchu.volume.dto.VolumeResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class VolumeService {

    private final TableTokenRepository tableTokenRepository;

    @Transactional(readOnly = true)
    public VolumeResponse getVolume(UUID tableToken) {
        Store store = findTable(tableToken).getStore();
        return VolumeResponse.from(store);
    }

    @Transactional
    public VolumeResponse updateVolume(UUID tableToken, UpdateVolumeRequest request) {
        Store store = findTable(tableToken).getStore();
        store.updateVolume(request.volume(), request.muted());
        return VolumeResponse.from(store);
    }

    private TableToken findTable(UUID tableToken) {
        return tableTokenRepository.findByToken(tableToken)
                .orElseThrow(() -> new GokchuException(ErrorCode.TABLE_TOKEN_NOT_FOUND));
    }
}
