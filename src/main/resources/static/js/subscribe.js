// Global variables for managing EventSource connections and subscriptions
let allEventSource = null; // EventSource for subscribing to all heroes
let heroEventSources = new Map(); // Map to store event sources for individual heroes
let reconnectAttempts = 0; // Counter for reconnection attempts
const maxReconnectAttempts = 5; // Maximum number of reconnection attempts
// baseUrl is defined in config.js
let subscribedHeroes = new Set(); // Set to track subscribed hero IDs

// Updates the connection status indicator in the UI
function updateStatus(connected) {
    const status = document.getElementById('status');
    status.className = `status ${connected ? 'connected' : 'disconnected'}`;
    status.textContent = connected ? 'Connected' : 'Disconnected';
}

// Updates the list of subscribed heroes in the UI
function updateSubscriptionList() {
    const subscriptionList = document.getElementById('subscriptionList');
    subscriptionList.innerHTML = '';
    subscribedHeroes.forEach(heroId => {
        const item = document.createElement('div');
        item.className = 'subscription-item';
        item.innerHTML = `
            <span onclick="showSubscribedHeroDetails('${heroId}')">Hero ID: ${heroId}</span>
            <button onclick="unsubscribeHero('${heroId}')">x</button>
        `;
        subscriptionList.appendChild(item);
    });
}

// Handles subscription to all heroes
function handleSubscribeAll(checked) {
    if (checked) {
        // Close all individual hero event sources
        heroEventSources.forEach((eventSource, heroId) => {
            eventSource.close();
            heroEventSources.delete(heroId);
        });

        // Close existing allEventSource if any
        if (allEventSource) {
            allEventSource.close();
        }

        // Create new EventSource for all heroes
        allEventSource = new EventSource(`${baseUrl}/api/notifications/subscribeAll`);
        updateStatus(true);
        reconnectAttempts = 0;
        subscribedHeroes.clear();
        updateSubscriptionList();
        document.getElementById('subscribedHeroDetails').innerHTML = '<h3>Subscribed to all heroes</h3>';

        // Handle incoming messages for all heroes
        allEventSource.onmessage = function(event) {
            try {
                if (event.data.endsWith('ping')) {
                    const pingElement = document.createElement('div');
                    pingElement.className = 'update ping';
                    pingElement.textContent = 'Ping received:' + event.data;
                    document.getElementById('updates').appendChild(pingElement);
                    return;
                }
                console.log("get by handleSubscribeAll")
                console.log(event);
                const update = JSON.parse(event.data);
                const hero = update.hero;
                const type = update.updateType;
                
                const updateElement = document.createElement('div');
                updateElement.className = 'update';
                updateElement.innerHTML = `
                    <strong>${type} ${hero.id}:${hero.name}</strong>
                `;
                document.getElementById('updates').appendChild(updateElement);
            } catch (error) {
                addUpdate('Error parsing update: ' + error.message, true);
            }
        };

        // Handle connection errors and attempt reconnection
        allEventSource.onerror = function(error) {
            console.error('All EventSource failed:', error);
            updateStatus(false);
            
            if (reconnectAttempts < maxReconnectAttempts) {
                reconnectAttempts++;
                addUpdate(`Connection lost. Attempting to reconnect (${reconnectAttempts}/${maxReconnectAttempts})...`, true);
                setTimeout(handleSubscribeAll(true), 5000);
            } else {
                addUpdate('Connection lost. Maximum reconnection attempts reached. Please try again later.', true);
            }
        };
    } else {
        // Unsubscribe from all heroes
        if (allEventSource) {
            allEventSource.close();
            allEventSource = null;
        }
        updateStatus(false);
        subscribedHeroes.clear();
        updateSubscriptionList();
        
        document.getElementById('subscriptionList').style.display = 'block';
        document.getElementById('subscribedHeroDetails').style.display = 'block';
        document.getElementById('subscribedHeroDetails').innerHTML = '<h3>No hero subscribed yet</h3>';
    }
}

// Subscribes to a specific hero's updates
function subscribe() {
    const heroId = document.getElementById('heroId').value;
    
    // Uncheck the subscribeAll checkbox
    document.getElementById('subscribeAll').checked = false;
    
    // Clear previous updates
    document.getElementById('updates').innerHTML = '';
    
    // Close allEventSource if it exists
    if (allEventSource) {
        allEventSource.close();
        allEventSource = null;
    }

    // Close existing event source for this hero if it exists
    if (heroEventSources.has(heroId)) {
        heroEventSources.get(heroId).close();
        heroEventSources.delete(heroId);
    }

    // Create new EventSource for this hero
    eventSource = new EventSource(`${baseUrl}/api/notifications/subscribe/${heroId}`);
    heroEventSources.set(heroId, eventSource);
    updateStatus(true);
    reconnectAttempts = 0;
    subscribedHeroes.add(heroId);
    updateSubscriptionList();
    showSubscribedHeroDetails(heroId);

    // Handle incoming messages for the specific hero
    eventSource.onmessage = function(event) {
        try {
            if (event.data.endsWith('ping')) {
                const pingElement = document.createElement('div');
                pingElement.className = 'update ping';
                pingElement.textContent = 'Ping Received:' + event.data;
                document.getElementById('updates').appendChild(pingElement);
                return;
            }
            console.log("get by subscribe specific")
            console.log(event);
            const update = JSON.parse(event.data);
            const hero = update.hero;
            const type = update.updateType;
            
            const updateElement = document.createElement('div');
            updateElement.className = 'update';
            updateElement.innerHTML = `
                <strong>${type} ${hero.id}:${hero.name}</strong>
            `;
            document.getElementById('updates').appendChild(updateElement);
        } catch (error) {
            addUpdate('Error parsing update: ' + error.message, true);
        }
    };

    // Handle connection errors and attempt reconnection
    eventSource.onerror = function(error) {
        console.error(heroId + ' EventSource failed:', error);
        updateStatus(false);
        eventSource.close();
        eventSource = null;

        if (reconnectAttempts < maxReconnectAttempts) {
            reconnectAttempts++;
            addUpdate(`Connection lost. Attempting to reconnect (${reconnectAttempts}/${maxReconnectAttempts})...`, true);
            setTimeout(subscribe, 5000); // Try to reconnect after 5 seconds
        } else {
            addUpdate('Connection lost. Maximum reconnection attempts reached.', true);
        }
    };
}

// Unsubscribes from a specific hero
function unsubscribeHero(heroId) {
    if (heroEventSources.has(heroId)) {
        heroEventSources.get(heroId).close();
        heroEventSources.delete(heroId);
        updateStatus(false);
        
        // Call unsubscribe endpoint
        fetch(`${baseUrl}/api/notifications/unsubscribe/${heroId}`, {
            method: 'POST'
        }).then(() => {
            subscribedHeroes.delete(heroId);
            updateSubscriptionList();
            addUpdate(`Unsubscribed from hero ${heroId}`);
        }).catch(error => {
            console.error('Unsubscribe failed:', error);
            addUpdate('Error unsubscribing: ' + error.message, true);
        });
    }
}

// Unsubscribes from the hero specified in the input field
function unsubscribe() {
    const heroId = document.getElementById('heroId').value;
    unsubscribeHero(heroId);
}

// Adds an update message to the updates section
function addUpdate(message, isError = false) {
    const update = document.createElement('div');
    update.className = 'update' + (isError ? ' error' : '');
    update.textContent = message;
    document.getElementById('updates').appendChild(update);
}

