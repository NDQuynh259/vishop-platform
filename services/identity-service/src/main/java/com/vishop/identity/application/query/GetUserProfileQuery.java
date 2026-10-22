package com.vishop.identity.application.query;

import io.github.coreplatform.common.domain.cqrs.Query;
import com.vishop.identity.application.dto.UserResponseDto;
import java.util.UUID;

/**
 * Query representing retrieval of user profile by identifier.
 */
public record GetUserProfileQuery(
        UUID userId
) implements Query<UserResponseDto> {
}
