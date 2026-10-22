package com.vishop.identity.presentation.rest;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.coreplatform.common.security.constants.SecurityHeaders;
import com.vishop.identity.application.dto.AuthResponseDto;
import com.vishop.identity.application.dto.LoginRequest;
import com.vishop.identity.application.dto.RegisterRequest;
import com.vishop.identity.application.dto.UserResponseDto;
import com.vishop.identity.application.service.AuthService;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = AuthController.class)
@AutoConfigureMockMvc(addFilters = false)
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private AuthService authService;

    private final UUID userId = UUID.randomUUID();
    private final UUID tenantId = UUID.randomUUID();

    @Test
    @DisplayName("POST /api/v1/auth/login should return 200 and set session cookie")
    void shouldLoginAndSetCookie() throws Exception {
        LoginRequest request = new LoginRequest("admin@vishop.vn", "Password123!");
        UserResponseDto userDto = new UserResponseDto(
                userId, tenantId, "admin@vishop.vn", "Admin User", "OWNER", "ACTIVE", Instant.now());
        AuthResponseDto responseDto = new AuthResponseDto("dummy.jwt.token", "Bearer", 86400, userDto);

        when(authService.login(any(LoginRequest.class))).thenReturn(responseDto);

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(header().exists("Set-Cookie"))
                .andExpect(header().string("Set-Cookie", containsString("better-auth.session_token=dummy.jwt.token")))
                .andExpect(jsonPath("$.accessToken").value("dummy.jwt.token"))
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.user.email").value("admin@vishop.vn"));
    }

    @Test
    @DisplayName("POST /api/v1/auth/register should return 201 and set session cookie")
    void shouldRegisterAndSetCookie() throws Exception {
        RegisterRequest request = new RegisterRequest(
                "newuser@vishop.vn", "Password123!", "New User", tenantId, "OWNER");
        UserResponseDto userDto = new UserResponseDto(
                userId, tenantId, "newuser@vishop.vn", "New User", "OWNER", "ACTIVE", Instant.now());
        AuthResponseDto responseDto = new AuthResponseDto("dummy.jwt.token", "Bearer", 86400, userDto);

        when(authService.register(any(RegisterRequest.class))).thenReturn(responseDto);

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Set-Cookie"))
                .andExpect(jsonPath("$.accessToken").value("dummy.jwt.token"))
                .andExpect(jsonPath("$.user.fullName").value("New User"));
    }

    @Test
    @DisplayName("GET /api/v1/auth/me should return 200 with user profile")
    void shouldGetMe() throws Exception {
        UserResponseDto userDto = new UserResponseDto(
                userId, tenantId, "admin@vishop.vn", "Admin User", "OWNER", "ACTIVE", Instant.now());

        when(authService.getMe(userId)).thenReturn(userDto);

        mockMvc.perform(get("/api/v1/auth/me")
                        .header(SecurityHeaders.X_USER_ID, userId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(userId.toString()))
                .andExpect(jsonPath("$.email").value("admin@vishop.vn"));
    }

    @Test
    @DisplayName("POST /api/v1/auth/logout should clear session cookie")
    void shouldLogoutAndClearCookie() throws Exception {
        mockMvc.perform(post("/api/v1/auth/logout"))
                .andExpect(status().isOk())
                .andExpect(header().string("Set-Cookie", containsString("Max-Age=0")));
    }
}
