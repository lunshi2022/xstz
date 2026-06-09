package com.huaying.xstz.util

/**
 * 全局业务常量
 * 存放与 UI 布局无关的纯业务逻辑常量
 */
object AppConstants {

    // ==================== 百分比计算 ====================
    /** 百分比基数 */
    const val PERCENTAGE_BASE = 100.0
    /** 百分比基数（整数版本） */
    const val PERCENTAGE_BASE_INT = 100

    // ==================== 数值精度 ====================
    /** 比例相等判断阈值 */
    const val RATIO_EQUALITY_THRESHOLD = 0.01
    /** 预设比例容差 */
    const val PRESET_RATIO_TOLERANCE = 0.5
    /** 最小绝对偏离度（0.5%） */
    const val MIN_ABS_DEVIATION = 0.005

    // ==================== 交易规则 ====================
    /** A股最小交易单位（1手 = 100股） */
    const val MIN_TRADE_UNIT = 100L
    /** A股最小交易单位（Double 版本） */
    const val MIN_TRADE_UNIT_DOUBLE = 100.0

    // ==================== 数据采样 ====================
    /** 图表最大采样点数 */
    const val MAX_CHART_SAMPLE_SIZE = 100
    /** 图表数据缓存最大条目数 */
    const val MAX_CHART_CACHE_SIZE = 10

    // ==================== 输入限制 ====================
    /** 基金代码长度 */
    const val FUND_CODE_LENGTH = 6
    /** 阈值输入最大长度 */
    const val THRESHOLD_INPUT_MAX_LENGTH = 2

    // ==================== 再平衡阈值范围 ====================
    /** 百分比模式最小阈值 */
    const val THRESHOLD_PERCENT_MIN = 5
    /** 百分比模式最大阈值 */
    const val THRESHOLD_PERCENT_MAX = 25
    /** 百分点模式最小阈值 */
    const val THRESHOLD_POINT_MIN = 1
    /** 百分点模式最大阈值 */
    const val THRESHOLD_POINT_MAX = 10

    // ==================== 偏离度判断 ====================
    /** 严重偏离阈值（%） */
    const val SEVERE_DEVIATION_THRESHOLD = 10.0

    // ==================== 布局偏移 ====================
    /** 顶部标题栏偏移（dp） */
    const val TOP_BAR_OFFSET_DP = 120
    /** 底部导航栏偏移（dp） */
    const val BOTTOM_NAV_OFFSET_DP = 140

    // ==================== 再平衡计算 ====================
    /** 无现金基金时最大迭代次数 */
    const val REBALANCE_MAX_ITERATIONS = 5
    /** 有现金基金时迭代次数 */
    const val REBALANCE_CASH_ITERATIONS = 1
    /** 再平衡收敛阈值 */
    const val REBALANCE_CONVERGENCE_THRESHOLD = 10.0
    /** 默认百分比阈值 */
    const val DEFAULT_THRESHOLD_PERCENT = 20f
    /** 默认百分点阈值 */
    const val DEFAULT_THRESHOLD_POINT = 5f

    // ==================== 动画常量 ====================
    /** 骨架屏动画目标值 */
    const val SHIMMER_TARGET_VALUE = 1000f
    /** 骨架屏动画时长（毫秒） */
    const val SHIMMER_DURATION_MS = 1200

    // ==================== 逻辑模式 ====================
    /** 智能再平衡模式 */
    const val LOGIC_MODE_SMART = 0
    /** 强制再平衡模式 */
    const val LOGIC_MODE_FULL = 1

    // ==================== 零值常量 ====================
    /** 零值 Double */
    const val ZERO_DOUBLE = 0.0
    /** 零值 Float */
    const val ZERO_FLOAT = 0f
    /** 零值 Long */
    const val ZERO_LONG = 0L

    // ==================== 预设比例 ====================
    /** 目标占比预设值列表 */
    val PRESET_RATIOS = listOf(0, 10, 20, 30, 50)

    // ==================== 时间转换 ====================
    /** 每秒毫秒数 */
    const val MILLISECONDS_PER_SECOND = 1000L
    /** 每分钟毫秒数 */
    const val MILLISECONDS_PER_MINUTE = 1000L * 60
    /** 每小时毫秒数 */
    const val MILLISECONDS_PER_HOUR = 1000L * 60 * 60
    /** 每天毫秒数 */
    const val MILLISECONDS_PER_DAY = 1000L * 60 * 60 * 24

    // ==================== 刷新机制 ====================
    /** 默认自动刷新间隔（秒） */
    const val DEFAULT_REFRESH_INTERVAL_SECONDS = 3L
    /** 启动延迟后首次刷新（毫秒） */
    const val REFRESH_STARTUP_DELAY_MS = 500L
    /** 自动刷新检查间隔（毫秒） */
    const val AUTO_REFRESH_CHECK_INTERVAL_MS = 500L

    // ==================== 网络超时 ====================
    /** NTP请求超时（毫秒） */
    const val NTP_TIMEOUT_MS = 5000
}
