package com.comac.rpm.common;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 统一分页返回体：{ records, total, page, size }
 */
public class PageVO<T> implements Serializable {

    private static final long serialVersionUID = 1L;

    private List<T> records = new ArrayList<>();
    private long total = 0L;
    private long page = 1L;
    private long size = 10L;

    public PageVO() {
    }

    public static <T> PageVO<T> of(Page<T> p) {
        PageVO<T> vo = new PageVO<>();
        if (p != null) {
            vo.setRecords(p.getRecords());
            vo.setTotal(p.getTotal());
            vo.setPage(p.getCurrent());
            vo.setSize(p.getSize());
        }
        return vo;
    }

    public static <T> PageVO<T> of(List<T> records, long total, long page, long size) {
        PageVO<T> vo = new PageVO<>();
        vo.setRecords(records == null ? new ArrayList<>() : records);
        vo.setTotal(total);
        vo.setPage(page);
        vo.setSize(size);
        return vo;
    }

    public List<T> getRecords() {
        return records;
    }

    public void setRecords(List<T> records) {
        this.records = records;
    }

    public long getTotal() {
        return total;
    }

    public void setTotal(long total) {
        this.total = total;
    }

    public long getPage() {
        return page;
    }

    public void setPage(long page) {
        this.page = page;
    }

    public long getSize() {
        return size;
    }

    public void setSize(long size) {
        this.size = size;
    }
}
