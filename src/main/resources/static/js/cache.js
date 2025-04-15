// Cache-related functionality for managing hero data

// Updates the cache statistics display
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

// Displays all keys in the cache
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

// Displays details of a specific hero from the cache
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
                <p>${error.message}</p>
            `;
        });
}

// Enables editing of a hero's name
function editHeroName(heroId) {
    const heroNameElement = document.getElementById('heroName');
    const currentName = heroNameElement.textContent;
    
    heroNameElement.innerHTML = `
        <input type="text" id="nameInput" value="${currentName}">
        <button onclick="saveHeroName('${heroId}')">Save</button>
        <button onclick="cancelEdit()">Cancel</button>
    `;
}

// Saves the edited name of a hero
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

// Cancels the editing of a hero's name
function cancelEdit() {
    const heroId = document.getElementById('heroId').value;
    showHeroDetails(heroId);
}

// Displays details of a subscribed hero
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
                <p>${error.message}</p>
            `;
        });
}

// Enables editing of a subscribed hero's name
function editSubscribedHeroName(heroId) {
    const heroNameElement = document.getElementById('subscribedHeroName');
    const currentName = heroNameElement.textContent;
    
    heroNameElement.innerHTML = `
        <input type="text" id="subscribedNameInput" value="${currentName}">
        <button onclick="saveSubscribedHeroName('${heroId}')">Save</button>
        <button onclick="cancelSubscribedEdit()">Cancel</button>
    `;
}

// Saves the edited name of a subscribed hero
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

// Cancels the editing of a subscribed hero's name
function cancelSubscribedEdit() {
    const heroId = document.getElementById('heroId').value;
    showSubscribedHeroDetails(heroId);
}

// Update cache stats periodically
setInterval(updateCacheStats, 5000); // Update every 5 seconds 