/*==========================================================================
Copyright © 2026 Charlie's Tenacity Ltd. All rights reserved.

This source code is proprietary and confidential. Unauthorised copying, modification, distribution or use is prohibited.
============================================================================*/
package com.example.security.model;

public enum Role {
    PATIENT,
    OFFICE,
    OFFICE_ADMIN,
    HQ,
    SUPER,

    /** Legacy values retained so old Atlas user documents can still be read. */
    USER,
    DEVELOPER;

    public Role normalized() {
        return switch (this) {
            case USER -> PATIENT;
            case DEVELOPER -> OFFICE_ADMIN;
            default -> this;
        };
    }
}
