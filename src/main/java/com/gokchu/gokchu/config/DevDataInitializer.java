package com.gokchu.gokchu.config;

import com.gokchu.gokchu.fallback.FallbackTrack;
import com.gokchu.gokchu.fallback.FallbackTrackRepository;
import com.gokchu.gokchu.store.Store;
import com.gokchu.gokchu.store.StoreRepository;
import com.gokchu.gokchu.tabletoken.TableToken;
import com.gokchu.gokchu.tabletoken.TableTokenRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Slf4j
@Profile("local")
@Component
@RequiredArgsConstructor
public class DevDataInitializer implements CommandLineRunner {

    private final StoreRepository storeRepository;
    private final TableTokenRepository tableTokenRepository;
    private final FallbackTrackRepository fallbackTrackRepository;

    @Override
    public void run(String... args) {
        if (storeRepository.count() > 0) {
            return;
        }

        Store store = storeRepository.save(Store.builder().name("데모 매장").regionCode("KR").build());
        TableToken table = tableTokenRepository.save(TableToken.builder().store(store).label("1번 테이블").build());

        fallbackTrackRepository.save(FallbackTrack.builder()
                .store(store).videoId("jNQXAC9IVRw")
                .title("Me at the zoo").thumbnailUrl("https://i.ytimg.com/vi/jNQXAC9IVRw/hqdefault.jpg")
                .build());
        fallbackTrackRepository.save(FallbackTrack.builder()
                .store(store).videoId("9bZkp7q19f0")
                .title("PSY - GANGNAM STYLE(강남스타일) M/V")
                .thumbnailUrl("https://i.ytimg.com/vi/9bZkp7q19f0/hqdefault.jpg")
                .build());

        log.info("데모 매장 생성 완료 - storeId={}, tableToken={}", store.getId(), table.getToken());
    }
}
