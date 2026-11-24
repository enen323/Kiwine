package com.prepdeck.interview.dto.model;

import java.util.List;

public record ResumeProfile(
        List<EducationItem> education,
        List<ProjectItem> projects,
        List<SkillCategory> skills,
        String summary
) {
    public record EducationItem(String field, List<String> highlights) {}

    public record ProjectItem(String name, List<String> technologies, List<String> highlights) {}

    public record SkillCategory(String name, List<String> skills) {}
}
