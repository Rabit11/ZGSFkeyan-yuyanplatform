package com.comac.rpm.common.util;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * 业务编号生成工具
 */
public final class SeqUtil {

    private SeqUtil() {
    }

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");

    /** 项目编号：XM + yyyyMMdd + 6 位序列 */
    public static String projectNo(long seq) {
        return "XM" + LocalDate.now().format(DATE_FMT) + String.format("%06d", seq);
    }

    /** 成果编号：CG + yyyyMMdd + 4 位序列 */
    public static String achievementNo(long seq) {
        return "CG" + LocalDate.now().format(DATE_FMT) + String.format("%04d", seq);
    }

    /** 申报单号：SB + yyyyMMdd + 4 位序列 */
    public static String applyNo(long seq) {
        return "SB" + LocalDate.now().format(DATE_FMT) + String.format("%04d", seq);
    }

    /** 变更单号：BG + yyyyMMdd + 4 位序列 */
    public static String changeNo(long seq) {
        return "BG" + LocalDate.now().format(DATE_FMT) + String.format("%04d", seq);
    }

    /** 备案编号：BA + yyyyMMdd + 4 位序列 */
    public static String filingNo(long seq) {
        return "BA" + LocalDate.now().format(DATE_FMT) + String.format("%04d", seq);
    }

    /** 拨付申请单号：BF + yyyyMMdd + 4 位序列 */
    public static String transferNo(long seq) {
        return "BF" + LocalDate.now().format(DATE_FMT) + String.format("%04d", seq);
    }
}
