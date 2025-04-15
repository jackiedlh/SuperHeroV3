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
            <span onclick="showSubscribedHeroDetails('${heroId}')">Hero ID: ${heroId}</span>
            <button onclick="unsubscribeHero('${heroId}')">x</button>
        `;
        subscriptionList.appendChild(item);
    });
}

function showSubscribedHeroDetails(heroId) {
    const heroDetails = document.getElementById('subscribedHeroDetails');
    heroDetails.innerHTML = '<h3>Loading hero details...</h3>';
    
    fetch(`${baseUrl}/api/cache/${heroId}`)
        .then(response => {
            if (!response.ok) {
                throw new Error('Hero not found in cache');
            }
            return response.json();
        })
        .then(hero => {
            heroDetails.innerHTML = `
                <h3>Subscribed Hero Details</h3>
                <table>
                    <tr>
                        <th>ID</th>
                        <td>${hero.id}</td>
                    </tr>
                    <tr>
                        <th>Name</th>
                        <td>
                            <span id="subscribedHeroName">${hero.name}</span>
                            <button onclick="editSubscribedHeroName('${hero.id}')">Edit</button>
                        </td>
                    </tr>
                </table>
            `;
        })
        .catch(error => {
            heroDetails.innerHTML = `
<!--                <h3>Error loading hero details</h3>-->
                <p>${error.message}</p>
            `;
        });
}

function editSubscribedHeroName(heroId) {
    const heroNameElement = document.getElementById('subscribedHeroName');
    const currentName = heroNameElement.textContent;
    
    heroNameElement.innerHTML = `
        <input type="text" id="subscribedNameInput" value="${currentName}">
        <button onclick="saveSubscribedHeroName('${heroId}')">Save</button>
        <button onclick="cancelSubscribedEdit()">Cancel</button>
    `;
}

function saveSubscribedHeroName(heroId) {
    const newName = document.getElementById('subscribedNameInput').value;
    
    fetch(`${baseUrl}/api/cache/${heroId}/name`, {
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
        document.getElementById('subscribedHeroName').innerHTML = hero.name;
        addUpdate(`Updated hero name to: ${hero.name}`);
        showSubscribedHeroDetails(heroId); // Refresh the details
    })
    .catch(error => {
        addUpdate('Error updating hero name: ' + error.message, true);
        showSubscribedHeroDetails(heroId); // Reload the original data
    });
}

function cancelSubscribedEdit() {
    const heroId = document.getElementById('heroId').value;
    showSubscribedHeroDetails(heroId);
}

function handleSubscribeAll(checked) {
    if (checked) {
        // Close existing connection if any
        if (eventSource) {
            eventSource.close();
        }

        // Create new EventSource for all heroes
        eventSource = new EventSource(`${baseUrl}/api/notifications/subscribe`);
        updateStatus(true);
        reconnectAttempts = 0;
        subscribedHeroes.clear();
        updateSubscriptionList();
        document.getElementById('subscribedHeroDetails').innerHTML = '<h3>Subscribed to all heroes</h3>';

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
    } else {
        // Unsubscribe from all heroes
        if (eventSource) {
            eventSource.close();
            eventSource = null;
            updateStatus(false);
            subscribedHeroes.clear();
            updateSubscriptionList();
            
            // Show subscription sections again
            document.getElementById('subscriptionList').style.display = 'block';
            document.getElementById('subscribedHeroDetails').style.display = 'block';
            document.getElementById('subscribedHeroDetails').innerHTML = '<h3>No hero subscribed yet</h3>';
        }
    }
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
    showSubscribedHeroDetails(heroId);

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

function updateCacheStats() {
    fetch(`${baseUrl}/api/cache/stats`)
        .then(response => {
            if (!response.ok) {
                throw new Error('Failed to fetch cache stats');
            }
            return response.json();
        })
        .then(stats => {
            document.getElementById('cacheSize').textContent = stats.size;
            document.getElementById('cacheHitCount').textContent = stats.hitCount;
            document.getElementById('cacheMissCount').textContent = stats.missCount;
            document.getElementById('cacheHitRate').textContent = (stats.hitRate * 100).toFixed(2) + '%';
            document.getElementById('cacheEvictionCount').textContent = stats.evictionCount;
        })
        .catch(error => {
            console.error('Error fetching cache stats:', error);
        });
}

function showAllCacheKeys() {
    const cacheKeysElement = document.getElementById('cacheKeysList');
    cacheKeysElement.innerHTML = '<div class="cache-key">Loading cache keys...</div>';
    
    // Update cache stats
    updateCacheStats();
    
    fetch(`${baseUrl}/api/cache/keys`)
        .then(response => {
            if (!response.ok) {
                throw new Error('Failed to fetch cache keys');
            }
            return response.json();
        })
        .then(keys => {
            if (keys.length === 0) {
                cacheKeysElement.innerHTML = '<div class="cache-key">No keys in cache</div>';
                return;
            }
            
            cacheKeysElement.innerHTML = '';
            keys.sort((a, b) => a - b).forEach(key => {
                const keyElement = document.createElement('div');
                keyElement.className = 'cache-key';
                keyElement.textContent = key;
                keyElement.onclick = () => showHeroDetails(key);
                cacheKeysElement.appendChild(keyElement);
            });
        })
        .catch(error => {
            console.error('Error fetching cache keys:', error);
            cacheKeysElement.innerHTML = `<div class="cache-key error">Error: ${error.message}</div>`;
        });
}

function showHeroDetails(heroId) {
    const heroDetails = document.getElementById('heroDetails');
    heroDetails.innerHTML = '<h3>Loading hero details...</h3>';
    
    fetch(`${baseUrl}/api/cache/${heroId}`)
        .then(response => {
            if (!response.ok) {
                throw new Error('Hero not found in cache');
            }
            return response.json();
        })
        .then(hero => {
            heroDetails.innerHTML = `
                <h3>Hero Details</h3>
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
<!--                <h3>Error loading hero details</h3>-->
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
    
    fetch(`${baseUrl}/api/cache/${heroId}/name`, {
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
        showHeroDetails(heroId); // Refresh the details
    })
    .catch(error => {
        addUpdate('Error updating hero name: ' + error.message, true);
        showHeroDetails(heroId); // Reload the original data
    });
}

function cancelEdit() {
    const heroId = document.getElementById('heroId').value;
    showHeroDetails(heroId);
}

// Update cache stats periodically
setInterval(updateCacheStats, 5000); // Update every 5 seconds

async function searchHero() {
    const searchInput = document.getElementById('searchInput');
    const searchTerm = searchInput.value.trim();
    
    if (!searchTerm) {
        alert('Please enter a search term');
        return;
    }

    const searchResultsBody = document.getElementById('searchResultsBody');
    searchResultsBody.innerHTML = '<tr><td colspan="2">Searching...</td></tr>';

    try {
        const response = await fetch(`${baseUrl}/api/hero/search?name=${encodeURIComponent(searchTerm)}`);
        if (!response.ok) {
            throw new Error('Search failed');
        }
        
        const data = await response.json();
        if (data.length === 0) {
            searchResultsBody.innerHTML = '<tr><td colspan="2">No heroes found</td></tr>';
            return;
        }

        // Add the hero rows
        searchResultsBody.innerHTML = data.map(hero => `
            <tr onclick="selectHero('${hero.id}')">
                <td>${hero.id}</td>
                <td>${hero.name}</td>
            </tr>
        `).join('');

        // Add the count row at the bottom
        const countRow = document.createElement('tr');
        countRow.style.backgroundColor = '#f2f2f2';
        countRow.style.fontWeight = 'bold';
        countRow.innerHTML = `<td colspan="2" style="text-align: center;">Total Heroes Found: ${data.length}</td>`;
        searchResultsBody.appendChild(countRow);
    } catch (error) {
        console.error('Search error:', error);
        searchResultsBody.innerHTML = '<tr><td colspan="2">Error searching for heroes</td></tr>';
    }
}

function selectHero(heroId) {
    document.getElementById('heroId').value = heroId;
    subscribeToHero();
} 