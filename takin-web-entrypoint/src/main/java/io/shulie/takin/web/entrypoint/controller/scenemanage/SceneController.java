package io.shulie.takin.web.entrypoint.controller.scenemanage;

import java.util.Map;
import java.util.List;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.stream.Collectors;
import java.util.concurrent.atomic.AtomicInteger;

import javax.validation.Valid;
import javax.annotation.Resource;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;

import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.TypeReference;

import cn.hutool.core.util.StrUtil;
import cn.hutool.core.bean.BeanUtil;
import org.apache.commons.collections4.CollectionUtils;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestMapping;

import io.shulie.takin.cloud.common.utils.JmxUtil;
import io.shulie.takin.web.ext.util.WebPluginUtils;
import io.shulie.takin.ext.content.script.ScriptNode;
import io.shulie.takin.web.biz.service.LeakSqlService;
import io.shulie.takin.web.biz.constant.BizOpConstants;
import io.shulie.takin.web.data.model.mysql.SceneEntity;
import io.shulie.takin.common.beans.annotation.ModuleDef;
import io.shulie.takin.web.biz.service.scene.SceneService;
import io.shulie.takin.ext.content.enginecall.PtConfigExt;
import io.shulie.takin.common.beans.response.ResponseResult;
import io.shulie.takin.web.data.dao.filemanage.FileManageDAO;
import io.shulie.takin.web.data.result.linkmange.SceneResult;
import io.shulie.takin.cloud.open.resp.strategy.StrategyResp;
import io.shulie.takin.web.common.exception.TakinWebException;
import io.shulie.takin.common.beans.annotation.ActionTypeEnum;
import io.shulie.takin.common.beans.annotation.AuthVerification;
import io.shulie.takin.web.common.exception.TakinWebExceptionEnum;
import io.shulie.takin.web.data.dao.scriptmanage.ScriptFileRefDAO;
import io.shulie.takin.web.biz.pojo.request.scene.NewSceneRequest;
import io.shulie.takin.web.data.result.scene.SceneLinkRelateResult;
import io.shulie.takin.ext.content.enginecall.ThreadGroupConfigExt;
import io.shulie.takin.cloud.open.req.scenemanage.SceneTaskStartReq;
import io.shulie.takin.cloud.open.api.scene.manage.MultipleSceneApi;
import io.shulie.takin.cloud.open.request.scene.manage.SceneRequest;
import io.shulie.takin.web.biz.service.scenemanage.SceneManageService;
import io.shulie.takin.web.data.result.scriptmanage.ScriptFileRefResult;
import io.shulie.takin.cloud.open.response.scene.manage.SceneDetailResponse;
import io.shulie.takin.web.biz.pojo.request.leakcheck.LeakSqlBatchRefsRequest;
import io.shulie.takin.web.biz.pojo.response.leakcheck.LeakSqlBatchRefsResponse;

/**
 * 场景管理控制器 - 新
 *
 * @author 张天赐
 */
@RestController
@RequestMapping("/api/v2/scene")
@Api(tags = "压测场景-新", value = "压测场景-新")
public class SceneController {
    @Resource
    SceneService sceneService;
    @Resource
    FileManageDAO fileManageDao;
    @Resource
    LeakSqlService leakSqlService;
    @Resource
    MultipleSceneApi multipleSceneApi;
    @Resource
    ScriptFileRefDAO scriptFileRefDao;
    @Resource
    SceneManageService sceneManageService;

    /**
     * 创建压测场景 - 新
     *
     * @return 创建的场景的自增ID
     */
    @PostMapping("create")
    @ApiOperation("创建压测场景 - 新")
    @ModuleDef(
        moduleName = BizOpConstants.Modules.PRESSURE_TEST_MANAGE,
        subModuleName = BizOpConstants.SubModules.PRESSURE_TEST_SCENE,
        logMsgKey = BizOpConstants.Message.MESSAGE_PRESSURE_TEST_SCENE_CREATE
    )
    @AuthVerification(needAuth = ActionTypeEnum.CREATE, moduleCode = BizOpConstants.ModuleCode.PRESSURE_TEST_SCENE)
    public ResponseResult<Long> create(@RequestBody @Valid NewSceneRequest request) {
        SceneRequest sceneRequest = buildSceneRequest(request);
        WebPluginUtils.fillCloudUserData(sceneRequest);
        return multipleSceneApi.create(sceneRequest);
    }

    /**
     * 更新压测场景 - 新
     *
     * @return 操作结果
     */
    @PostMapping("update")
    @ApiOperation("更新压测场景 - 新")
    @ModuleDef(
        moduleName = BizOpConstants.Modules.PRESSURE_TEST_MANAGE,
        subModuleName = BizOpConstants.SubModules.PRESSURE_TEST_SCENE,
        logMsgKey = BizOpConstants.Message.MESSAGE_PRESSURE_TEST_SCENE_UPDATE
    )
    @AuthVerification(needAuth = ActionTypeEnum.UPDATE, moduleCode = BizOpConstants.ModuleCode.PRESSURE_TEST_SCENE)
    public ResponseResult<Boolean> update(@RequestBody @Valid NewSceneRequest request) {
        if (null == request.getBasicInfo().getSceneId()) {
            return ResponseResult.fail(TakinWebExceptionEnum.SCENE_VALIDATE_ERROR.getErrorCode(), "压测场景ID不能为空");
        }
        SceneRequest sceneRequest = buildSceneRequest(request);
        WebPluginUtils.fillCloudUserData(sceneRequest);
        return multipleSceneApi.update(sceneRequest);
    }

    /**
     * 生成场景请求入参-请求Cloud用
     *
     * @param request 前端入参
     * @return 调用Cloud接口用的入参
     */
    private SceneRequest buildSceneRequest(NewSceneRequest request) {
        // 1. 基本信息
        SceneRequest sceneRequest = BeanUtil.copyProperties(request, SceneRequest.class);
        // 2. 线程组施压配置 （字段相同，但是由于类型不同，导致的无法拷贝属性，需要手动转换）
        {
            // 1. 构建实例
            PtConfigExt ptConfig = BeanUtil.copyProperties(request.getConfig(), PtConfigExt.class);
            // 2. 构建实例内容
            ptConfig.setThreadGroupConfigMap(new HashMap<>());
            // 3. 填充实例内容
            request.getConfig().getThreadGroupConfigMap().forEach((k, v) -> ptConfig.getThreadGroupConfigMap().put(
                k, BeanUtil.copyProperties(v, ThreadGroupConfigExt.class)));
            sceneRequest.setConfig(ptConfig);
        }
        // 3. 填充脚本解析结果
        {
            SceneResult scene = sceneService.getScene(sceneRequest.getBasicInfo().getBusinessFlowId());
            if (scene == null) {throw new TakinWebException(TakinWebExceptionEnum.ERROR_COMMON, "未获取到业务流程");}
            if (StrUtil.isBlank(scene.getScriptJmxNode())) {throw new TakinWebException(TakinWebExceptionEnum.ERROR_COMMON, "业务流程未保存脚本解析结果");}
            if (!scene.getLinkRelateNum().equals(scene.getTotalNodeNum())) {throw new TakinWebException(TakinWebExceptionEnum.ERROR_COMMON, "业务流程尚未匹配完成");}
            sceneRequest.setAnalysisResult(JSONObject.parseArray(scene.getScriptJmxNode(), ScriptNode.class));
            sceneRequest.getBasicInfo().setType(0);
            sceneRequest.getBasicInfo().setScriptType(0);
            sceneRequest.getBasicInfo().setScriptId(scene.getScriptDeployId());
        }
        // 4. 填充压测内容
        {
            // 1. 获取业务流程关联的业务活动
            List<SceneLinkRelateResult> links = sceneService.getSceneLinkRelates(sceneRequest.getBasicInfo().getBusinessFlowId());
            if (CollectionUtils.isEmpty(links)) {throw new TakinWebException(TakinWebExceptionEnum.ERROR_COMMON, "未获取到业务流程关联的业务活动");}
            // 2. 转换业务活动为压测你日工
            List<SceneRequest.Content> content = links.stream().map(t -> new SceneRequest.Content() {{
                setPathMd5(t.getScriptXpathMd5());
                setBusinessActivityId(Long.valueOf(t.getBusinessLinkId()));
            }}).collect(Collectors.toList());
            // 3. 根据脚本解析结果，填充压测内容
            // 3.1. 脚本解析结果转换为一维数据
            List<ScriptNode> nodes = JmxUtil.toOneDepthList(sceneRequest.getAnalysisResult());
            if (CollectionUtils.isEmpty(nodes)) {throw new TakinWebException(TakinWebExceptionEnum.ERROR_COMMON, "脚本解析结果转换为一维数据失败");}
            // 3.2. 一维数据转换为Map，获得xPathMD5 和 脚本节点名称的对应关系
            Map<String, String> nodeMap = nodes.stream().collect(Collectors.toMap(ScriptNode::getXpathMd5, ScriptNode::getTestName));
            // 3.3 遍历压测内容并从Map中填充数据
            for (SceneRequest.Content item : content) {
                if (!nodeMap.containsKey(item.getPathMd5())) {throw new TakinWebException(TakinWebExceptionEnum.ERROR_COMMON, "脚本解析结果存在不能匹配的业务活动");}
                item.setName(nodeMap.get(item.getPathMd5()));
                item.setApplicationId(sceneManageService.getAppIdsByBusinessActivityId(item.getBusinessActivityId()));
            }
            sceneRequest.setContent(content);
        }
        // 5. 填充SLA
        {
            // 1. 销毁的监控目标
            List<SceneRequest.MonitoringGoal> destroyMonitoringGoal = request.getDestroyMonitoringGoal().stream()
                .map(t -> BeanUtil.copyProperties(t, SceneRequest.MonitoringGoal.class))
                .peek(t -> t.setType(0)).collect(Collectors.toList());
            // 2. 警告的监控目标
            List<SceneRequest.MonitoringGoal> warnMonitoringGoal = request.getWarnMonitoringGoal().stream()
                .map(t -> BeanUtil.copyProperties(t, SceneRequest.MonitoringGoal.class))
                .peek(t -> t.setType(1)).collect(Collectors.toList());
            // 3. 组合警告目标
            List<SceneRequest.MonitoringGoal> monitoringGoal =
                new ArrayList<>(destroyMonitoringGoal.size() + warnMonitoringGoal.size());
            monitoringGoal.addAll(destroyMonitoringGoal);
            monitoringGoal.addAll(warnMonitoringGoal);
            // 4. 填充
            sceneRequest.setMonitoringGoal(monitoringGoal);
        }
        // 6. 填充压测文件
        sceneRequest.setFile(assembleFileList(sceneRequest.getBasicInfo().getScriptId()));
        return sceneRequest;
    }

    /**
     * 获取压测场景详情 - 新
     *
     * @return 压测场景详情
     */
    @GetMapping("detail")
    @ApiOperation("获取压测场景详情 - 新")
    @ModuleDef(
        moduleName = BizOpConstants.Modules.PRESSURE_TEST_MANAGE,
        subModuleName = BizOpConstants.SubModules.PRESSURE_TEST_SCENE,
        logMsgKey = BizOpConstants.Message.MESSAGE_PRESSURE_TEST_SCENE_UPDATE
    )
    @AuthVerification(needAuth = ActionTypeEnum.UPDATE, moduleCode = BizOpConstants.ModuleCode.PRESSURE_TEST_SCENE)
    public ResponseResult<SceneDetailResponse> detail(@RequestParam(required = false) Long sceneId) {
        SceneTaskStartReq request = new SceneTaskStartReq() {{setSceneId(sceneId);}};
        WebPluginUtils.fillCloudUserData(request);
        return multipleSceneApi.detail(request);
    }

    /**
     * 获取业务流程列表
     *
     * @return 业务流程列表
     */
    @GetMapping("business_activity_flow")
    @ApiOperation("获取业务流程列表 - 压测场景用")
    public ResponseResult<List<SceneEntity>> listBusinessActivityFlow() {
        return ResponseResult.success(sceneService.businessActivityFlowList());
    }

    /**
     * 获取业务流程详情
     *
     * @param id 业务流程主键
     * @return 业务流程详情
     */
    @GetMapping("business_activity_flow/detail")
    @ApiOperation("获取业务流程详情 - 压测场景用")
    public ResponseResult<SceneEntity> businessActivityFlowDetail(
        @RequestParam(name = "id", required = false) Long id) {
        return ResponseResult.success(sceneService.businessActivityFlowDetail(id));
    }

    /**
     * 获取业务流程下的漏数脚本
     *
     * @param id 业务流程主键
     * @return 漏数校验脚本
     */
    @GetMapping("business_activity_flow/leak_sql")
    @ApiOperation("获取业务流程下的漏数脚本 - 压测场景用")
    public ResponseResult<List<LeakSqlBatchRefsResponse>> businessActivityFlowLeakSql(
        @RequestParam(name = "id", required = false) Long id) {
        // 1. 获取业务流程关联的业务活动
        List<SceneLinkRelateResult> links = sceneService.getSceneLinkRelates(id);
        // 2. 组装业务活动主键
        List<Long> collect = links.stream().map(t -> Long.valueOf(t.getBusinessLinkId()))
            .collect(Collectors.toList());
        // 3. 提前返回
        if (collect.size() == 0) {return ResponseResult.success(new ArrayList<>(0));}
        // 4. 调用原有业务逻辑
        List<LeakSqlBatchRefsResponse> batchLeakCheckConfig =
            leakSqlService.getBatchLeakCheckConfig(new LeakSqlBatchRefsRequest() {{
                setBusinessActivityIds(collect);
            }});
        // 5. 返回数据
        return ResponseResult.success(batchLeakCheckConfig);
    }

    /**
     * 获取建议Pod数
     *
     * @param request 请求体
     * @return 最大/最小Pod数
     */
    @PostMapping("pod_number")
    @ApiOperation("获取建议Pod数 - 压测场景用")
    public ResponseResult<StrategyResp> getPodNumber(@RequestBody Map<String, Map<String, String>> request) {
        AtomicInteger tpsNum = new AtomicInteger(0);
        AtomicInteger concurrenceNum = new AtomicInteger(0);
        request.forEach((k, v) -> {
            // 并发模式
            if ("0".equals(v.get("type"))) {concurrenceNum.updateAndGet(t -> t + Integer.parseInt(v.get("threadNum")));}
            // TPS模式
            if ("1".equals(v.get("type"))) {tpsNum.updateAndGet(t -> t + Integer.parseInt(v.get("tpsSum")));}
        });
        return sceneManageService.getIpNum(concurrenceNum.get(), tpsNum.get());
    }

    /**
     * 组装场景文件列表
     *
     * @param scriptId 脚本主键
     * @return 文件列表
     */
    private List<SceneRequest.File> assembleFileList(long scriptId) {
        // 根据脚本主键获取文件主键集合
        List<ScriptFileRefResult> scriptFileRefResults = scriptFileRefDao.selectFileIdsByScriptDeployId(scriptId);
        // 根据文件主键集合查询文件信息
        List<Long> fileIds = scriptFileRefResults.stream().map(ScriptFileRefResult::getFileId).collect(Collectors.toList());
        // 组装返回数据
        return fileManageDao.selectFileManageByIds(fileIds).stream().map(t -> {
            Map<String, Object> extend = JSONObject.parseObject(t.getFileExtend(), new TypeReference<Map<String, Object>>() {});
            return new SceneRequest.File() {{
                setName(t.getFileName());
                setPath(t.getUploadPath());
                setType(t.getFileType());
                setExtend(extend);
            }};
        }).collect(Collectors.toList());
    }
}