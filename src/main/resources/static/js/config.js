// Configuration file for the application
const config = {
    baseUrl: 'http://localhost:8080'  // Default value, can be changed as needed
};

// Export the configuration
window.appConfig = config;

// Shared configuration variables
const baseUrl = window.appConfig.baseUrl; // Base URL for API endpoints 