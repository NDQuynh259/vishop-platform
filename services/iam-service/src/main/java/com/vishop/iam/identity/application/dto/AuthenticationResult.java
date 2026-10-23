package com.vishop.iam.identity.application.dto;

public record AuthenticationResult(AuthResponse response, String sessionToken) {}
