const ITEM_ID = "item1"; // Hardcoded for demo
const USER_ID = "user_" + Math.floor(Math.random() * 1000);

const stockCountEl = document.getElementById('stock-count');
const buyBtn = document.getElementById('buy-btn');
const toastContainer = document.getElementById('toast-container');

let isRequestInFlight = false;

// Poll Stock every 5 seconds
async function fetchStock() {
    try {
        const response = await fetch(`/api/inventory/stock/${ITEM_ID}`);
        if (!response.ok) throw new Error('Failed to fetch stock');
        const stock = await response.json();
        stockCountEl.textContent = stock;
        
        if (stock <= 0) {
            buyBtn.disabled = true;
            buyBtn.textContent = "Sold Out";
        }
    } catch (error) {
        console.error("Stock fetch error:", error);
    }
}

setInterval(fetchStock, 5000);
fetchStock(); // Initial fetch

// Buy Button Handler
buyBtn.addEventListener('click', async () => {
    if (isRequestInFlight) return;
    
    isRequestInFlight = true;
    buyBtn.disabled = true;
    buyBtn.textContent = "Processing...";
    
    // Construct params for backend
    const params = new URLSearchParams({
        userId: USER_ID,
        itemId: ITEM_ID,
        paymentType: 'creditCard', // Hardcoded strategy
        price: 1999.99
    });

    try {
        const response = await fetch(`/api/orders?${params.toString()}`, {
            method: 'POST'
        });
        
        const message = await response.text();
        
        if (response.ok) {
            showToast("Success: " + message, 'success');
            // Refresh stock immediately
            fetchStock();
        } else {
            showToast("Failed: " + message, 'error');
        }
    } catch (error) {
        showToast("Network Error", 'error');
        console.error(error);
    } finally {
        isRequestInFlight = false;
        if (buyBtn.textContent !== "Sold Out") {
            buyBtn.disabled = false;
            buyBtn.textContent = "Buy Now";
        }
    }
});

function showToast(message, type) {
    const toast = document.createElement('div');
    toast.className = `toast ${type}`;
    toast.textContent = message;
    
    toastContainer.appendChild(toast);
    
    // Remove from DOM after animation (3s total)
    setTimeout(() => {
        toast.remove();
    }, 3000);
}
