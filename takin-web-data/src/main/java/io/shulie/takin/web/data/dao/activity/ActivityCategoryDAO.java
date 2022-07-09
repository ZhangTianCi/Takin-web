package io.shulie.takin.web.data.dao.activity;

import java.util.List;

import io.shulie.takin.web.data.model.mysql.ActivityCategoryEntity;

public interface ActivityCategoryDAO {

    long ROOT_ID = 0; // 前端默认0=全部(根节点)
    long ROOT_PARENT_ID = -1;
    String ROOT_NAME = "全部";
    String RELATION_CODE_DELIMITER = "_";
    String ROOT_RELATION_CODE = String.valueOf(ROOT_ID);

    boolean hasChildren(Long parentId);

    boolean exists(Long id);

    ActivityCategoryEntity findById(Long id);

    boolean deleteById(Long id);

    boolean updateById(ActivityCategoryEntity entity);

    boolean save(ActivityCategoryEntity entity);

    List<ActivityCategoryEntity> list();

    boolean updateRelationCode(Long categoryId, String relationCode);
}
