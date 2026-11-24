package com.prepdeck.interview.dto.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.prepdeck.resume.dto.response.ResumeVO;
import com.prepdeck.resume.dto.response.SectionVO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class ResumeProfileConverterTest {

    private final ObjectMapper om = new ObjectMapper().registerModule(new JavaTimeModule());

    @Test
    void convertFromResumeVO_education_fieldAndHighlightsOnly() throws Exception {
        ResumeVO resumeVO = buildResumeVO();

        ResumeProfile profile = ResumeProfileConverter.fromResumeVO(resumeVO);

        assertEquals(1, profile.education().size());
        assertEquals("软件工程", profile.education().getFirst().field());
        assertEquals(List.of("一等奖学金"), profile.education().getFirst().highlights());

        assertEquals(1, profile.projects().size());
        assertEquals("蜂购", profile.projects().getFirst().name());
        assertEquals(List.of("Spring", "Redis"), profile.projects().getFirst().technologies());
        assertEquals(List.of("QPS优化"), profile.projects().getFirst().highlights());

        assertEquals(1, profile.skills().size());
        assertEquals("Java", profile.skills().getFirst().name());
        assertEquals(List.of("熟悉多线程"), profile.skills().getFirst().skills());

        assertEquals("学习能力强", profile.summary());
    }

    @Test
    void convertFromResumeVO_nullInput_returnsEmptyProfile() {
        ResumeProfile profile = ResumeProfileConverter.fromResumeVO(null);
        assertTrue(profile.education().isEmpty());
        assertTrue(profile.projects().isEmpty());
        assertTrue(profile.skills().isEmpty());
        assertEquals("", profile.summary());
    }

    @Test
    void convertFromResumeVO_malformedSection_skipsGracefully() throws Exception {
        ResumeVO resumeVO = buildMalformedResumeVO();

        ResumeProfile profile = ResumeProfileConverter.fromResumeVO(resumeVO);
        assertTrue(profile.education().isEmpty());
        assertEquals("ok", profile.summary());
    }

    private ResumeVO buildResumeVO() throws Exception {
        String json = om.writeValueAsString(new ResumeVOFixture(
                1L, 1L, "resume.pdf", "http://file", "", "", true,
                "我的简历", "modern", "zh", "{}", true, "token", 0,
                List.of(
                        new SectionVO(1L, 1L, "education", "教育经历", 0, Boolean.TRUE,
                                "{\"items\":[{\"field\":\"软件工程\",\"degree\":\"本科\",\"institution\":\"福建师范大学\",\"highlights\":[\"一等奖学金\"]}]}",
                                LocalDateTime.now(), LocalDateTime.now()),
                        new SectionVO(2L, 1L, "project", "项目经历", 1, Boolean.TRUE,
                                "{\"items\":[{\"name\":\"蜂购\",\"technologies\":[\"Spring\",\"Redis\"],\"highlights\":[\"QPS优化\"]}]}",
                                LocalDateTime.now(), LocalDateTime.now()),
                        new SectionVO(3L, 1L, "skills", "专业技能", 2, Boolean.TRUE,
                                "{\"categories\":[{\"name\":\"Java\",\"skills\":[\"熟悉多线程\"]}]}",
                                LocalDateTime.now(), LocalDateTime.now()),
                        new SectionVO(4L, 1L, "summary", "个人概述", 3, Boolean.TRUE,
                                "{\"text\":\"学习能力强\"}",
                                LocalDateTime.now(), LocalDateTime.now())
                ),
                LocalDateTime.now(), LocalDateTime.now()
        ));
        return om.readValue(json, ResumeVO.class);
    }

    private ResumeVO buildMalformedResumeVO() throws Exception {
        String json = om.writeValueAsString(new ResumeVOFixture(
                1L, 1L, "resume.pdf", "http://file", "", "", true,
                "我的简历", "modern", "zh", "{}", true, "token", 0,
                List.of(
                        new SectionVO(1L, 1L, "education", "教育", 0, Boolean.TRUE,
                                "not-json", LocalDateTime.now(), LocalDateTime.now()),
                        new SectionVO(2L, 1L, "summary", "概述", 1, Boolean.TRUE,
                                "{\"text\":\"ok\"}", LocalDateTime.now(), LocalDateTime.now())
                ),
                LocalDateTime.now(), LocalDateTime.now()
        ));
        return om.readValue(json, ResumeVO.class);
    }

    private record ResumeVOFixture(
            Long id, Long userId, String fileName, String fileUrl,
            String contentText, String contentJson, Boolean isActive,
            String title, String template, String language, String themeConfig,
            Boolean isPublic, String shareToken, Integer viewCount,
            List<SectionVO> sections, LocalDateTime createTime, LocalDateTime updateTime
    ) {}
}
