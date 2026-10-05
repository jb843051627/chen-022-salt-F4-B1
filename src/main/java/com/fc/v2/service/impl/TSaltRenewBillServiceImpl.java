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

    /** 档口：0县初核 1市复审 2省核准；省核准凑齐即办结 */
    private static final int MAX_NODE = 2;
    /** 同档算齐办法：0一名即可 1两名须同判 */
    private static final int MODE_OR = 0;
    private static final int MODE_AND = 1;
    /** 核签情形：0在核 1已办结 2已挪回 */
    private static final int STATUS_RUNNING = 0;
    private static final int STATUS_PASS = 1;
    private static final int STATUS_VETO = 2;

    @javax.annotation.Resource
    private TSaltRenewBillMapper saltRenewBillMapper;

    @Override
    public TSaltRenewBill selectTSaltRenewBillById(Long id) {
        return this.saltRenewBillMapper.selectById(id);
    }

    @Override
    public TSaltRenewBill approve(Long id, String approver, String comment) {
        TSaltRenewBill r = this.saltRenewBillMapper.selectById(id);
        if (r == null || approver == null || approver.trim().isEmpty()) {
            return null;
        }
        // 存量兼容：status 为空按在核论；已办结/已挪回不得再落印。
        int status = r.getStatus() == null ? STATUS_RUNNING : r.getStatus().intValue();
        if (status == STATUS_PASS || status == STATUS_VETO) {
            return null;
        }
        int node = r.getNodeNo() == null ? 0 : r.getNodeNo().intValue();
        if (node > MAX_NODE) {
            return null;
        }

        int signed = r.getSignCount() == null ? 0 : r.getSignCount().intValue();
        signed++;
        r.setSignCount(Integer.valueOf(signed));

        if (signed < quorumOf(r)) {
            // 本档印还没落齐，留在当档继续等。
            r.setStatus(Integer.valueOf(STATUS_RUNNING));
            this.saltRenewBillMapper.updateById(r);
            return r;
        }

        // 本档算齐：清印挪档；省核准算齐即办结。
        r.setSignCount(Integer.valueOf(0));
        if (node >= MAX_NODE) {
            r.setStatus(Integer.valueOf(STATUS_PASS));
        } else {
            r.setNodeNo(Integer.valueOf(node + 1));
            r.setStatus(Integer.valueOf(STATUS_RUNNING));
        }
        this.saltRenewBillMapper.updateById(r);
        return r;
    }

    /** 本档应落印数：AND 模式以 needCount 为准（缺省兜 1），OR 模式一名即可 */
    private int quorumOf(TSaltRenewBill r) {
        int mode = r.getSignMode() == null ? MODE_OR : r.getSignMode().intValue();
        if (mode == MODE_AND) {
            Integer need = r.getNeedCount();
            return need == null || need.intValue() < 1 ? 1 : need.intValue();
        }
        return 1;
    }

    @Override
    public TSaltRenewBill reject(Long id, String approver, String comment) {
        TSaltRenewBill r = this.saltRenewBillMapper.selectById(id);
        if (r == null || approver == null || approver.trim().isEmpty()) {
            return null;
        }
        int status = r.getStatus() == null ? STATUS_RUNNING : r.getStatus().intValue();
        if (status == STATUS_PASS || status == STATUS_VETO) {
            return null;
        }
        // 否决即挪回；印数清零，交回上一环节重走由 rollback 落档口。
        r.setSignCount(Integer.valueOf(0));
        r.setStatus(Integer.valueOf(STATUS_VETO));
        this.saltRenewBillMapper.updateById(r);
        return r;
    }

    @Override
    public TSaltRenewBill rollback(Long id, String comment) {
        TSaltRenewBill r = this.saltRenewBillMapper.selectById(id);
        if (r == null) {
            return null;
        }
        int status = r.getStatus() == null ? STATUS_RUNNING : r.getStatus().intValue();
        if (status == STATUS_PASS) {
            // 已办结不得退回。
            return null;
        }
        int node = r.getNodeNo() == null ? 0 : r.getNodeNo().intValue();
        if (node <= 0) {
            // 县初核已是头一档，无处可退。
            return null;
        }
        r.setNodeNo(Integer.valueOf(node - 1));
        r.setSignCount(Integer.valueOf(0));
        r.setStatus(Integer.valueOf(STATUS_RUNNING));
        this.saltRenewBillMapper.updateById(r);
        return r;
    }
}
