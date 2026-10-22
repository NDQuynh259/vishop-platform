package com.vishop.identity.application.handler;

import io.github.coreplatform.common.domain.cqrs.CommandHandler;
import io.github.coreplatform.common.security.constants.CommonPermissions;
import com.vishop.identity.application.command.LoginCommand;
import com.vishop.identity.application.dto.AuthResponseDto;
import com.vishop.identity.application.dto.UserResponseDto;
import com.vishop.identity.application.port.PasswordHasherPort;
import com.vishop.identity.application.port.TokenIssuerPort;
import com.vishop.identity.application.port.UserEventPublisherPort;
import com.vishop.identity.domain.aggregate.User;
import com.vishop.identity.domain.exception.InvalidCredentialsException;
import com.vishop.identity.domain.policy.UserAccessPolicy;
import com.vishop.identity.domain.repository.UserRepositoryPort;
import com.vishop.identity.domain.valueobject.Email;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Command Handler executing user authentication and login use case.
 */
@Component
public class LoginCommandHandler implements CommandHandler<LoginCommand, AuthResponseDto> {

    private final UserRepositoryPort userRepository;
    private final PasswordHasherPort passwordHasher;
    private final TokenIssuerPort tokenIssuer;
    private final UserEventPublisherPort eventPublisher;
    private final UserAccessPolicy userAccessPolicy = new UserAccessPolicy();

    public LoginCommandHandler(
            UserRepositoryPort userRepository,
            PasswordHasherPort passwordHasher,
            TokenIssuerPort tokenIssuer,
            UserEventPublisherPort eventPublisher) {
        this.userRepository = userRepository;
        this.passwordHasher = passwordHasher;
        this.tokenIssuer = tokenIssuer;
        this.eventPublisher = eventPublisher;
    }

    @Override
    @Transactional
    public AuthResponseDto handle(LoginCommand command) {
        Email email = Email.of(command.email());

        // 1. Fetch user by email
        User user = userRepository.findByEmail(email.value())
                .orElseThrow(InvalidCredentialsException::new);

        // 2. Verify password match
        if (!passwordHasher.matches(command.password(), user.getPasswordHash())) {
            throw new InvalidCredentialsException();
        }

        // 3. Enforce access policy (active vs suspended)
        userAccessPolicy.checkCanAuthenticate(user);

        // 4. Mutate aggregate state & register UserLoggedInEvent
        user.recordLogin();
        User saved = userRepository.save(user);
        if (saved == null) {
            saved = user;
        }

        // 5. Pull and publish recorded domain events
        eventPublisher.publishAll(saved.pullDomainEvents());

        // 6. Resolve roles and permissions
        List<String> roles = List.of(saved.getRole());
        List<String> permissions = resolvePermissions(saved.getRole());

        // 7. Issue access token
        String token = tokenIssuer.generateAccessToken(saved, roles, permissions);

        return new AuthResponseDto(
                token,
                "Bearer",
                tokenIssuer.getExpirationSeconds(),
                new UserResponseDto(
                        saved.getId(),
                        saved.getTenantId(),
                        saved.getEmail(),
                        saved.getFullName(),
                        saved.getRole(),
                        saved.getStatus().name(),
                        saved.getCreatedAt()
                )
        );
    }

    private List<String> resolvePermissions(String role) {
        if ("OWNER".equalsIgnoreCase(role) || "ADMIN".equalsIgnoreCase(role)) {
            return List.of(
                    CommonPermissions.Tenant.VIEW,
                    CommonPermissions.Tenant.MANAGE,
                    CommonPermissions.Tenant.MEMBER_VIEW,
                    CommonPermissions.Tenant.MEMBER_INVITE,
                    CommonPermissions.Order.VIEW,
                    CommonPermissions.Order.CREATE,
                    CommonPermissions.Order.UPDATE,
                    CommonPermissions.Product.VIEW,
                    CommonPermissions.Product.CREATE,
                    CommonPermissions.Inventory.VIEW,
                    CommonPermissions.Inventory.ADJUST
            );
        }
        return List.of(
                CommonPermissions.Tenant.VIEW,
                CommonPermissions.Order.VIEW,
                CommonPermissions.Product.VIEW,
                CommonPermissions.Inventory.VIEW
        );
    }
}
