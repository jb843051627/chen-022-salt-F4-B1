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

    private static final int MAX_STAGE = 3;
    private static final int STATUS_INIT = 0;
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

    /** 已封卷（终态）判定：status=2，或步次已到末档（存量老行两列可能不同步，任一满足即视为封卷） */
    private static boolean isTerminal(TSaltRecordFlow r) {
        if (r.getStatus() != null && r.getStatus().intValue() == STATUS_TERMINAL) {
            return true;
        }
        return r.getStage() != null && r.getStage().intValue() >= MAX_STAGE;
    }

    /**
     * 推进一档：步次 +1（不跳档），到末档置已封卷、其余置在办；已封卷为终态，拒绝返回 null。
     */
    @Override
    public TSaltRecordFlow advance(Long id, String remark) {
        TSaltRecordFlow r = this.saltRecordFlowMapper.selectById(id);
        if (r == null || isTerminal(r)) {
            return null;
        }
        int st = r.getStage() == null ? 0 : r.getStage().intValue();
        if (st < 0) {
            st = 0;
        }
        int next = Math.min(st + 1, MAX_STAGE);
        r.setStage(Integer.valueOf(next));
        r.setStatus(Integer.valueOf(next >= MAX_STAGE ? STATUS_TERMINAL : STATUS_ACTIVE));
        r.setLastAction(remark);
        this.saltRecordFlowMapper.updateById(r);
        return r;
    }

    /**
     * 回退一档：步次 -1（不到零不清零跳变），退到 0 档置未起、其余置在办；已封卷为终态，拒绝返回 null。
     */
    @Override
    public TSaltRecordFlow rollback(Long id, String remark) {
        TSaltRecordFlow r = this.saltRecordFlowMapper.selectById(id);
        if (r == null || isTerminal(r)) {
            return null;
        }
        int st = r.getStage() == null ? 0 : r.getStage().intValue();
        int next = Math.max(st - 1, 0);
        r.setStage(Integer.valueOf(next));
        r.setStatus(Integer.valueOf(next == 0 ? STATUS_INIT : STATUS_ACTIVE));
        r.setLastAction(remark);
        this.saltRecordFlowMapper.updateById(r);
        return r;
    }

    /** 修改内容：已办结（已封卷）不得再改，被拒返回 false */
    @Override
    public boolean updateContent(Long id, String remark) {
        TSaltRecordFlow r = this.saltRecordFlowMapper.selectById(id);
        if (r == null || isTerminal(r)) {
            return false;
        }
        r.setContent(remark);
        return this.saltRecordFlowMapper.updateById(r) > 0;
    }

    /** 删除单据：已办结（已封卷）不得删除，被拒返回 false */
    @Override
    public boolean remove(Long id) {
        TSaltRecordFlow r = this.saltRecordFlowMapper.selectById(id);
        if (r == null || isTerminal(r)) {
            return false;
        }
        return this.saltRecordFlowMapper.deleteById(id) > 0;
    }

}
