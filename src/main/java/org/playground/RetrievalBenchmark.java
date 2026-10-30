package org.playground;

import org.chunk.ChineseTextSplitter;
import org.chunk.FixedSizeSplitter;
import org.chunk.TextSplitter;
import org.demo.local.CjkAwareHashingEmbeddingService;
import org.demo.local.HashingEmbeddingService;
import org.json.JSONArray;
import org.json.JSONObject;
import org.service.embedding.EmbeddingService;

/**
 * 检索质量小评测：同一批文档、同一批问题，对比不同分块策略的命中率。
 *
 * <p>运行方式：
 * <pre>
 * mvn -q -DskipTests compile org.codehaus.mojo:exec-maven-plugin:3.6.3:java \
 *   -Dexec.mainClass=org.playground.RetrievalBenchmark
 * </pre>
 *
 * <p>指标说明：Hit@K 表示"正确的片段是否出现在相似度最高的前 K 个片段里"。
 * 判定方式为命中关键词——这是简化做法，真实评测应由人工标注片段 ID。</p>
 */
public final class RetrievalBenchmark {

    private static final int TOP_K = 3;

    /** 两种策略必须使用相同的块大小预算，否则不可比。 */
    private static final int CHUNK_SIZE = 120;

    private static final int CHUNK_OVERLAP = 20;

    private static final String[][] CORPUS = {
            {"deploy.md", "部署说明：本项目支持三种启动方式。第一是命令行启动，执行 mvn exec:java 即可，"
                    + "适合快速验证。第二是 Docker Compose，一条命令同时拉起前端与后端，适合演示。"
                    + "第三是打包成可执行 jar，用 java -jar 启动，适合服务器长期运行。"
                    + "无论哪种方式，后端默认监听 8080 端口，前端默认监听 5173 端口。"
                    + "如果端口冲突，可以通过环境变量 RAG_PLAYGROUND_PORT 覆盖后端端口。"},

            {"config.md", "配置说明：模型相关配置分为兩类。生成模型既可以使用 OpenAI 兼容接口，"
                    + "也可以使用本地 Ollama。嵌入模型默认使用哈希向量，属于离线方案，不依赖网络。"
                    + "所有密钥都应通过环境变量注入，禁止写进代码或提交到仓库。"
                    + "配置文件支持 jwt-secret.properties 与 llm-local.properties 两个可选文件。"},

            {"security.md", "安全说明：系统不会把密钥写入日志。上传的文件会先落到临时目录，"
                    + "解析完成后再写入存储，临时文件会被清理。接口默认开启跨域，"
                    + "生产环境应当收敛 Access-Control-Allow-Origin 的范围，只允许可信域名。"
                    + "管理员账号的初始密码必须在首次上线后立即修改。"},

            {"retrieval.md", "检索原理：文档被切成片段后，每个片段都会计算一次向量表示。"
                    + "用户提问时，问题向量与所有片段向量计算余弦相似度，按相似度从高到低排序。"
                    + "系统取前 K 个片段作为上下文，只把这些片段送入生成阶段。"
                    + "K 越大召回越全，但噪声也越多；K 越小噪声越少，但容易漏掉关键证据。"},

            {"performance.md", "性能调优：向量化的耗时与片段数量成正比，因此片段过小会导致计算量激增。"
                    + "哈希向量使用固定维度，维度越高碰撞越少，但内存占用也越大。"
                    + "对于十万级片段，建议改用 Elasticsearch 做持久化检索，避免全部驻留内存。"
                    + "另外，提问频率较高时可以缓存问题向量，命中缓存可显著降低平均延迟。"}
    };

    private static final String[][] QUESTIONS = {
            {"如何用 Docker Compose 启动这个项目？", "Docker Compose"},
            {"后端默认监听哪个端口？", "8080"},
            {"端口冲突时怎么改后端端口？", "RAG_PLAYGROUND_PORT"},
            {"怎么把密钥交给程序而不写进代码？", "环境变量"},
            {"嵌入模型可以用本地的吗？", "哈希向量"},
            {"跨域在生产环境应该怎么处理？", "Access-Control-Allow-Origin"},
            {"上传的临时文件会被保留吗？", "临时目录"},
            {"相似度是怎么算出来的？", "余弦相似度"},
            {"Top-K 取值太大有什么问题？", "噪声"},
            {"十万级片段应该用什么方案？", "Elasticsearch"}
    };

    private RetrievalBenchmark() {
    }

    public static void main(String[] args) throws Exception {
        System.out.println("| 分块策略 | 向量策略 | Hit@1 | Hit@3 | 整句结束率 |");
        System.out.println("|---|---|---|---|---|");
        report("FixedSize(" + CHUNK_SIZE + ")", new FixedSizeSplitter(CHUNK_SIZE),
                "Hashing(原)", new HashingEmbeddingService());
        report("FixedSize(" + CHUNK_SIZE + ")", new FixedSizeSplitter(CHUNK_SIZE),
                "CJK-bigram(新)", new CjkAwareHashingEmbeddingService());
        report("ChineseSplitter(" + CHUNK_SIZE + "," + CHUNK_OVERLAP + ")",
                new ChineseTextSplitter(CHUNK_SIZE, CHUNK_OVERLAP),
                "Hashing(原)", new HashingEmbeddingService());
        report("ChineseSplitter(" + CHUNK_SIZE + "," + CHUNK_OVERLAP + ")",
                new ChineseTextSplitter(CHUNK_SIZE, CHUNK_OVERLAP),
                "CJK-bigram(新)", new CjkAwareHashingEmbeddingService());
    }

    private static void report(String splitterLabel, TextSplitter splitter,
                               String embeddingLabel, EmbeddingService embedding) throws Exception {
        PlaygroundEngine engine = new PlaygroundEngine(
                splitter,
                embedding,
                new PlaygroundExtractiveChatService(),
                "offline://extractive",
                "offline-extractive",
                "local",
                TOP_K
        );

        int chunkCount = 0;
        for (String[] document : CORPUS) {
            JSONObject added = engine.addDocument(document[0], document[1]);
            chunkCount += added.getInt("chunks");
        }

        int hit1 = 0;
        int hit3 = 0;
        for (String[] question : QUESTIONS) {
            JSONObject result = engine.ask(question[0]);
            JSONArray citations = result.getJSONArray("citations");
            int rank = rankOf(citations, question[1]);
            if (rank == 1) {
                hit1++;
            }
            if (rank >= 1) {
                hit3++;
            }
        }

        double avgChunks = (double) chunkCount / CORPUS.length;
        double sentenceEndRate = sentenceEndRate(splitter);
        System.out.printf("| %s | %s | %d/%d | %d/%d | %.0f%% |%n",
                splitterLabel, embeddingLabel, hit1, QUESTIONS.length, hit3, QUESTIONS.length, sentenceEndRate);
    }

    /**
     * 统计某个分块策略切出的片段中，以句末标点结尾的比例。
     * 该指标与向量策略无关，只反映分块边界质量。
     */
    private static double sentenceEndRate(TextSplitter splitter) {
        int total = 0;
        int ended = 0;
        for (String[] document : CORPUS) {
            for (String chunk : splitter.split(document[1])) {
                String trimmed = chunk.trim();
                if (trimmed.isEmpty()) {
                    continue;
                }
                total++;
                if ("。！？；!?;".indexOf(trimmed.charAt(trimmed.length() - 1)) >= 0) {
                    ended++;
                }
            }
        }
        return total == 0 ? 0.0 : (double) ended / total * 100;
    }

    /**
     * 返回正确片段首次出现的名次（1 起），未命中返回 -1。
     */
    private static int rankOf(JSONArray citations, String keyword) {
        for (int i = 0; i < citations.length(); i++) {
            String content = citations.getJSONObject(i).getString("content");
            if (content.contains(keyword)) {
                return i + 1;
            }
        }
        return -1;
    }
}
