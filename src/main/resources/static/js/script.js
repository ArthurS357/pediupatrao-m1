// CSRF: injeta o token em fetch() que altera estado (os metas _csrf vem do layout)
(() => {
    const token = document.querySelector("meta[name='_csrf']");
    const header = document.querySelector("meta[name='_csrf_header']");
    if (!token || !header) return;
    const original = window.fetch;
    window.fetch = (url, opts = {}) => {
        const metodo = (opts.method || "GET").toUpperCase();
        const mesmaOrigem = new URL(url, location.href).origin === location.origin;
        if (mesmaOrigem && !["GET", "HEAD", "OPTIONS"].includes(metodo)) {
            opts.headers = { ...opts.headers, [header.content]: token.content };
        }
        return original(url, opts);
    };
})();

// Alternar sidebar em telas pequenas
document.querySelector('.toggle-btn').addEventListener('click', () => {
    document.getElementById('sidebar').classList.toggle('active');
});

// Alternar submenu
function toggleSubmenu(event, element) {
    event.preventDefault();
    element.parentElement.classList.toggle('open');
}