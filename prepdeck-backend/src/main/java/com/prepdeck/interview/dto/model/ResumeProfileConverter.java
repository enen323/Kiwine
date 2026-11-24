package com.prepdeck.interview.dto.model;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.prepdeck.resume.dto.response.ResumeVO;
import com.prepdeck.resume.dto.response.SectionVO;

import java.util.ArrayList;
import java.util.List;

public class ResumeProfileConverter {

    private static final ObjectMapper OM = new ObjectMapper();

    public static ResumeProfile fromResumeVO(ResumeVO resume) {
        if (resume == null || resume.sections() == null) {
            return new ResumeProfile(List.of(), List.of(), List.of(), "");
        }

        List<ResumeProfile.EducationItem> education = new ArrayList<>();
        List<ResumeProfile.ProjectItem> projects = new ArrayList<>();
        List<ResumeProfile.SkillCategory> skills = new ArrayList<>();
        String summary = "";

        for (SectionVO section : resume.sections()) {
            if (section == null || section.content() == null) continue;

            switch (section.type()) {
                case "education" -> parseEducation(section.content(), education);
                case "project" -> parseProjects(section.content(), projects);
                case "skills" -> parseSkills(section.content(), skills);
                case "summary" -> summary = parseSummary(section.content());
            }
        }
        return new ResumeProfile(education, projects, skills, summary);
    }

    private static void parseEducation(String content, List<ResumeProfile.EducationItem> target) {
        try {
            JsonNode root = OM.readTree(content);
            JsonNode items = root.path("items");
            if (items.isArray()) {
                for (JsonNode item : items) {
                    String field = item.path("field").asText("");
                    List<String> highlights = new ArrayList<>();
                    JsonNode hl = item.path("highlights");
                    if (hl.isArray()) {
                        for (JsonNode h : hl) highlights.add(h.asText());
                    }
                    target.add(new ResumeProfile.EducationItem(field, highlights));
                }
            }
        } catch (Exception e) {
            // YAGNI: skip malformed section
        }
    }

    private static void parseProjects(String content, List<ResumeProfile.ProjectItem> target) {
        try {
            JsonNode root = OM.readTree(content);
            JsonNode items = root.path("items");
            if (items.isArray()) {
                for (JsonNode item : items) {
                    String name = item.path("name").asText("");
                    List<String> tech = new ArrayList<>();
                    JsonNode t = item.path("technologies");
                    if (t.isArray()) {
                        for (JsonNode techNode : t) tech.add(techNode.asText());
                    }
                    List<String> highlights = new ArrayList<>();
                    JsonNode hl = item.path("highlights");
                    if (hl.isArray()) {
                        for (JsonNode h : hl) highlights.add(h.asText());
                    }
                    target.add(new ResumeProfile.ProjectItem(name, tech, highlights));
                }
            }
        } catch (Exception e) {
            // YAGNI: skip malformed section
        }
    }

    private static void parseSkills(String content, List<ResumeProfile.SkillCategory> target) {
        try {
            JsonNode root = OM.readTree(content);
            JsonNode cats = root.path("categories");
            if (cats.isArray()) {
                for (JsonNode cat : cats) {
                    String name = cat.path("name").asText("");
                    List<String> skillList = new ArrayList<>();
                    JsonNode skills = cat.path("skills");
                    if (skills.isArray()) {
                        for (JsonNode s : skills) skillList.add(s.asText());
                    }
                    target.add(new ResumeProfile.SkillCategory(name, skillList));
                }
            }
        } catch (Exception e) {
            // YAGNI: skip malformed section
        }
    }

    private static String parseSummary(String content) {
        try {
            JsonNode root = OM.readTree(content);
            return root.path("text").asText("");
        } catch (Exception e) {
            return "";
        }
    }
}
