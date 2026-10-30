package org.chunk;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * {@link TextSplitterFactory} 的语言判定与策略选择测试。
 */
public class TextSplitterFactoryTest {

    @Test
    public void chineseDocumentUsesChineseSplitter() {
        String text = "Java-RAG 是一个纯 Java 实现的检索增强生成工具箱。它不依赖任何框架，可以直接嵌入企业系统。";
        assertTrue(TextSplitterFactory.isChinese(text));
        assertTrue(TextSplitterFactory.forText(text) instanceof ChineseTextSplitter);
    }

    @Test
    public void englishDocumentKeepsFixedSizeSplitter() {
        String text = "The toolkit runs on Java 8. It needs no API key and no model download.";
        assertFalse(TextSplitterFactory.isChinese(text));
        assertTrue(TextSplitterFactory.forText(text) instanceof FixedSizeSplitter);
    }

    @Test
    public void occasionalChineseTermDoesNotSwitchStrategy() {
        // 英文文档里夹几个中文术语，不应被误判为中文文档
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < 60; i++) {
            builder.append("retrieval augmented generation pipeline. ");
        }
        builder.append("术语：向量");
        assertFalse(TextSplitterFactory.isChinese(builder.toString()));
    }

    @Test
    public void nullOrEmptyFallsBackToFixedSizeSplitter() {
        assertFalse(TextSplitterFactory.isChinese(null));
        assertTrue(TextSplitterFactory.forText("") instanceof FixedSizeSplitter);
    }
}
