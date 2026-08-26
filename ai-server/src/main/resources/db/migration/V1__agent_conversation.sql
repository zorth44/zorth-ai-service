CREATE TABLE agent_conversation (
    id VARCHAR(128) NOT NULL,
    user_id VARCHAR(128) NOT NULL,
    title VARCHAR(80) NULL,
    datasource_id VARCHAR(128) NULL,
    database_name VARCHAR(128) NULL,
    created_at TIMESTAMP(3) NOT NULL,
    updated_at TIMESTAMP(3) NOT NULL,
    PRIMARY KEY (id)
);

CREATE INDEX idx_agent_conversation_user_updated
    ON agent_conversation (user_id, updated_at, id);

CREATE TABLE agent_message (
    id VARCHAR(128) NOT NULL,
    conversation_id VARCHAR(128) NOT NULL,
    role VARCHAR(16) NOT NULL,
    content TEXT NOT NULL,
    tools_json TEXT NULL,
    created_at TIMESTAMP(3) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_agent_message_conversation
        FOREIGN KEY (conversation_id) REFERENCES agent_conversation (id) ON DELETE CASCADE
);

CREATE INDEX idx_agent_message_conversation_created
    ON agent_message (conversation_id, created_at, id);
