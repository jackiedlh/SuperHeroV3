# SuperHero Proxy

A high-performance Java Spring Boot application that provides a robust caching layer and real-time updates for the SuperHero API using gRPC and Server-Sent Events (SSE). The system is designed for scalability, reliability, and high performance.

## System Architecture

The application follows a microservices-oriented architecture with clear separation of concerns:

```
src/
├── main/
│   ├── java/com/example/superheroproxy/    
│   │   ├── client/              # gRPC client implementations
handling
│   │   │   ├── NotificationCmdClient.java    # Command client for notifications
│   │   │   └── SuperheroGrpcClient.java      # gRPC client implementation
│   │   ├── config/              # Configuration Layer
│   │   │   ├── AppConfig.java                # Application configuration
│   │   │   ├── CacheConfig.java              # Cache configuration
│   │   │   └── GrpcWebConfig.java            # gRPC web configuration
│   │   ├── controller/          # REST API Layer
│   │   │   ├── CacheController.java          # REST endpoints for cache management
│   │   │   ├── GlobalExceptionHandler.java   # Global error handling
│   │   │   ├── HeroController.java           # REST endpoints for hero operations
│   │   │   └── NotificationController.java    # REST endpoints for real-time updates
│   │   ├── dto/                 # Data Transfer Objects
│   │   │   └── HeroDto.java                  # Hero data transfer objects
│   │   ├── service/            # Business Logic Layer
│   │   │   ├── CacheStatService.java         # Cache statistics and monitoring
│   │   │   ├── HeroCheckScheduleService.java # hero staus check scheduling
│   │   │   ├── ExternalApiService.java       # External API integration
│   │   │   ├── NotificationService.java      # Real-time notification handling
│   │   │   ├── SuperheroInnerService.java    # Internal hero operations with cache
│   │   │   └── SuperheroProxyService.java    # Hero search service
│   │   ├── utils/              # Utility Layer
│   │   │   ├── Converter.java               # Data conversion utilities
│   │   │   ├── ResponseGenerator.java       # Response formatting
│   │   │   └── SuperheroIdStore.java        # Hero ID management
│   │   └── SuperheroProxyApplication.java   # Application entry point
│   ├── proto/                  # Protocol Buffers
│   │   └── *.proto                          # gRPC service definitions
│   └── resources/
│       ├── static/             # Frontend Application
│       │   ├── js/            # JavaScript Modules
│       │   │   ├── subscribe.js   # SSE subscription handling
│       │   │   ├── cache.js       # Cache management
│       │   │   ├── search.js      # Search functionality
│       │   │   └── config.js      # Frontend configuration
│       │   └── index.html     # Main application page
│       └── application.yml    # Application configuration
└── test/                      # Test files
```

## Key Features

### 1. High-Performance Caching System
- **Caffeine Cache**: Advanced in-memory caching with high throughput
- **Configurable Settings**: Dynamic cache size and expiration policies
- **Scheduled Updates**: Automated cache refresh mechanism
- **Cache Statistics**: Comprehensive monitoring and metrics
- **Multi-level Caching**: Optimized for different access patterns

### 2. Real-time Updates
- **Server-Sent Events (SSE)**: Efficient one-way communication
- **gRPC Integration**: High-performance service-to-service communication
- **Connection Management**: Automatic reconnection and error handling
- **Backpressure Handling**: Rate limiting and flow control
- **Subscription Management**: Flexible subscription options

### 3. Advanced Error Handling
- **Global Exception Handler**: Consistent error responses
- **SSE Error Handling**: Proper error events for real-time clients
- **HTTP Error Responses**: Standardized error formats
- **Circuit Breakers**: Protection against cascading failures
- **Retry Mechanisms**: Automatic retry for transient failures

### 4. Security Features
- **Input Validation**: Comprehensive data validation
- **CORS Configuration**: Secure cross-origin requests

### 5. Monitoring and Observability
- **Metrics Collection**: Cache statistics and performance metrics
- **Logging**: Comprehensive application logging
- **Alerting**: Configurable alerting system
- **Dashboard**: Real-time monitoring interface

## Configuration

### Application Properties
```yaml
# Cache Configuration
spring:
  cache:
    type: caffeine
    caffeine:
      spec: maximumSize=1000,expireAfterWrite=65m

# gRPC Configuration
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
- Protobuf 3.25.2
- Caffeine Cache
- Jsoup 1.17.2
- Lombok
- Spring Boot Starter Web
- Spring Boot Starter Cache
- Spring Boot Starter Test
- Guava 33.0.0-jre

## Development

### Prerequisites
- Java 17 or higher
- Maven 3.8 or higher
- SuperHero API token

### Building
```bash
mvn clean install
```

### Running
```bash
mvn spring-boot:run
```

### Testing
```bash
# Run all tests
mvn test

```

## Monitoring and Maintenance

### Cache Statistics
- Cache hit/miss rates
- Eviction statistics
- Size monitoring
- Performance metrics
- Resource utilization

### Error Monitoring
- Detailed error logging
- Error rate tracking
- Exception patterns analysis
- Alerting system
- Performance impact analysis

### Performance Metrics
- Response times
- Connection statistics
- Resource utilization
- Throughput monitoring
- Latency analysis


## Performance Optimization

### Caching Strategies
- Multi-level caching
- Cache invalidation
- Cache warming
- Cache statistics
- Cache optimization


