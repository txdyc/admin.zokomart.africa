package africa.zokomart.admin.common.result;

import com.baomidou.mybatisplus.core.metadata.IPage;
import lombok.Data;

import java.util.List;

/**
 * 统一分页结果。records 当前页数据 / total 总数 / current 当前页 / size 每页大小。
 */
@Data
public class PageResult<T> {

    private List<T> records;
    private long total;
    private long current;
    private long size;

    public static <T> PageResult<T> of(IPage<T> page) {
        PageResult<T> p = new PageResult<>();
        p.records = page.getRecords();
        p.total = page.getTotal();
        p.current = page.getCurrent();
        p.size = page.getSize();
        return p;
    }

    /** 由已转换的 VO 列表 + 分页元数据构造（用于 service 层将 entity 转 VO 后包装）。 */
    public static <T> PageResult<T> of(List<T> records, long total, long current, long size) {
        PageResult<T> p = new PageResult<>();
        p.records = records;
        p.total = total;
        p.current = current;
        p.size = size;
        return p;
    }
}
