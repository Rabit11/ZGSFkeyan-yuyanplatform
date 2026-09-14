package com.comac.rpm.modules.system.service;

import com.comac.rpm.config.BackupProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.stereotype.Component;

import java.time.ZoneId;

/**
 * 按配置 cron 做全量定时备份（默认每天 02:00）。
 */
@Component
public class BackupScheduler implements SchedulingConfigurer {

    private static final Logger log = LoggerFactory.getLogger(BackupScheduler.class);

    private final BackupService backupService;
    private final BackupProperties props;

    public BackupScheduler(BackupService backupService, BackupProperties props) {
        this.backupService = backupService;
        this.props = props;
    }

    @Override
    public void configureTasks(ScheduledTaskRegistrar taskRegistrar) {
        taskRegistrar.addTriggerTask(
                () -> {
                    try {
                        backupService.create("SCHEDULED", "定时自动备份");
                    } catch (Exception e) {
                        log.warn("定时备份失败: {}", e.getMessage());
                    }
                },
                new CronTrigger(props.getCron(), ZoneId.of("Asia/Shanghai")));
    }
}
