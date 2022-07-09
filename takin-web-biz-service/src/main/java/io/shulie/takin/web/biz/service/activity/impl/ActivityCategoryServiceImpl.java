package io.shulie.takin.web.biz.service.activity.impl;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import javax.annotation.Resource;

import io.shulie.takin.web.biz.pojo.request.activity.ActivityCategoryCreateRequest;
import io.shulie.takin.web.biz.pojo.request.activity.ActivityCategoryUpdateRequest;
import io.shulie.takin.web.biz.pojo.response.activity.ActivityCategoryTreeResponse;
import io.shulie.takin.web.biz.service.activity.ActivityCategoryService;
import io.shulie.takin.web.data.dao.activity.ActivityCategoryDAO;
import io.shulie.takin.web.data.model.mysql.ActivityCategoryEntity;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
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
    public void deleteCategory(Long id) {
        if (isRoot(id)) {
            throw new RuntimeException("根节点不允许删除");
        }
        ActivityCategoryEntity category = activityCategoryDAO.findById(id);
        if (Objects.isNull(category)) {
            return;
        }
        if (activityCategoryDAO.hasChildren(id)) {
            throw new RuntimeException("分类存在子级不允许删除");
        }
        activityCategoryDAO.deleteById(id);
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
        return activityCategoryDAO.startWithRelationCode(category.getRelationCode());
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
