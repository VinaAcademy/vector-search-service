package hcmute.vina.vectorsearchservice.service.rerank;

import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import ai.djl.inference.Predictor;
import ai.djl.repository.zoo.Criteria;
import ai.djl.repository.zoo.ZooModel;
import ai.djl.training.util.ProgressBar;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

@Service
public class BgeRerankerService {

    private ZooModel<RankInput, Float> model;
    private Predictor<RankInput, Float> predictor;

    @Value("${search.rerank.enabled:false}")
    private boolean rerankEnabled;

    @PostConstruct
    public void init() {
        if (!rerankEnabled) {
            System.out.println("ℹ️ Reranker disabled in config (search.rerank.enabled=false)");
            predictor = null;
            model = null;
            return;
        }

        try {
            Criteria<RankInput, Float> criteria = Criteria.builder()
                    .setTypes(RankInput.class, Float.class)
                    .optModelUrls("djl://ai.djl.huggingface.pytorch/BAAI/bge-reranker-v2-m3")
                    .optEngine("PyTorch")
                    .optTranslator(new BgeRerankerTranslator())
                    .optProgress(new ProgressBar())
                    .build();

            model = criteria.loadModel();
            predictor = model.newPredictor();
            System.out.println("✅ BAAI/bge-reranker-v2-m3 (multilingual) loaded");
        } catch (Exception e) {
            System.err.println("⚠️ Failed to load Reranker: " + e.getMessage());
            e.printStackTrace();
            predictor = null;
            model = null;
        }
    }

    public List<ScoredDocument> batchRerank(String query, List<String> docs) {
        List<ScoredDocument> result = new ArrayList<>();

        if (predictor == null) {
            return result;
        }

        // No internal batching; iterate and predict per item to avoid TorchScript shape issues
        for (int i = 0; i < docs.size(); i++) {
            try {
                RankInput input = new RankInput(query, docs.get(i));
                Float score = predictor.predict(input);
                if (score != null && !score.isNaN() && !score.isInfinite()) {
                    result.add(new ScoredDocument(i, docs.get(i), score));
                } else {
                    result.add(new ScoredDocument(i, docs.get(i), 0.5f));
                }
            } catch (Exception e) {
                System.err.println("⚠️ Rerank failed for doc " + i + ": " + e.getMessage());
                result.add(new ScoredDocument(i, docs.get(i), 0.5f));
            }
        }

        result.sort((a, b) -> Float.compare(b.getScore(), a.getScore()));
        return result;
    }

    @PreDestroy
    public void cleanup() {
        if (predictor != null) predictor.close();
        if (model != null) model.close();
    }

    public boolean isAvailable() {
        return predictor != null;
    }
}
