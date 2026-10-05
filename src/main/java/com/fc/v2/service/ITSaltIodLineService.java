package com.fc.v2.service;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

import com.fc.v2.model.auto.TSaltIodLine;

/**
 * 碘含量分档判定线 Service接口（rule-eval 形状：规则求值，无增删改）
 * <p>
 * 可用口径（全接口唯一定义，定位/列表/计数/求值/旁路复用一律同此）：del_flag 正常 + 在场
 * + at 落在 [eff_start, eff_end) 内（让位之日不含）。
 * <p>
 * 存量老行取舍（老系统带过来的行照此判，定死）：eff_start 缺省视为自古生效；eff_end 缺省视为
 * 永不让位；status 缺省视为在场；del_flag 缺省视为正常；priority 缺省为 0。
 * 分档边界：等于上限取高一档；输入有效域 [0,100]，越界一律返回 0。
 *
 * @author fuce
 * @date 2026-09-14
 */
public interface ITSaltIodLineService {

    /** 按主键查询规则 */
    TSaltIodLine selectTSaltIodLineById(Long id);

    /**
     * 单规则求值：按 ruleCode 在 at 时刻生效的版本判档（1..4）。
     * 无生效版本 / 输入越界 / 参数缺失一律返回 0。
     */
    int evaluate(String ruleCode, BigDecimal input, Date at);

    /** 多规则求值：at 时刻可用规则中优先级最高者的档位；无可用规则返回 0 */
    int evaluateTop(BigDecimal input, Date at);

    /** at 时刻可用规则（优先级降序、ruleCode 降序） */
    List<TSaltIodLine> listAvailable(Date at);

    /** 单条规则在 at 时刻是否可用（批量导入/报表等旁路复用，口径须与定位/列表一致） */
    boolean usable(Long id, Date at);

    /** at 时刻可用规则条数（列表页角标 / 看板） */
    int countAvailable(Date at);
}
