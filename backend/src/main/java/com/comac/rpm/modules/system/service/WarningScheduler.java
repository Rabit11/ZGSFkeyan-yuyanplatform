package com.comac.rpm.modules.system.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 预警定时任务：每天 06:10 扫描一次（到期前 30 天黄色、超期红色），启动后 60 秒补扫一次。
 * cron 可用 rpm.warning.cron 覆盖。
 */
@Component
public class WarningScheduler {

    private static final Logger log = LoggerFactory.getLogger(WarningScheduler.class);

    private final WarningService warningService;

    public WarningScheduler(WarningService warningService) {
        this.warningService = warningService;
    }

    @Scheduled(cron = "${rpm.warning.cron:0 10 6 * * ?}")
    public void daily() {
        runSafely("定时");
    }

    @Scheduled(initialDelay = 60_000, fixedDelay = Long.MAX_VALUE)
    public void onStartup() {
        runSafely("启动补扫");
    }

    private void runSafely(String trigger) {
        try {
            int n = warningService.scan();
            log.info("预警扫描（{}）新增 {} 条", trigger, n);
        } catch (Exception e) {
            log.error("预警扫描（{}）失败", trigger, e);
        }
    }
}
