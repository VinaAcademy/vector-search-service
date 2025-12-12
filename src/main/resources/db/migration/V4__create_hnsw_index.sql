CREATE INDEX IF NOT EXISTS idx_course_embedding_hnsw
    ON course_embedding
    USING hnsw (embedding vector_cosine_ops);
