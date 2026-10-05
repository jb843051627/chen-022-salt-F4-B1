package com.fc.v2.service.impl;

import java.math.BigDecimal;
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

    /** 输入有效域 [0,100]，越界一律按不可用（返回 0） */
    private static final BigDecimal MIN_INPUT = BigDecimal.ZERO;
    private static final BigDecimal MAX_INPUT = new BigDecimal("100");

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

    /**
     * 时段比较统一走**墙钟字符串**（yyyy-MM-dd HH:mm:ss）：JDBC 的 serverTimezone 与本机
     * 时区不对称，直接把 java.util.Date 作参数会整体偏移，使"恰好到期"这类边界用例错判。
     */
    private static String ts(Date d) {
        return new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(d);
    }

    /**
     * 可用口径（**唯一核定点**：定位/列表/计数/求值全部走这里，批量导入与报表等旁路复用同一口径）：
     * del_flag 正常 + 在场 + at 落在 [eff_start, eff_end) 内（让位之日不含）。
     * <p>
     * 存量老行取舍（老系统带过来的行照此判，定死不再两处各说各话）：
     * eff_start 缺省视为自古生效；eff_end 缺省视为永不让位；status 缺省视为在场；
     * del_flag 缺省视为正常；priority 缺省为 0。
     */
    private static boolean effectiveAt(TSaltIodLine r, Date at) {
        if (r == null || at == null) {
            return false;
        }
        if (r.getDelFlag() != null && r.getDelFlag().intValue() != 0) {
            return false;
        }
        if (r.getStatus() != null && r.getStatus().intValue() != 0) {
            return false;
        }
        String t = ts(at);
        if (r.getEffStart() != null && ts(r.getEffStart()).compareTo(t) > 0) {
            return false;
        }
        if (r.getEffEnd() != null && ts(r.getEffEnd()).compareTo(t) <= 0) {
            return false;
        }
        return true;
    }

    private static boolean outOfRange(BigDecimal input) {
        return input.compareTo(MIN_INPUT) < 0 || input.compareTo(MAX_INPUT) > 0;
    }

    @Override
    public TSaltIodLine selectTSaltIodLineById(Long id) {
        return this.saltIodLineMapper.selectById(id);
    }

    @Override
    public List<TSaltIodLine> listAvailable(Date at) {
        List<TSaltIodLine> out = new java.util.ArrayList<TSaltIodLine>();
        if (at == null) {
            return out;
        }
        // 老行 del_flag/status 可能缺省，库层不便统一表达，捞回内存按同一口径过滤
        List<TSaltIodLine> all = this.saltIodLineMapper.selectList(new QueryWrapper<TSaltIodLine>());
        for (TSaltIodLine r : all) {
            if (effectiveAt(r, at)) {
                out.add(r);
            }
        }
        out.sort(PRIORITY_THEN_CODE_DESC);
        return out;
    }

    @Override
    public int evaluate(String ruleCode, BigDecimal input, Date at) {
        if (ruleCode == null || ruleCode.trim().isEmpty() || input == null || at == null) {
            return 0;
        }
        if (outOfRange(input)) {
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
        if (input == null || at == null) {
            return 0;
        }
        if (outOfRange(input)) {
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
        return effectiveAt(this.saltIodLineMapper.selectById(id), at);
    }

    @Override
    public int countAvailable(Date at) {
        return listAvailable(at).size();
    }

    /** 按 ruleCode 定位 at 时刻生效的版本：同码多版并存时取优先级最高者（并列按 ruleCode 降序） */
    private TSaltIodLine findRule(String ruleCode, Date at) {
        List<TSaltIodLine> hit = this.saltIodLineMapper.selectList(
                new QueryWrapper<TSaltIodLine>().eq("rule_code", ruleCode));
        TSaltIodLine best = null;
        for (TSaltIodLine r : hit) {
            if (!effectiveAt(r, at)) {
                continue;
            }
            if (best == null || PRIORITY_THEN_CODE_DESC.compare(r, best) < 0) {
                best = r;
            }
        }
        return best;
    }

    /** 分档：等于上限取高一档（严格小于才落入本档） */
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
