CREATE TABLE event_store
(
  id             UUID PRIMARY KEY,
  aggregate_type VARCHAR(64)  NOT NULL,
  aggregate_id   UUID         NOT NULL,
  event_type     VARCHAR(128) NOT NULL,
  payload        JSONB        NOT NULL,
  version        BIGINT       NOT NULL,
  created_at     TIMESTAMP    NOT NULL
);
