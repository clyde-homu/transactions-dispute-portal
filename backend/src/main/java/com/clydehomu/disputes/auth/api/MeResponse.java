package com.clydehomu.disputes.auth.api;

import com.clydehomu.disputes.auth.application.AuthenticatedUser;
import com.clydehomu.disputes.auth.domain.Role;
import java.util.UUID;

public record MeResponse(UUID id, String username, String displayName, Role role) {

    static MeResponse from(AuthenticatedUser user) {
        return new MeResponse(user.getId(), user.getUsername(), user.getDisplayName(), user.getRole());
    }
}
