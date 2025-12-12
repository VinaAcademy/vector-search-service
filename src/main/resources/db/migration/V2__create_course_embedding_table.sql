CREATE TABLE IF NOT EXISTS course_embedding (
    course_id UUID PRIMARY KEY,
    embedding vector(1536)
);
