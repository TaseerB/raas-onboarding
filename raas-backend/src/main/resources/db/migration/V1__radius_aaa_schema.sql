-- =============================================================================
-- V1: RADIUS AAA Schema — Topic 2
-- Tables: radius_users, authorization_attributes, radius_clients,
--         accounting_sessions
-- Compatible with PostgreSQL (prod) and H2 MODE=PostgreSQL (test)
-- =============================================================================

CREATE TABLE radius_users (
    id            UUID                     NOT NULL,
    username      VARCHAR(64)              NOT NULL,
    password_hash VARCHAR(255)             NOT NULL,
    enabled       BOOLEAN                  NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_radius_users          PRIMARY KEY (id),
    CONSTRAINT uk_radius_users_username UNIQUE      (username)
);

CREATE TABLE authorization_attributes (
    id              UUID         NOT NULL,
    user_id         UUID         NOT NULL,
    attribute_name  VARCHAR(64)  NOT NULL,
    attribute_value VARCHAR(255) NOT NULL,
    CONSTRAINT pk_authorization_attributes PRIMARY KEY (id),
    CONSTRAINT fk_auth_attrs_user
        FOREIGN KEY (user_id) REFERENCES radius_users(id) ON DELETE CASCADE
);

CREATE TABLE radius_clients (
    id          UUID                     NOT NULL,
    shortname   VARCHAR(64)              NOT NULL,
    ip_address  VARCHAR(45)              NOT NULL,
    secret_hash VARCHAR(255)             NOT NULL,
    enabled     BOOLEAN                  NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_radius_clients           PRIMARY KEY (id),
    CONSTRAINT uk_radius_clients_shortname UNIQUE      (shortname)
);

CREATE TABLE accounting_sessions (
    id           UUID                     NOT NULL,
    username     VARCHAR(64)              NOT NULL,
    nas_ip       VARCHAR(45),
    session_id   VARCHAR(128)             NOT NULL,
    event_type   VARCHAR(16)              NOT NULL
        CHECK (event_type IN ('START', 'STOP', 'INTERIM')),
    session_time INTEGER,
    bytes_in     BIGINT,
    bytes_out    BIGINT,
    occurred_at  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_accounting_sessions            PRIMARY KEY (id),
    CONSTRAINT uk_accounting_sessions_session_id UNIQUE      (session_id)
);

-- Indexes for common query patterns
CREATE INDEX idx_auth_attrs_user_id  ON authorization_attributes(user_id);
CREATE INDEX idx_accounting_username ON accounting_sessions(username);
CREATE INDEX idx_accounting_occurred ON accounting_sessions(occurred_at);
