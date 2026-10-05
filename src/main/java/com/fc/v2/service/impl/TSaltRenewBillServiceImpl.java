package com.fc.v2.service.impl;

import org.springframework.stereotype.Service;

import com.fc.v2.mapper.auto.TSaltRenewBillMapper;
import com.fc.v2.model.auto.TSaltRenewBill;
import com.fc.v2.service.ITSaltRenewBillService;

/**
 * 延续换证核签单 Service业务层处理（approval-chain 形状：多阶段签批）
 *
 * @author fuce
 * @date 2026-09-14
 */
@Service
public class TSaltRenewBillServiceImpl implements ITSaltRenewBillService {

    private static final int MAX_NODE = 2;
    private static final int MODE_OR = 0;
    private static final int MODE_AND = 1;
    private static final int STATUS_RUNNING = 0;
    private static final int STATUS_PASS = 1;
    private static final int STATUS_VETO = 2;

    @javax.annotation.Resource
    private TSaltRenewBillMapper saltRenewBillMapper;

    @Override
    public TSaltRenewBill selectTSaltRenewBillById(Long id) {
        return this.saltRenewBillMapper.selectById(id);
    }

    /** 在核判定：status=0；存量老行 status 缺省按在核（与名录/挂接两处同口径） */
    private static boolean isRunning(TSaltRenewBill r) {
        return r.getStatus() == null || r.getStatus().intValue() == STATUS_RUNNING;
    }

    /** 本档应落人数：同判档按 need_count，其余一档一人；缺省按 1（存量老行未维护人数时兜住） */
    private static int requiredSigns(TSaltRenewBill r) {
        if (r.getSignMode() != null && r.getSignMode().intValue() == MODE_AND
                && r.getNeedCount() != null && r.getNeedCount().intValue() > 1) {
            return r.getNeedCount().intValue();
        }
        return 1;
    }

    /**
     * 签批一票：仅在核单可签（已办结/已挪回拒签，返回 null）。
     * 一名即可档一票挪档；两名须同判档凑齐应落人数才挪档，未凑齐原地计印。
     * 签满末档（node_no=2）方办结；挪档后已落印数清零重计。
     */
    @Override
    public TSaltRenewBill approve(Long id, String approver, String comment) {
        TSaltRenewBill r = this.saltRenewBillMapper.selectById(id);
        if (r == null || approver == null || approver.trim().isEmpty() || !isRunning(r)) {
            return null;
        }
        int node = r.getNodeNo() == null ? 0 : r.getNodeNo().intValue();
        int signed = (r.getSignCount() == null ? 0 : r.getSignCount().intValue()) + 1;
        if (signed < requiredSigns(r)) {
            // 同判档未凑齐：原地计印，不挪档
            r.setSignCount(Integer.valueOf(signed));
            r.setStatus(Integer.valueOf(STATUS_RUNNING));
            this.saltRenewBillMapper.updateById(r);
            return r;
        }
        if (node >= MAX_NODE) {
            // 末档签满方办结
            r.setSignCount(Integer.valueOf(signed));
            r.setStatus(Integer.valueOf(STATUS_PASS));
        } else {
            r.setNodeNo(Integer.valueOf(node + 1));
            r.setSignCount(Integer.valueOf(0));
            r.setStatus(Integer.valueOf(STATUS_RUNNING));
        }
        this.saltRenewBillMapper.updateById(r);
        return r;
    }

    /**
     * 否决：仅在核单可否（已办结/已挪回拒绝，返回 null）；落已挪回。
     */
    @Override
    public TSaltRenewBill reject(Long id, String approver, String comment) {
        TSaltRenewBill r = this.saltRenewBillMapper.selectById(id);
        if (r == null || approver == null || approver.trim().isEmpty() || !isRunning(r)) {
            return null;
        }
        r.setStatus(Integer.valueOf(STATUS_VETO));
        this.saltRenewBillMapper.updateById(r);
        return r;
    }

    /**
     * 退回上一环节：档口 -1（不到零不跳变），已落印数清零、情形回在核；
     * 已办结不得退回（返回 null）；已挪回单退回即回到在核。
     */
    @Override
    public TSaltRenewBill rollback(Long id, String comment) {
        TSaltRenewBill r = this.saltRenewBillMapper.selectById(id);
        if (r == null || (r.getStatus() != null && r.getStatus().intValue() == STATUS_PASS)) {
            return null;
        }
        int node = r.getNodeNo() == null ? 0 : r.getNodeNo().intValue();
        r.setNodeNo(Integer.valueOf(Math.max(0, node - 1)));
        r.setSignCount(Integer.valueOf(0));
        r.setStatus(Integer.valueOf(STATUS_RUNNING));
        this.saltRenewBillMapper.updateById(r);
        return r;
    }
}
