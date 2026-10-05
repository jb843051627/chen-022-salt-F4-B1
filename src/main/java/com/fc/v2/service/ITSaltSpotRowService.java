package com.fc.v2.service;

import java.util.List;

import com.fc.v2.model.auto.TSaltSpotRow;

/**
 * 抽检结果报送核收行 Service接口（batch-process 形状：整批提交，无单条增删改）
 *
 * @author fuce
 * @date 2026-09-14
 */
public interface ITSaltSpotRowService {

    /** 按主键回查单行 */
    TSaltSpotRow selectTSaltSpotRowById(Long id);

    /**
     * 整批提交：逐行校验，合法的入库、非法的记入失败明细（保留原始行号），返回**成功行数**。
     * 同一批次重复提交不得重复入库；行数超过单批上限返回 -1 且整批不入库；空批次返回 0。
     * <p>
     * 口径定死：
     * <ul>
     * <li>批次码以入参 batchNo 为准（提交与失败明细同一条码）；入参为空回退首行批次码，两者皆缺返回 0；</li>
     * <li>行号 = 原报行次（提交位置，1 起），成功行与失败行同一套编号；</li>
     * <li>同一企业代号同批出现多次：首条<b>合法</b>行收下，后续同码行记失败明细；</li>
     * <li>重复提交（含重试）不再入库，直接回报该批已入库的成功行数。</li>
     * </ul>
     */
    int submitBatch(String batchNo, List<TSaltSpotRow> rows);

    /** 该批次的失败行明细（按行号升序） */
    List<TSaltSpotRow> listErrors(String batchNo);
}
