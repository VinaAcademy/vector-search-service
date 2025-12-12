package hcmute.vina.vectorsearchservice.service.rerank;

public class RankInput {
    private final String query;
    private final String document;

    public RankInput(String query, String document) {
        this.query = query;
        this.document = document;
    }

    public String getQuery() {
        return query;
    }

    public String getDocument() {
        return document;
    }
}
