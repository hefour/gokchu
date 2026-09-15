package com.gokchu.gokchu.store;

import com.gokchu.gokchu.common.exception.ErrorCode;
import com.gokchu.gokchu.common.exception.GokchuException;
import com.gokchu.gokchu.store.dto.CreateStoreRequest;
import com.gokchu.gokchu.store.dto.CreateTableTokenRequest;
import com.gokchu.gokchu.store.dto.StoreResponse;
import com.gokchu.gokchu.tabletoken.TableToken;
import com.gokchu.gokchu.tabletoken.TableTokenRepository;
import com.gokchu.gokchu.tabletoken.dto.TableTokenResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StoreServiceTest {

    @Mock
    private StoreRepository storeRepository;

    @Mock
    private TableTokenRepository tableTokenRepository;

    @InjectMocks
    private StoreService storeService;

    private Store store;

    @BeforeEach
    void setUp() {
        store = Store.builder().name("데모 매장").regionCode("KR").build();
        ReflectionTestUtils.setField(store, "id", 1L);
    }

    @Test
    void createStore_이름과_지역코드로_매장을_생성한다() {
        when(storeRepository.save(any(Store.class))).thenAnswer(invocation -> {
            Store saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", 1L);
            return saved;
        });

        StoreResponse response = storeService.createStore(new CreateStoreRequest("곡추 매장", "KR"));

        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.name()).isEqualTo("곡추 매장");
        assertThat(response.regionCode()).isEqualTo("KR");
    }

    @Test
    void createTable_존재하는_매장이면_테이블을_생성한다() {
        when(storeRepository.findById(1L)).thenReturn(Optional.of(store));
        when(tableTokenRepository.save(any(TableToken.class))).thenAnswer(invocation -> {
            TableToken saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", 10L);
            return saved;
        });

        TableTokenResponse response = storeService.createTable(1L, new CreateTableTokenRequest("1번 테이블"));

        assertThat(response.id()).isEqualTo(10L);
        assertThat(response.label()).isEqualTo("1번 테이블");
        assertThat(response.token()).isNotNull();
    }

    @Test
    void createTable_존재하지_않는_매장이면_예외가_발생한다() {
        when(storeRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> storeService.createTable(999L, new CreateTableTokenRequest("1번 테이블")))
                .isInstanceOf(GokchuException.class)
                .extracting(e -> ((GokchuException) e).getErrorCode())
                .isEqualTo(ErrorCode.STORE_NOT_FOUND);
    }

    @Test
    void getTables_매장의_테이블_목록을_반환한다() {
        TableToken table = TableToken.builder().store(store).label("1번 테이블").build();
        ReflectionTestUtils.setField(table, "id", 10L);
        when(storeRepository.findById(1L)).thenReturn(Optional.of(store));
        when(tableTokenRepository.findByStoreId(1L)).thenReturn(List.of(table));

        List<TableTokenResponse> responses = storeService.getTables(1L);

        assertThat(responses).hasSize(1);
        assertThat(responses.get(0).label()).isEqualTo("1번 테이블");
    }

    @Test
    void getTables_존재하지_않는_매장이면_예외가_발생한다() {
        when(storeRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> storeService.getTables(999L))
                .isInstanceOf(GokchuException.class)
                .extracting(e -> ((GokchuException) e).getErrorCode())
                .isEqualTo(ErrorCode.STORE_NOT_FOUND);
    }
}
