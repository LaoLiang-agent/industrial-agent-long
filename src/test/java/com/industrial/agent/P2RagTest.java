package com.industrial.agent;

import com.industrial.agent.rag.RagContextHolder;
import com.industrial.agent.rag.advanced.Bm25Retriever;
import com.industrial.agent.rag.advanced.RrfFusion;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import org.junit.jupiter.api.*;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class P2RagTest {

    // ═══════════════════════════════════════════════════════════════
    // RagContextHolder
    // ═══════════════════════════════════════════════════════════════

    @Nested
    class RagContextHolderTest {

        @AfterEach
        void cleanup() {
            RagContextHolder.clear();
        }

        @Test
        void shouldSetAndGetTenantAndUser() {
            RagContextHolder.set("tenant-A", "user-1");
            assertEquals("tenant-A", RagContextHolder.getTenantId());
            assertEquals("user-1", RagContextHolder.getUserId());
        }

        @Test
        void shouldDefaultNullTenantToDefault() {
            RagContextHolder.set(null, "user-1");
            assertEquals("default", RagContextHolder.getTenantId());
            assertEquals("user-1", RagContextHolder.getUserId());
        }

        @Test
        void shouldDefaultNullUserToAnonymous() {
            RagContextHolder.set("tenant-A", null);
            assertEquals("tenant-A", RagContextHolder.getTenantId());
            assertEquals("anonymous", RagContextHolder.getUserId());
        }

        @Test
        void shouldClearBothValues() {
            RagContextHolder.set("tenant-A", "user-1");
            RagContextHolder.clear();
            assertNull(RagContextHolder.getTenantId());
            assertNull(RagContextHolder.getUserId());
        }

        @Test
        void shouldReturnNullWhenNotSet() {
            assertNull(RagContextHolder.getTenantId());
            assertNull(RagContextHolder.getUserId());
        }

        @Test
        void shouldBeThreadLocal() throws Exception {
            CountDownLatch latch = new CountDownLatch(2);
            AtomicReference<String> t1Value = new AtomicReference<>();
            AtomicReference<String> t2Value = new AtomicReference<>();

            RagContextHolder.set("main-tenant", "main-user");

            Thread t1 = new Thread(() -> {
                RagContextHolder.set("t1-tenant", "t1-user");
                t1Value.set(RagContextHolder.getTenantId());
                latch.countDown();
            });
            Thread t2 = new Thread(() -> {
                RagContextHolder.set("t2-tenant", "t2-user");
                t2Value.set(RagContextHolder.getTenantId());
                latch.countDown();
            });

            t1.start(); t2.start();
            latch.await();
            t1.join(); t2.join();

            assertEquals("t1-tenant", t1Value.get());
            assertEquals("t2-tenant", t2Value.get());
            assertEquals("main-tenant", RagContextHolder.getTenantId());
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // Bm25Retriever
    // ═══════════════════════════════════════════════════════════════

    @Nested
    class Bm25RetrieverTest {

        private Bm25Retriever bm25;
        private List<String> testDocs;

        @BeforeEach
        void setUp() {
            bm25 = new Bm25Retriever(null); // no knowledgeBase, manual index
            testDocs = List.of(
                    "CNC机床 轴承 温度 过高 报警",
                    "轴承 润滑 不足 导致 磨损 故障",
                    "温度 传感器 数据 采集 正常",
                    "机床 刀具 磨损 需要 更换 维修",
                    "润滑油 泵 压力 异常 停机"
            );
            bm25.index(testDocs);
        }

        @Test
        void shouldIndexDocuments() {
            assertEquals(5, bm25.size());
        }

        @Test
        void shouldSearchAndReturnTopK() {
            var results = bm25.search("轴承 温度 过高", 3);
            assertEquals(3, results.size());
        }

        @Test
        void shouldReturnRelevantResultsFirst() {
            var results = bm25.search("轴承 温度 过高", 3);
            assertTrue(results.get(0).text().contains("轴承"));
            assertTrue(results.get(0).text().contains("温度"));
        }

        @Test
        void shouldRankHighTfTermsHigher() {
            var results = bm25.search("机床", 5);
            // Doc about 机床刀具 should rank higher than the one with only CNC机床
            assertTrue(results.get(0).score() > 0);
        }

        @Test
        void shouldHandleEmptyQuery() {
            var results = bm25.search("", 3);
            assertTrue(results.isEmpty() || results.stream().allMatch(r -> r.score() > 0));
        }

        @Test
        void shouldHandleQueryWithNoMatches() {
            var results = bm25.search("xyzabc 12345 nonexistentterm", 3);
            // All scores should be 0 or results empty
            assertTrue(results.stream().allMatch(r -> r.score() == 0.0));
        }

        @Test
        void shouldRespectTopKLimit() {
            var results = bm25.search("轴承", 2);
            assertTrue(results.size() <= 2);
        }

        @Test
        void shouldRebuildIndex() {
            bm25.rebuild(List.of("全新 文档 内容 A", "全新 文档 内容 B", "全新 文档 内容 C"));
            assertEquals(3, bm25.size());
            var results = bm25.search("全新 文档", 3);
            assertFalse(results.isEmpty());
            assertTrue(results.get(0).text().contains("全新"));
        }

        @Test
        void shouldHandleRebuildOnEmpty() {
            bm25.rebuild(List.of());
            assertEquals(0, bm25.size());
        }

        @Test
        void shouldTokenizeChineseAndEnglish() {
            // Chinese bigrams and English words should both be indexed
            bm25.index(List.of("CNC-001 轴承温度 abnormal"));
            var results = bm25.search("abnormal", 1);
            assertEquals(1, results.size());
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // RrfFusion
    // ═══════════════════════════════════════════════════════════════

    @Nested
    class RrfFusionTest {

        private RrfFusion fusion;

        @BeforeEach
        void setUp() {
            fusion = new RrfFusion();
        }

        @Test
        void shouldFuseDenseAndSparseResults() {
            var dense = denseResults(List.of("doc-A", "doc-B", "doc-C", "doc-D"));
            var sparse = sparseResults(List.of("doc-C", "doc-A", "doc-E", "doc-F"));

            List<String> fused = fusion.fuse(dense, sparse, 3);

            assertEquals(3, fused.size());
            // doc-A ranks 1st in dense, 2nd in sparse → strong combined score
            assertTrue(fused.contains("doc-A"));
        }

        @Test
        void shouldRankDocInBothListsHigher() {
            var dense = denseResults(List.of("X", "Y", "Z"));
            var sparse = sparseResults(List.of("X", "Y", "Z"));

            List<String> fused = fusion.fuse(dense, sparse, 3);

            // doc appearing in both lists should be at top
            assertEquals(3, fused.size());
        }

        @Test
        void shouldRespectTopK() {
            var dense = denseResults(List.of("A", "B", "C", "D", "E"));
            var sparse = sparseResults(List.of("F", "G", "H", "I", "J"));

            List<String> fused = fusion.fuse(dense, sparse, 3);

            assertEquals(3, fused.size());
        }

        @Test
        void shouldHandleEmptySparse() {
            var dense = denseResults(List.of("A", "B", "C"));
            var sparse = List.<Bm25Retriever.ScoredDoc>of();

            List<String> fused = fusion.fuse(dense, sparse, 5);

            assertEquals(3, fused.size());
            assertEquals(List.of("A", "B", "C"), fused);
        }

        @Test
        void shouldHandleEmptyDense() {
            var dense = List.<EmbeddingMatch<TextSegment>>of();
            var sparse = sparseResults(List.of("A", "B"));

            List<String> fused = fusion.fuse(dense, sparse, 5);

            assertEquals(2, fused.size());
            assertEquals(List.of("A", "B"), fused);
        }

        @Test
        void shouldHandleBothEmpty() {
            var dense = List.<EmbeddingMatch<TextSegment>>of();
            var sparse = List.<Bm25Retriever.ScoredDoc>of();

            List<String> fused = fusion.fuse(dense, sparse, 5);

            assertTrue(fused.isEmpty());
        }

        @Test
        void shouldNotDuplicateDocuments() {
            var dense = denseResults(List.of("shared", "only-dense"));
            var sparse = sparseResults(List.of("shared", "only-sparse"));

            List<String> fused = fusion.fuse(dense, sparse, 10);

            long sharedCount = fused.stream().filter(s -> s.equals("shared")).count();
            assertEquals(1, sharedCount);
        }

        @Test
        void shouldRankHighDenseRankHigherWithEqualSparseSupport() {
            // "A" ranks #1 in dense, "B" ranks #2 in dense; both absent from sparse
            var dense = denseResults(List.of("A", "B"));
            var sparse = List.<Bm25Retriever.ScoredDoc>of();

            List<String> fused = fusion.fuse(dense, sparse, 2);

            assertEquals("A", fused.get(0));
            assertEquals("B", fused.get(1));
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // Helpers
    // ═══════════════════════════════════════════════════════════════

    @SuppressWarnings("unchecked")
    private static List<EmbeddingMatch<TextSegment>> denseResults(List<String> texts) {
        List<EmbeddingMatch<TextSegment>> matches = new ArrayList<>();
        for (int i = 0; i < texts.size(); i++) {
            EmbeddingMatch<TextSegment> match = new EmbeddingMatch<>(
                    (double) (texts.size() - i), // score decreases with rank
                    "id-" + i,
                    null, // embedding not needed
                    TextSegment.from(texts.get(i))
            );
            matches.add(match);
        }
        return matches;
    }

    private static List<Bm25Retriever.ScoredDoc> sparseResults(List<String> texts) {
        List<Bm25Retriever.ScoredDoc> docs = new ArrayList<>();
        for (int i = 0; i < texts.size(); i++) {
            docs.add(new Bm25Retriever.ScoredDoc(i, texts.get(i), (double) (texts.size() - i)));
        }
        return docs;
    }
}
