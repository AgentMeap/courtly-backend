-- =============================================================
-- V3: Cho phep ma OTP dung de dat lai mat khau (quen mat khau)
-- V2 gioi han cot purpose bang CHECK constraint chi gom 'REGISTER',
-- nen phai thay constraint de chap nhan them 'RESET_PASSWORD'.
-- =============================================================

ALTER TABLE email_otps DROP CONSTRAINT CK_email_otps_purpose;

ALTER TABLE email_otps
    ADD CONSTRAINT CK_email_otps_purpose CHECK (purpose IN ('REGISTER', 'RESET_PASSWORD'));
