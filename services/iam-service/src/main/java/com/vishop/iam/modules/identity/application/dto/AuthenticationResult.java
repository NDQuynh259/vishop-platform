package com.vishop.iam.modules.identity.application.dto;

public record AuthenticationResult(AuthResponse response, String sessionToken) {}
