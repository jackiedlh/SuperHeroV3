# SuperHero Proxy Application Features

## Overview
The SuperHero Proxy is a Java Spring Boot application that provides a caching layer and real-time updates for the SuperHero API. It implements a gRPC service for real-time notifications and uses Caffeine for efficient caching.

## Core Features

### 1. Caching System
- **Caffeine Cache Implementation**
  - Maximum cache size: 1000 entries
  - Cache expiration: 65 minutes
  - Case-insensitive key storage
  - Automatic cache eviction

### 2. Real-time Updates
- **gRPC Notification Service**
  - Real-time hero updates via gRPC streaming
  - Support for subscribing to specific heroes
  - Automatic notification on cache updates
  - Thread-safe subscriber management

### 3. Search Functionality
- **Hero Search Service**
  - Case-insensitive search
  - Automatic cache population
  - Error handling and logging
  - REST API integration with SuperHero API

### 4. Cache Update Service
- **Scheduled Updates**
  - Hourly cache refresh (configurable)
  - Monitored heroes tracking
  - Automatic cache invalidation
  - Thread-safe operations using ConcurrentSkipListSet

### 5. Performance Features
- **Efficient Caching**
  - Memory-efficient storage
  - Automatic cache cleanup
  - Configurable cache parameters
  - Cache statistics tracking

### 6. Security
- **API Token Management**
  - Secure token storage
  - Environment-based configuration
  - Token rotation support

## Technical Details

### Cache Configuration
```yaml
spring:
  cache:
    type: caffeine
    caffeine:
      spec: maximumSize=1000,expireAfterWrite=65m
```

### Update Schedule
```yaml
superhero:
  cache:
    update:
      interval: 3600  # in seconds (1 hour)
```

### API Configuration
```yaml
superhero:
  api:
    url: https://superheroapi.com/api
    token: ${SUPERHERO_API_TOKEN}
```

### gRPC Configuration
```yaml
grpc:
  server:
    port: 9090
```

## Testing
- Comprehensive test coverage
- Mock implementations for external services
- Cache behavior verification
- gRPC service testing
- Integration tests for full workflow

## Monitoring
- Cache hit/miss statistics
- Update frequency tracking
- Error logging
- Performance metrics

## Future Enhancements
1. Cache persistence
2. Distributed caching
3. Rate limiting
4. Advanced monitoring
5. API versioning support

## Dependencies
- Spring Boot 3.2.3
- gRPC Spring Boot Starter
- Caffeine Cache
- Spring Web
- Spring Cache
- JUnit 5
- Mockito

## Configuration
The application supports multiple profiles:
- `dev`: Development environment
- `test`: Testing environment
- `prod`: Production environment

## Getting Started
1. Set up environment variables
2. Configure API token
3. Start the application
4. Connect gRPC clients
5. Begin using the caching service

## Best Practices
1. Use case-insensitive hero names
2. Monitor cache statistics
3. Implement proper error handling
4. Use appropriate timeouts
5. Monitor memory usage

## Troubleshooting
1. Check cache statistics
2. Verify API connectivity
3. Monitor gRPC connections
4. Check update schedule
5. Review error logs 