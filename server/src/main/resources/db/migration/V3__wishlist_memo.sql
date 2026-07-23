-- 위시리스트에 저장 이유(추천받아서/SNS에서 봐서 등)를 남길 수 있도록 memo 컬럼을 추가한다.
-- 기존 행은 값이 없으므로 nullable로 둔다.
ALTER TABLE wishlist ADD COLUMN memo text;
