package io.shulie.takin.web.data.dao.activity.impl;

import java.util.List;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.baomidou.mybatisplus.extension.toolkit.SqlHelper;
import io.shulie.takin.web.data.dao.activity.ActivityCategoryDAO;
import io.shulie.takin.web.data.mapper.mysql.ActivityCategoryMapper;
import io.shulie.takin.web.data.model.mysql.ActivityCategoryEntity;
import io.shulie.takin.web.data.util.MPUtil;
import org.springframework.stereotype.Repository;

@Repository
public class ActivityCategoryDAOImpl extends ServiceImpl<ActivityCategoryMapper, ActivityCategoryEntity>
    implements ActivityCategoryDAO, MPUtil<ActivityCategoryEntity> {

    @Override
    public List<ActivityCategoryEntity> list() {
        return baseMapper.selectList(Wrappers.emptyWrapper());
    }

    @Override
    public boolean hasChildren(Long parentId) {
        LambdaQueryWrapper<ActivityCategoryEntity> queryWrapper = this.getLambdaQueryWrapper()
            .eq(ActivityCategoryEntity::getParentId, parentId);
        return SqlHelper.retBool(baseMapper.selectCount(queryWrapper));
    }

    @Override
    public boolean exists(Long id) {
        LambdaQueryWrapper<ActivityCategoryEntity> queryWrapper = this.getLambdaQueryWrapper()
            .eq(ActivityCategoryEntity::getId, id);
        return SqlHelper.retBool(baseMapper.selectCount(queryWrapper));
    }

    @Override
    public ActivityCategoryEntity findById(Long id) {
        return baseMapper.selectById(id);
    }

    @Override
    public boolean save(ActivityCategoryEntity entity) {
        return SqlHelper.retBool(baseMapper.insert(entity));
    }

    @Override
    public boolean updateById(ActivityCategoryEntity entity) {
        return SqlHelper.retBool(baseMapper.updateById(entity));
    }

    @Override
    public boolean deleteById(Long id) {
        return SqlHelper.retBool(baseMapper.deleteById(id));
    }

    @Override
    public ActivityCategoryEntity findRoot() {
        LambdaQueryWrapper<ActivityCategoryEntity> queryWrapper = this.getLambdaQueryWrapper()
            .eq(ActivityCategoryEntity::getParentId, ROOT_PARENT_ID);
        return baseMapper.selectOne(queryWrapper);
    }
}
