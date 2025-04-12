Testing Challenge - SuperHero API - v3

The SuperHero API is a quantified and accessible data source for superhero data. We've gathered information on hundreds of characters, organized it in a developer-friendly structure, and exposed it via a REST API so that you can consume it effortlessly in your applications.

For this challenge, imagine that one of our critical products depends on the SuperHero API to get character data (see https://superheroapi.com/).

To access the API, you’ll need an access token, which can be generated using your GitHub account at https://superheroapi.com/.

Part One: Proxy Service
Objective: Create a gRPC service that acts as a proxy for the SuperHero API.

Create a single gRPC service call that mimics the behaviour of the SuperHero API’s endpoint: GET /api/{access-token}/search/{name}.
The gRPC service should:
Accept any required parameters (such as name).
Call the SuperHero API’s REST endpoint (GET /api/{access-token}/search/{name}) internally.
Convert the REST API response to a gRPC-compatible format and return it to the client.
Part Two: Caching
Objective: Implement a local caching mechanism to improve response times for character search requests.

Extend the service from Part 1 by adding a caching layer.
Cache all characters retrieved from the SuperHero API based on search terms.
The cache should support expiration or invalidation policies to handle outdated data.
The cache must support both storing and retrieving characters locally.
When a client makes a request to the gRPC service:
Check the cache first.
If the data is available and valid, return it from the cache.
If not, fetch fresh data from the SuperHero API, update the cache, and return the data.
(bonus) Implement metrics or logs to show cache hit and miss statistics.

Part Three: Advanced (are you a superdev?)
Objective: Design an asynchronous, event-driven system that processes and updates character data based on updates from the SuperHero API.

Implement a service that listens to updates (polling or webhook simulation) from the SuperHero API and asynchronously updates the local cache.
The system should:
Continuously check for updates from the SuperHero API or simulate event-driven updates (using polling if no events are available).
Process and update the cache without blocking other incoming requests.
Implement a mechanism to notify clients (e.g., via gRPC streaming or WebSocket) when new characters are added or updated.
Ensure the system is resilient (handle failures, retries, etc.) and performs efficiently under load.
