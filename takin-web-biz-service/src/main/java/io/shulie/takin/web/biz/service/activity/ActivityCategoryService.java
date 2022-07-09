package io.shulie.takin.web.biz.service.activity;

import io.shulie.takin.web.biz.pojo.request.activity.ActivityCategoryCreateRequest;
import io.shulie.takin.web.biz.pojo.request.activity.ActivityCategoryUpdateRequest;
import io.shulie.takin.web.biz.pojo.response.activity.ActivityCategoryTreeResponse;

public interface ActivityCategoryService {

    ActivityCategoryTreeResponse list();

    Long addCategory(ActivityCategoryCreateRequest createRequest);

    void updateCategory(ActivityCategoryUpdateRequest updateRequest);

    void deleteCategory(Long id);
}
