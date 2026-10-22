package com.vishop.identity.domain.policy;

import com.vishop.identity.domain.aggregate.User;
import com.vishop.identity.domain.exception.InvalidUserStateException;
import com.vishop.identity.domain.exception.UserSuspendedException;
import com.vishop.identity.domain.valueobject.UserStatus;

/**
 * Domain Policy defining authorization and access eligibility rules for User accounts.
 */
public class UserAccessPolicy {

    /**
     * Checks if a user is eligible to authenticate into the platform.
     *
     * @param user the User aggregate
     * @throws UserSuspendedException if the account is suspended
     * @throws InvalidUserStateException if the account is inactive
     */
    public void checkCanAuthenticate(User user) {
        if (user.getStatus() == UserStatus.SUSPENDED) {
            throw new UserSuspendedException("Account has been suspended. Please contact administrator.");
        }
        if (user.getStatus() == UserStatus.INACTIVE) {
            throw new InvalidUserStateException("Account is currently inactive.");
        }
    }
}
