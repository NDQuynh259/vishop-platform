package com.vishop.gateway.docs;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.server.RequestPredicates;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerResponse;

@Configuration
public class ScalarDocsConfig {

  @Bean
  public RouterFunction<ServerResponse> scalarDocsRoute() {
    return RouterFunctions.route(
        RequestPredicates.GET("/docs").or(RequestPredicates.GET("/scalar")),
        request -> {
          String service = request.queryParam("service").orElse("iam");
          boolean isSubscription =
              "subscription".equalsIgnoreCase(service) || "billing".equalsIgnoreCase(service);
          boolean isAudit = "audit".equalsIgnoreCase(service);
          String specUrl =
              isSubscription
                  ? "/v3/api-docs/subscription-service"
                  : isAudit ? "/v3/api-docs/audit-service" : "/v3/api-docs/iam-service";
          String title =
              isSubscription
                  ? "ViShop - Subscription Service API Reference"
                  : isAudit
                      ? "ViShop - Audit Service API Reference"
                      : "ViShop - IAM Service API Reference";
          String iamClass = isAudit || isSubscription ? "" : "active";
          String auditClass = isAudit ? "active" : "";
          String subscriptionClass = isSubscription ? "active" : "";

          String gatewayUrl = "http://localhost:8000";
          var hostHeader = request.headers().firstHeader("Host");
          if (hostHeader != null && !hostHeader.isBlank()) {
            var scheme = request.headers().firstHeader("X-Forwarded-Proto");
            if (scheme == null || scheme.isBlank()) {
              scheme = request.uri().getScheme() != null ? request.uri().getScheme() : "http";
            }
            gatewayUrl = scheme + "://" + hostHeader;
          }

          String html =
              """
              <!doctype html>
              <html lang="en">
                <head>
                  <title>%s</title>
                  <meta charset="utf-8" />
                  <meta name="viewport" content="width=device-width, initial-scale=1" />
                  <style>
                    body { margin: 0; padding: 0; font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif; background-color: #0f172a; }
                    .vishop-nav { display: flex; align-items: center; justify-content: space-between; padding: 10px 24px; background: #1e293b; border-bottom: 1px solid #334155; }
                    .vishop-title { color: #f8fafc; font-weight: 700; font-size: 15px; display: flex; align-items: center; gap: 8px; }
                    .vishop-tabs { display: flex; gap: 8px; }
                    .vishop-tab { padding: 6px 14px; border-radius: 6px; text-decoration: none; font-size: 13px; font-weight: 600; color: #94a3b8; background: #0f172a; border: 1px solid #334155; transition: all 0.2s; }
                    .vishop-tab:hover { color: #f8fafc; border-color: #64748b; }
                    .vishop-tab.active { color: #ffffff; background: #6366f1; border-color: #6366f1; }
                  </style>
                </head>
                <body>
                  <div class="vishop-nav">
                    <div class="vishop-title">🚀 ViShop Platform API Reference</div>
                    <div class="vishop-tabs">
                      <a class="vishop-tab %s" href="/docs?service=iam">IAM Service</a>
                      <a class="vishop-tab %s" href="/docs?service=audit">Audit Service</a>
                      <a class="vishop-tab %s" href="/docs?service=subscription">Subscription Service</a>
                    </div>
                  </div>
                  <div id="api-reference"></div>
                  <script src="https://cdn.jsdelivr.net/npm/@scalar/api-reference"></script>
                  <script>
                    Scalar.createApiReference('#api-reference', {
                      url: '%s', theme: 'purple', darkMode: true, layout: 'modern', showSidebar: true,
                      servers: [{url: '%s', description: 'API Gateway'}],
                      customFetch: (input, init = {}) => {
                        const headers = new Headers(init.headers || (input instanceof Request ? input.headers : undefined));
                        headers.set('X-Requested-With', 'XMLHttpRequest');
                        return window.fetch(input, {...init, headers, credentials: 'include'});
                      }
                    });
                  </script>
                </body>
              </html>
              """
                  .formatted(title, iamClass, auditClass, subscriptionClass, specUrl, gatewayUrl);

          return ServerResponse.ok().contentType(MediaType.TEXT_HTML).bodyValue(html);
        });
  }
}
