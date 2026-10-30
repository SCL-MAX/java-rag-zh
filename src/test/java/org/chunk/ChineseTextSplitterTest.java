package org.chunk;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * {@link ChineseTextSplitter} 的单元测试。
 *
 * <p>覆盖四类风险：空输入、句子被切断、块超长、重叠失效。
 */
public class ChineseTextSplitterTest {

    private static final String DOCUMENT =
            "Java-RAG 是一个纯 Java 实现的检索增强生成工具箱。"
                    + "它不依赖 Spring 或任何框架，可以直接嵌入传统企业系统。"
                    + "默认的本地模式使用确定性哈希向量和抽取式生成器。"
                    + "因此不需要云端 API Key，也不需要提前下载模型。"
                    + "文档会先被解析，再被分块，然后向量化并写入内存索引。"
                    + "当用户提问时，问题会被向量化，并与所有片段计算相似度。"
                    + "系统取相似度最高的前 K 个片段作为上下文。"
                    + "最终答案由生成器基于这些片段产出，并附带引用编号。";

    @Test
    public void nullOrBlankReturnsNoChunks() {
        assertTrue(new ChineseTextSplitter().split(null).isEmpty());
        assertTrue(new ChineseTextSplitter().split("   \n\t ").isEmpty());
    }

    @Test
    public void shortTextStaysInOneChunk() {
        List<String> chunks = new ChineseTextSplitter(700, 0).split("这是一句很短的话。");
        assertEquals(1, chunks.size());
        assertEquals("这是一句很短的话。", chunks.get(0));
    }

    @Test
    public void everyChunkEndsWithSentenceDelimiter() {
        List<String> chunks = new ChineseTextSplitter(120, 0).split(DOCUMENT);
        assertTrue("文本应被切成多块，实际 " + chunks.size() + " 块", chunks.size() > 1);
        for (String chunk : chunks) {
            char last = chunk.charAt(chunk.length() - 1);
            assertTrue("分块没有以句末标点结束: " + chunk, "。！？；!?;".indexOf(last) >= 0);
        }
    }

    @Test
    public void chunksNeverExceedMaxSize() {
        int maxChunkSize = 120;
        for (String chunk : new ChineseTextSplitter(maxChunkSize, 40).split(DOCUMENT)) {
            assertTrue("块超长（" + chunk.length() + " 字符）: " + chunk, chunk.length() <= maxChunkSize);
        }
    }

    @Test
    public void adjacentChunksOverlap() {
        List<String> chunks = new ChineseTextSplitter(120, 40).split(DOCUMENT);
        assertTrue(chunks.size() > 1);
        for (int i = 1; i < chunks.size(); i++) {
            String previous = chunks.get(i - 1);
            String current = chunks.get(i);
            // 判据：存在长度大于 0 的公共部分，且它既是上一块的结尾、又是当前块的开头
            int sharedLength = 0;
            for (int len = Math.min(previous.length(), current.length()); len >= 1; len--) {
                if (previous.endsWith(current.substring(0, len))) {
                    sharedLength = len;
                    break;
                }
            }
            assertTrue("相邻块之间没有重叠: " + current, sharedLength > 0);
        }
    }

    @Test
    public void overlongSentenceIsHardSplit() {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < 40; i++) {
            builder.append("这是一段没有任何标点符号的很长的中文内容");
        }
        List<String> chunks = new ChineseTextSplitter(100, 10).split(builder.toString());
        assertTrue(chunks.size() > 1);
        for (String chunk : chunks) {
            assertTrue("硬切后仍然超长: " + chunk.length(), chunk.length() <= 100);
        }
    }

    @Test
    public void englishPeriodIsBoundaryButDecimalIsNot() {
        List<String> chunks = new ChineseTextSplitter(30, 0)
                .split("The toolkit runs on Java 8. It needs no API key. Version 1.2 works fine.");
        assertTrue("英文句号应触发切分", chunks.size() >= 2);
        assertTrue("小数 1.2 不应该被切断", chunks.stream().anyMatch(chunk -> chunk.contains("Version 1.2")));
    }
}
