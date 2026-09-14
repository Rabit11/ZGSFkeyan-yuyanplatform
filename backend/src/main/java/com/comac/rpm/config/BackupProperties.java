package com.comac.rpm.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 备份回滚策略
 */
@ConfigurationProperties(prefix = "rpm.backup")
public class BackupProperties {

    /** 本地快照目录 */
    private String dir = "./data/backups";
    /** 成功快照最多保留天数 */
    private int keepDays = 30;
    /** 成功快照最多保留份数（含手动/风险前置） */
    private int maxCount = 20;
    /** 定时备份 cron，默认每天 02:00 */
    private String cron = "0 0 2 * * ?";
    /** 是否把附件对象一并复制进备份前缀（默认只记清单，恢复后缺件可对账） */
    private boolean includeFiles = false;

    public String getDir() {
        return dir;
    }

    public void setDir(String dir) {
        this.dir = dir;
    }

    public int getKeepDays() {
        return keepDays;
    }

    public void setKeepDays(int keepDays) {
        this.keepDays = keepDays;
    }

    public int getMaxCount() {
        return maxCount;
    }

    public void setMaxCount(int maxCount) {
        this.maxCount = maxCount;
    }

    public String getCron() {
        return cron;
    }

    public void setCron(String cron) {
        this.cron = cron;
    }

    public boolean isIncludeFiles() {
        return includeFiles;
    }

    public void setIncludeFiles(boolean includeFiles) {
        this.includeFiles = includeFiles;
    }
}
