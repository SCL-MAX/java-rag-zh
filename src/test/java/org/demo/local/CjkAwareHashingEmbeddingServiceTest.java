package org.demo.local;

import org.junit.Test;
import org.utils.DistanceUtils;

import java.io.IOException;

import static org.junit.Assert.assertTrue;

/**
 * {@link CjkAwareHashingEmbeddingService} 的中文向量质量测试。
 */
public class CjkAwareHashingEmbeddingServiceTest {

    private final CjkAwareHashingEmbeddingService service = new CjkAwareHashingEmbeddingService();

    @Test
    public void chineseTextProducesNonZeroVector() throws Exception {
        double[] vector = service.getEmbedding("offline://hashing", "中文文档为什么要按标点切句？");
        int nonZero = 0;
        for (double value : vector) {
            if (value != 0) {
                nonZero++;
            }
        }
        assertTrue("中文应产生非零向量", nonZero > 0);
    }

    @Test
    public void relatedChineseTextIsCloserThanUnrelated() throws Exception {
        String query = "中文文档为什么要按标点切句？";
        String related = "中文没有空格分词，按固定字符数硬切会把一句话从中间截断。";
        String unrelated = "十万级片段建议改用 Elasticsearch 做持久化检索。";

        double relatedSimilarity = similarity(query, related);
        double unrelatedSimilarity = similarity(query, unrelated);

        assertTrue("相关文本的相似度应高于无关文本：related=" + relatedSimilarity
                        + ", unrelated=" + unrelatedSimilarity,
                relatedSimilarity > unrelatedSimilarity);
    }

    @Test(expected = IOException.class)
    public void blankInputIsRejected() throws Exception {
        service.getEmbedding("offline://hashing", "   ");
    }

    private double similarity(String left, String right) throws Exception {
        double[] leftVector = service.getEmbedding("offline://hashing", left);
        double[] rightVector = service.getEmbedding("offline://hashing", right);
        return 1.0 - DistanceUtils.cosineError(leftVector, rightVector);
    }
}
