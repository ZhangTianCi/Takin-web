package io.shulie.takin.web.biz.service.activity.impl;

import java.util.ArrayList;
import java.util.Collections;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

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

    private static final ActivityCategoryEntity ROOT =
        new ActivityCategoryEntity(ROOT_ID, ROOT_PARENT_ID, ROOT_NAME, ROOT_RELATION_CODE);

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
        activityCategoryDAO.updateRelationCode(categoryId,
            ActivityCategoryDAO.completedEndIfNecessary(parentRelationCode) + categoryId);
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
    public void deleteCategory(Long categoryId) {
        if (isRoot(categoryId)) {
            throw new RuntimeException("根节点不允许删除");
        }
        ActivityCategoryEntity category = activityCategoryDAO.findById(categoryId);
        if (Objects.isNull(category)) {
            return;
        }
        activityCategoryDAO.deleteById(categoryId);
        resetChildren(category);
    }

    @Override
    public List<Long> findDescendants(Long categoryId) {
        if (isRoot(categoryId)) {
            return new ArrayList<>(0);
        }
        ActivityCategoryEntity category = activityCategoryDAO.findById(categoryId);
        if (Objects.isNull(category)) {
            return new ArrayList<>(0);
        }
        return activityCategoryDAO.startWithRelationCode(category.getRelationCode());
    }

    @Override
    public void move(Long fromCategoryId, Long toCategoryId) {
        ActivityCategoryEntity sourceCategory = activityCategoryDAO.findById(fromCategoryId);
        if (Objects.isNull(sourceCategory)) {
            throw new RuntimeException("源业务活动分类不存在");
        }
        ActivityCategoryEntity destCategory = activityCategoryDAO.findById(toCategoryId);
        if (Objects.isNull(destCategory)) {
            throw new RuntimeException("目标业务活动分类不存在");
        }
        move(sourceCategory, destCategory);
    }

    // 移动下级节点到根节点
    private void resetChildren(ActivityCategoryEntity parentCategory) {
        // 直接下级
        Long categoryId = parentCategory.getId();
        List<ActivityCategoryEntity> children = activityCategoryDAO.queryChildren(categoryId);
        if (!CollectionUtils.isEmpty(children)) {
            children.forEach(category -> move(category, ROOT));
        }
        activityService.clearCategory(Collections.singletonList(categoryId));
    }

    /**
     * 移动节点到目标节点下
     *
     * @param sourceCategory 源节点
     * @param destCategory   目标节点
     */
    private void move(ActivityCategoryEntity sourceCategory, ActivityCategoryEntity destCategory) {
        Long sourceCategoryId = sourceCategory.getId(); // 源Id
        String sourceCategoryRelationCode = sourceCategory.getRelationCode(); // 源关联关系
        Long destCategoryId = destCategory.getId(); // 目标Id
        String destCategoryRelationCode = destCategory.getRelationCode(); // 目标关联关系

        Date date = new Date();
        List<ActivityCategoryEntity> updateCategory = new ArrayList<>(); // 待更新数据集合
        // 原始关联code_
        String sourceRelationCode = ActivityCategoryDAO.completedEndIfNecessary(sourceCategoryRelationCode);
        // 目标关联code_
        String destRelationCode = ActivityCategoryDAO.completedEndIfNecessary(destCategoryRelationCode);
        // 自己及递归下级Id
        List<Long> descendantIds = activityCategoryDAO.startWithRelationCode(sourceCategoryRelationCode);
        // 递归子级
        descendantIds.remove(sourceCategoryId);
        if (!CollectionUtils.isEmpty(descendantIds)) {
            List<ActivityCategoryEntity> descendants = activityCategoryDAO.findByIds(descendantIds);
            descendants.forEach(category -> {
                category.setRelationCode(category.getRelationCode().replaceFirst(sourceRelationCode, destRelationCode));
                category.setGmtUpdate(date);
            });
            updateCategory.addAll(descendants);
        }
        sourceCategory.setGmtUpdate(date);
        sourceCategory.setParentId(destCategoryId);
        sourceCategory.setRelationCode(destRelationCode + sourceCategoryId);
        updateCategory.add(sourceCategory);
        activityCategoryDAO.updateBatchById(updateCategory);
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
}
