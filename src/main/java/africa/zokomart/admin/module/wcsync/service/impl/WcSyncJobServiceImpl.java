package africa.zokomart.admin.module.wcsync.service.impl;

import africa.zokomart.admin.module.wcsync.entity.WcSyncJob;
import africa.zokomart.admin.module.wcsync.entity.WcSyncJobStatus;
import africa.zokomart.admin.module.wcsync.mapper.WcSyncJobMapper;
import africa.zokomart.admin.module.wcsync.service.WcSyncJobService;
import africa.zokomart.admin.module.wcsync.vo.WcSyncJobVO;
import africa.zokomart.admin.module.wcsync.vo.WcSyncRowError;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

@Service
@RequiredArgsConstructor
public class WcSyncJobServiceImpl implements WcSyncJobService {

    private final WcSyncJobMapper jobMapper;
    private final ObjectMapper om;

    @Override
    public WcSyncJob createRunning(Long supplierId, List<Long> brandIds, int total, String operator) {
        WcSyncJob job = new WcSyncJob();
        job.setSupplierId(supplierId);
        job.setBrandIds(toJson(brandIds));
        job.setOperator(operator);
        job.setStatus(WcSyncJobStatus.RUNNING);
        job.setTotal(total);
        job.setProcessed(0);
        job.setCreatedCount(0);
        job.setUpdatedCount(0);
        job.setDraftedCount(0);
        job.setFailedCount(0);
        job.setFailedItems("[]");
        job.setStartTime(LocalDateTime.now());
        jobMapper.insert(job);
        return job;
    }

    @Override
    public void save(WcSyncJob job) {
        jobMapper.updateById(job);
    }

    @Override
    public WcSyncJobVO getVO(Long jobId) {
        WcSyncJob job = jobMapper.selectById(jobId);
        return job == null ? null : toVO(job);
    }

    @Override
    public IPage<WcSyncJobVO> page(Long supplierId, long current, long size) {
        Page<WcSyncJob> page = new Page<>(current, size);
        IPage<WcSyncJob> raw = jobMapper.selectPage(page,
                Wrappers.<WcSyncJob>lambdaQuery()
                        .eq(supplierId != null, WcSyncJob::getSupplierId, supplierId)
                        .orderByDesc(WcSyncJob::getId));
        return raw.convert(this::toVO);
    }

    private WcSyncJobVO toVO(WcSyncJob job) {
        WcSyncJobVO vo = new WcSyncJobVO();
        vo.setJobId(job.getId());
        vo.setStatus(job.getStatus());
        vo.setTotal(nz(job.getTotal()));
        vo.setProcessed(nz(job.getProcessed()));
        vo.setCreated(nz(job.getCreatedCount()));
        vo.setUpdated(nz(job.getUpdatedCount()));
        vo.setDrafted(nz(job.getDraftedCount()));
        vo.setFailed(nz(job.getFailedCount()));
        vo.setFailedItems(parseItems(job.getFailedItems()));
        vo.setStartTime(job.getStartTime());
        vo.setEndTime(job.getEndTime());
        return vo;
    }

    private int nz(Integer v) { return v == null ? 0 : v; }

    private String toJson(Object o) {
        try { return om.writeValueAsString(o); }
        catch (Exception e) { return "[]"; }
    }

    private List<WcSyncRowError> parseItems(String json) {
        if (json == null || json.isBlank()) return Collections.emptyList();
        try { return om.readValue(json, new TypeReference<List<WcSyncRowError>>() {}); }
        catch (Exception e) { return Collections.emptyList(); }
    }
}
