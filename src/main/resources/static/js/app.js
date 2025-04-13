let eventSource = null;
let reconnectAttempts = 0;
const maxReconnectAttempts = 5;
const baseUrl = 'http://localhost:8080';  // HTTP port
let subscribedHeroes = new Set();

function updateStatus(connected) {
    const status = document.getElementById('status');
    status.className = `status ${connected ? 'connected' : 'disconnected'}`;
    status.textContent = connected ? 'Connected' : 'Disconnected';
}

function updateSubscriptionList() {
    const subscriptionList = document.getElementById('subscriptionList');
    subscriptionList.innerHTML = '';
    subscribedHeroes.forEach(heroId => {
        const item = document.createElement('div');
        item.className = 'subscription-item';
        item.innerHTML = `
            <span onclick="showHeroDetails('${heroId}')">Hero ID: ${heroId}</span>
            <button onclick="unsubscribeHero('${heroId}')">x</button>
        `;
        subscriptionList.appendChild(item);
    });
}

function showHeroDetails(heroId) {
    const heroDetails = document.getElementById('heroDetails');
    heroDetails.innerHTML = '<h3>Loading hero details...</h3>';
    
    fetch(`${baseUrl}/api/${heroId}`)
        .then(response => {
            if (!response.ok) {
                throw new Error('Hero not found in cache');
            }
            return response.json();
        })
        .then(hero => {
            heroDetails.innerHTML = `
                <table>
                    <tr>
                        <th>ID</th>
                        <td>${hero.id}</td>
                    </tr>
                    <tr>
                        <th>Name</th>
                        <td>
                            <span id="heroName">${hero.name}</span>
                            <button onclick="editHeroName('${hero.id}')">Edit</button>
                        </td>
                    </tr>
                </table>
            `;
        })
        .catch(error => {
            heroDetails.innerHTML = `
                <h3>Error loading hero details</h3>
                <p>${error.message}</p>
            `;
        });
}

function editHeroName(heroId) {
    const heroNameElement = document.getElementById('heroName');
    const currentName = heroNameElement.textContent;
    
    heroNameElement.innerHTML = `
        <input type="text" id="nameInput" value="${currentName}">
        <button onclick="saveHeroName('${heroId}')">Save</button>
        <button onclick="cancelEdit()">Cancel</button>
    `;
}

function saveHeroName(heroId) {
    const newName = document.getElementById('nameInput').value;
    
    fetch(`${baseUrl}/api/${heroId}/name`, {
        method: 'POST',
        headers: {
            'Content-Type': 'application/json',
        },
        body: JSON.stringify({ name: newName })
    })
    .then(response => {
        if (!response.ok) {
            throw new Error('Failed to update name');
        }
        return response.json();
    })
    .then(hero => {
        document.getElementById('heroName').innerHTML = hero.name;
        addUpdate(`Updated hero name to: ${hero.name}`);
    })
    .catch(error => {
        addUpdate('Error updating hero name: ' + error.message, true);
        showHeroDetails(heroId); // Reload the original data
    });
}

function cancelEdit() {
    showHeroDetails(document.getElementById('heroId').value);
}

function subscribe() {
    const heroId = document.getElementById('heroId').value;
    
    // Clear previous updates
    document.getElementById('updates').innerHTML = '';
    
    // Close existing connection if any
    if (eventSource) {
        eventSource.close();
    }

    // Create new EventSource
    eventSource = new EventSource(`${baseUrl}/api/notifications/subscribe/${heroId}`);
    updateStatus(true);
    reconnectAttempts = 0;
    subscribedHeroes.add(heroId);
    updateSubscriptionList();
    showHeroDetails(heroId);

    eventSource.onmessage = function(event) {
        try {
            if (event.data === 'ping') {
                // Handle ping message
                const pingElement = document.createElement('div');
                pingElement.className = 'update ping';
                pingElement.textContent = 'Ping received';
                document.getElementById('updates').appendChild(pingElement);
                return;
            }

            const update = JSON.parse(event.data);
            const hero = update.hero;
            
            const updateElement = document.createElement('div');
            updateElement.className = 'update';
            updateElement.innerHTML = `
                <strong>${hero.name}</strong><br>
                Power Stats: ${JSON.stringify(hero.powerstats)}<br>
                Biography: ${JSON.stringify(hero.biography)}
            `;
            document.getElementById('updates').appendChild(updateElement);
        } catch (error) {
            addUpdate('Error parsing update: ' + error.message, true);
        }
    };

    eventSource.onerror = function(error) {
        console.error('EventSource failed:', error);
        updateStatus(false);
        eventSource.close();
        eventSource = null;

        // Attempt to reconnect
        if (reconnectAttempts < maxReconnectAttempts) {
            reconnectAttempts++;
            addUpdate(`Connection lost. Attempting to reconnect (${reconnectAttempts}/${maxReconnectAttempts})...`, true);
            setTimeout(subscribe, 5000); // Try to reconnect after 5 seconds
        } else {
            addUpdate('Connection lost. Maximum reconnection attempts reached.', true);
        }
    };
}

function unsubscribeHero(heroId) {
    if (eventSource) {
        eventSource.close();
        eventSource = null;
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

function unsubscribe() {
    const heroId = document.getElementById('heroId').value;
    unsubscribeHero(heroId);
}

function addUpdate(message, isError = false) {
    const update = document.createElement('div');
    update.className = 'update' + (isError ? ' error' : '');
    update.textContent = message;
    document.getElementById('updates').appendChild(update);
} 