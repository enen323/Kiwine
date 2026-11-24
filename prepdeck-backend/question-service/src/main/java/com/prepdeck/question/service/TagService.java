package com.prepdeck.question.service;

import com.prepdeck.question.domain.TagEntity;
import com.prepdeck.question.dto.CreateTagRequest;
import com.prepdeck.question.dto.TagVO;
import com.prepdeck.question.dto.UpdateTagRequest;

import java.util.List;

public interface TagService {

    List<TagVO> listTags();

    TagVO createTag(CreateTagRequest request);

    TagVO updateTag(Long id, UpdateTagRequest request);

    void deleteTag(Long id);
}
