package com.gokchu.gokchu.store;

import com.gokchu.gokchu.store.dto.CreateStoreRequest;
import com.gokchu.gokchu.store.dto.CreateTableTokenRequest;
import com.gokchu.gokchu.store.dto.StoreResponse;
import com.gokchu.gokchu.tabletoken.dto.TableTokenResponse;
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

@RestController
@RequestMapping("/api/stores")
@RequiredArgsConstructor
public class StoreController {

    private final StoreService storeService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public StoreResponse createStore(@Valid @RequestBody CreateStoreRequest request) {
        return storeService.createStore(request);
    }

    @PostMapping("/{storeId}/tables")
    @ResponseStatus(HttpStatus.CREATED)
    public TableTokenResponse createTable(
            @PathVariable Long storeId, @Valid @RequestBody CreateTableTokenRequest request) {
        return storeService.createTable(storeId, request);
    }

    @GetMapping("/{storeId}/tables")
    public List<TableTokenResponse> getTables(@PathVariable Long storeId) {
        return storeService.getTables(storeId);
    }
}
