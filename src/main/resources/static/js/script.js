const toastContainer = document.getElementById('toast-container');
const productGrid = document.getElementById('product-grid');

let products = [];
let isRequestInFlight = new Set(); // Track requests per itemId

async function init() {
    try {
        // Fetch Product List
        const res = await apiFetch('/api/products');
        products = await res.json();
        renderProducts(products);
        
        // Start Stock Polling
        setInterval(updateAllStocks, 5000);
        updateAllStocks(); // Initial call
    } catch (err) {
        console.error("Failed to init:", err);
        productGrid.innerHTML = '<div class="error">Failed to load deals. Please refresh.</div>';
    }
}

function renderProducts(products) {
    productGrid.innerHTML = products.map(p => `
        <div class="card" id="card-${p.id}">
            <img src="${safeImage(p.imageUrl)}" alt="${escapeHtml(p.name)}" class="card-img" onerror="this.src='https://placehold.co/600x400/333/FFF?text=Tech+Item'">
            <div class="card-body">
                <h3 class="card-title">${escapeHtml(p.name)}</h3>
                <p class="card-desc">${escapeHtml(p.description)}</p>
                
                <div class="stock-meter">
                    Stock: <span id="stock-${p.id}" class="stock-value">Checking...</span>
                </div>
                
                <div class="card-footer">
                    <span class="price">$${p.price}</span>
                </div>
                <button id="btn-${p.id}" class="btn" onclick="buyItem('${p.id}')">Buy Now</button>
            </div>
        </div>
    `).join('');
}

async function updateAllStocks() {
    products.forEach(async (p) => {
        try {
            const res = await apiFetch(`/api/inventory/stock/${p.id}`);
            if (res.ok) {
                const stock = await res.json();
                const stockEl = document.getElementById(`stock-${p.id}`);
                const btn = document.getElementById(`btn-${p.id}`);
                
                if (stockEl) {
                    stockEl.textContent = stock;
                    stockEl.className = stock < 5 ? "stock-value low" : "stock-value";
                    
                    if (stock <= 0) {
                        btn.disabled = true;
                        btn.textContent = "Sold Out";
                        stockEl.textContent = "Out of Stock";
                    }
                }
            }
        } catch (e) {
            console.error(`Error fetching stock for ${p.id}`, e);
        }
    });
}

async function buyItem(itemId) {
    if (!window.currentUser) {
        window.location.href = 'login.html';
        return;
    }
    
    if (isRequestInFlight.has(itemId)) return;
    
    const btn = document.getElementById(`btn-${itemId}`);
    btn.disabled = true;
    btn.textContent = "Processing...";
    isRequestInFlight.add(itemId);

    const params = new URLSearchParams({itemId});


    try {
        const response = await apiFetch(`/api/orders?${params.toString()}`, {
            method: 'POST'
        });
        const message = await response.text();
        
        if (response.ok) {
            showToast(`Success! Ordered Item`, 'success');
            // Immediate stock refresh for this item
            const res = await apiFetch(`/api/inventory/stock/${itemId}`);
            const stock = await res.json();
            document.getElementById(`stock-${itemId}`).textContent = stock;
        } else {
            showToast("Failed: " + message, 'error');
        }
    } catch (error) {
        showToast("Network Error", 'error');
    } finally {
        isRequestInFlight.delete(itemId);
        if (btn.textContent !== "Sold Out") {
            btn.disabled = false;
            btn.textContent = "Buy Now";
        }
    }
}

function showToast(message, type) {
    const toast = document.createElement('div');
    toast.className = `toast ${type}`;
    toast.textContent = message;
    
    toastContainer.appendChild(toast);
    setTimeout(() => toast.remove(), 3000);
}

// Start
apiFetch('/api/auth/me').then(res => res.json()).then(data => {
    if (data.authenticated === 'true') window.currentUser = data.username;
});
init();
