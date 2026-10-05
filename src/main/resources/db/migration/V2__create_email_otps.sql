-- =============================================================
-- V2: Ma OTP gui qua email (xac minh email khi dang ky)
-- =============================================================

CREATE TABLE email_otps (
    id          UNIQUEIDENTIFIER NOT NULL CONSTRAINT DF_email_otps_id DEFAULT NEWID(),
    -- Email nhan ma (lowercase)
    email       NVARCHAR(255)    NOT NULL,
    purpose     VARCHAR(30)      NOT NULL,
    -- BCrypt cua ma 6 so; KHONG luu ma tho (ma 6 so neu chi SHA-256 thi do lai rat nhanh)
    code_hash   VARCHAR(100)     NOT NULL,
    expires_at  DATETIME2        NOT NULL,
    -- So lan nhap sai; vuot gioi han thi ma bi vo hieu
    attempts    INT              NOT NULL CONSTRAINT DF_email_otps_attempts DEFAULT 0,
    -- 1 khi ma da duoc dung thanh cong HOAC bi thay the boi ma moi
    consumed    BIT              NOT NULL CONSTRAINT DF_email_otps_consumed DEFAULT 0,
    created_at  DATETIME2        NOT NULL,
    CONSTRAINT PK_email_otps PRIMARY KEY (id),
    CONSTRAINT CK_email_otps_purpose CHECK (purpose IN ('REGISTER'))
);

-- Tra cuu ma moi nhat theo email + muc dich, dem so lan gui trong 1 gio
CREATE INDEX IX_email_otps_email_purpose_created ON email_otps(email, purpose, created_at);
