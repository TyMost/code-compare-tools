from playwright.sync_api import sync_playwright

with sync_playwright() as p:
    browser = p.chromium.launch(headless=True)
    page = browser.new_page()
    page.goto("http://localhost:8082")
    # Bypass overlay
    page.evaluate("localStorage.setItem('code_compare_token', 'test_token');")
    page.evaluate("""
        const style = document.createElement('style');
        style.textContent = '#webpack-dev-server-client-overlay { display: none !important; }';
        document.head.appendChild(style);
    """)
    page.goto("http://localhost:8082")
    page.screenshot(path="screenshot.png")
    browser.close()
