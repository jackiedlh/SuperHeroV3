const baseUrl = window.appConfig.baseUrl; 

// Searches for heroes by name
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

// Selects a hero from the search results
function selectHero(heroId) {
    document.getElementById('heroId').value = heroId;
    //subscribeToHero();
} 