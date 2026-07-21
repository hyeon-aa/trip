-- jeju_place.embedding을 text에서 진짜 pgvector vector 타입으로 바꾼다.
-- 기존 저장된 값(텍스트 "[0.021, -0.104, ...]" 형태)을 USING 절로 그대로
-- 재캐스팅해서 데이터 손실 없이 변환한다.
-- 차원(3072)은 gemini-embedding-001의 기본 출력 차원을 DB에서 직접 확인한 값.
ALTER TABLE jeju_place
    ALTER COLUMN embedding TYPE vector(3072)
    USING embedding::vector;
