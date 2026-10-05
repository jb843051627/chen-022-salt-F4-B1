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

    /** 挂接进展：0待挂 1已挂讫 2压页（终态） */
    private static final int STATUS_PENDING = 0;
    private static final int STATUS_DONE = 1;
    private static final int STATUS_HELD = 2;

    @Autowired
    private TSaltEntMapper saltEntMapper;

    @Override
    public TSaltLinkPage selectTSaltLinkPageById(Long id) {
        return this.baseMapper.selectOne(new QueryWrapper<TSaltLinkPage>()
                .eq("id", id)
                .eq("del_flag", 0));
    }

    @Override
    public List<TSaltLinkPage> selectTSaltLinkPageList(Wrapper<TSaltLinkPage> queryWrapper) {
        // 分页与过滤条件一律由调用方给；这里只兜底"不查已删"，不再强插页码/状态。
        QueryWrapper<TSaltLinkPage> wrapper = queryWrapper instanceof QueryWrapper
                ? (QueryWrapper<TSaltLinkPage>) queryWrapper
                : new QueryWrapper<TSaltLinkPage>();
        wrapper.eq("del_flag", 0);
        return this.baseMapper.selectList(wrapper);
    }

    @Override
    public int insertTSaltLinkPage(TSaltLinkPage record) {
        if (record == null) {
            return 0;
        }

        TSaltEnt refArch = resolveSite(record);
        if (refArch == null) {
            return 0;
        }
        if (refArch.getStatus() != null && refArch.getStatus() == 1) {
            // 已摘牌企业不再新挂
            return 0;
        }
        record.setSiteId(Integer.valueOf(refArch.getId().intValue()));
        record.setSiteNo(refArch.getSiteNo());
        if (StringUtils.isNotEmpty(record.getBillNo())) {
            Integer dupCnt = this.baseMapper.selectCount(new QueryWrapper<TSaltLinkPage>()
                    .eq("bill_no", record.getBillNo()).eq("del_flag", 0));
            if (dupCnt != null && dupCnt > 0) {
                return 0;
            }
        }
        record.setDelFlag(0);
        if (record.getStatus() == null) {
            record.setStatus(Integer.valueOf(STATUS_PENDING));
        }
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

        // 改挂企业时与新增走同一处核对；不携带企业字段的普通修改绝不回头反查名录，
        // 名录后动（摘牌、改号、换主键）不牵连既有簿页。
        if (StringUtils.isNotEmpty(record.getSiteNo()) || record.getSiteId() != null) {
            TSaltEnt refArch = resolveSite(record);
            if (refArch == null) {
                return 0;
            }
            record.setSiteId(Integer.valueOf(refArch.getId().intValue()));
            record.setSiteNo(refArch.getSiteNo());
        }

        record.setUpdateTime(new Date());
        return this.baseMapper.update(record, new UpdateWrapper<TSaltLinkPage>()
                .eq("id", record.getId())
                .eq("del_flag", 0));
    }

    /**
     * 名录核对的唯一入口（新增/改挂并到一处）：以稳定代号 site_no 为准，
     * 代号没带才退到 id。只认在册、未删除的名录行。
     */
    private TSaltEnt resolveSite(TSaltLinkPage record) {
        TSaltEnt arch = null;
        if (StringUtils.isNotEmpty(record.getSiteNo())) {
            arch = saltEntMapper.selectOne(new QueryWrapper<TSaltEnt>()
                    .eq("site_no", record.getSiteNo().trim()).eq("del_flag", 0)
                    .last("limit 1"));
        }
        if (arch == null && record.getSiteId() != null) {
            arch = saltEntMapper.selectOne(new QueryWrapper<TSaltEnt>()
                    .eq("id", record.getSiteId()).eq("del_flag", 0));
        }
        return arch;
    }

    @Override
    public int advanceTSaltLinkPage(Long id) {
        if (id == null) {
            return 0;
        }
        TSaltLinkPage cur = this.baseMapper.selectById(id);
        if (cur == null || cur.getDelFlag() == null || cur.getDelFlag().intValue() == 1) {
            return 0;
        }
        // 存量兼容：status 为空按 0待挂 论，推进/退回两处口径一致。
        int st = cur.getStatus() == null ? STATUS_PENDING : cur.getStatus().intValue();
        if (st != STATUS_PENDING && st != STATUS_DONE) {
            // 压页是终态，且不许跳态
            return 0;
        }
        int next = st + 1;
        // 推进必留核验串；格式与退回保留的那一份是同一个字段，不再另立口径。
        String token = (cur.getBillNo() == null ? String.valueOf(id) : cur.getBillNo()) + "-" + next;
        UpdateWrapper<TSaltLinkPage> uw = new UpdateWrapper<TSaltLinkPage>()
                .eq("id", id).eq("del_flag", 0);
        uw.set("status", next);
        uw.set("content", token);
        uw.set("update_time", new Date());
        return this.baseMapper.update(null, uw);
    }

    @Override
    public int revertTSaltLinkPage(Long id) {
        if (id == null) {
            return 0;
        }
        TSaltLinkPage cur = this.baseMapper.selectById(id);
        if (cur == null || cur.getDelFlag() == null || cur.getDelFlag().intValue() == 1) {
            return 0;
        }
        Integer stRaw = cur.getStatus();
        if (stRaw == null) {
            return 0;
        }
        int st = stRaw.intValue();
        // 只准 已挂讫→待挂；待挂无可退，压页不许退。
        if (st != STATUS_DONE) {
            return 0;
        }
        UpdateWrapper<TSaltLinkPage> uw = new UpdateWrapper<TSaltLinkPage>()
                .eq("id", id).eq("del_flag", 0);
        uw.set("status", STATUS_PENDING);
        // 核验串保留作沉底旧记录，不清空。
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
