package com.fc.v2.service.impl;

import java.util.Date;
import java.util.List;

import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fc.v2.mapper.auto.TSaltYearTaskMapper;
import com.fc.v2.model.auto.TSaltYearTask;
import com.fc.v2.service.ITSaltYearTaskService;

/**
 * 年检到期催办单 Service业务层处理（scheduling-job 形状：周期执行）
 *
 * @author fuce
 * @date 2026-09-14
 */
@Service
public class TSaltYearTaskServiceImpl implements ITSaltYearTaskService {

    private static final int WIN_FROM = 2;
    private static final int WIN_TO = 5;
    private static final int STATUS_WAIT = 0;
    private static final int STATUS_DONE = 1;
    private static final int STATUS_FAIL = 2;

    @javax.annotation.Resource
    private TSaltYearTaskMapper saltYearTaskMapper;

    @Override
    public TSaltYearTask selectTSaltYearTaskById(Long id) {
        return this.saltYearTaskMapper.selectById(id);
    }

    /**
     * 时段比较统一走**墙钟字符串**（yyyy-MM-dd HH:mm:ss）：JDBC 的 serverTimezone 与本机
     * 时区不对称，直接把 java.util.Date 作参数会整体偏移，使"恰好到期"这类边界用例错判。
     */
    private static String ts(Date d) {
        return new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(d);
    }

    /**
     * 该时刻可处理的条目，三个条件同时满足：
     * <ul>
     * <li>执行窗口内：at 的墙钟小时落在 [WIN_FROM, WIN_TO)（每日 02:00 含 — 05:00 不含）；</li>
     * <li>到期：due_at &lt;= at（**含恰好到期**，墙钟字符串比较）；</li>
     * <li>尚未处理：status = 0 待派。</li>
     * </ul>
     * 存量老行取舍：status 缺省按待派、del_flag 缺省按正常（老系统未维护这两列的行照样能被兜住）。
     */
    @Override
    public List<TSaltYearTask> listDue(Date at) {
        List<TSaltYearTask> due = new java.util.ArrayList<TSaltYearTask>();
        if (at == null) {
            return due;
        }
        int hour = Integer.parseInt(new java.text.SimpleDateFormat("HH").format(at));
        if (hour < WIN_FROM || hour >= WIN_TO) {
            return due;
        }
        String now = ts(at);
        List<TSaltYearTask> all = this.saltYearTaskMapper.selectList(new QueryWrapper<TSaltYearTask>());
        for (TSaltYearTask r : all) {
            if (r.getDelFlag() != null && r.getDelFlag().intValue() == 1) {
                continue;
            }
            if (r.getStatus() != null && r.getStatus().intValue() != STATUS_WAIT) {
                continue;
            }
            if (r.getDueAt() == null) {
                continue;
            }
            if (ts(r.getDueAt()).compareTo(now) <= 0) {
                due.add(r);
            }
        }
        return due;
    }

    /**
     * 执行一次：逐条派出并落库（已派出）；amount 缺失的存量老行本分不出，标记"分不出"后跳过；
     * 单条异常跳过继续，不拖垮整轮。返回成功条数；无可处理条目返回 0。
     */
    @Override
    public int runOnce(Date at) {
        List<TSaltYearTask> due = listDue(at);
        if (due.isEmpty()) {
            return 0;
        }
        int ok = 0;
        for (TSaltYearTask r : due) {
            try {
                if (r.getAmount() == null) {
                    r.setStatus(Integer.valueOf(STATUS_FAIL));
                    this.saltYearTaskMapper.updateById(r);
                    continue;
                }
                r.setStatus(Integer.valueOf(STATUS_DONE));
                this.saltYearTaskMapper.updateById(r);
                ok++;
            } catch (Exception e) {
                // 单条失败跳过继续
            }
        }
        return ok;
    }
}
