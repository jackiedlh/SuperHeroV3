// Utility functions for the application

/**
 * Formats a date to UTC+8 (Asia/Shanghai) timezone
 * @param {Date|string|number} date - The date to format
 * @param {string} [format='yyyy-MM-dd HH:mm:ss'] - The format string
 * @returns {string} Formatted date string
 */
function formatDateUTC8(date, format = 'yyyy-MM-dd HH:mm:ss') {
    const d = new Date(date);
    // Convert to UTC+8
    const utc8Date = new Date(d.getTime() + (8 * 60 * 60 * 1000));
    
    const pad = (num) => num.toString().padStart(2, '0');
    
    const replacements = {
        'yyyy': utc8Date.getUTCFullYear(),
        'MM': pad(utc8Date.getUTCMonth() + 1),
        'dd': pad(utc8Date.getUTCDate()),
        'HH': pad(utc8Date.getUTCHours()),
        'mm': pad(utc8Date.getUTCMinutes()),
        'ss': pad(utc8Date.getUTCSeconds())
    };
    
    return format.replace(/yyyy|MM|dd|HH|mm|ss/g, match => replacements[match]);
}

/**
 * Formats a timestamp to a readable date string in UTC+8
 * @param {number} timestamp - Unix timestamp in milliseconds
 * @returns {string} Formatted date string
 */
function formatTimestampUTC8(timestamp) {
    return formatDateUTC8(timestamp);
}

// Export the functions
window.utils = {
    formatDateUTC8,
    formatTimestampUTC8
}; 