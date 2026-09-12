async function apiFetch(url, options = {}) {
    const method = (options.method || 'GET').toUpperCase();
    if (!['GET', 'HEAD', 'OPTIONS'].includes(method)) {
        const response = await fetch('/api/auth/csrf', {cache: 'no-store'});
        if (!response.ok) throw new Error('Unable to obtain request token');
        const csrf = await response.json();
        options.headers = {...options.headers, [csrf.headerName]: csrf.token};
    }
    return fetch(url, options);
}

async function submitSecureForm(form) {
    const response = await fetch('/api/auth/csrf', {cache: 'no-store'});
    if (!response.ok) throw new Error('Unable to obtain request token');
    const csrf = await response.json();
    const input = document.createElement('input');
    input.type = 'hidden';
    input.name = csrf.parameterName;
    input.value = csrf.token;
    form.appendChild(input);
    form.submit();
}

function escapeHtml(value) {
    const element = document.createElement('span');
    element.textContent = value == null ? '' : String(value);
    return element.innerHTML.replaceAll('"', '&quot;').replaceAll("'", '&#39;');
}

function safeImage(value) {
    try {
        return new URL(value).protocol === 'https:' ? escapeHtml(value) : '';
    } catch {
        return '';
    }
}

document.addEventListener('DOMContentLoaded', () => {
    const login = document.querySelector('form[action="/perform_login"]');
    if (login) login.addEventListener('submit', event => {
        event.preventDefault();
        submitSecureForm(login).catch(() => alert('Login unavailable; retry'));
    });
});
