// API Client for POS System
class POSApi {
    constructor(baseUrl = '') {
        this.baseUrl = baseUrl;
        this.headers = {
            'Content-Type': 'application/json',
            'Accept': 'application/json'
        };
    }

    async request(endpoint, options = {}) {
        const url = `${this.baseUrl}/api${endpoint}`;
        const config = {
            headers: this.headers,
            ...options
        };

        try {
            const response = await fetch(url, config);
            const data = await response.json();

            if (!response.ok) {
                throw new Error(data.message || `HTTP error! status: ${response.status}`);
            }

            return data;
        } catch (error) {
            console.error('API request failed:', error);
            throw error;
        }
    }

    // GET requests
    async get(endpoint) {
        return this.request(endpoint, { method: 'GET' });
    }

    // POST requests
    async post(endpoint, data) {
        return this.request(endpoint, {
            method: 'POST',
            body: JSON.stringify(data)
        });
    }

    // PUT requests
    async put(endpoint, data) {
        return this.request(endpoint, {
            method: 'PUT',
            body: JSON.stringify(data)
        });
    }

    // DELETE requests
    async delete(endpoint) {
        return this.request(endpoint, { method: 'DELETE' });
    }

    // Specific API endpoints
    async getStatus() {
        return this.get('/status');
    }

    async getProducts() {
        return this.get('/products');
    }

    async getProduct(id) {
        return this.get(`/products/${id}`);
    }

    async createProduct(product) {
        return this.post('/products', product);
    }

    async updateProduct(id, product) {
        return this.put(`/products/${id}`, product);
    }

    async deleteProduct(id) {
        return this.delete(`/products/${id}`);
    }

    async getTransactions() {
        return this.get('/transactions');
    }

    async getTransaction(id) {
        return this.get(`/transactions/${id}`);
    }

    async createTransaction(transaction) {
        return this.post('/transactions', transaction);
    }

    async getReports(type = 'daily') {
        return this.get(`/reports?type=${type}`);
    }

    async getStats() {
        return this.get('/stats');
    }
}

// Global API instance
const api = new POSApi();

// Utility functions for common operations
const ApiUtils = {
    // Show loading state
    showLoading(elementId) {
        const element = document.getElementById(elementId);
        if (element) {
            element.innerHTML = '<div class="loading"></div>';
        }
    },

    // Show error message
    showError(message, elementId = null) {
        console.error('API Error:', message);

        if (elementId) {
            const element = document.getElementById(elementId);
            if (element) {
                element.innerHTML = `<div class="error-message">${message}</div>`;
            }
        }

        // You could also show a toast notification here
        this.showToast(message, 'error');
    },

    // Show success message
    showSuccess(message) {
        this.showToast(message, 'success');
    },

    // Simple toast notification system
    showToast(message, type = 'info') {
        // Create toast element
        const toast = document.createElement('div');
        toast.className = `toast toast-${type}`;
        toast.textContent = message;

        // Add to page
        document.body.appendChild(toast);

        // Show toast
        setTimeout(() => toast.classList.add('show'), 100);

        // Remove toast after 3 seconds
        setTimeout(() => {
            toast.classList.remove('show');
            setTimeout(() => document.body.removeChild(toast), 300);
        }, 3000);
    },

    // Format currency
    formatCurrency(amount) {
        return new Intl.NumberFormat('en-US', {
            style: 'currency',
            currency: 'USD'
        }).format(amount);
    },

    // Format date
    formatDate(dateString) {
        return new Date(dateString).toLocaleString();
    },

    // Debounce function for search inputs
    debounce(func, wait) {
        let timeout;
        return function executedFunction(...args) {
            const later = () => {
                clearTimeout(timeout);
                func(...args);
            };
            clearTimeout(timeout);
            timeout = setTimeout(later, wait);
        };
    }
};

// Add CSS for toast notifications
const toastCSS = `
    .toast {
        position: fixed;
        top: 20px;
        right: 20px;
        padding: 12px 20px;
        border-radius: 4px;
        color: white;
        font-weight: 500;
        z-index: 10000;
        transform: translateX(100%);
        transition: transform 0.3s ease;
        max-width: 300px;
    }

    .toast.show {
        transform: translateX(0);
    }

    .toast-info {
        background-color: #3498db;
    }

    .toast-success {
        background-color: #2ecc71;
    }

    .toast-error {
        background-color: #e74c3c;
    }

    .toast-warning {
        background-color: #f39c12;
    }

    .error-message {
        color: #e74c3c;
        padding: 10px;
        border: 1px solid #e74c3c;
        border-radius: 4px;
        background-color: #fdf2f2;
        margin: 10px 0;
    }
`;

// Inject toast CSS
const style = document.createElement('style');
style.textContent = toastCSS;
document.head.appendChild(style);