package io.shulie.takin.web.biz.service.activity.impl;

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

import static java.util.stream.Collectors.groupingBy;

@Slf4j
@Service
public class ActivityCategoryServiceImpl implements ActivityCategoryService {

    @Resource
    private ActivityCategoryDAO activityCategoryDAO;

    @Override
    public ActivityCategoryTreeResponse list() {
        List<ActivityCategoryEntity> entityList = activityCategoryDAO.list();
        Map<Long, List<ActivityCategoryEntity>> parentMap = entityList.stream().collect(
            groupingBy(ActivityCategoryEntity::getParentId));
        ActivityCategoryEntity rootCategory = parentMap.get(ROOT_PARENT_ID).get(0);
        ActivityCategoryTreeResponse root = new ActivityCategoryTreeResponse(String.valueOf(rootCategory.getId()),
            rootCategory.getTitle(), String.valueOf(rootCategory.getParentId()));
        recursion(root, parentMap);
        return root;
    }

    @Override
    public Long addCategory(ActivityCategoryCreateRequest createRequest) {
        Long parentId = createRequest.getParentId();
        if (!activityCategoryDAO.exists(parentId)) {
            throw new RuntimeException("业务活动分类上级不存在");
        }
        Date now = new Date();
        ActivityCategoryEntity entity = new ActivityCategoryEntity();
        entity.setParentId(parentId);
        entity.setTitle(createRequest.getTitle());
        entity.setGmtCreate(now);
        entity.setGmtUpdate(now);
        activityCategoryDAO.save(entity);
        return entity.getId();
    }

    @Override
    public void updateCategory(ActivityCategoryUpdateRequest updateRequest) {
        Long id = updateRequest.getId();
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
        ActivityCategoryEntity category = activityCategoryDAO.findById(id);
        if (Objects.isNull(category)) {
            return;
        }
        Long parentId = category.getParentId();
        if (Objects.isNull(parentId) || Objects.equals(parentId, ROOT_PARENT_ID)) {
            throw new RuntimeException("分类根节点不允许删除");
        }
        if (activityCategoryDAO.hasChildren(id)) {
            throw new RuntimeException("分类存在子级不允许删除");
        }
        activityCategoryDAO.deleteById(id);
    }

    private void recursion(ActivityCategoryTreeResponse parent, Map<Long, List<ActivityCategoryEntity>> parentMap) {
        String parentId = parent.getId();
        List<ActivityCategoryEntity> children = parentMap.get(Long.valueOf(parentId));
        if (!CollectionUtils.isEmpty(children)) {
            children.forEach(child -> {
                ActivityCategoryTreeResponse childResponse = new ActivityCategoryTreeResponse(
                    String.valueOf(child.getId()), child.getTitle(), parentId);
                recursion(childResponse, parentMap);
                parent.addChild(childResponse);
            });
        }
    }
}
