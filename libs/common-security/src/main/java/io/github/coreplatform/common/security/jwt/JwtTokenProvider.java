package io.github.coreplatform.common.security.jwt;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.LocatorAdapter;
import io.jsonwebtoken.ProtectedHeader;
import java.security.Key;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import javax.crypto.SecretKey;

/** JWT cryptography only. Callers own claim names, token purposes and issuance policies. */
public final class JwtTokenProvider {
  private final SecretKey key;
  private final RSAPrivateKey privateKey;
  private final String keyId;
  private final JwtParser parser;

  public JwtTokenProvider(String secret) {
    byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
    if (bytes.length < 32)
      throw new IllegalArgumentException("JWT secret must contain at least 32 bytes");
    key = Keys.hmacShaKeyFor(bytes);
    privateKey = null;
    keyId = null;
    parser = Jwts.parser().verifyWith(key).build();
  }

  /** Signs RS256 tokens with a kid; accepts only configured public keys for verification. */
  public JwtTokenProvider(
      String keyId, RSAPrivateKey privateKey, Map<String, RSAPublicKey> publicKeys) {
    var keys = Map.copyOf(publicKeys);
    var current = keys.get(keyId);
    if (keyId == null || keyId.isBlank() || privateKey == null || current == null
        || privateKey.getModulus().bitLength() < 2048
        || !privateKey.getModulus().equals(current.getModulus())
        || keys.values().stream().anyMatch(k -> k.getModulus().bitLength() < 2048)) {
      throw new IllegalArgumentException("A matching RSA key pair of at least 2048 bits is required");
    }
    this.key = null;
    this.privateKey = privateKey;
    this.keyId = keyId;
    this.parser = Jwts.parser().keyLocator(new LocatorAdapter<Key>() {
      @Override
      public Key locate(ProtectedHeader header) {
        if (!"RS256".equals(header.getAlgorithm()) || header.getKeyId() == null)
          throw new IllegalArgumentException("RS256 token with kid is required");
        var publicKey = keys.get(header.getKeyId());
        if (publicKey == null) throw new IllegalArgumentException("Unknown signing key");
        return publicKey;
      }
    }).build();
  }

  public String encode(Map<String, ?> claims) {
    if (privateKey != null)
      return Jwts.builder().header().keyId(keyId).and().claims(claims)
          .signWith(privateKey, Jwts.SIG.RS256).compact();
    return Jwts.builder().claims(claims).signWith(key).compact();
  }

  /** Verifies signature and standard exp/nbf constraints when present; returns verified claims. */
  public Claims verify(String token) {
    return parser.parseSignedClaims(token).getPayload();
  }

  /** Decodes only after verification; never exposes unsigned claims as trusted data. */
  public Claims decode(String token) {
    return verify(token);
  }
}
