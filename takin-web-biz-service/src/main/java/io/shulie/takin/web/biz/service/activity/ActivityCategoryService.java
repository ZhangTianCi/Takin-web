package io.shulie.takin.web.biz.service.activity;

import io.shulie.takin.web.biz.pojo.request.activity.ActivityCategoryCreateRequest;
import io.shulie.takin.web.biz.pojo.request.activity.ActivityCategoryUpdateRequest;
import io.shulie.takin.web.biz.pojo.response.activity.ActivityCategoryTreeResponse;

public interface ActivityCategoryService {

    long ROOT_PARENT_ID = -1;

    ActivityCategoryTreeResponse list();

    Long addCategory(ActivityCategoryCreateRequest createRequest);

    void updateCategory(ActivityCategoryUpdateRequest updateRequest);

    void deleteCategory(Long id);
}
