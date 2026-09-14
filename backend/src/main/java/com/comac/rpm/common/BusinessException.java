package com.comac.rpm.common;

/**
 * 业务异常：携带 code / msg，由 GlobalExceptionHandler 统一转成 R
 */
public class BusinessException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private int code = 500;

    public BusinessException(String msg) {
        super(msg);
    }

    public BusinessException(int code, String msg) {
        super(msg);
        this.code = code;
    }

    public int getCode() {
        return code;
    }
}
