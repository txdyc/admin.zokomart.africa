package africa.zokomart.admin.module.dashboard.service;

import africa.zokomart.admin.module.dashboard.vo.DailyTrendVO;
import africa.zokomart.admin.module.dashboard.vo.DashboardOverviewVO;

import java.time.LocalDate;
import java.util.List;

/** 仪表盘只读聚合服务。 */
public interface DashboardService {

    /** CEO 概览。from/to 为空时默认最近 30 天（含今日）。 */
    DashboardOverviewVO overview(LocalDate from, LocalDate to);

    /** 每日销售趋势。from/to 为空时默认最近 30 天（含今日）。 */
    List<DailyTrendVO> salesTrend(LocalDate from, LocalDate to);
}
