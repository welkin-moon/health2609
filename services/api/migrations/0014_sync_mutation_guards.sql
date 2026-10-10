-- D1 batch transactions use this CHECK to reject mutations whose credential or
-- key snapshot changed between reading it and writing the encrypted envelopes.
-- Guard rows are removed in the same transaction and never persist on failure.
CREATE TABLE IF NOT EXISTS sync_mutation_guards_v2 (
  id TEXT PRIMARY KEY,
  valid INTEGER NOT NULL CHECK (valid = 1)
);
