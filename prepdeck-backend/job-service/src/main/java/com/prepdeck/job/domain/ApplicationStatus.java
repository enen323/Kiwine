package com.prepdeck.job.domain;

import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public enum ApplicationStatus {

    APPLIED("applied"),
    INTERVIEWING("interviewing"),
    OFFERED("offered"),
    REJECTED("rejected"),
    WITHDRAWN("withdrawn");

    private final String code;

    ApplicationStatus(String code) {
        this.code = code;
    }

    public String getCode() {
        return code;
    }

    private static final Map<String, ApplicationStatus> CODE_MAP =
            Stream.of(values()).collect(Collectors.toMap(s -> s.code, s -> s));

    public static ApplicationStatus parse(String raw) {
        if (raw == null) {
            return null;
        }
        String normalized = raw.trim().toLowerCase(Locale.ROOT);
        return CODE_MAP.get(normalized);
    }
}
