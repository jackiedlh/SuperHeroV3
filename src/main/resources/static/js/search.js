// Search-related functionality
// baseUrl is defined in config.js

let currentPage = 1;
const pageSize = 10;
let totalPages = 1;
let totalCount = 0;

// Searches for heroes by name
async function searchHero(page = 1) {
    const searchInput = document.getElementById('searchInput');
    const searchTerm = searchInput.value.trim();
    
    if (!searchTerm) {
        alert('Please enter a search term');
        return;
    }

    currentPage = page;
    const searchResultsBody = document.getElementById('searchResultsBody');
    searchResultsBody.innerHTML = '<tr><td colspan="2">Searching...</td></tr>';

    try {
        const response = await fetch(`${baseUrl}/api/hero/search?name=${encodeURIComponent(searchTerm)}&page=${page}&pageSize=${pageSize}`);
        if (!response.ok) {
            throw new Error('Search failed');
        }
        
        const paginatedResponse = await response.json();
        const results = paginatedResponse.results;
        
        if (results.length === 0) {
            searchResultsBody.innerHTML = '<tr><td colspan="2">No heroes found</td></tr>';
            totalPages = 1;
            totalCount = 0;
            updatePaginationControls();
            return;
        }

        // Update pagination info from response
        currentPage = paginatedResponse.currentPage;
        totalPages = paginatedResponse.totalPages;
        totalCount = paginatedResponse.totalCount;

        // Add the hero rows
        searchResultsBody.innerHTML = results.map(hero => `
            <tr onclick="selectHero('${hero.id}')">
                <td>${hero.id}</td>
                <td>${hero.name}</td>
            </tr>
        `).join('');

        // Add pagination and total count to table footer
        const footerRow = document.createElement('tr');
        footerRow.style.backgroundColor = '#f2f2f2';
        footerRow.innerHTML = `<td colspan="2" style="text-align: center;" id="tableFooter"></td>`;
        searchResultsBody.appendChild(footerRow);

        // Update pagination controls
        updatePaginationControls();
    } catch (error) {
        console.error('Search error:', error);
        searchResultsBody.innerHTML = '<tr><td colspan="2">Error searching for heroes</td></tr>';
    }
}

// Update pagination controls
function updatePaginationControls() {
    const paginationDiv = document.getElementById('tableFooter');
    if (!paginationDiv) return;

    let paginationHtml = '';
    
    // Previous button
    if (currentPage > 1) {
        paginationHtml += `<button onclick="searchHero(${currentPage - 1})">&lt;</button> `;
    }
    
    // Page numbers
    for (let i = 1; i <= totalPages; i++) {
        if (i === currentPage) {
            paginationHtml += `<span class="current-page">${i}</span> `;
        } else {
            paginationHtml += `<button onclick="searchHero(${i})">${i}</button> `;
        }
    }
    
    // Next button
    if (currentPage < totalPages) {
        paginationHtml += `<button onclick="searchHero(${currentPage + 1})">&gt;</button> `;
    }

    // Add total count display
    paginationHtml += `<span style="margin-left: 10px; font-weight: bold;">Total: ${totalCount}</span>`;
    
    paginationDiv.innerHTML = paginationHtml;
}

// Selects a hero from the search results
function selectHero(heroId) {
    document.getElementById('heroId').value = heroId;
    //subscribeToHero();
} 