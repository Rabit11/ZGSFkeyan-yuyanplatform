package com.comac.rpm.common.util;

import com.comac.rpm.common.enums.ColorStatusEnum;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * 四色预警计算工具（全平台核心规则）
 * <p>
 * 规则：
 * <ol>
 *   <li>已完成（finished=true）→ GREEN</li>
 *   <li>无到期日 → BLUE</li>
 *   <li>已逾期（到期日 &lt; 今天）→ RED</li>
 *   <li>30 天内到期（含 30 天）→ YELLOW</li>
 *   <li>其余 → BLUE</li>
 * </ol>
 * 严重程度：RED &gt; YELLOW &gt; BLUE &gt; GREEN
 */
public final class ColorUtil {

    private ColorUtil() {
    }

    /** 临期阈值（天） */
    public static final int WARN_DAYS = 30;

    /**
     * 四色状态计算
     *
     * @param dueDate  到期日
     * @param finished 是否已完成
     */
    public static ColorStatusEnum calc(LocalDate dueDate, boolean finished) {
        if (finished) {
            return ColorStatusEnum.GREEN;
        }
        if (dueDate == null) {
            return ColorStatusEnum.BLUE;
        }
        long days = ChronoUnit.DAYS.between(LocalDate.now(), dueDate);
        if (days < 0) {
            return ColorStatusEnum.RED;
        }
        if (days <= WARN_DAYS) {
            return ColorStatusEnum.YELLOW;
        }
        return ColorStatusEnum.BLUE;
    }

    /**
     * 直接返回颜色编码字符串
     */
    public static String calcCode(LocalDate dueDate, boolean finished) {
        return calc(dueDate, finished).getCode();
    }

    /**
     * 取一组颜色中最严重的一个（RED &gt; YELLOW &gt; BLUE &gt; GREEN）
     */
    public static ColorStatusEnum worst(Collection<String> colors) {
        ColorStatusEnum result = ColorStatusEnum.GREEN;
        if (colors == null) {
            return result;
        }
        for (String c : colors) {
            if (c == null) {
                continue;
            }
            ColorStatusEnum e = ColorStatusEnum.of(c);
            if (e.weight() > result.weight()) {
                result = e;
            }
        }
        return result;
    }

    /**
     * 取一组颜色中最严重的一个，返回编码字符串
     */
    public static String worstCode(Collection<String> colors) {
        return worst(colors).getCode();
    }

    /**
     * 把字符串按 逗号/顿号/分号/斜杠 拆分成列表
     */
    public static List<String> splitMaterials(String text) {
        List<String> list = new ArrayList<>();
        if (text == null) {
            return list;
        }
        String[] arr = text.split("[,，、;；/|]");
        for (String s : arr) {
            String v = s.trim();
            if (!v.isEmpty()) {
                list.add(v);
            }
        }
        return list;
    }
}
