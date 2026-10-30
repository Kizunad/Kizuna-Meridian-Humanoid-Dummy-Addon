package dev.kizuna.meridian.dummy.server;

/** 训练假人纯规则的固定错误文本，命令与网络层只转述，不拼接内部状态。 */
public final class DummyErrors {
    public static final String REPORT_SIZE = "击打报告长度超出范围";
    public static final String REPORT_FORMAT = "击打报告无法解码";
    public static final String REPORT_VERSION = "击打报告版本不受支持";
    public static final String REPORT_UNKNOWN_FIELD = "击打报告含未知字段";
    public static final String REPORT_NAME = "击打报告名称无效";
    public static final String REPORT_VALUE = "击打报告数值越界";
    public static final String REPORT_KIND = "击打报告伤害类型无效";
    public static final String REPORT_LIST = "击打报告条目过多";
    public static final String REPORT_CHANGE = "击打报告部位状态无效";
    public static final String HIT_PART = "命中目标不属于身体构型中的任何部位";
    public static final String REALM = "未知的训练假人档位";

    private DummyErrors() {
        // 只保存常量。
    }
}
