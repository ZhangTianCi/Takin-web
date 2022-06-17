package io.shulie.takin.cloud.biz.notify.processor.jmeter;

import javax.annotation.Resource;

import io.shulie.takin.cloud.biz.collector.collector.AbstractIndicators;
import io.shulie.takin.cloud.biz.notify.CloudNotifyProcessor;
import io.shulie.takin.cloud.constant.enums.CallbackType;
import io.shulie.takin.cloud.data.util.PressureStartCache;
import io.shulie.takin.cloud.model.callback.basic.JobExample;
import io.shulie.takin.web.common.util.RedisClientUtil;
import org.springframework.stereotype.Component;

@Component
public class PressureInterruptProcessor extends AbstractIndicators
    implements CloudNotifyProcessor<PressureInterruptNotifyParam> {

    @Resource
    private RedisClientUtil redisClientUtil;

    @Override
    public String process(PressureInterruptNotifyParam param) {
        processInterrupt(param);
        return String.valueOf(param.getData().getResourceId());
    }

    private void processInterrupt(PressureInterruptNotifyParam param) {
        JobExample data = param.getData();
        String resourceId = String.valueOf(data.getResourceId());
        if (!redisClientUtil.hasKey(PressureStartCache.getResourceKey(resourceId))) {
            return;
        }
        String jmeterId = String.valueOf(data.getJobExampleId());
        if (redisClientUtil.lockNoExpire(PressureStartCache.getJmeterErrorFirstKey(resourceId), jmeterId)) {
            callRunningFailedEvent(String.valueOf(data.getResourceId()), "jmeter正常中断", true);
        }
        removeSuccessKey(resourceId, String.valueOf(data.getResourceExampleId()), jmeterId, param.getTime());
    }

    @Override
    public CallbackType type() {
        return CallbackType.JOB_EXAMPLE_SUCCESSFUL;
    }
}
