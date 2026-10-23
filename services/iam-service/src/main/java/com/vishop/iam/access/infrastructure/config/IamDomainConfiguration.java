package com.vishop.iam.access.infrastructure.config;

import com.vishop.iam.access.domain.ports.MembershipRepositoryPort;
import com.vishop.iam.access.domain.ports.RoleRepositoryPort;
import com.vishop.iam.access.domain.ports.UserActivityPort;
import com.vishop.iam.access.domain.services.RoleAssignmentPolicy;
import io.github.coreplatform.common.security.constants.CommonPermissions;
import java.util.HashSet;
import java.util.Set;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class IamDomainConfiguration {
  @Bean
  RoleAssignmentPolicy roleAssignmentPolicy(
      RoleRepositoryPort roles, MembershipRepositoryPort members, UserActivityPort users) {
    Set<String> catalogue = new HashSet<>();
    for (Class<?> group : CommonPermissions.class.getDeclaredClasses())
      for (var field : group.getFields()) {
        if (field.getType() == String.class)
          try {
            catalogue.add((String) field.get(null));
          } catch (IllegalAccessException failure) {
            throw new IllegalStateException(failure);
          }
      }
    return new RoleAssignmentPolicy(catalogue, users, members, roles);
  }
}
