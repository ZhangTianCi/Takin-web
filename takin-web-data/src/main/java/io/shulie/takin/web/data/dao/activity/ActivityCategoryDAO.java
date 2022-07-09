package io.shulie.takin.web.data.dao.activity;

import java.util.List;

import io.shulie.takin.web.data.model.mysql.ActivityCategoryEntity;

public interface ActivityCategoryDAO {

    boolean hasChildren(Long parentId);

    boolean exists(Long id);

    ActivityCategoryEntity findById(Long id);

    boolean deleteById(Long id);

    boolean updateById(ActivityCategoryEntity entity);

    boolean save(ActivityCategoryEntity entity);

    List<ActivityCategoryEntity> list();

}
