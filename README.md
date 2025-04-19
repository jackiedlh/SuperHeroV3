# SuperHero Proxy

A Java Spring Boot application that provides a caching layer and mock real-time updates for the SuperHero API using gRPC and Server-Sent Events (SSE).

## Code Structure Overview

The application follows a layered architecture with clear separation of concerns:

```
src/
├── main/
│   ├── java/com/example/superheroproxy/    
│   │   ├── client/              # test client for gPRC services
│   │   │   ├── NotificationClient.java       # Client for notification handling
│   │   │   ├── NotificationCmdClient.java    # Command client for notifications
│   │   │   └── SuperheroGrpcClient.java      # gRPC client implementation
│   │   ├── config/              # Configuration Layer
│   │   │   ├── AppConfig.java                # Application configuration
│   │   │   ├── CacheConfig.java              # Cache configuration
│   │   │   └── GrpcWebConfig.java            # gRPC web configuration
│   │   ├── controller/          # Restful API Layer for demo page
│   │   │   ├── CacheController.java          # REST endpoints for cache management
│   │   │   ├── GlobalExceptionHandler.java   # Global error handling
│   │   │   ├── HeroController.java           # REST endpoints for hero operations
│   │   │   └── NotificationController.java    # REST endpont support SSE for real-time updates
│   │   ├── dto/                 # Data Transfer Objects for demo page
│   │   │   └── HeroDto.java                  # Hero data transfer objects
│   │   ├── service/            # Business Logic Layer
│   │   │   ├── CacheStatService.java         # Cache statistics and monitoring
│   │   │   ├── CacheUpdateScheduleService.java # Cache update scheduling job service
│   │   │   ├── ExternalApiService.java       # External API integration serive
│   │   │   ├── NotificationService.java      # Real-time notification handling gPRC service
│   │   │   ├── SuperheroInnerService.java    # Internal hero operations service with cache
│   │   │   └── SuperheroProxyService.java    # Hero search gPRC service
│   │   ├── utils/              # Utility Layer
│   │   │   ├── Converter.java               # Data conversion utilities
│   │   │   ├── ResponseGenerator.java       # Response formatting utilities
│   │   │   └── SuperheroIdStore.java        # Hero IDs for demo/test
│   │   └── SuperheroProxyApplication.java   # Application entry point
│   ├── proto/                  # Protocol Buffers
│   │   └── *.proto                          # gRPC service definitions
│   └── resources/
│       ├── static/             # Demo/Test Frontend
│       │   ├── js/            # JavaScript Modules
│       │   │   ├── subscribe.js   # SSE subscription handling
│       │   │   ├── cache.js       # Cache management
│       │   │   ├── search.js      # Search functionality
│       │   │   └── config.js      # Frontend configuration
│       │   └── index.html     # Main test application page
│       └── application.yml    # Application configuration
└── test/                      # Test files
```

## Key Components

### 1. Backend Components

#### Client Layer
- **NotificationClient**: Handles notification subscriptions and events
- **NotificationCmdClient**: Processes notification commands
- **SuperheroGrpcClient**: Manages gRPC communication

### 2. Frontend Components

#### HTML Structure (index.html)
- Responsive layout with Bootstrap
- Real-time update panels
- Search interface
- Cache management controls

#### JavaScript Modules
- **subscribe.js**: Manages SSE subscriptions and real-time updates
- **cache.js**: Handles cache operations and statistics
- **search.js**: Implements search functionality
- **config.js**: Frontend configuration settings

## Features

### 1. Backend Features

#### Caching System
- **Caffeine Cache**: High-performance in-memory caching
- **Configurable Settings**: Adjustable cache size and expiration
- **Scheduled Updates**: Automatic cache refresh mechanism
- **Cache Statistics**: Monitoring and metrics collection

#### Real-time Updates
- **Server-Sent Events**: Efficient one-way communication
- **gRPC Integration**: Reliable service-to-service communication
- **Connection Management**: Automatic reconnection and error handling

#### Error Handling
- **Global Exception Handler**: Consistent error responses
- **SSE Error Handling**: Proper error events for real-time clients
- **HTTP Error Responses**: Standardized error formats

### 2. Frontend Features
- **Real-time Updates UI**
  - Live hero data updates
  - SSE connection status
  - Automatic reconnection handling

- **Cache Management Interface**
  - Cache statistics visualization
  - Manual cache operations
  - Update scheduling controls

- **Search Interface**
  - Instant search results
  - Advanced filtering options
  - Result highlighting

## Configuration

### Application Properties
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

The application provides comprehensive error handling:

1. **Global Exception Handler**
   - Handles all uncaught exceptions
   - Provides consistent error responses
   - Supports both REST and SSE error formats

2. **Error Response Formats**
   ```json
   // REST Error Response
   {
     "status": 500,
     "error": "Internal Server Error",
     "message": "Error message"
   }

   // SSE Error Event
   {
     "error": "Error Type",
     "message": "Error Message"
   }
   ```

3. **Exception Types**
   - Internal Server Errors
   - Bad Requests
   - gRPC Communication Errors
   - External API Errors
   - JSON Processing Errors
   - Cache Retrieval Errors

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

# Run specific test class
mvn test -Dtest=NotificationClientTest
```

## Monitoring and Maintenance

### Cache Statistics
- Cache hit/miss rates
- Eviction statistics
- Size monitoring

### Error Monitoring
- Detailed error logging
- Error rate tracking
- Exception patterns analysis

### Performance Metrics
- Response times
- Connection statistics
- Resource utilization
