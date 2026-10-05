package com.fc.v2.service.impl;

import java.util.List;

import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fc.v2.mapper.auto.TSaltRecordFlowMapper;
import com.fc.v2.model.auto.TSaltRecordFlow;
import com.fc.v2.service.ITSaltRecordFlowService;

/**
 * 跨省经营备案单 Service业务层处理（state-machine 形状：单据流转）
 *
 * @author fuce
 * @date 2026-09-14
 */
@Service
public class TSaltRecordFlowServiceImpl implements ITSaltRecordFlowService {

    /** 步次：0递单受理 1材料核验 2生效确认 3缴回封卷 */
    private static final int MIN_STAGE = 0;
    private static final int MAX_STAGE = 3;
    /** 状态：0未起 1在办 2已封卷（终态） */
    private static final int STATUS_ACTIVE = 1;
    private static final int STATUS_TERMINAL = 2;

    @javax.annotation.Resource
    private TSaltRecordFlowMapper saltRecordFlowMapper;

    @Override
    public TSaltRecordFlow selectTSaltRecordFlowById(Long id) {
        return this.saltRecordFlowMapper.selectById(id);
    }

    @Override
    public List<TSaltRecordFlow> selectTSaltRecordFlowList(QueryWrapper<TSaltRecordFlow> queryWrapper) {
        return this.saltRecordFlowMapper.selectList(queryWrapper);
    }

    @Override
    public TSaltRecordFlow advance(Long id, String remark) {
        TSaltRecordFlow r = this.saltRecordFlowMapper.selectById(id);
        if (r == null) {
            return null;
        }
        // 已封卷终态不许再推；步次到顶也不许。
        if (r.getStatus() != null && r.getStatus().intValue() == STATUS_TERMINAL) {
            return null;
        }
        int st = r.getStage() == null ? MIN_STAGE : r.getStage().intValue();
        if (st >= MAX_STAGE) {
            return null;
        }
        int next = st + 1;
        r.setStage(Integer.valueOf(next));
        // 走到缴回封卷即封卷；之前各档一律在办。状态口径与回退/改内容/删除共用。
        r.setStatus(Integer.valueOf(next == MAX_STAGE ? STATUS_TERMINAL : STATUS_ACTIVE));
        r.setLastAction(remark);
        this.saltRecordFlowMapper.updateById(r);
        return r;
    }

    @Override
    public TSaltRecordFlow rollback(Long id, String remark) {
        TSaltRecordFlow r = this.saltRecordFlowMapper.selectById(id);
        if (r == null) {
            return null;
        }
        // 已封卷终态不许回退。
        if (r.getStatus() != null && r.getStatus().intValue() == STATUS_TERMINAL) {
            return null;
        }
        int st = r.getStage() == null ? MIN_STAGE : r.getStage().intValue();
        // 只回退一档；递单受理已是头一档，无可退。
        if (st <= MIN_STAGE) {
            return null;
        }
        r.setStage(Integer.valueOf(st - 1));
        r.setStatus(Integer.valueOf(STATUS_ACTIVE));
        r.setLastAction(remark);
        this.saltRecordFlowMapper.updateById(r);
        return r;
    }

    @Override
    public boolean updateContent(Long id, String remark) {
        TSaltRecordFlow r = this.saltRecordFlowMapper.selectById(id);
        if (r == null) {
            return false;
        }
        // 已办结（封卷）不得再改。
        if (r.getStatus() != null && r.getStatus().intValue() == STATUS_TERMINAL) {
            return false;
        }
        r.setContent(remark);
        return this.saltRecordFlowMapper.updateById(r) > 0;
    }

    @Override
    public boolean remove(Long id) {
        TSaltRecordFlow r = this.saltRecordFlowMapper.selectById(id);
        if (r == null) {
            return false;
        }
        // 已办结（封卷）不得删除。
        if (r.getStatus() != null && r.getStatus().intValue() == STATUS_TERMINAL) {
            return false;
        }
        return this.saltRecordFlowMapper.deleteById(id) > 0;
    }

}
