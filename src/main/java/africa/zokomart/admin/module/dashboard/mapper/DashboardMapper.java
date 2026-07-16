package africa.zokomart.admin.module.dashboard.mapper;

import africa.zokomart.admin.module.dashboard.vo.DailyTrendVO;
import africa.zokomart.admin.module.dashboard.vo.FinancialSummaryVO;
import africa.zokomart.admin.module.dashboard.vo.InventorySummaryVO;
import africa.zokomart.admin.module.dashboard.vo.NamedAmountVO;
import africa.zokomart.admin.module.dashboard.vo.OpenPoVO;
import africa.zokomart.admin.module.dashboard.vo.ReturnStatVO;
import africa.zokomart.admin.module.dashboard.vo.StatusCountVO;
import africa.zokomart.admin.module.dashboard.vo.TopProductVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 仪表盘聚合查询（只读）。全部为 sales_order 主口径 + raw_order 顶端下单量 + 库存/采购快照。
 * 时间参数：sales_order 用 create_time（LocalDateTime，半开区间 [from, to)）；
 * raw_order 用 order_date（LocalDate，半开区间 [fromDate, toDate)）。
 */
@Mapper
public interface DashboardMapper {

    /** 财务原始汇总（gmv/netRevenue/codOutstanding/deliveryFeeTotal/rejectionCost/completedOrders）。 */
    FinancialSummaryVO financialRaw(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    /** 已售商品成本 COGS。 */
    BigDecimal cogs(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    /** 各状态订单计数。 */
    List<StatusCountVO> statusCounts(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    /** 明细件数退货统计。 */
    ReturnStatVO returnStat(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    /** 平均妥投时长（小时）。 */
    Double avgDeliveryHours(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    /** 原始下单量（raw_order 顶端漏斗）。 */
    Long placedCount(@Param("from") LocalDate from, @Param("to") LocalDate to);

    /** 按分类收入 Top N。 */
    List<NamedAmountVO> revenueByCategory(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to,
                                          @Param("limit") int limit);

    /** 按品牌收入 Top N。 */
    List<NamedAmountVO> revenueByBrand(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to,
                                       @Param("limit") int limit);

    /** 供应商采购支出 Top N（actual_purchase_order）。 */
    List<NamedAmountVO> topSuppliers(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to,
                                     @Param("limit") int limit);

    /** 热销产品 Top N。 */
    List<TopProductVO> topProducts(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to,
                                   @Param("limit") int limit);

    /** 库存货值快照。 */
    InventorySummaryVO inventorySummary();

    /** 待付款采购订单快照。 */
    OpenPoVO openPurchaseOrders();

    /** 每日销售趋势。 */
    List<DailyTrendVO> salesTrend(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);
}
