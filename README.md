# SuperHero Proxy

A Java Spring Boot application that provides a caching layer and real-time updates for the SuperHero API using gRPC.

## Main Features

- **Efficient Caching**: Caffeine-based cache with configurable size and expiration
- **Real-time Updates**: gRPC streaming service for instant hero updates
- **Case-insensitive Search**: Smart search functionality with automatic caching
- **Scheduled Updates**: Automatic cache refresh with configurable intervals
- **Thread-safe Operations**: Concurrent-safe implementation for high performance

## Prerequisites

- Java 17 or higher
- Maven 3.8 or higher
- SuperHero API token

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

## Testing the gRPC Service

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

### 3. Testing with gRPC Client

The application provides a gRPC client for testing. Here's a sample code to test the notification service:

```java
@SpringBootTest
public class NotificationClientTest {

    @Autowired
    private NotificationClient notificationClient;

    @Test
    void testSubscribeToUpdates() throws InterruptedException {
        List<String> heroNames = List.of("spider-man");
        CountDownLatch latch = new CountDownLatch(1);
        
        StreamObserver<HeroUpdate> responseObserver = new StreamObserver<>() {
            @Override
            public void onNext(HeroUpdate update) {
                System.out.println("Received update for: " + update.getHero().getName());
                latch.countDown();
            }

            @Override
            public void onError(Throwable t) {
                System.err.println("Error: " + t.getMessage());
                latch.countDown();
            }

            @Override
            public void onCompleted() {
                System.out.println("Stream completed");
            }
        };

        notificationClient.subscribeToUpdates(heroNames, responseObserver);
        assertTrue(latch.await(10, TimeUnit.SECONDS));
    }
}
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

# gRPC Configuration
grpc:
  server:
    port: 9090

# SuperHero API Configuration
superhero:
  api:
    url: https://superheroapi.com/api
    token: ${SUPERHERO_API_TOKEN}
  cache:
    update:
      interval: 3600  # 1 hour in seconds
```

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
