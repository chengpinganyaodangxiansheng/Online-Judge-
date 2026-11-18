package org.example.judge.util;

/** 判题输出的固定比较规则：统一换行、忽略末尾换行，保留其他空白。 */
public final class JudgeOutputNormalizer {
    /** 工具类不允许实例化。 */
    private JudgeOutputNormalizer() {}

    /** 将不同平台换行统一并移除末尾连续换行，返回用于比较的新字符串；保留行首行末空格、制表符及内部空行，空引用表示缺失证据并抛出异常。 */
    public static String normalize(String output) {
        // 空引用代表结果缺失，空字符串才代表确实输出了零个字符，两者不能混为一谈。
        if (output == null) {
            throw new IllegalArgumentException("缺少判题输出");
        }
        String normalized = output.replace("\r\n", "\n").replace('\r', '\n');
        // 先把两个字符的系统换行换成单个换行，再处理剩余的单独回车，顺序可避免多生成一个换行。
        // 从末尾向前跳过换行，不使用去除所有首尾空白的操作，以免错误答案被误判通过。
        int end = normalized.length();
        // end 表示保留内容的右边界，初始覆盖整个字符串，后面只在发现末尾换行时向左移动。
        while (end > 0 && normalized.charAt(end - 1) == '\n') {
            // 必须先确保边界大于零，再检查最后一个保留字符，防止空字符串访问负数下标。
            end--;
            // 每轮跳过一个末尾换行；例如“答案加两个换行”会循环两次，遇到案字后停止。
        }
        // 截取左闭右开的范围；例如“3 5加换行”得到“3 5”，数字间空格仍参与答案比较。
        return normalized.substring(0, end);
    }
}
