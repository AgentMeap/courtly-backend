-- =============================================================
-- V1: Bang xac thuc cho Courtly (SQL Server / T-SQL)
-- =============================================================

CREATE TABLE users (
    id             UNIQUEIDENTIFIER NOT NULL CONSTRAINT DF_users_id DEFAULT NEWID(),
    full_name      NVARCHAR(100)    NOT NULL,
    -- Email luon duoc luu dang lowercase (ung dung chuan hoa truoc khi ghi)
    email          NVARCHAR(255)    NOT NULL,
    phone          VARCHAR(20)      NULL,
    -- NULL voi tai khoan chi dang nhap bang Google
    password_hash  VARCHAR(100)     NULL,
    role           VARCHAR(20)      NOT NULL,
    auth_provider  VARCHAR(20)      NOT NULL,
    firebase_uid   VARCHAR(128)     NULL,
    avatar_url     NVARCHAR(500)    NULL,
    enabled        BIT              NOT NULL CONSTRAINT DF_users_enabled DEFAULT 1,
    created_at     DATETIME2        NOT NULL,
    updated_at     DATETIME2        NOT NULL,
    CONSTRAINT PK_users PRIMARY KEY (id),
    CONSTRAINT CK_users_role CHECK (role IN ('CUSTOMER', 'ADMIN')),
    CONSTRAINT CK_users_auth_provider CHECK (auth_provider IN ('LOCAL', 'GOOGLE'))
);

-- Email luon NOT NULL nen dung unique index thuong.
CREATE UNIQUE INDEX UX_users_email ON users(email);

-- LUU Y SQL Server: UNIQUE constraint / unique index thuong coi NULL la mot gia tri,
-- nen chi cho phep DUNG MOT dong co gia tri NULL. phone va firebase_uid co the NULL
-- o rat nhieu dong (user khong nhap SDT, user khong dung Google), vi vay phai dung
-- FILTERED UNIQUE INDEX: chi ap dung rang buoc unique cho cac dong co gia tri.
CREATE UNIQUE INDEX UX_users_phone ON users(phone) WHERE phone IS NOT NULL;
CREATE UNIQUE INDEX UX_users_firebase_uid ON users(firebase_uid) WHERE firebase_uid IS NOT NULL;

CREATE TABLE refresh_tokens (
    id           UNIQUEIDENTIFIER NOT NULL CONSTRAINT DF_refresh_tokens_id DEFAULT NEWID(),
    user_id      UNIQUEIDENTIFIER NOT NULL,
    -- SHA-256 (hex) cua refresh token; KHONG BAO GIO luu token tho
    token_hash   CHAR(64)         NOT NULL,
    expires_at   DATETIME2        NOT NULL,
    revoked      BIT              NOT NULL CONSTRAINT DF_refresh_tokens_revoked DEFAULT 0,
    created_at   DATETIME2        NOT NULL,
    -- id cua token moi da thay the token nay khi xoay vong (rotation)
    replaced_by  UNIQUEIDENTIFIER NULL,
    CONSTRAINT PK_refresh_tokens PRIMARY KEY (id),
    CONSTRAINT UQ_refresh_tokens_token_hash UNIQUE (token_hash),
    CONSTRAINT FK_refresh_tokens_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE INDEX IX_refresh_tokens_user_id ON refresh_tokens(user_id);
