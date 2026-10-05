package com.fc.v2.service;

import com.fc.v2.model.auto.TSaltRenewBill;

/**
 * 延续换证核签单 Service接口（approval-chain 形状：多阶段签批，无增删改查入口）
 * <p>
 * 口径定死：一名即可档一票挪档；两名须同判档凑齐本档应落人数（need_count）才挪档，
 * 未凑齐原地计印；签满末档（node_no=2 省核准）方办结；挪档后已落印数清零重计。
 * 仅在核单可签可否：已办结、已挪回一律拒绝；存量老行 status 缺省按在核。
 * 已办结不得退回；退回档口 -1、已落印数清零、情形回在核。
 *
 * @author fuce
 * @date 2026-09-14
 */
public interface ITSaltRenewBillService {

    /** 按主键回查单据 */
    TSaltRenewBill selectTSaltRenewBillById(Long id);

    /** 签批一票：返回更新后的单据；被拒返回 null */
    TSaltRenewBill approve(Long id, String approver, String comment);

    /** 否决：返回更新后的单据；被拒返回 null */
    TSaltRenewBill reject(Long id, String approver, String comment);

    /** 退回上一环节：返回更新后的单据；被拒返回 null */
    TSaltRenewBill rollback(Long id, String comment);
}
