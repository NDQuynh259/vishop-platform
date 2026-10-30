package com.vishop.iam.infra.security;

import io.github.coreplatform.common.security.jwt.JwtTokenProvider;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;

/** IAM alone owns the private signing key. Previous public keys support overlapping rotation. */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(IamJwtKeyConfiguration.Properties.class)
public class IamJwtKeyConfiguration {
  @ConfigurationProperties("vishop.security.jwt")
  public record Properties(String keyId, Resource privateKeyLocation,
      Resource publicKeyLocation, Map<String, Resource> previousPublicKeys) {}

  public record KeySet(String keyId, RSAPrivateKey privateKey, Map<String, RSAPublicKey> publicKeys) {}

  @Bean
  KeySet iamJwtKeys(Properties properties) throws Exception {
    var factory = KeyFactory.getInstance("RSA");
    var privateKey = (RSAPrivateKey) factory.generatePrivate(
        new PKCS8EncodedKeySpec(pem(properties.privateKeyLocation(), "PRIVATE KEY")));
    var publicKeys = new LinkedHashMap<String, RSAPublicKey>();
    publicKeys.put(properties.keyId(), publicKey(factory, properties.publicKeyLocation()));
    if (properties.previousPublicKeys() != null) {
      for (var entry : properties.previousPublicKeys().entrySet()) {
        if (entry.getKey().isBlank() || publicKeys.containsKey(entry.getKey()))
          throw new IllegalArgumentException("Public key IDs must be nonempty and distinct");
        publicKeys.put(entry.getKey(), publicKey(factory, entry.getValue()));
      }
    }
    return new KeySet(properties.keyId(), privateKey, Map.copyOf(publicKeys));
  }

  @Bean
  JwtTokenProvider jwtTokenProvider(KeySet keys) {
    return new JwtTokenProvider(keys.keyId(), keys.privateKey(), keys.publicKeys());
  }

  private RSAPublicKey publicKey(KeyFactory factory, Resource resource) throws Exception {
    return (RSAPublicKey) factory.generatePublic(new X509EncodedKeySpec(pem(resource, "PUBLIC KEY")));
  }

  private byte[] pem(Resource resource, String type) throws IOException {
    if (resource == null) throw new IllegalArgumentException("JWT key resource is required");
    String value = resource.getContentAsString(StandardCharsets.US_ASCII);
    String begin = "-----BEGIN " + type + "-----";
    String end = "-----END " + type + "-----";
    if (!value.contains(begin) || !value.contains(end))
      throw new IllegalArgumentException("Expected PEM " + type);
    return Base64.getDecoder().decode(value.replace(begin, "").replace(end, "").replaceAll("\\s", ""));
  }
}
