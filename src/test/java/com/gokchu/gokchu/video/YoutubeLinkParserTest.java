package com.gokchu.gokchu.video;

import com.gokchu.gokchu.common.exception.GokchuException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class YoutubeLinkParserTest {

    @ParameterizedTest
    @ValueSource(strings = {
            "https://www.youtube.com/watch?v=dQw4w9WgXcQ",
            "https://www.youtube.com/watch?v=dQw4w9WgXcQ&t=30s",
            "https://youtu.be/dQw4w9WgXcQ",
            "https://youtu.be/dQw4w9WgXcQ?si=abcd",
            "https://www.youtube.com/live/dQw4w9WgXcQ",
            "https://www.youtube.com/shorts/dQw4w9WgXcQ",
            "https://www.youtube.com/embed/dQw4w9WgXcQ",
            "dQw4w9WgXcQ"
    })
    void extractsVideoIdFromVariousFormats(String input) {
        assertThat(YoutubeLinkParser.extractVideoId(input)).isEqualTo("dQw4w9WgXcQ");
    }

    @Test
    void throwsOnInvalidLink() {
        assertThatThrownBy(() -> YoutubeLinkParser.extractVideoId("https://example.com/not-youtube"))
                .isInstanceOf(GokchuException.class);
    }

    @Test
    void throwsOnBlankInput() {
        assertThatThrownBy(() -> YoutubeLinkParser.extractVideoId("  "))
                .isInstanceOf(GokchuException.class);
    }
}
