package com.vishop.identity.application.port;

import com.vishop.identity.domain.aggregate.User;
import java.util.List;

/**
 * Outbound Port for generating access tokens (e.g. JWT).
 */
public interface TokenIssuerPort {

    String generateAccessToken(User user, List<String> roles, List<String> permissions);

    long getExpirationSeconds();
}
