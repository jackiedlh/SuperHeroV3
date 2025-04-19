# Resilience4j Demo Project

This project demonstrates various features of Resilience4j in a Spring Boot application. It showcases:
- Circuit Breaker
- Rate Limiter
- Retry
- Bulkhead
- Time Limiter

## Prerequisites

- Java 17 or higher
- Maven 3.6 or higher

## Building and Running

1. Build the project:
```bash
mvn clean install
```

2. Run the application:
```bash
mvn spring-boot:run
```

The application will start on port 8080.

## Demo Endpoints

### Circuit Breaker
- URL: `http://localhost:8080/api/demo/circuit-breaker`
- Parameters:
  - `shouldFail` (boolean, default: false): Simulates failures to trigger the circuit breaker
- Example: `http://localhost:8080/api/demo/circuit-breaker?shouldFail=true`

### Rate Limiter
- URL: `http://localhost:8080/api/demo/rate-limiter`
- The rate limiter is configured to allow 10 requests per second

### Retry
- URL: `http://localhost:8080/api/demo/retry`
- Parameters:
  - `shouldFail` (boolean, default: false): Simulates failures to trigger retries
- Example: `http://localhost:8080/api/demo/retry?shouldFail=true`

### Bulkhead
- URL: `http://localhost:8080/api/demo/bulkhead`
- The bulkhead is configured to allow 10 concurrent calls

### Time Limiter
- URL: `http://localhost:8080/api/demo/time-limiter`
- The time limiter is configured with a 2-second timeout

## Monitoring

The application exposes several actuator endpoints for monitoring:

- Circuit Breaker Status: `http://localhost:8080/actuator/circuitbreakers`
- Rate Limiter Status: `http://localhost:8080/actuator/ratelimiters`
- Retry Status: `http://localhost:8080/actuator/retries`
- Bulkhead Status: `http://localhost:8080/actuator/bulkheads`
- Health Status: `http://localhost:8080/actuator/health`

## Configuration

All Resilience4j configurations can be found in `src/main/resources/application.yml`. The configurations include:

- Circuit Breaker: Sliding window, failure threshold, wait duration
- Rate Limiter: Requests per second, refresh period
- Retry: Maximum attempts, wait duration
- Bulkhead: Maximum concurrent calls
- Time Limiter: Timeout duration

## Testing the Features

1. **Circuit Breaker**:
   - Make multiple requests with `shouldFail=true` to trigger the circuit breaker
   - The circuit breaker will open after the failure threshold is reached
   - After the wait duration, it will transition to half-open state

2. **Rate Limiter**:
   - Make rapid requests to the rate limiter endpoint
   - After exceeding the limit, requests will be rejected

3. **Retry**:
   - Make requests with `shouldFail=true`
   - The system will retry the operation up to the configured number of times

4. **Bulkhead**:
   - Make multiple concurrent requests to the bulkhead endpoint
   - After exceeding the concurrent call limit, new requests will be rejected

5. **Time Limiter**:
   - The time limiter will timeout operations that take longer than the configured duration 