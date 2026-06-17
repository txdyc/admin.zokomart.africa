package africa.zokomart.admin.module.basedata.util;

import africa.zokomart.admin.common.exception.BusinessException;
import africa.zokomart.admin.common.result.ResultCode;
import africa.zokomart.admin.module.basedata.entity.Category;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 把 "父>子" 分类路径解析为唯一 categoryId。空路径返回 null；
 * 未命中或某级出现重名（无法唯一确定）抛 BusinessException。
 */
public final class CategoryPathResolver {

    private CategoryPathResolver() {
    }

    public static Long resolve(List<Category> all, String path) {
        if (path == null || path.isBlank()) {
            return null;
        }
        Map<Long, List<Category>> byParent = new HashMap<>();
        for (Category c : all) {
            long pid = c.getParentId() == null ? 0L : c.getParentId();
            byParent.computeIfAbsent(pid, k -> new ArrayList<>()).add(c);
        }
        long parent = 0L;
        Long current = null;
        String[] segments = path.split(">");
        for (String raw : segments) {
            String seg = raw.trim();
            if (seg.isEmpty()) {
                throw new BusinessException(ResultCode.BAD_REQUEST, "分类路径非法: " + path);
            }
            List<Category> matched = byParent.getOrDefault(parent, List.of()).stream()
                    .filter(c -> seg.equals(c.getName()))
                    .toList();
            if (matched.size() != 1) {
                throw new BusinessException(ResultCode.BAD_REQUEST,
                        "分类未找到或重名: " + path);
            }
            current = matched.get(0).getId();
            parent = current;
        }
        return current;
    }
}
