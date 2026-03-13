# API Gateway

Production-ready API Gateway for the E-Commerce platform built with Spring Boot 3.2, Spring Cloud Gateway, and Java 21.

## Features
- JWT validation and header propagation (`X-User-Id`, `X-User-Role`)
- Redis-backed rate limiting (100 requests/minute per IP)
- Centralized CORS configuration
- Request logging and correlation IDs
- Circuit breaker, retry, and timeout policies
- Actuator health and Prometheus metrics
- OpenAPI/Swagger UI
- Docker and docker-compose support

## Project Structure
```
api-gateway
+-- src/main/java/com/ecommerce/gateway
¦   +-- config
¦   +-- filter
¦   +-- ratelimit
¦   +-- exception
¦   +-- route
¦   +-- ApiGatewayApplication.java
+-- src/main/resources
¦   +-- application.yml
¦   +-- logback-spring.xml
+-- Dockerfile
+-- docker-compose.yml
+-- build.gradle
+-- README.md
```

## Run Locally
### Build
```
./gradlew bootJar
```

### Run
```
./gradlew bootRun
```

## Run With Docker
```
docker compose up --build
```

## Configuration
Key properties (see `src/main/resources/application.yml`):
- `JWT_SECRET` or `JWT_JWK_SET_URI` for token validation
- `REDIS_HOST` / `REDIS_PORT` for rate limiting
- `security.public-paths` for unauthenticated endpoints

## Routes
- `/auth/**` ? `auth-service`
- `/users/**` ? `user-service`
- `/products/**` ? `product-service`
- `/orders/**` ? `order-service`
- `/media/**` ? `media-service`
- `/payments/**` ? `payment-service`
- `/notifications/**` ? `notification-service`

## Observability
- Health: `/actuator/health`
- Metrics: `/actuator/metrics`
- Prometheus: `/actuator/prometheus`

## JWT Claims
The gateway forwards:
- `X-User-Id` from `userId` claim (fallback: `sub`)
- `X-User-Role` from `roles` or `scope`

## Service Discovery
Default configuration uses Spring Cloud Simple Discovery for local dev. Replace with Eureka by adding the Eureka client dependency and configuration if needed.
