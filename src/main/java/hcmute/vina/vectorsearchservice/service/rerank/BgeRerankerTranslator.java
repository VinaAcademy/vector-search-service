package hcmute.vina.vectorsearchservice.service.rerank;

import java.io.IOException;
import java.util.List;

import ai.djl.huggingface.tokenizers.Encoding;
import ai.djl.huggingface.tokenizers.HuggingFaceTokenizer;
import ai.djl.ndarray.NDArray;
import ai.djl.ndarray.NDList;
import ai.djl.ndarray.NDManager;
import ai.djl.translate.Batchifier;
import ai.djl.translate.Translator;
import ai.djl.translate.TranslatorContext;

public class BgeRerankerTranslator implements Translator<RankInput, Float> {

    private HuggingFaceTokenizer tokenizer;
    // CRITICAL: Must match model's trace length (512 exactly) to avoid TorchScript shape errors
    // Padding/truncation to fixed length ensures tensor shapes are predictable
    private final int maxLength = 512; 

    @Override
    public void prepare(TranslatorContext ctx) throws IOException {
        // Model name trên HuggingFace
        tokenizer = HuggingFaceTokenizer.newInstance("BAAI/bge-reranker-v2-m3");
    }

    @Override
    public NDList processInput(TranslatorContext ctx, RankInput input) {
        try {
            // Pair encoding for BAAI/bge-reranker: query and document
            Encoding encoding = tokenizer.encode(input.query(), input.doc());
            
            NDManager manager = ctx.getNDManager();

            long[] inputIds = encoding.getIds();
            long[] attentionMask = encoding.getAttentionMask();
            long[] tokenTypeIds = encoding.getTypeIds();

            // Truncate and pad to fixed maxLength
            int lengthToCopy = Math.min(inputIds.length, maxLength);
            long[] paddedIds = new long[maxLength];
            long[] paddedMask = new long[maxLength];
            long[] paddedTypeIds = new long[maxLength];

            System.arraycopy(inputIds, 0, paddedIds, 0, lengthToCopy);
            System.arraycopy(attentionMask, 0, paddedMask, 0, lengthToCopy);
            System.arraycopy(tokenTypeIds, 0, paddedTypeIds, 0, lengthToCopy);

            // Batch dimension [1, maxLength]
            NDArray idsArray = manager.create(paddedIds).expandDims(0);
            NDArray maskArray = manager.create(paddedMask).expandDims(0);
            NDArray typeArray = manager.create(paddedTypeIds).expandDims(0);

            // Name inputs to help TorchScript binding
            idsArray.setName("input_ids");
            maskArray.setName("attention_mask");
            typeArray.setName("token_type_ids");

            return new NDList(idsArray, maskArray, typeArray);
        } catch (Exception e) {
            throw new RuntimeException("Failed to process input for reranking: " + e.getMessage(), e);
        }
    }

    @Override
    public Float processOutput(TranslatorContext ctx, NDList list) {
        try {
            // Get the output tensor (logits)
            NDArray output = list.get(0);
            
            // Handle different output shapes
            float logits;
            if (output.getShape().dimension() > 0) {
                logits = output.getFloat(0);
            } else {
                logits = output.getFloat();
            }
            
            // Apply sigmoid to convert logits to probability [0, 1]
            float probability = (float) (1.0 / (1.0 + Math.exp(-logits)));
            
            // Ensure valid range
            return Math.max(0.0f, Math.min(1.0f, probability));
        } catch (Exception e) {
            System.err.println("⚠️ Error processing reranker output: " + e.getMessage());
            return 0.5f; // Return neutral score on error
        }
    }

    @Override
    public Batchifier getBatchifier() {
        // Disable DJL batching to avoid TorchScript fixed-shape issues
        return Batchifier.STACK;
    }
}