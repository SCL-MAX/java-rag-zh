package org.chunk;

/**
 * 按文档语言选择分块策略。
 *
 * <p>中文没有空格分词，按字符硬切会切断句子，因此中文文档使用
 * {@link ChineseTextSplitter}；英文等使用空格分隔的语言保留原有的
 * {@link FixedSizeSplitter}，避免改变既有行为。</p>
 */
public final class TextSplitterFactory {

    private static final int DEFAULT_MAX_CHUNK_SIZE = 700;

    private static final int DEFAULT_OVERLAP = 80;

    /** 中文字符占非空白字符的比例达到该阈值即视为中文文档。 */
    private static final double CJK_RATIO_THRESHOLD = 0.15;

    private TextSplitterFactory() {
    }

    public static TextSplitter forText(String text) {
        if (isChinese(text)) {
            return new ChineseTextSplitter(DEFAULT_MAX_CHUNK_SIZE, DEFAULT_OVERLAP);
        }
        return new FixedSizeSplitter(DEFAULT_MAX_CHUNK_SIZE);
    }

    /**
     * 判断文本是否以中文为主。用字符比例而不是简单包含判断，避免
     * 英文文档里出现几个中文术语就被误判。
     */
    public static boolean isChinese(String text) {
        if (text == null || text.isEmpty()) {
            return false;
        }
        int cjkCount = 0;
        int visibleCount = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (Character.isWhitespace(c)) {
                continue;
            }
            visibleCount++;
            if (isCjk(c)) {
                cjkCount++;
            }
        }
        return visibleCount > 0 && (double) cjkCount / visibleCount >= CJK_RATIO_THRESHOLD;
    }

    private static boolean isCjk(char c) {
        return (c >= '\u4E00' && c <= '\u9FFF') || (c >= '\u3400' && c <= '\u4DBF');
    }
}
