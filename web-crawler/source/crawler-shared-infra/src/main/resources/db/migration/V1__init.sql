CREATE TABLE visited_url (
  url               VARCHAR(2048) PRIMARY KEY,
  domain            VARCHAR(255) NOT NULL,
  last_crawled_at   TIMESTAMP WITH TIME ZONE NOT NULL,
  last_content_hash CHAR(64) NOT NULL,
  created_at        TIMESTAMP WITH TIME ZONE NOT NULL,
  updated_at        TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE INDEX idx_visited_url_stale ON visited_url(last_crawled_at);

CREATE TABLE content_seen (
  hash         CHAR(64) PRIMARY KEY,
  storage_key  VARCHAR(512) NOT NULL,
  created_at   TIMESTAMP WITH TIME ZONE NOT NULL
);
