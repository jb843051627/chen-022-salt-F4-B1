package com.fc.v2.service.impl;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fc.v2.mapper.auto.TSaltSpotRowMapper;
import com.fc.v2.model.auto.TSaltSpotRow;
import com.fc.v2.service.ITSaltSpotRowService;

/**
 * 抽检结果报送核收行 Service业务层处理（batch-process 形状：整批提交）
 *
 * @author fuce
 * @date 2026-09-14
 */
@Service
public class TSaltSpotRowServiceImpl implements ITSaltSpotRowService {

    private static final int MAX_ROWS = 500;
    private static final int STATUS_OK = 1;
    private static final int STATUS_FAIL = 2;

    @javax.annotation.Resource
    private TSaltSpotRowMapper saltSpotRowMapper;

    @Override
    public TSaltSpotRow selectTSaltSpotRowById(Long id) {
        return this.saltSpotRowMapper.selectById(id);
    }

    /**
     * 整批提交。口径（与失败明细同源同码）：
     * <ul>
     * <li>批次码一律以入参 batchNo 为准；入参为空时回退首行批次码；两者皆缺返回 0，整批不入库；</li>
     * <li>空批次返回 0；超过单批上限返回 -1，整批不入库；</li>
     * <li>逐行校验：合法行收下（状态1），非法行记入失败明细（状态2），行号一律写**原报行次**
     * （提交时的位置，1 起），成功行与失败行共用同一套行号；</li>
     * <li>同一企业代号同批出现多次：首条**合法**行收下，后续同码行记失败明细；</li>
     * <li>同一批次重复提交不重复入库，直接回报该批已入库的成功行数（重试安全）。</li>
     * </ul>
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int submitBatch(String batchNo, List<TSaltSpotRow> rows) {
        // 空批次兜底：无行直接返回 0，不触库
        if (rows == null || rows.isEmpty()) {
            return 0;
        }
        // 单批规模把关：超量整批拒收
        if (rows.size() > MAX_ROWS) {
            return -1;
        }
        // 批次码口径：提交与失败明细两条路径同用这一条码
        String no = (batchNo != null && !batchNo.trim().isEmpty()) ? batchNo.trim()
                : rows.get(0) == null ? null : rows.get(0).getBatchNo();
        if (no == null || no.trim().isEmpty()) {
            return 0;
        }
        // 幂等：同一批次已有落账（含失败明细），重复提交不再入库，回报已入库成功行数
        List<TSaltSpotRow> existing = this.saltSpotRowMapper.selectList(
                new QueryWrapper<TSaltSpotRow>().eq("batch_no", no));
        if (!existing.isEmpty()) {
            int ok = 0;
            for (TSaltSpotRow r : existing) {
                if (r.getStatus() != null && r.getStatus().intValue() == STATUS_OK) {
                    ok++;
                }
            }
            return ok;
        }
        java.util.Set<String> seen = new java.util.HashSet<String>();
        int ok = 0;
        for (int i = 0; i < rows.size(); i++) {
            TSaltSpotRow r = rows.get(i);
            if (r == null) {
                continue;
            }
            // 行号 = 原报行次（提交位置，1 起），成功行与失败行同一套编号
            r.setRowNo(Integer.valueOf(i + 1));
            r.setBatchNo(no);
            String code = r.getItemCode() == null ? null : r.getItemCode().trim();
            boolean illegal = code == null || code.isEmpty()
                    || r.getQty() == null
                    || r.getQty().compareTo(java.math.BigDecimal.ZERO) <= 0
                    || !seen.add(code);
            if (illegal) {
                r.setStatus(STATUS_FAIL);
            } else {
                r.setStatus(STATUS_OK);
                ok++;
            }
            r.setDelFlag(Integer.valueOf(0));
            this.saltSpotRowMapper.insert(r);
        }
        return ok;
    }

    @Override
    public List<TSaltSpotRow> listErrors(String batchNo) {
        return this.saltSpotRowMapper.selectList(new QueryWrapper<TSaltSpotRow>()
                .eq("batch_no", batchNo).eq("status", STATUS_FAIL)
                .orderByAsc("row_no"));
    }
}
