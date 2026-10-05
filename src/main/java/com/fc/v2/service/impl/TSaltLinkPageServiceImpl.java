package com.fc.v2.service.impl;

import java.util.Arrays;
import java.util.Date;
import java.util.List;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fc.v2.common.support.ConvertUtil;
import com.fc.v2.mapper.auto.TSaltLinkPageMapper;
import com.fc.v2.mapper.auto.TSaltEntMapper;
import com.fc.v2.model.auto.TSaltLinkPage;
import com.fc.v2.model.auto.TSaltEnt;
import com.fc.v2.service.ITSaltLinkPageService;
import com.fc.v2.util.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * 品种挂接页Service业务层处理
 *
 * @author fuce
 * @date 2026-09-12
 */
@Service
public class TSaltLinkPageServiceImpl extends ServiceImpl<TSaltLinkPageMapper, TSaltLinkPage> implements ITSaltLinkPageService {

    /** 挂接进展：0待核对 1已核对 2已冻住（终态） */
    private static final int ST_PENDING = 0;
    private static final int ST_CHECKED = 1;
    private static final int ST_FROZEN = 2;

    @Autowired
    private TSaltEntMapper saltEntMapper;

    @Override
    public TSaltLinkPage selectTSaltLinkPageById(Long id) {
        return this.baseMapper.selectOne(new QueryWrapper<TSaltLinkPage>()
                .eq("id", id)
                .eq("del_flag", 0));
    }

    /**
     * 按条件查询列表：条件与分页全由调用方给定，这里不再内置过滤与分页
     * （内置 PageHelper 的 ThreadLocal 一旦泄漏会牵连同线程后续无关查询）。
     */
    @Override
    public List<TSaltLinkPage> selectTSaltLinkPageList(Wrapper<TSaltLinkPage> queryWrapper) {
        return this.baseMapper.selectList(queryWrapper);
    }

    /** 名录在册校验：企业须存在、未删除、未摘牌；新增与修改同口径 */
    private TSaltEnt checkEnt(Integer siteId) {
        if (siteId == null) {
            return null;
        }
        TSaltEnt refArch = saltEntMapper.selectOne(new QueryWrapper<TSaltEnt>()
                .eq("id", siteId).eq("del_flag", 0));
        if (refArch == null) {
            return null;
        }
        if (refArch.getStatus() != null && refArch.getStatus() == 1) {
            return null;
        }
        return refArch;
    }

    @Override
    public int insertTSaltLinkPage(TSaltLinkPage record) {
        if (record == null) {
            return 0;
        }

        TSaltEnt refArch = checkEnt(record.getSiteId());
        if (refArch == null) {
            return 0;
        }
        // 落账即快照：企业代号随页定死，名录事后变动不牵连已落账记录
        record.setSiteNo(refArch.getSiteNo());
        if (StringUtils.isNotEmpty(record.getBillNo())) {
            Integer dupCnt = this.baseMapper.selectCount(new QueryWrapper<TSaltLinkPage>()
                    .eq("bill_no", record.getBillNo()).eq("del_flag", 0));
            if (dupCnt != null && dupCnt > 0) {
                return 0;
            }
        }
        record.setDelFlag(0);
        return this.baseMapper.insert(record);
    }

    @Override
    public int updateTSaltLinkPage(TSaltLinkPage record) {
        if (record == null || record.getId() == null) {
            return 0;
        }

        if (StringUtils.isNotEmpty(record.getBillNo())) {
            Integer dupCnt = this.baseMapper.selectCount(new QueryWrapper<TSaltLinkPage>()
                    .eq("bill_no", record.getBillNo()).ne("id", record.getId()).eq("del_flag", 0));
            if (dupCnt != null && dupCnt > 0) {
                return 0;
            }
        }

        // siteId 变更时按名录重解 siteNo（与新增同口径），保持页上企业快照与所属企业一致
        if (record.getSiteId() != null) {
            TSaltEnt refArch = checkEnt(record.getSiteId());
            if (refArch == null) {
                return 0;
            }
            record.setSiteNo(refArch.getSiteNo());
        }

        record.setUpdateTime(new Date());
        return this.baseMapper.update(record, new UpdateWrapper<TSaltLinkPage>()
                .eq("id", record.getId())
                .eq("del_flag", 0));
    }

    /**
     * 推进一态（待核对→已核对→已冻住）：逐态推进不跳态，已冻住为终态拒推；
     * 存量老行状态缺省按待核对（与退回同口径）；推进即生成核验串随页留存。
     */
    @Override
    public int advanceTSaltLinkPage(Long id) {
        if (id == null) {
            return 0;
        }
        TSaltLinkPage cur = this.baseMapper.selectById(id);
        if (cur == null || (cur.getDelFlag() != null && cur.getDelFlag().intValue() == 1)) {
            return 0;
        }
        int st = cur.getStatus() == null ? ST_PENDING : cur.getStatus().intValue();
        if (st < ST_PENDING || st >= ST_FROZEN) {
            return 0;
        }
        int next = st + 1;
        String verify = "CHK-" + (StringUtils.isNotEmpty(cur.getBillNo()) ? cur.getBillNo() : String.valueOf(id))
                + "-" + next;
        UpdateWrapper<TSaltLinkPage> uw = new UpdateWrapper<TSaltLinkPage>()
                .eq("id", id).eq("del_flag", 0);
        uw.set("status", next);
        uw.set("content", verify);
        uw.set("update_time", new Date());
        return this.baseMapper.update(null, uw);
    }

    /**
     * 退回一态（已核对→待核对；旧记录沉底）：仅已核对可退，待核对无可退、已冻住不可退；
     * 存量老行状态缺省按待核对（与推进同口径）；退回落态但**不清核验串**，原核验串随页沉底留存。
     */
    @Override
    public int revertTSaltLinkPage(Long id) {
        if (id == null) {
            return 0;
        }
        TSaltLinkPage cur = this.baseMapper.selectById(id);
        if (cur == null || (cur.getDelFlag() != null && cur.getDelFlag().intValue() == 1)) {
            return 0;
        }
        int st = cur.getStatus() == null ? ST_PENDING : cur.getStatus().intValue();
        if (st != ST_CHECKED) {
            return 0;
        }
        UpdateWrapper<TSaltLinkPage> uw = new UpdateWrapper<TSaltLinkPage>()
                .eq("id", id).eq("del_flag", 0);
        uw.set("status", ST_PENDING);
        uw.set("update_time", new Date());
        return this.baseMapper.update(null, uw);
    }

    @Override
    public int deleteTSaltLinkPageByIds(String ids) {
        Long[] idArr = ConvertUtil.toLongArray(ids);
        return this.baseMapper.deleteBatchIds(Arrays.asList(idArr));
    }

    @Override
    public int deleteTSaltLinkPageById(Long id) {
        return this.baseMapper.deleteById(id);
    }
}
