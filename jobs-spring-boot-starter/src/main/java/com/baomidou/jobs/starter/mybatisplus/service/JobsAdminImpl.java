package com.baomidou.jobs.starter.mybatisplus.service;

import com.baomidou.jobs.core.JobsConstant;
import com.baomidou.jobs.core.model.HandleCallbackParam;
import com.baomidou.jobs.core.model.RegistryParam;
import com.baomidou.jobs.core.web.IJobsAdmin;
import com.baomidou.jobs.core.web.JobsResponse;
import com.baomidou.jobs.starter.entity.JobsInfo;
import com.baomidou.jobs.starter.entity.JobsLog;
import com.baomidou.jobs.starter.service.IJobsInfoService;
import com.baomidou.jobs.starter.service.IJobsLogService;
import com.baomidou.jobs.starter.service.IJobsRegistryService;
import com.baomidou.jobs.starter.trigger.JobsTriggerPool;
import com.baomidou.jobs.starter.trigger.TriggerTypeEnum;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.text.MessageFormat;
import java.util.Date;
import java.util.List;

/**
 * @author xuxueli 2017-07-27 21:54:20
 */
@Slf4j
@Service
public class JobsAdminImpl implements IJobsAdmin {
    @Autowired
    public IJobsLogService jobLogService;
    @Autowired
    private IJobsInfoService jobInfoService;
    @Autowired
    private IJobsRegistryService jobRegistryService;


    @Override
    public JobsResponse<String> callback(List<HandleCallbackParam> callbackParamList) {
        for (HandleCallbackParam handleCallbackParam : callbackParamList) {
            JobsResponse<String> callbackResult = callback(handleCallbackParam);
            log.debug(">>>>>>>>> JobApiController.callback {}, handleCallbackParam={}, callbackResult={}",
                    callbackResult.toString(), handleCallbackParam, callbackResult);
        }

        return JobsResponse.ok();
    }

    private JobsResponse<String> callback(HandleCallbackParam handleCallbackParam) {
        // valid log item
        JobsLog log = jobLogService.getById(handleCallbackParam.getLogId());
        if (log == null) {
            return JobsResponse.failed("log item not found.");
        }
        if (log.getHandleCode() > 0) {
            // avoid repeat callback, trigger child job etc
            return JobsResponse.failed("log repeate callback.");
        }

        // trigger success, to trigger child job
        String callbackMsg = null;
        if (JobsConstant.CODE_SUCCESS == handleCallbackParam.getExecuteResult().getCode()) {
            JobsInfo xxlJobInfo = jobInfoService.getById(log.getJobId());
            if (xxlJobInfo != null && xxlJobInfo.getChildJobid() != null && xxlJobInfo.getChildJobid().trim().length() > 0) {
                callbackMsg = "<br><br><span style=\"color:#00c0ef;\" > >>>>>>>>>>>触发子任务<<<<<<<<<<< </span><br>";

                String[] childJobIds = xxlJobInfo.getChildJobid().split(",");
                for (int i = 0; i < childJobIds.length; i++) {
                    int childJobId = (childJobIds[i] != null && childJobIds[i].trim().length() > 0 && isNumeric(childJobIds[i])) ? Integer.valueOf(childJobIds[i]) : -1;
                    if (childJobId > 0) {

                        JobsTriggerPool.trigger(childJobId, TriggerTypeEnum.PARENT, -1, null, null);
                        JobsResponse<String> triggerChildResult = JobsResponse.ok();

                        // add msg
                        callbackMsg += MessageFormat.format("{0}/{1} [任务ID={2}], 触发{3}, 触发备注: {4} <br>",
                                (i + 1),
                                childJobIds.length,
                                childJobIds[i],
                                (triggerChildResult.getCode() == JobsConstant.CODE_SUCCESS ? "成功" : "失败"),
                                triggerChildResult.getMsg());
                    } else {
                        callbackMsg += MessageFormat.format("{0}/{1} [任务ID={2}], 触发失败, 触发备注: 任务ID格式错误 <br>",
                                (i + 1),
                                childJobIds.length,
                                childJobIds[i]);
                    }
                }

            }
        }

        // handle msg
        StringBuffer handleMsg = new StringBuffer();
        if (log.getHandleMsg() != null) {
            handleMsg.append(log.getHandleMsg()).append("<br>");
        }
        if (handleCallbackParam.getExecuteResult().getMsg() != null) {
            handleMsg.append(handleCallbackParam.getExecuteResult().getMsg());
        }
        if (callbackMsg != null) {
            handleMsg.append(callbackMsg);
        }

        // success, save log
        log.setHandleTime(new Date());
        log.setHandleCode(handleCallbackParam.getExecuteResult().getCode());
        log.setHandleMsg(handleMsg.toString());
        jobLogService.updateById(log);
        return JobsResponse.ok();
    }

    private boolean isNumeric(String str) {
        try {
            int result = Integer.valueOf(str);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    @Override
    public JobsResponse<String> registry(RegistryParam registryParam) {
        int ret = jobRegistryService.update(registryParam.getRegistGroup(), registryParam.getRegistryKey(), registryParam.getRegistryValue());
        if (ret < 1) {
            jobRegistryService.save(registryParam.getRegistGroup(), registryParam.getRegistryKey(), registryParam.getRegistryValue());
        }
        return JobsResponse.ok();
    }

    @Override
    public JobsResponse<String> registryRemove(RegistryParam registryParam) {
        jobRegistryService.remove(registryParam.getRegistGroup(), registryParam.getRegistryKey(), registryParam.getRegistryValue());
        return JobsResponse.ok();
    }

}