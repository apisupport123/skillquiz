// =====================================================
//  Edit these two lines after you deploy the backend.
// =====================================================
window.SKILLQUIZ_CONFIG = {
  // Your Render backend URL (no slash at the end)
  apiBase: "https://skillquiz-api.onrender.com",

  // Shown in the header
  orgName: "Swami Intelligent Systems"
};

// Local testing: if you open the page from localhost or as a file, use the local backend
if (location.hostname === "localhost" || location.hostname === "127.0.0.1" || location.protocol === "file:") {
  window.SKILLQUIZ_CONFIG.apiBase = "http://localhost:8080";
}
