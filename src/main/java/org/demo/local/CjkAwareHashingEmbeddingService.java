package org.demo.local;

import org.service.embedding.EmbeddingService;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 支持中文的确定性哈希向量。
 *
 * <p>原 {@link HashingEmbeddingService} 用空白与标点切词，中文整段会变成一个超长 token，
 * 相似度实际退化成字符三元组匹配。本实现对中文按「单字 + 相邻二字」建特征，
 * 对拉丁文字保留原来的词 + 三元组策略。</p>
 *
 * <p>仍然是确定性哈希，不引入模型或网络依赖，便于离线评测与单元测试。</p>
 */
public final class CjkAwareHashingEmbeddingService implements EmbeddingService {

    private static final int DEFAULT_DIMENSIONS = 256;

    private static final double CJK_UNIGRAM_WEIGHT = 0.5;

    private static final double CJK_BIGRAM_WEIGHT = 1.0;

    private static final double LATIN_TOKEN_WEIGHT = 1.0;

    private static final double LATIN_TRIGRAM_WEIGHT = 0.35;

    private final int dimensions;

    public CjkAwareHashingEmbeddingService() {
        this(DEFAULT_DIMENSIONS);
    }

    public CjkAwareHashingEmbeddingService(int dimensions) {
        if (dimensions <= 0) {
            throw new IllegalArgumentException("dimensions must be greater than zero");
        }
        this.dimensions = dimensions;
    }

    @Override
    public double[] getEmbedding(String url, String input) throws IOException {
        return embed(input);
    }

    @Override
    public double[][] getEmbeddings(String url, String[] inputs) throws IOException {
        if (inputs == null) {
            throw new IOException("inputs cannot be null");
        }
        double[][] result = new double[inputs.length][];
        for (int i = 0; i < inputs.length; i++) {
            result[i] = embed(inputs[i]);
        }
        return result;
    }

    private double[] embed(String input) throws IOException {
        if (input == null || input.trim().isEmpty()) {
            throw new IOException("input cannot be blank");
        }

        double[] vector = new double[dimensions];
        List<String[]> features = new ArrayList<>();

        StringBuilder latin = new StringBuilder();
        StringBuilder cjk = new StringBuilder();
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            if (isCjk(c)) {
                flushLatin(latin, features);
                cjk.append(c);
            } else if (Character.isLetterOrDigit(c)) {
                flushCjk(cjk, features);
                latin.append(Character.toLowerCase(c));
            } else {
                flushLatin(latin, features);
                flushCjk(cjk, features);
            }
        }
        flushLatin(latin, features);
        flushCjk(cjk, features);

        if (features.isEmpty()) {
            throw new IOException("input does not contain embeddable tokens");
        }
        for (String[] feature : features) {
            addFeature(vector, feature[0], Double.parseDouble(feature[1]));
        }
        normalize(vector);
        return vector;
    }

    private void flushLatin(StringBuilder latin, List<String[]> features) {
        if (latin.length() == 0) {
            return;
        }
        String token = latin.toString();
        latin.setLength(0);
        features.add(new String[]{token, String.valueOf(LATIN_TOKEN_WEIGHT)});
        for (int i = 0; i <= token.length() - 3; i++) {
            features.add(new String[]{"#" + token.substring(i, i + 3), String.valueOf(LATIN_TRIGRAM_WEIGHT)});
        }
    }

    private void flushCjk(StringBuilder cjk, List<String[]> features) {
        if (cjk.length() == 0) {
            return;
        }
        String run = cjk.toString();
        cjk.setLength(0);
        for (int i = 0; i < run.length(); i++) {
            features.add(new String[]{"中" + run.charAt(i), String.valueOf(CJK_UNIGRAM_WEIGHT)});
            if (i + 1 < run.length()) {
                features.add(new String[]{"中" + run.substring(i, i + 2), String.valueOf(CJK_BIGRAM_WEIGHT)});
            }
        }
    }

    private void addFeature(double[] vector, String feature, double weight) {
        int index = Math.floorMod(feature.hashCode(), dimensions);
        vector[index] += weight;
    }

    private static void normalize(double[] vector) {
        double squaredMagnitude = 0.0;
        for (double value : vector) {
            squaredMagnitude += value * value;
        }
        if (squaredMagnitude == 0.0) {
            return;
        }
        double magnitude = Math.sqrt(squaredMagnitude);
        for (int i = 0; i < vector.length; i++) {
            vector[i] /= magnitude;
        }
    }

    private static boolean isCjk(char c) {
        return (c >= '\u4E00' && c <= '\u9FFF') || (c >= '\u3400' && c <= '\u4DBF');
    }
}
