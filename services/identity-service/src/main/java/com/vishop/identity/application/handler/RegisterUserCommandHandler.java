package com.vishop.identity.application.handler;

import io.github.coreplatform.common.domain.cqrs.CommandHandler;
import io.github.coreplatform.common.security.constants.CommonPermissions;
import com.vishop.identity.application.command.RegisterUserCommand;
import com.vishop.identity.application.dto.AuthResponseDto;
import com.vishop.identity.application.dto.UserResponseDto;
import com.vishop.identity.application.port.PasswordHasherPort;
import com.vishop.identity.application.port.TokenIssuerPort;
import com.vishop.identity.application.port.UserEventPublisherPort;
import com.vishop.identity.domain.entities.User;
import com.vishop.identity.domain.exception.EmailAlreadyExistsException;
import com.vishop.identity.domain.ports.UserRepositoryPort;
import com.vishop.identity.domain.services.PasswordPolicyService;
import com.vishop.identity.domain.valueobjects.Email;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Command Handler executing user registration use case.
 */
@Component
public class RegisterUserCommandHandler implements CommandHandler<RegisterUserCommand, AuthResponseDto> {

    private final UserRepositoryPort userRepository;
    private final PasswordHasherPort passwordHasher;
    private final TokenIssuerPort tokenIssuer;
    private final UserEventPublisherPort eventPublisher;
    private final PasswordPolicyService passwordPolicyService = new PasswordPolicyService();

    public RegisterUserCommandHandler(
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
    public AuthResponseDto handle(RegisterUserCommand command) {
        Email email = Email.of(command.email());

        // 1. Enforce password complexity policy
        passwordPolicyService.validate(command.password());

        // 2. Check email uniqueness invariant
        if (userRepository.existsByEmail(email.value())) {
            throw new EmailAlreadyExistsException(email.value());
        }

        // 3. Hash password via port
        String encodedPassword = passwordHasher.encode(command.password());

        // 4. Create Aggregate Root (emits UserRegisteredEvent)
        User user = User.create(
                command.tenantId(),
                email.value(),
                encodedPassword,
                command.fullName(),
                command.role()
        );

        // 5. Save aggregate
        User saved = userRepository.save(user);
        if (saved == null) {
            saved = user;
        }

        // 6. Pull and publish recorded domain events
        eventPublisher.publishAll(saved.pullDomainEvents());

        // 7. Resolve roles and permissions
        List<String> roles = List.of(saved.getRole());
        List<String> permissions = resolvePermissions(saved.getRole());

        // 8. Issue token
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
