// Dashboard functionality
class Dashboard {
    constructor() {
        this.refreshInterval = 30000; // 30 seconds
        this.updateTimer = null;
        this.init();
    }

    init() {
        this.setupEventListeners();
        this.loadInitialData();
        this.startAutoRefresh();
    }

    setupEventListeners() {
        // Refresh button
        const refreshBtn = document.querySelector('[onclick="refreshData()"]');
        if (refreshBtn) {
            refreshBtn.onclick = () => this.refreshData();
        }

        // Quick action buttons
        document.addEventListener('click', (e) => {
            if (e.target.matches('[onclick*="showReports"]')) {
                e.preventDefault();
                this.showReports();
            } else if (e.target.matches('[onclick*="openPOSTerminal"]')) {
                e.preventDefault();
                this.openPOSTerminal();
            }
        });

        // Handle visibility change to pause/resume updates
        document.addEventListener('visibilitychange', () => {
            if (document.hidden) {
                this.stopAutoRefresh();
            } else {
                this.startAutoRefresh();
            }
        });
    }

    async loadInitialData() {
        try {
            await Promise.all([
                this.updateStats(),
                this.updateSystemStatus(),
                this.updateRecentActivity()
            ]);
        } catch (error) {
            console.error('Failed to load initial data:', error);
            ApiUtils.showError('Failed to load dashboard data');
        }
    }

    async refreshData() {
        const refreshBtn = document.querySelector('[onclick="refreshData()"]');
        if (refreshBtn) {
            refreshBtn.disabled = true;
            refreshBtn.innerHTML = '<div class="loading"></div> Refreshing...';
        }

        try {
            await this.loadInitialData();
            ApiUtils.showSuccess('Dashboard updated successfully');
        } catch (error) {
            ApiUtils.showError('Failed to refresh dashboard');
        } finally {
            if (refreshBtn) {
                refreshBtn.disabled = false;
                refreshBtn.innerHTML = '<i class="icon-refresh"></i> Refresh';
            }
        }
    }

    async updateStats() {
        try {
            const stats = await api.getStats();

            // Update stat values
            this.updateElement('connectedClients', stats.connectedClients || 0);
            this.updateElement('todaySales',
                ApiUtils.formatCurrency(stats.todaySales || 0));
            this.updateElement('todayTransactions', stats.todayTransactions || 0);
            this.updateElement('totalProducts', stats.totalProducts || 0);

        } catch (error) {
            console.error('Failed to update stats:', error);
        }
    }

    async updateSystemStatus() {
        try {
            const status = await api.getStatus();

            // Update server time
            this.updateElement('serverTime', ApiUtils.formatDate(status.timestamp));
            this.updateElement('lastUpdated', ApiUtils.formatDate(status.timestamp));

            // Update status indicators (assuming they exist in your HTML)
            const statusIndicators = document.querySelectorAll('.status-indicator');
            statusIndicators.forEach(indicator => {
                indicator.className = 'status-indicator online';
            });

        } catch (error) {
            console.error('Failed to update system status:', error);

            // Show offline status
            const statusIndicators = document.querySelectorAll('.status-indicator');
            statusIndicators.forEach(indicator => {
                indicator.className = 'status-indicator offline';
            });
        }
    }

    async updateRecentActivity() {
        try {
            const transactions = await api.getTransactions();
            const activityContainer = document.getElementById('recentActivity');

            if (activityContainer && transactions.transactions) {
                const activityHTML = transactions.transactions
                    .slice(0, 5) // Show only last 5
                    .map(transaction => this.createActivityItem(transaction))
                    .join('');

                activityContainer.innerHTML = activityHTML ||
                    '<div class="no-activity">No recent activity</div>';
            }
        } catch (error) {
            console.error('Failed to update recent activity:', error);
        }
    }

    createActivityItem(transaction) {
        const timeAgo = this.getTimeAgo(new Date(transaction.timestamp));
        const statusClass = transaction.status.toLowerCase();

        return `
            <div class="activity-item">
                <div class="activity-time">${timeAgo}</div>
                <div class="activity-description">
                    <span class="transaction-status ${statusClass}">
                        ${transaction.status}
                    </span>
                    Transaction #${transaction.id} - ${ApiUtils.formatCurrency(transaction.total)}
                </div>
            </div>
        `;
    }

    getTimeAgo(date) {
        const now = new Date();
        const diffInSeconds = Math.floor((now - date) / 1000);

        if (diffInSeconds < 60) return 'Just now';
        if (diffInSeconds < 3600) return `${Math.floor(diffInSeconds / 60)} minutes ago`;
        if (diffInSeconds < 86400) return `${Math.floor(diffInSeconds / 3600)} hours ago`;
        return `${Math.floor(diffInSeconds / 86400)} days ago`;
    }

    updateElement(id, value) {
        const element = document.getElementById(id);
        if (element) {
            element.textContent = value;
        }
    }

    startAutoRefresh() {
        this.stopAutoRefresh(); // Clear any existing timer
        this.updateTimer = setInterval(() => {
            this.loadInitialData();
        }, this.refreshInterval);
    }

    stopAutoRefresh() {
        if (this.updateTimer) {
            clearInterval(this.updateTimer);
            this.updateTimer = null;
        }
    }

    showReports() {
        // You can either navigate to a reports page or show a modal
        window.location.href = '/reports';
    }

    openPOSTerminal() {
        // Open POS terminal in a new window/tab
        window.open('/pos-terminal', '_blank', 'width=1024,height=768');
    }
}

// Chart functionality for dashboard graphs
class DashboardCharts {
    constructor() {
        this.charts = {};
        this.initCharts();
    }

    async initCharts() {
        // Only initialize if chart containers exist
        if (document.getElementById('salesChart')) {
            await this.createSalesChart();
        }

        if (document.getElementById('transactionChart')) {
            await this.createTransactionChart();
        }
    }

    async createSalesChart() {
        try {
            const reports = await api.getReports('daily');

            // Mock data for demonstration
            const chartData = {
                labels: ['Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat', 'Sun'],
                datasets: [{
                    label: 'Daily Sales',
                    data: [1200, 1900, 800, 1300, 2000, 1800, 1400],
                    borderColor: '#3498db',
                    backgroundColor: 'rgba(52, 152, 219, 0.1)',
                    tension: 0.4
                }]
            };

            // You would use Chart.js or similar library here
            console.log('Sales chart data:', chartData);

        } catch (error) {
            console.error('Failed to create sales chart:', error);
        }
    }

    async createTransactionChart() {
        try {
            const stats = await api.getStats();

            // Mock hourly transaction data
            const hourlyData = Array.from({length: 24}, (_, i) =>
                Math.floor(Math.random() * 10));

            console.log('Transaction chart data:', hourlyData);

        } catch (error) {
            console.error('Failed to create transaction chart:', error);
        }
    }
}

// Global functions for backward compatibility
function refreshData() {
    if (window.dashboard) {
        window.dashboard.refreshData();
    }
}

function showReports() {
    if (window.dashboard) {
        window.dashboard.showReports();
    }
}

function openPOSTerminal() {
    if (window.dashboard) {
        window.dashboard.openPOSTerminal();
    }
}

// Initialize when DOM is loaded
document.addEventListener('DOMContentLoaded', () => {
    window.dashboard = new Dashboard();
    window.dashboardCharts = new DashboardCharts();

    console.log('Dashboard initialized successfully');
});

// Additional utility functions for dashboard
const DashboardUtils = {
    // Export data functionality
    async exportData(type = 'csv') {
        try {
            const data = await api.getTransactions();

            if (type === 'csv') {
                this.downloadCSV(data.transactions, 'transactions.csv');
            } else if (type === 'json') {
                this.downloadJSON(data, 'transactions.json');
            }

            ApiUtils.showSuccess(`Data exported as ${type.toUpperCase()}`);
        } catch (error) {
            ApiUtils.showError('Failed to export data');
        }
    },

    downloadCSV(data, filename) {
        if (!data || data.length === 0) return;

        const headers = Object.keys(data[0]);
        const csvContent = [
            headers.join(','),
            ...data.map(row => headers.map(header =>
                JSON.stringify(row[header] || '')).join(','))
        ].join('\n');

        this.downloadFile(csvContent, filename, 'text/csv');
    },

    downloadJSON(data, filename) {
        const jsonContent = JSON.stringify(data, null, 2);
        this.downloadFile(jsonContent, filename, 'application/json');
    },

    downloadFile(content, filename, contentType) {
        const blob = new Blob([content], { type: contentType });
        const url = URL.createObjectURL(blob);
        const link = document.createElement('a');
        link.href = url;
        link.download = filename;
        document.body.appendChild(link);
        link.click();
        document.body.removeChild(link);
        URL.revokeObjectURL(url);
    }
};