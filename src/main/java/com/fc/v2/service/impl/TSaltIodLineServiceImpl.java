package com.fc.v2.service.impl;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;

import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fc.v2.mapper.auto.TSaltIodLineMapper;
import com.fc.v2.model.auto.TSaltIodLine;
import com.fc.v2.service.ITSaltIodLineService;

/**
 * 碘含量分档判定线 Service业务层处理（rule-eval 形状）
 *
 * @author fuce
 * @date 2026-09-14
 */
@Service
public class TSaltIodLineServiceImpl implements ITSaltIodLineService {

    /** 输入合法区间（毫克每千克）：0..100，evaluate 与 evaluateTop 共用同一口径 */
    private static final BigDecimal INPUT_MIN = BigDecimal.ZERO;
    private static final BigDecimal INPUT_MAX = new BigDecimal("100");

    /** 优先级降序；并列按 ruleCode 降序（确定性） */
    private static final Comparator<TSaltIodLine> PRIORITY_THEN_CODE_DESC = new Comparator<TSaltIodLine>() {
        @Override
        public int compare(TSaltIodLine a, TSaltIodLine b) {
            int pa = a.getPriority() == null ? 0 : a.getPriority();
            int pb = b.getPriority() == null ? 0 : b.getPriority();
            if (pa != pb) {
                return pb - pa;
            }
            String ca = a.getRuleCode() == null ? "" : a.getRuleCode();
            String cb = b.getRuleCode() == null ? "" : b.getRuleCode();
            return cb.compareTo(ca);
        }
    };

    @javax.annotation.Resource
    private TSaltIodLineMapper saltIodLineMapper;

    @Override
    public TSaltIodLine selectTSaltIodLineById(Long id) {
        return this.saltIodLineMapper.selectById(id);
    }

    /**
     * 存量兼容口径（定死，两处以上共用，不得各判各的）：
     * 老系统带过来的行多数没有维护 status / 时段，故 status 为空按"在场"论，仅显式 1 才停用；
     * 时段为空表示一头不封；eff_end（让位之日）**不含**当日。
     */
    private boolean isActiveAt(TSaltIodLine r, Date at) {
        if (r == null || at == null) {
            return false;
        }
        if (r.getDelFlag() != null && r.getDelFlag().intValue() == 1) {
            return false;
        }
        if (r.getStatus() != null && r.getStatus().intValue() == 1) {
            return false;
        }
        if (r.getEffStart() != null && r.getEffStart().compareTo(at) > 0) {
            return false;
        }
        if (r.getEffEnd() != null && !at.before(r.getEffEnd())) {
            return false;
        }
        return true;
    }

    /** 输入越界口径：evaluate 与 evaluateTop 必须一致 */
    private boolean isInputInRange(BigDecimal input) {
        return input != null
                && input.compareTo(INPUT_MIN) >= 0
                && input.compareTo(INPUT_MAX) <= 0;
    }

    @Override
    public List<TSaltIodLine> listAvailable(Date at) {
        if (at == null) {
            return new ArrayList<TSaltIodLine>();
        }
        List<TSaltIodLine> all = this.saltIodLineMapper.selectList(
                new QueryWrapper<TSaltIodLine>().eq("del_flag", 0));
        List<TSaltIodLine> avail = new ArrayList<TSaltIodLine>();
        for (TSaltIodLine r : all) {
            if (isActiveAt(r, at)) {
                avail.add(r);
            }
        }
        Collections.sort(avail, PRIORITY_THEN_CODE_DESC);
        return avail;
    }

    @Override
    public int evaluate(String ruleCode, BigDecimal input, Date at) {
        if (ruleCode == null || ruleCode.trim().isEmpty() || at == null) {
            return 0;
        }
        if (!isInputInRange(input)) {
            return 0;
        }
        TSaltIodLine rule = findRule(ruleCode, at);
        if (rule == null) {
            return 0;
        }
        return levelOf(rule, input);
    }

    @Override
    public int evaluateTop(BigDecimal input, Date at) {
        if (at == null || !isInputInRange(input)) {
            return 0;
        }
        List<TSaltIodLine> avail = listAvailable(at);
        if (avail.isEmpty()) {
            return 0;
        }
        return levelOf(avail.get(0), input);
    }

    @Override
    public boolean usable(Long id, Date at) {
        if (id == null || at == null) {
            return false;
        }
        TSaltIodLine r = this.saltIodLineMapper.selectById(id);
        return isActiveAt(r, at);
    }

    @Override
    public int countAvailable(Date at) {
        if (at == null) {
            return 0;
        }
        return listAvailable(at).size();
    }

    /** 定版：与列表/可用性同一口径——at 时刻在场、同代号中顺位最先说话的那一版 */
    private TSaltIodLine findRule(String ruleCode, Date at) {
        for (TSaltIodLine r : listAvailable(at)) {
            if (ruleCode.equals(r.getRuleCode())) {
                return r;
            }
        }
        return null;
    }

    /** 分档：等于上限取高一档（严格小于才落本档）；某档阈值未维护则跳过该档 */
    private int levelOf(TSaltIodLine rule, BigDecimal input) {
        if (rule.getTh1Max() != null && input.compareTo(rule.getTh1Max()) < 0) {
            return 1;
        }
        if (rule.getTh2Max() != null && input.compareTo(rule.getTh2Max()) < 0) {
            return 2;
        }
        if (rule.getTh3Max() != null && input.compareTo(rule.getTh3Max()) < 0) {
            return 3;
        }
        return 4;
    }
}
