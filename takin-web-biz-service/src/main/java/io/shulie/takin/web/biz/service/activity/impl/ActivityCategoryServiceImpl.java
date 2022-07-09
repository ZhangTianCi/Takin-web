package io.shulie.takin.web.biz.service.activity.impl;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import javax.annotation.Resource;

import io.shulie.takin.web.biz.pojo.request.activity.ActivityCategoryCreateRequest;
import io.shulie.takin.web.biz.pojo.request.activity.ActivityCategoryUpdateRequest;
import io.shulie.takin.web.biz.pojo.response.activity.ActivityCategoryTreeResponse;
import io.shulie.takin.web.biz.service.ActivityService;
import io.shulie.takin.web.biz.service.activity.ActivityCategoryService;
import io.shulie.takin.web.data.dao.activity.ActivityCategoryDAO;
import io.shulie.takin.web.data.model.mysql.ActivityCategoryEntity;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import static io.shulie.takin.web.data.dao.activity.ActivityCategoryDAO.RELATION_CODE_DELIMITER;
import static io.shulie.takin.web.data.dao.activity.ActivityCategoryDAO.ROOT_ID;
import static io.shulie.takin.web.data.dao.activity.ActivityCategoryDAO.ROOT_NAME;
import static io.shulie.takin.web.data.dao.activity.ActivityCategoryDAO.ROOT_PARENT_ID;
import static io.shulie.takin.web.data.dao.activity.ActivityCategoryDAO.ROOT_RELATION_CODE;
import static java.util.stream.Collectors.groupingBy;

@Slf4j
@Service
public class ActivityCategoryServiceImpl implements ActivityCategoryService {

    @Resource
    private ActivityCategoryDAO activityCategoryDAO;

    @Resource
    private ActivityService activityService;

    @Override
    public ActivityCategoryTreeResponse list() {
        List<ActivityCategoryEntity> entityList = activityCategoryDAO.list();
        ActivityCategoryTreeResponse root = new ActivityCategoryTreeResponse(ROOT_ID, ROOT_NAME, ROOT_PARENT_ID);
        if (!CollectionUtils.isEmpty(entityList)) {
            Map<Long, List<ActivityCategoryEntity>> parentMap = entityList.stream().collect(
                groupingBy(ActivityCategoryEntity::getParentId));
            recursion(root, parentMap);
        }
        return root;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long addCategory(ActivityCategoryCreateRequest createRequest) {
        Long parentId = createRequest.getParentId();
        String parentRelationCode = ROOT_RELATION_CODE;
        if (!isRoot(parentId)) {
            ActivityCategoryEntity parentCategory = activityCategoryDAO.findById(parentId);
            if (Objects.isNull(parentCategory)) {
                throw new RuntimeException("业务活动分类上级不存在");
            }
            parentRelationCode = parentCategory.getRelationCode();
        }
        Date now = new Date();
        ActivityCategoryEntity entity = new ActivityCategoryEntity();
        entity.setParentId(parentId);
        entity.setTitle(createRequest.getTitle());
        entity.setGmtCreate(now);
        entity.setGmtUpdate(now);
        activityCategoryDAO.save(entity);

        Long categoryId = entity.getId();
        activityCategoryDAO.updateRelationCode(categoryId, parentRelationCode + RELATION_CODE_DELIMITER + categoryId);
        return categoryId;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateCategory(ActivityCategoryUpdateRequest updateRequest) {
        Long id = updateRequest.getId();
        if (isRoot(id)) {
            throw new RuntimeException("根节点不允许修改");
        }
        if (!activityCategoryDAO.exists(id)) {
            throw new RuntimeException("业务活动分类不存在");
        }
        ActivityCategoryEntity entity = new ActivityCategoryEntity();
        entity.setId(id);
        entity.setTitle(updateRequest.getTitle());
        entity.setGmtUpdate(new Date());
        activityCategoryDAO.updateById(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteCategory(Long id) {
        if (isRoot(id)) {
            throw new RuntimeException("根节点不允许删除");
        }
        ActivityCategoryEntity category = activityCategoryDAO.findById(id);
        if (Objects.isNull(category)) {
            return;
        }
        activityCategoryDAO.deleteById(id);
        resetChildren(category);
    }

    @Override
    public List<Long> findDescendants(Long id) {
        if (isRoot(id)) {
            return new ArrayList<>(0);
        }
        ActivityCategoryEntity category = activityCategoryDAO.findById(id);
        if (Objects.isNull(category)) {
            return new ArrayList<>(0);
        }
        return activityCategoryDAO.startWithRelationCode(completedEndIfNecessary(category.getRelationCode()));
    }

    // 移动下级节点到根节点，并重新设置 relation_code
    private void resetChildren(ActivityCategoryEntity parentCategory) {
        // 直接下级
        List<ActivityCategoryEntity> children = activityCategoryDAO.queryChildren(parentCategory.getId());
        if (!CollectionUtils.isEmpty(children)) {
            String relationCode = parentCategory.getRelationCode();
            String sourceSegment = completedEndIfNecessary(relationCode);
            String destSegment = completedEndIfNecessary(String.valueOf(ROOT_ID));
            resetRelationCode(children, sourceSegment, destSegment);
            List<Long> childrenIds = children.stream().map(ActivityCategoryEntity::getId).collect(Collectors.toList());
            // 下级及递归下级
            List<Long> descendantIds = activityCategoryDAO.startWithRelationCode(relationCode);
            descendantIds.removeAll(childrenIds);
            if (!CollectionUtils.isEmpty(descendantIds)) {
                List<ActivityCategoryEntity> descendants = activityCategoryDAO.findByIds(descendantIds);
                if (!CollectionUtils.isEmpty(descendants)) {
                    resetRelationCode(descendants, sourceSegment, destSegment);
                    children.addAll(descendants);
                }
            }
            activityCategoryDAO.updateBatchById(children);
            activityService.clearCategory(childrenIds);
        }
    }

    private void resetRelationCode(List<ActivityCategoryEntity> categories, String sourceSegment, String destSegment) {
        if (!CollectionUtils.isEmpty(categories) && !StringUtils.isAnyBlank(sourceSegment, destSegment)) {
            Date now = new Date();
            categories.forEach(category -> {
                category.setRelationCode(category.getRelationCode()
                    .replaceFirst(sourceSegment, destSegment));
                category.setGmtUpdate(now);
            });
        }
    }

    private void recursion(ActivityCategoryTreeResponse parent, Map<Long, List<ActivityCategoryEntity>> parentMap) {
        Long parentId = parent.getId();
        List<ActivityCategoryEntity> children = parentMap.get(parentId);
        if (!CollectionUtils.isEmpty(children)) {
            children.forEach(child -> {
                ActivityCategoryTreeResponse childResponse =
                    new ActivityCategoryTreeResponse(child.getId(), child.getTitle(), parentId);
                recursion(childResponse, parentMap);
                parent.addChild(childResponse);
            });
        }
    }

    private boolean isRoot(Long id) {
        return Objects.equals(ROOT_ID, id);
    }

    private String completedEndIfNecessary(String relationCode) {
        if (StringUtils.isBlank(relationCode) || StringUtils.endsWith(relationCode, RELATION_CODE_DELIMITER)) {
            return relationCode;
        }
        return relationCode.concat(RELATION_CODE_DELIMITER);
    }
}
