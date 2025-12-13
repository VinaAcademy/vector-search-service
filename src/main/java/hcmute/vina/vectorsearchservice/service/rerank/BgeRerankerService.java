package hcmute.vina.vectorsearchservice.service.rerank;

import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import ai.djl.inference.Predictor;
import ai.djl.repository.zoo.Criteria;
import ai.djl.repository.zoo.ZooModel;
import ai.djl.training.util.ProgressBar;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import ai.djl.translate.TranslateException;

@Service
public class BgeRerankerService {

    private ZooModel<RankInput[], float[]> model;
    private Predictor<RankInput[], float[]> predictor;

    @Value("${search.rerank:true}")
    private boolean rerankEnabled;

    @Value("${search.rerank.batchSize:32}")
    private int batchSize;

//    @PostConstruct
    public void init() {
        if (!rerankEnabled) {
            predictor = null;
            model = null;
            return;
        }
        try {
            Criteria<RankInput[], float[]> criteria = Criteria.builder()
                    .setTypes(RankInput[].class, float[].class)
                    .optModelUrls("djl://ai.djl.huggingface.pytorch/BAAI/bge-reranker-v2-m3")
                    .optEngine("PyTorch")
                    .optTranslator(new BgeRerankerTranslator())
                    .optProgress(new ProgressBar())
                    .build();

            model = criteria.loadModel();
            predictor = model.newPredictor();
            System.out.println("BGE Reranker loaded.");
        } catch (Exception e) {
            e.printStackTrace();
            predictor = null;
            model = null;
        }
    }

    public List<ScoredDocument> batchRerank(String query, List<String> docs) {
        List<ScoredDocument> out = new ArrayList<>();
        if (predictor == null) return out;

        // create RankInput array
        RankInput[] all = docs.stream().map(d -> new RankInput(query, d)).toArray(RankInput[]::new);

        try {
            float[] scores = predictWithChunking(all, batchSize);
            for (int i = 0; i < docs.size(); ++i) {
                out.add(new ScoredDocument(i, docs.get(i), scores[i]));
            }
            out.sort((a, b) -> Float.compare(b.getScore(), a.getScore()));
            return out;
        } catch (Exception e) {
            e.printStackTrace();
            // fallback: return unchanged with default score
            for (int i = 0; i < docs.size(); ++i) {
                out.add(new ScoredDocument(i, docs.get(i), 0.5f));
            }
            return out;
        }
    }

    private float[] predictWithChunking(RankInput[] all, int chunkSize) throws TranslateException {
        List<Float> acc = new ArrayList<>();
        for (int i = 0; i < all.length; i += chunkSize) {
            int end = Math.min(all.length, i + chunkSize);
            RankInput[] sub = Arrays.copyOfRange(all, i, end);
            float[] part = predictor.predict(sub);
            for (float v : part) acc.add(v);
        }
        float[] res = new float[acc.size()];
        for (int i = 0; i < res.length; ++i) res[i] = acc.get(i);
        return res;
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
