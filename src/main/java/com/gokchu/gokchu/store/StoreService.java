package com.gokchu.gokchu.store;

import com.gokchu.gokchu.common.exception.ErrorCode;
import com.gokchu.gokchu.common.exception.GokchuException;
import com.gokchu.gokchu.store.dto.CreateStoreRequest;
import com.gokchu.gokchu.store.dto.StoreResponse;
import com.gokchu.gokchu.tabletoken.TableToken;
import com.gokchu.gokchu.tabletoken.TableTokenRepository;
import com.gokchu.gokchu.store.dto.CreateTableTokenRequest;
import com.gokchu.gokchu.tabletoken.dto.TableTokenResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class StoreService {

    private final StoreRepository storeRepository;
    private final TableTokenRepository tableTokenRepository;

    @Transactional
    public StoreResponse createStore(CreateStoreRequest request) {
        Store store = Store.builder()
                .name(request.name())
                .regionCode(request.regionCode())
                .build();
        return StoreResponse.from(storeRepository.save(store));
    }

    @Transactional
    public TableTokenResponse createTable(Long storeId, CreateTableTokenRequest request) {
        Store store = findStore(storeId);
        TableToken table = TableToken.builder()
                .store(store)
                .label(request.label())
                .build();
        return TableTokenResponse.from(tableTokenRepository.save(table));
    }

    @Transactional(readOnly = true)
    public List<TableTokenResponse> getTables(Long storeId) {
        findStore(storeId);
        return tableTokenRepository.findByStoreId(storeId).stream()
                .map(TableTokenResponse::from)
                .toList();
    }

    private Store findStore(Long storeId) {
        return storeRepository.findById(storeId)
                .orElseThrow(() -> new GokchuException(ErrorCode.STORE_NOT_FOUND));
    }
}
