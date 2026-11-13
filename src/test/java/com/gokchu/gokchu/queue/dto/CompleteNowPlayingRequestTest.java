package com.gokchu.gokchu.queue.dto;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CompleteNowPlayingRequestTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void setUp() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void tearDown() {
        factory.close();
    }

    @Test
    void 신청곡_id만_있으면_통과한다() {
        assertThat(validator.validate(new CompleteNowPlayingRequest(1L, null))).isEmpty();
    }

    @Test
    void 폴백_videoId만_있으면_통과한다() {
        assertThat(validator.validate(new CompleteNowPlayingRequest(null, "dQw4w9WgXcQ"))).isEmpty();
    }

    @Test
    void 둘_다_없으면_거절한다() {
        assertThat(validator.validate(new CompleteNowPlayingRequest(null, null))).hasSize(1);
    }

    @Test
    void 폴백_videoId가_공백뿐이면_없는_것으로_보고_거절한다() {
        assertThat(validator.validate(new CompleteNowPlayingRequest(null, "  "))).hasSize(1);
    }

    @Test
    void 둘_다_있으면_어느_곡인지_모호하므로_거절한다() {
        assertThat(validator.validate(new CompleteNowPlayingRequest(1L, "dQw4w9WgXcQ"))).hasSize(1);
    }
}
