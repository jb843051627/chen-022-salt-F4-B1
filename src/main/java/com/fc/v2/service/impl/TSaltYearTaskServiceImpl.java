package com.fc.v2.service.impl;

import java.util.ArrayList;
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

    /** 条目情形：0待派 1已派出 2分不出 */
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
     * 可处理条目口径（与 runOnce 共用同一筛选，不得一处查全表一处查半表）：
     * 未删除 + 待派 + 已到止点时刻（due_at <= at，含当日）。存量行 status/del_flag 为空按待派、正常论。
     */
    @Override
    public List<TSaltYearTask> listDue(Date at) {
        if (at == null) {
            return new ArrayList<TSaltYearTask>();
        }
        List<TSaltYearTask> all = this.saltYearTaskMapper.selectList(new QueryWrapper<TSaltYearTask>());
        List<TSaltYearTask> due = new ArrayList<TSaltYearTask>();
        for (TSaltYearTask r : all) {
            if (r.getDelFlag() != null && r.getDelFlag().intValue() == 1) {
                continue;
            }
            if (r.getStatus() != null && r.getStatus().intValue() != STATUS_WAIT) {
                continue;
            }
            if (r.getDueAt() != null && !r.getDueAt().after(at)) {
                due.add(r);
            }
        }
        return due;
    }

    /**
     * 执行一次：空批次直接返回 0，不报错；逐条处理，单条失败（含数据残缺/落库异常）
     * 跳过继续，不牵连其余条目。amount（可提前开口日数）为空或为负即判"分不出"，
     * 不计成功；能派出的置已派出。返回成功派出条数。
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
                if (r.getAmount() == null || r.getAmount().signum() < 0) {
                    r.setStatus(Integer.valueOf(STATUS_FAIL));
                    this.saltYearTaskMapper.updateById(r);
                    continue;
                }
                r.setStatus(Integer.valueOf(STATUS_DONE));
                this.saltYearTaskMapper.updateById(r);
                ok++;
            } catch (RuntimeException ex) {
                // 单条落库失败：跳过，下一条照派。
                continue;
            }
        }
        return ok;
    }
}
