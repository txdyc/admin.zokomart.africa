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
}
