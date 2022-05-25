package io.shulie.takin.web.biz.service.interfaceperformance.vo;

import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

import java.util.List;
import io.shulie.takin.cloud.sdk.model.response.scenemanage.*;

/**
 * @Author: vernon
 * @Date: 2022/5/25 14:59
 * @Description:
 */
public class PressureConfigDetailVO {
    /**
     * 基础信息
     */
    private BasicInfo basicInfo;

    /**
     * 验证信息
     */
    private DataValidation dataValidation;

    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class BasicInfo extends io.shulie.takin.cloud.sdk.model.response.scenemanage.SceneRequest.BasicInfo {
        @ApiModelProperty(value = "是否定时执行")
        private Boolean isScheduler;
        @ApiModelProperty(name = "executeTime", value = "定时执行时间")
        private String executeTime;
    }

    @Getter
    @Setter
    public static class DataValidation extends SceneRequest.DataValidation {
        @ApiModelProperty("排除的应用id列表")
        private List<String> excludedApplicationIds;
    }

}
