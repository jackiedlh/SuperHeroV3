# Superhero Proxy System Architecture

## System Components

```mermaid
graph TB
    %% Client Layer at Top
    Web[Web Client] & SuperheroGrpcClient[Superhero gRPC Client] & NotificationCmdClient[Notification Cmd Client]
    
    %% Controller Layer
    HeroController[Hero Controller] & NotificationController[Notification Controller] & CacheController[Cache Controller]
    
    %% Service Layer
    SuperheroProxyService[Superhero Proxy Service]
    NotificationService[Notification Service] & HeroCheckScheduleService[Hero Check Schedule Service]
    SuperheroInnerService[Superhero Inner Service]
    
    %% External Systems at Bottom
    ExternalApiService[External API Service]
    Cache[(Cache)]
    ExternalAPI[External API]

    %% Connections - Client to Controller
    Web -- HTTP Request --> HeroController
    Web -- HTTP Request --> NotificationController
    Web -- HTTP Request --> CacheController
    NotificationController -- SSE --> Web

    %% Connections - gRPC Client to Service
    SuperheroGrpcClient -- gRPC Request-Response --> SuperheroProxyService
    NotificationCmdClient -- gRPC Request-Response --> NotificationService
    NotificationService -- gRPC Response Stream --> NotificationCmdClient

    %% Connections - Controller to Service
    HeroController -- gRPC Request --> SuperheroProxyService
    NotificationController -- gRPC Request --> NotificationService
    NotificationService -- gRPC Response Stream --> NotificationController
    
    %% Service Layer Connections
    SuperheroProxyService -- gRPC --> SuperheroInnerService
    SuperheroInnerService -- gRPC --> ExternalApiService
    
    %% Cache Connections
    CacheController -- Cache --> Cache
    SuperheroInnerService -- Cache --> Cache
    HeroCheckScheduleService -- Cache --> Cache
    
    %% External Systems Connections
    ExternalApiService -- HTTP --> ExternalAPI
    HeroCheckScheduleService -- Local Call --> NotificationService
```

## Key Flows

### 1. Search Hero Flow
```mermaid
sequenceDiagram
    participant SC as Superhero Client
    participant SPS as Superhero Proxy Service
    participant SIS as Superhero Inner Service
    participant EAS as External API Service
    participant ExtAPI as External API
    participant Cache as Cache
    
    SC->>SPS: gRPC Search Request
    SPS->>SIS: gRPC Call
    SIS->>Cache: Check Cache
    alt Cache Hit
        Cache-->>SIS: Return Cached Data
    else Cache Miss
        SIS->>EAS: gRPC Call
        EAS->>ExtAPI: HTTP Request
        ExtAPI-->>EAS: Response
        EAS-->>SIS: Processed Data
        SIS->>Cache: Store Result
    end
    SIS-->>SPS: gRPC Response
    SPS-->>SC: gRPC Response
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
    
    %% Response stream
    loop Stream Updates
        NS->>NC: gRPC Stream Update
        NC->>Web: WebSocket/SSE Update
    end
```

### 3. Hero Check Schedule Flow
```mermaid
sequenceDiagram
    participant HCS as Hero Check Schedule Service
    participant Cache as Cache
    participant NS as Notification Service
    participant NC as Notification Cmd Client
    participant Web as Web Client
    
    HCS->>Cache: Check Hero Data
    alt Hero Changed/New
        HCS->>NS: Local Call
        NS->>NC: gRPC Stream Update
        NC->>Web: WebSocket/SSE Update
    end
```

## Component Descriptions

1. **Web Layer**
   - `HeroController`: Handles HTTP requests for hero-related operations and receives gRPC stream updates
   - `NotificationController`: Manages notification subscriptions and receives gRPC stream updates
   - `CacheController`: Provides cache management

2. **Service Layer**
   - `SuperheroProxyService`: Main service orchestrating hero operations with bidirectional gRPC streaming
   - `SuperheroInnerService`: Internal service for hero data processing with bidirectional gRPC streaming
   - `NotificationService`: Manages notification subscriptions and delivery with bidirectional gRPC streaming
   - `ExternalApiService`: Handles communication with external superhero API
   - `HeroCheckScheduleService`: Scheduled job for checking hero changes, updates cache and notifies locally

3. **Client Layer**
   - `SuperheroGrpcClient`: gRPC client for internal superhero services with bidirectional streaming
   - `NotificationCmdClient`: Client for notification command operations with bidirectional streaming

4. **External Systems**
   - External Superhero API: Source of superhero data
   - Cache: In-memory cache for performance optimization 