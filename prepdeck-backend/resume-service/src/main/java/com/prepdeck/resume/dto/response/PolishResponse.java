package com.prepdeck.resume.dto.response;

import java.util.List;

public record PolishResponse(String polishedText, List<SectionResult> sections) {

    public PolishResponse(String polishedText) {
        this(polishedText, null);
    }

    public PolishResponse(List<SectionResult> sections) {
        this(null, sections);
    }

    public record SectionResult(String sectionType, String polishedText) {}
}
