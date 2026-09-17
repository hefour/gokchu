package com.gokchu.gokchu.volume;

import com.gokchu.gokchu.common.exception.ErrorCode;
import com.gokchu.gokchu.common.exception.GokchuException;
import com.gokchu.gokchu.store.Store;
import com.gokchu.gokchu.tabletoken.TableToken;
import com.gokchu.gokchu.tabletoken.TableTokenRepository;
import com.gokchu.gokchu.volume.dto.UpdateVolumeRequest;
import com.gokchu.gokchu.volume.dto.VolumeResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class VolumeServiceTest {

    @Mock
    private TableTokenRepository tableTokenRepository;

    @InjectMocks
    private VolumeService volumeService;

    private Store store;
    private UUID tableToken;

    @BeforeEach
    void setUp() {
        store = Store.builder().name("데모 매장").regionCode("KR").build();
        ReflectionTestUtils.setField(store, "id", 1L);

        TableToken table = TableToken.builder().store(store).label("1번 테이블").build();
        ReflectionTestUtils.setField(table, "id", 10L);
        tableToken = table.getToken();

        when(tableTokenRepository.findByToken(tableToken)).thenReturn(Optional.of(table));
    }

    @Test
    void getVolume_기본값을_반환한다() {
        VolumeResponse response = volumeService.getVolume(tableToken);

        assertThat(response.volume()).isEqualTo(70);
        assertThat(response.muted()).isFalse();
    }

    @Test
    void getVolume_존재하지_않는_테이블이면_예외가_발생한다() {
        UUID unknownToken = UUID.randomUUID();
        when(tableTokenRepository.findByToken(unknownToken)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> volumeService.getVolume(unknownToken))
                .isInstanceOf(GokchuException.class)
                .extracting(e -> ((GokchuException) e).getErrorCode())
                .isEqualTo(ErrorCode.TABLE_TOKEN_NOT_FOUND);
    }

    @Test
    void updateVolume_매장의_볼륨_상태를_바꾼다() {
        VolumeResponse response = volumeService.updateVolume(tableToken, new UpdateVolumeRequest(30, true));

        assertThat(response.volume()).isEqualTo(30);
        assertThat(response.muted()).isTrue();
        assertThat(store.getVolume()).isEqualTo(30);
        assertThat(store.isMuted()).isTrue();
    }
}
