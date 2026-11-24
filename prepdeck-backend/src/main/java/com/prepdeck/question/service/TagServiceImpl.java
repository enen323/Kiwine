package com.prepdeck.question.service;

import com.prepdeck.question.domain.TagEntity;
import com.prepdeck.question.dto.CreateTagRequest;
import com.prepdeck.question.dto.TagVO;
import com.prepdeck.question.dto.UpdateTagRequest;
import com.prepdeck.question.repository.TagMapper;
import com.prepdeck.common.enums.ErrorCode;
import com.prepdeck.common.exception.DuplicateException;
import com.prepdeck.common.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(rollbackFor = Exception.class)
public class TagServiceImpl implements TagService {

    private final TagMapper tagMapper;

    @Override
    @Transactional(readOnly = true)
    public List<TagVO> listTags() {
        return tagMapper.selectList(null).stream()
                .filter(t -> !Boolean.TRUE.equals(t.getDeleted()))
                .map(this::toVO)
                .toList();
    }

    @Override
    public TagVO createTag(CreateTagRequest request) {
        TagEntity exist = tagMapper.selectOne(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<TagEntity>()
                .eq(TagEntity::getName, request.name())
                .last("LIMIT 1"));
        if (exist != null && !Boolean.TRUE.equals(exist.getDeleted())) {
            throw new DuplicateException(ErrorCode.TAG_ALREADY_EXISTS);
        }

        TagEntity entity = new TagEntity();
        entity.setName(request.name());
        entity.setDescription(request.description());
        tagMapper.insert(entity);
        return toVO(entity);
    }

    @Override
    public TagVO updateTag(Long id, UpdateTagRequest request) {
        TagEntity entity = tagMapper.selectById(id);
        if (entity == null || Boolean.TRUE.equals(entity.getDeleted())) {
            throw new NotFoundException(ErrorCode.TAG_NOT_FOUND);
        }

        entity.setName(request.name());
        entity.setDescription(request.description());
        tagMapper.updateById(entity);
        return toVO(entity);
    }

    @Override
    public void deleteTag(Long id) {
        TagEntity entity = tagMapper.selectById(id);
        if (entity == null || Boolean.TRUE.equals(entity.getDeleted())) {
            throw new NotFoundException(ErrorCode.TAG_NOT_FOUND);
        }
        entity.setDeleted(true);
        tagMapper.updateById(entity);
    }

    private TagVO toVO(TagEntity entity) {
        return new TagVO(entity.getId(), entity.getName(), entity.getDescription(), entity.getCreateTime());
    }
}
