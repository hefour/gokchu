package com.gokchu.gokchu.video;

import com.gokchu.gokchu.common.exception.ErrorCode;
import com.gokchu.gokchu.common.exception.GokchuException;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class YoutubeLinkParser {

    private static final Pattern RAW_VIDEO_ID_PATTERN = Pattern.compile("^[a-zA-Z0-9_-]{11}$");
    private static final Pattern URL_VIDEO_ID_PATTERN = Pattern.compile(
            "(?:youtube\\.com/(?:watch\\?v=|live/|shorts/|embed/)|youtu\\.be/)([a-zA-Z0-9_-]{11})"
    );

    private YoutubeLinkParser() {
    }

    public static String extractVideoId(String input) {
        if (input == null || input.isBlank()) {
            throw new GokchuException(ErrorCode.INVALID_YOUTUBE_LINK);
        }

        String trimmed = input.trim();

        if (RAW_VIDEO_ID_PATTERN.matcher(trimmed).matches()) {
            return trimmed;
        }

        Matcher matcher = URL_VIDEO_ID_PATTERN.matcher(trimmed);
        if (matcher.find()) {
            return matcher.group(1);
        }

        throw new GokchuException(ErrorCode.INVALID_YOUTUBE_LINK);
    }
}
