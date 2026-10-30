package org.chunk;

import java.util.ArrayList;
import java.util.List;

/**
 * 中文友好的文本分块器。
 *
 * <p>与 {@link FixedSizeSplitter} 的“按字符数硬切”不同，本实现遵循两点：
 * <ol>
 *   <li><b>不切断句子</b>：先按中文/英文句末标点切句，再按长度合并，保证每块都是完整句子；</li>
 *   <li><b>相邻块重叠</b>：新块开头带上上一块的结尾片段，避免答案恰好跨块时被检索丢失。</li>
 * </ol>
 *
 * <p>超过 {@code maxChunkSize} 的单句会被硬切，切分长度预留了 {@code overlap} 的空间，
 * 以保证加上重叠前缀后仍不超过上限。
 */
public class ChineseTextSplitter implements TextSplitter {

    /** 句末标点：中文标点 + 英文标点 + 换行。 */
    private static final String SENTENCE_DELIMITERS = "。！？；……!?;\n";

    /** 句末标点之后可能紧跟的收尾符号，应归入同一句。 */
    private static final String CLOSING_MARKS = "”’」』）)]】》\"";

    private static final int DEFAULT_MAX_CHUNK_SIZE = 700;

    private static final int DEFAULT_OVERLAP = 80;

    private final int maxChunkSize;

    private final int overlap;

    public ChineseTextSplitter() {
        this(DEFAULT_MAX_CHUNK_SIZE, DEFAULT_OVERLAP);
    }

    public ChineseTextSplitter(int maxChunkSize, int overlap) {
        if (maxChunkSize <= 0) {
            throw new IllegalArgumentException("maxChunkSize must be positive");
        }
        if (overlap < 0 || overlap >= maxChunkSize) {
            throw new IllegalArgumentException("overlap must be in [0, maxChunkSize)");
        }
        this.maxChunkSize = maxChunkSize;
        this.overlap = overlap;
    }

    @Override
    public List<String> split(String text) {
        List<String> chunks = new ArrayList<>();
        if (text == null) {
            return chunks;
        }
        String normalized = text.replace("\r\n", "\n").replace('\r', '\n').trim();
        if (normalized.isEmpty()) {
            return chunks;
        }

        StringBuilder current = new StringBuilder();
        for (String sentence : splitIntoSentences(normalized)) {
            // 单句超过上限时先硬切，切分长度预留 overlap 的空间
            for (String piece : hardSplit(sentence)) {
                boolean wouldOverflow = current.length() > 0
                        && current.length() + piece.length() > maxChunkSize;
                if (wouldOverflow) {
                    chunks.add(current.toString().trim());
                    current = new StringBuilder(overlapPrefix(chunks.get(chunks.size() - 1)));
                }
                current.append(piece);
            }
        }
        if (current.length() > 0) {
            String last = current.toString().trim();
            if (!last.isEmpty()) {
                chunks.add(last);
            }
        }
        return chunks;
    }

    /**
     * 判断某个位置是否为句末：中文标点直接算；英文句号仅在后面是空白或文本结束时算，
     * 以免把 3.14、v1.2 这类小数/版本号切断。
     */
    private boolean isSentenceEnd(String text, int index) {
        char c = text.charAt(index);
        if (SENTENCE_DELIMITERS.indexOf(c) >= 0) {
            return true;
        }
        return c == '.' && (index + 1 == text.length() || Character.isWhitespace(text.charAt(index + 1)));
    }

    /**
     * 按句末标点切句，标点本身保留在句子末尾，紧接着的收尾符号也归入同一句。
     */
    private List<String> splitIntoSentences(String text) {
        List<String> sentences = new ArrayList<>();
        int start = 0;
        for (int i = 0; i < text.length(); i++) {
            if (!isSentenceEnd(text, i)) {
                continue;
            }
            int end = i + 1;
            // 把连续的标点（如 。。。 或 ？！）和收尾符号并入当前句
            while (end < text.length()
                    && (SENTENCE_DELIMITERS.indexOf(text.charAt(end)) >= 0
                    || CLOSING_MARKS.indexOf(text.charAt(end)) >= 0)) {
                end++;
            }
            sentences.add(text.substring(start, end));
            start = end;
            i = end - 1;
        }
        if (start < text.length()) {
            sentences.add(text.substring(start));
        }
        return sentences;
    }

    /**
     * 把超长句切成多段，每段长度不超过 {@code maxChunkSize - overlap}，
     * 这样即使加上重叠前缀也不会突破总上限。
     */
    private List<String> hardSplit(String sentence) {
        List<String> pieces = new ArrayList<>();
        int limit = Math.max(1, maxChunkSize - overlap);
        int index = 0;
        while (sentence.length() - index > limit) {
            pieces.add(sentence.substring(index, index + limit));
            index += limit;
        }
        pieces.add(sentence.substring(index));
        return pieces;
    }

    /**
     * 从上一块的结尾取重叠片段，优先从一个完整的句子开头开始，避免以半句作为新块开头。
     */
    private String overlapPrefix(String previousChunk) {
        if (overlap <= 0 || previousChunk.isEmpty()) {
            return "";
        }
        int from = Math.max(0, previousChunk.length() - overlap);
        for (int i = from; i < previousChunk.length(); i++) {
            if (SENTENCE_DELIMITERS.indexOf(previousChunk.charAt(i)) >= 0) {
                return stripLeadingWhitespace(previousChunk.substring(i + 1));
            }
        }
        return stripLeadingWhitespace(previousChunk.substring(from));
    }

    private String stripLeadingWhitespace(String value) {
        int index = 0;
        while (index < value.length() && Character.isWhitespace(value.charAt(index))) {
            index++;
        }
        return value.substring(index);
    }
}
