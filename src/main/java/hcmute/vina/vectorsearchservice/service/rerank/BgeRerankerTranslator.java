package hcmute.vina.vectorsearchservice.service.rerank;

import ai.djl.huggingface.tokenizers.Encoding;
import ai.djl.huggingface.tokenizers.HuggingFaceTokenizer;

import ai.djl.ndarray.NDArray;
import ai.djl.ndarray.NDList;
import ai.djl.ndarray.NDManager;
import ai.djl.translate.Batchifier;
import ai.djl.translate.Translator;
import ai.djl.translate.TranslatorContext;

import java.io.IOException;
import java.util.List;

public class BgeRerankerTranslator implements Translator<RankInput[], float[]> {

    private HuggingFaceTokenizer tokenizer;

    // BGE Reranker v2-m3 (XLM-R architecture) -> pad token ALWAYS id = 1
    private static final long PAD_TOKEN_ID = 1;

    public BgeRerankerTranslator() {
    }

    @Override
    public void prepare(TranslatorContext ctx) throws IOException {
        tokenizer = HuggingFaceTokenizer.newInstance("BAAI/bge-reranker-v2-m3");
    }

    @Override
    public NDList processInput(TranslatorContext ctx, RankInput[] batch) throws Exception {
        NDManager manager = ctx.getNDManager();
        int batchSize = batch.length;

        Encoding[] encs = new Encoding[batchSize];
        int maxLen = 0;

        for (int i = 0; i < batchSize; ++i) {
            encs[i] = tokenizer.encode(batch[i].getQuery(), batch[i].getDocument());
            int len = safeLength(encs[i]);
            if (len > maxLen) maxLen = len;
        }

        long[][] ids2d = new long[batchSize][maxLen];
        long[][] mask2d = new long[batchSize][maxLen];

        for (int i = 0; i < batchSize; ++i) {
            long[] ids = safeLongArray(encs[i].getIds());
            long[] attn = safeLongArray(encs[i].getAttentionMask());

            for (int j = 0; j < maxLen; ++j) {
                if (j < ids.length) {
                    ids2d[i][j] = ids[j];
                    mask2d[i][j] = j < attn.length ? attn[j] : 1;
                } else {
                    ids2d[i][j] = PAD_TOKEN_ID;
                    mask2d[i][j] = 0;
                }
            }
        }

        NDArray idsArr = manager.create(ids2d);
        NDArray maskArr = manager.create(mask2d);
        return new NDList(idsArr, maskArr);
    }

    @Override
    public float[] processOutput(TranslatorContext ctx, NDList list) {
        NDArray logits = list.get(0); // [batch, 1] hoặc [batch]
        try {
            return logits.squeeze(-1).toFloatArray();
        } catch (Exception e) {
            return logits.toFloatArray();
        }
    }

    @Override
    public Batchifier getBatchifier() {
        return null;
    }

    // -------- helpers --------

    private int safeLength(Encoding e) {
        try { return e.getIds().length; } catch (Exception ignore) {}
        return 0;
    }

    private long[] safeLongArray(long[] arr) {
        return arr != null ? arr : new long[0];
    }
}
