# SuperHero Proxy

A Java Spring Boot application that provides a caching layer and real-time updates for the SuperHero API using gRPC.

## Main Features

- **Efficient Caching**: Caffeine-based cache with configurable size and expiration
- **Real-time Updates**: Server-Sent Events (SSE) with gRPC client integration for instant hero updates
- **Case-insensitive Search**: Smart search functionality with automatic caching
- **Scheduled Updates**: Automatic cache refresh with configurable intervals
- **Thread-safe Operations**: Concurrent-safe implementation for high performance
- **HTML Parsing**: Jsoup integration for parsing HTML content
- **Comprehensive Error Handling**: Global exception handling with detailed error responses

## Prerequisites

- Java 17 or higher
- Maven 3.8 or higher
- SuperHero API token

## Project Structure

```
src/main/java/com/example/superheroproxy/
├── controller/     # REST controllers and gRPC clients
│   ├── HeroController.java        # REST endpoints for hero operations
│   ├── CacheController.java       # REST endpoints for cache management
│   ├── NotificationController.java # SSE endpoints for real-time updates
│   └── SuperheroGrpcClient.java   # gRPC client implementation
├── service/        # Business logic and service implementations
├── client/         # External API clients
├── utils/          # Utility classes and helpers
├── config/         # Configuration classes
├── dto/            # Data Transfer Objects
└── SuperheroProxyApplication.java
```

## Quick Start

1. Clone the repository:
```bash
git clone https://github.com/yourusername/superhero-proxy.git
cd superhero-proxy
```

2. Set up environment variables:
```bash
export SUPERHERO_API_TOKEN=your_api_token_here
```

3. Build the project:
```bash
mvn clean install
```

4. Run the application:
```bash
mvn spring-boot:run
```

## Real-time Updates

The application provides real-time updates through Server-Sent Events (SSE) with gRPC client integration:

### Subscribe to Updates
```bash
# Subscribe to updates for a specific hero
curl -N http://localhost:8080/api/notifications/subscribe/{heroId}

# Subscribe to updates for all heroes
curl -N http://localhost:8080/api/notifications/subscribeAll
```

### Unsubscribe from Updates
```bash
curl -X POST http://localhost:8080/api/notifications/unsubscribe/{heroId}
```

## Testing

### 1. Start the Application
```bash
mvn spring-boot:run
```

### 2. Run the Tests
```bash
# Run all tests
mvn test

# Run specific test class
mvn test -Dtest=NotificationClientTest
```

## Configuration

The application uses the following default configurations:

```yaml
# Cache Configuration
spring:
  cache:
    type: caffeine
    caffeine:
      spec: maximumSize=1000,expireAfterWrite=65m

# gRPC Client Configuration
grpc:
  client:
    channel:
      host: localhost
      port: 9090
      keep-alive:
        time: 60
        timeout: 20
        without-calls: true

# SuperHero API Configuration
superhero:
  api:
    url: https://superheroapi.com/api
    token: ${SUPERHERO_API_TOKEN}
  cache:
    update:
      interval: 3600  # 1 hour in seconds
```

## Dependencies

- Spring Boot 3.2.3
- gRPC 1.61.0
- Protobuf 3.19.6
- Caffeine Cache
- Jsoup 1.17.2
- Lombok
- Spring Boot Starter Web
- Spring Boot Starter Cache
- Spring Boot Starter Test

## Error Handling

The application provides comprehensive error handling through the GlobalExceptionHandler:

- **Internal Server Errors**: Handles unexpected exceptions with detailed logging
- **Bad Requests**: Handles invalid parameters and input validation
- **gRPC Communication Errors**: Handles gRPC service communication issues
- **External API Errors**: Handles issues with the SuperHero API
- **JSON Processing Errors**: Handles JSON serialization/deserialization issues
- **Cache Retrieval Errors**: Handles cache-related exceptions

## Profiles

The application supports different profiles:

- `dev`: Development environment
- `test`: Testing environment
- `prod`: Production environment

To run with a specific profile:
```bash
mvn spring-boot:run -Dspring.profiles.active=dev
```

## Troubleshooting

1. **Cache Issues**
   - Check cache statistics in logs
   - Verify cache configuration
   - Monitor memory usage

2. **gRPC Connection Issues**
   - Verify gRPC server is running
   - Check port configuration
   - Review error logs

3. **API Connection Issues**
   - Verify API token
   - Check network connectivity
   - Review API response logs

4. **HTML Parsing Issues**
   - Check HTML structure compatibility
   - Verify Jsoup selectors
   - Review parsing logs

5. **SSE Connection Issues**
   - Check client connection status
   - Verify event stream configuration
   - Monitor connection timeouts
