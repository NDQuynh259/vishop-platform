package com.vishop.iam.infra.security;

import java.math.BigInteger;
import java.time.Duration;
import java.util.Arrays;
import java.util.Base64;
import java.util.Map;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Publishes public verification material only; never serializes the private-key holder. */
@RestController
public class JwksController {
  private final Map<String, Object> document;

  public JwksController(IamJwtKeyConfiguration.KeySet keys) {
    var publicKeys = keys.publicKeys().entrySet().stream().map(entry -> {
      var key = entry.getValue();
      return Map.of("kty", "RSA", "use", "sig", "alg", "RS256", "kid", entry.getKey(),
          "n", unsigned(key.getModulus()), "e", unsigned(key.getPublicExponent()));
    }).toList();
    document = Map.of("keys", publicKeys);
  }

  @GetMapping("/.well-known/jwks.json")
  public ResponseEntity<Map<String, Object>> keys() {
    return ResponseEntity.ok().cacheControl(CacheControl.maxAge(Duration.ofMinutes(5)).cachePublic())
        .body(document);
  }

  private static String unsigned(BigInteger value) {
    byte[] bytes = value.toByteArray();
    if (bytes[0] == 0) bytes = Arrays.copyOfRange(bytes, 1, bytes.length);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  }
}
