ALTER TABLE evaluation_runs
  ALTER COLUMN average_score TYPE DOUBLE PRECISION USING average_score::double precision;

ALTER TABLE evaluation_case_results
  ALTER COLUMN answer_similarity TYPE DOUBLE PRECISION USING answer_similarity::double precision,
  ALTER COLUMN source_coverage TYPE DOUBLE PRECISION USING source_coverage::double precision;
