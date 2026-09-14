package com.comac.rpm.modules.file;

import com.comac.rpm.common.BusinessException;

public final class TransformFilePolicy {
    private TransformFilePolicy() {}
    public static final String URL_PREFIX = "/api/files/transform/download?fileId=";
    public static String fileId(String url) {
        if (url == null || !url.matches("/api/files/transform/download\\?fileId=[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"))
            throw new BusinessException(400, "请在本项目成果转化页面上传佐证材料");
        return url.substring(URL_PREFIX.length());
    }
}
