package com.fc.v2.service;

import java.util.Date;
import java.util.List;

import com.fc.v2.model.auto.TSaltYearTask;

/**
 * 年检到期催办单 Service接口（scheduling-job 形状：周期执行，无增删改查入口）
 *
 * @author fuce
 * @date 2026-09-14
 */
public interface ITSaltYearTaskService {

    /** 按主键回查条目 */
    TSaltYearTask selectTSaltYearTaskById(Long id);

    /**
     * 该时刻可处理的条目（执行窗口内 + 到期 + 尚未处理）。
     * <p>
     * 口径定死：执行窗口按 at 的墙钟小时 [2,5)（02:00 含—05:00 不含）；到期含恰好到期（due_at &lt;= at）；
     * 存量老行 status 缺省按待派、del_flag 缺省按正常。
     */
    List<TSaltYearTask> listDue(Date at);

    /**
     * 执行一次，返回**成功条数**；单条失败跳过继续。
     * <p>
     * 口径定死：成功条目落"已派出"；amount 缺失的存量老行落"分不出"并跳过，不再反复卡整轮；
     * 无可处理条目返回 0。
     */
    int runOnce(Date at);
}
