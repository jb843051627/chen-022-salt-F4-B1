package com.fc.v2.service.impl;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

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
     * 整批提交。口径（提交与失败明细两条路径共用 {@link #isValidRow}，不得再各判各的）：
     * 1. 空批次（null/无行）直接返回 0，不报错、不入库；
     * 2. 行数超过 {@link #MAX_ROWS} 返回 -1，整批一行都不入库；
     * 3. 册码一律以入参 batchNo 为准，行内自带的 batchNo 不作数；
     * 4. 同一册码已提交过则整次重试幂等跳过，返回当前已收下行数，不产生第二份；
     * 5. 逐行校验：合法行收下（status=1），非法行/册内重码行退回（status=2）并进失败明细，
     *    单行填错只退该行，不牵连整批；
     * 6. 失败明细保留原报行次（行内未带 row_no 时按提交位置 1 起编），listErrors 按行号升序。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int submitBatch(String batchNo, List<TSaltSpotRow> rows) {
        if (rows == null || rows.isEmpty()) {
            return 0;
        }
        if (rows.size() > MAX_ROWS) {
            return -1;
        }
        String no = batchNo == null ? null : batchNo.trim();
        if (no == null || no.isEmpty()) {
            return 0;
        }

        // 同批次重试：已有落库记录即整次幂等，不重复入库。
        // 存量兼容口径（定死）：老系统带过来的行 del_flag/status 可能为空，
        // 只要未显式删除（del_flag != 1）就算"已收过册"，重试只回成功数、不留第二份。
        Integer existed = this.saltSpotRowMapper.selectCount(new QueryWrapper<TSaltSpotRow>()
                .eq("batch_no", no).ne("del_flag", 1));
        if (existed != null && existed.intValue() > 0) {
            Integer accepted = this.saltSpotRowMapper.selectCount(new QueryWrapper<TSaltSpotRow>()
                    .eq("batch_no", no).ne("del_flag", 1).eq("status", STATUS_OK));
            return accepted == null ? 0 : accepted.intValue();
        }

        Set<String> seenCodes = new LinkedHashSet<String>();
        int ok = 0;
        for (int i = 0; i < rows.size(); i++) {
            TSaltSpotRow r = rows.get(i);
            r.setBatchNo(no);
            // 原报行次：行里带了就照用，没带按提交位置补；任何情况都不许用"非法行计数"顶替。
            if (r.getRowNo() == null) {
                r.setRowNo(Integer.valueOf(i + 1));
            }
            r.setDelFlag(0);

            String code = r.getItemCode() == null ? null : r.getItemCode().trim();
            boolean duplicate = code != null && !code.isEmpty() && !seenCodes.add(code);
            if (!duplicate && isValidRow(r)) {
                r.setItemCode(code);
                r.setStatus(STATUS_OK);
                this.saltSpotRowMapper.insert(r);
                ok++;
            } else {
                // 非法行 / 同册重码行：退回并留痕，合法行照常入库。
                if (code != null) {
                    r.setItemCode(code);
                }
                r.setStatus(STATUS_FAIL);
                this.saltSpotRowMapper.insert(r);
            }
        }
        return ok;
    }

    /** 单行合法性的唯一口径：企业代号非空、不合格件数为正。提交与失败明细都走这里。 */
    private boolean isValidRow(TSaltSpotRow r) {
        if (r == null) {
            return false;
        }
        String code = r.getItemCode();
        if (code == null || code.trim().isEmpty()) {
            return false;
        }
        if (r.getQty() == null || r.getQty().signum() <= 0) {
            return false;
        }
        return true;
    }

    @Override
    public List<TSaltSpotRow> listErrors(String batchNo) {
        return this.saltSpotRowMapper.selectList(new QueryWrapper<TSaltSpotRow>()
                .eq("batch_no", batchNo)
                .ne("del_flag", 1)
                .eq("status", STATUS_FAIL)
                .orderByAsc("row_no"));
    }
}
