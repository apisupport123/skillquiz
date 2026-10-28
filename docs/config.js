// =====================================================
//  Edit these values if needed, then commit.
// =====================================================
window.SKILLQUIZ_CONFIG = {
  // Your Render backend URL (no slash at the end)
  apiBase: "https://skillquiz-api.onrender.com",

  // Shown in the header
  orgName: "Swami Intelligent Systems",

  // How many times an employee may leave the exam screen (switch app/tab, phone call,
  // notification, screen lock) and come back with only a warning.
  // 0 = the test is submitted the first time they leave.
  leaveWarnings: 0
};

// Local testing: if you open the page from localhost or as a file, use the local backend
if (location.hostname === "localhost" || location.hostname === "127.0.0.1" || location.protocol === "file:") {
  window.SKILLQUIZ_CONFIG.apiBase = "http://localhost:8080";
}
