# Superhero Proxy System Architecture

## System Components

```mermaid
graph TD
    %% Web Layer
    Web[Web Client] --> |HTTP| HeroController[Hero Controller]
    Web --> |HTTP| NotificationController[Notification Controller]
    Web --> |HTTP| CacheController[Cache Controller]
    
    %% Controller Layer
    HeroController --> |gRPC| SuperheroProxyService[Superhero Proxy Service]
    NotificationController --> |gRPC| NotificationService[Notification Service]
    CacheController --> |Cache| Cache[(Cache)]
    
    %% Service Layer
    SuperheroProxyService --> |gRPC| SuperheroInnerService[Superhero Inner Service]
    SuperheroProxyService --> |gRPC| ExternalApiService[External API Service]
    SuperheroProxyService --> |Cache| Cache
    
    %% Client Layer
    SuperheroInnerService --> |gRPC| SuperheroGrpcClient[Superhero gRPC Client]
    NotificationService --> |gRPC| NotificationClient[Notification Client]
    NotificationService --> |gRPC| NotificationCmdClient[Notification Cmd Client]
    
    %% External Systems
    ExternalApiService --> |HTTP| ExternalAPI[External Superhero API]
    HeroCheckScheduleService --> |Schedule| ExternalAPI
    HeroCheckScheduleService --> |Events| NotificationService
```

## Key Flows

### 1. Search Hero Flow
```mermaid
sequenceDiagram
    participant Web as Web Client
    participant HC as Hero Controller
    participant SPS as Superhero Proxy Service
    participant Cache as Cache
    participant EAS as External API Service
    participant ExtAPI as External API
    
    Web->>HC: HTTP Request
    HC->>SPS: gRPC Call
    SPS->>Cache: Check Cache
    alt Cache Hit
        Cache-->>SPS: Return Cached Data
    else Cache Miss
        SPS->>EAS: gRPC Call
        EAS->>ExtAPI: HTTP Request
        ExtAPI-->>EAS: Response
        EAS-->>SPS: Processed Data
        SPS->>Cache: Store Result
    end
    SPS-->>HC: gRPC Response
    HC-->>Web: HTTP Response
```

### 2. Subscribe to Hero Changes Flow
```mermaid
sequenceDiagram
    participant Web as Web Client
    participant NC as Notification Controller
    participant NS as Notification Service
    participant NCC as Notification Cmd Client
    
    Web->>NC: HTTP Subscribe Request
    NC->>NS: gRPC Call
    NS->>NCC: gRPC Call
    NCC-->>NS: Subscription Confirmation
    NS-->>NC: gRPC Response
    NC-->>Web: HTTP Response
```

### 3. Hero Change Notification Flow
```mermaid
sequenceDiagram
    participant HCS as Hero Check Schedule Service
    participant NS as Notification Service
    participant NC as Notification Client
    participant Web as Web Client
    
    HCS->>NS: Hero Change Event
    NS->>NC: gRPC Notification
    NC->>Web: WebSocket/SSE Update
```

## Component Descriptions

1. **Web Layer**
   - `HeroController`: Handles HTTP requests for hero-related operations
   - `NotificationController`: Manages notification subscriptions and delivery
   - `CacheController`: Provides cache management

2. **Service Layer**
   - `SuperheroProxyService`: Main service orchestrating hero operations
   - `SuperheroInnerService`: Internal service for hero data processing
   - `NotificationService`: Manages notification subscriptions and delivery
   - `ExternalApiService`: Handles communication with external superhero API
   - `HeroCheckScheduleService`: Scheduled job for checking hero changes

3. **Client Layer**
   - `SuperheroGrpcClient`: gRPC client for internal superhero services
   - `NotificationClient`: Client for notification delivery
   - `NotificationCmdClient`: Client for notification command operations

4. **External Systems**
   - External Superhero API: Source of superhero data
   - Cache: In-memory cache for performance optimization 