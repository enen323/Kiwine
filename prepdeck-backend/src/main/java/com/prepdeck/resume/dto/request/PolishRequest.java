package com.prepdeck.resume.dto.request;

import java.util.List;

public record PolishRequest(
        String sectionType,
        String rawText,
        String format,
        Long resumeId,
        List<String> sectionTypes
) {
    public PolishRequest(String sectionType, String rawText, String format) {
        this(sectionType, rawText, format, null, null);
    }
}
