package com.se183891.badminton_backend.common.exception;

import org.springframework.core.NestedExceptionUtils;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.Locale;

/**
 * Doi loi vi pham unique index cua SQL Server thanh thong bao than thien.
 * Ten index phai khop voi V1__create_auth_tables.sql.
 */
public final class UniqueViolations {

    private UniqueViolations() {
    }

    public static String messageFor(DataIntegrityViolationException ex) {
        Throwable root = NestedExceptionUtils.getMostSpecificCause(ex);
        String detail = root.getMessage() == null ? "" : root.getMessage().toUpperCase(Locale.ROOT);
        if (detail.contains("UX_USERS_EMAIL")) {
            return ErrorMessages.EMAIL_TAKEN;
        }
        if (detail.contains("UX_USERS_PHONE")) {
            return ErrorMessages.PHONE_TAKEN;
        }
        return ErrorMessages.DUPLICATE_DATA;
    }
}
