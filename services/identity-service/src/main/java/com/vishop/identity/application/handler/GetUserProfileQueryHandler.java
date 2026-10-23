package com.vishop.identity.application.handler;

import io.github.coreplatform.common.domain.cqrs.QueryHandler;
import io.github.coreplatform.common.domain.exception.EntityNotFoundException;
import com.vishop.identity.application.dto.UserResponseDto;
import com.vishop.identity.application.query.GetUserProfileQuery;
import com.vishop.identity.domain.entities.User;
import com.vishop.identity.domain.ports.UserRepositoryPort;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Query Handler retrieving user profile.
 */
@Component
public class GetUserProfileQueryHandler implements QueryHandler<GetUserProfileQuery, UserResponseDto> {

    private final UserRepositoryPort userRepository;

    public GetUserProfileQueryHandler(UserRepositoryPort userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public UserResponseDto handle(GetUserProfileQuery query) {
        User user = userRepository.findById(query.userId())
                .orElseThrow(() -> new EntityNotFoundException("User not found with ID: " + query.userId()));

        return new UserResponseDto(
                user.getId(),
                user.getTenantId(),
                user.getEmail(),
                user.getFullName(),
                user.getRole(),
                user.getStatus().name(),
                user.getCreatedAt()
        );
    }
}
