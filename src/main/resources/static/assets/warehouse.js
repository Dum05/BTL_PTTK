const main = document.querySelector('#warehouse');
const get = id => document.getElementById(id);
const cart = new Map();
let supplier;
let product;
let saving = false;
let pending = 0;

function refreshSave() {
    get('save').disabled = saving || pending > 0;
}

function lockDraft(locked) {
    saving = locked;
    get('draft-editor').inert = locked;
    document.querySelector('header').inert = locked;
    get('draft-editor').setAttribute('aria-busy', String(locked));
    get('saving-status').hidden = !locked;
    refreshSave();
}

const money = value => BigInt(value).toLocaleString('vi-VN') + ' đ';
const error = message => {
    get('error').hidden = !message;
    get('error').textContent = message;
    if (message) get('error').scrollIntoView({block: 'nearest'});
};

async function request(path, params, method = 'GET') {
    error('');
    const data = new URLSearchParams(params);
    if (method === 'POST') data.set('csrf', main.dataset.csrf);
    let response;
    try {
        response = await fetch('/warehouse/' + path + (method === 'GET' ? '?' + data : ''),
            {method, body: method === 'POST' ? data : undefined});
    } catch {
        throw new Error('Không kết nối được máy chủ. Dữ liệu đang nhập được giữ lại; hãy thử lại.');
    }
    if (response.redirected) {
        location.assign(response.url);
        throw new Error('Phiên làm việc đã kết thúc.');
    }
    let payload;
    if ((response.headers.get('content-type') || '').includes('application/json')) {
        try { payload = await response.json(); } catch { /* Dùng thông báo chung bên dưới. */ }
    }
    if (!response.ok) {
        const message = response.status >= 500
            ? 'Máy chủ tạm thời không xử lí được yêu cầu. Hãy thử lại.'
            : 'Dữ liệu không hợp lệ hoặc phiên đã hết hạn. Hãy kiểm tra và thử lại.';
        throw new Error(typeof payload?.error === 'string' ? payload.error : message);
    }
    if (payload === undefined) throw new Error('Phản hồi máy chủ không hợp lệ. Hãy thử lại.');
    return payload;
}

function wire(formId, run) {
    const form = get(formId);
    form.addEventListener('submit', async event => {
        event.preventDefault();
        if (saving) return;
        error('');
        const button = form.querySelector('button');
        button.disabled = true;
        pending++;
        refreshSave();
        try {
            await run(new FormData(form), form);
        } catch (ex) {
            error(ex.message);
        } finally {
            button.disabled = false;
            pending--;
            refreshSave();
        }
    });
}

function chooseSupplier(row) {
    if (saving) return;
    supplier = row;
    get('supplier-selection').textContent = 'Đã chọn: ' + row.name;
}

function chooseProduct(row) {
    if (saving) return;
    product = row;
    get('product-selection').textContent = row.sku + ' · ' + row.name + ' · Tồn ' + row.stock;
    get('line-add').elements.quantity.focus();
}

function renderResults(element, rows, label, choose) {
    element.replaceChildren();
    if (rows.length === 0) element.textContent = 'Không tìm thấy. Có thể thêm mới bên dưới.';
    for (const row of rows) {
        const button = document.createElement('button');
        button.type = 'button';
        button.className = 'secondary';
        button.textContent = label(row);
        button.addEventListener('click', () => choose(row));
        element.append(button);
    }
}

function wireCatalog(kind, route, label, choose) {
    const search = get(kind + '-search');
    const results = get(kind + '-results');
    const details = get(kind + '-create');
    let searchVersion = 0;
    const hideAdd = () => {
        details.hidden = true;
        details.open = false;
    };
    search.elements.name.addEventListener('input', () => {
        searchVersion++;
        hideAdd();
        results.replaceChildren();
    });
    wire(search.id, async data => {
        const version = ++searchVersion;
        hideAdd();
        results.replaceChildren();
        if (kind === 'product' && !supplier) throw new Error('Chọn nhà cung cấp trước khi tìm mặt hàng.');
        try {
            const rows = await request(route, data);
            if (version !== searchVersion) return;
            renderResults(results, rows, label, choose);
            details.hidden = rows.length !== 0;
        } catch (ex) {
            if (version === searchVersion) throw ex;
        }
    });
    wire(kind + '-add', async (data, form) => {
        if (details.hidden) throw new Error('Hãy tìm theo tên trước khi thêm danh mục.');
        const version = searchVersion;
        try {
            const row = await request(route, data, 'POST');
            // The insert may succeed after the operator has started a different search.
            if (version !== searchVersion) return;
            form.reset();
            hideAdd();
            renderResults(results, [row], label, choose);
            choose(row);
        } catch (ex) {
            if (version === searchVersion) throw ex;
        }
    });
}

wireCatalog('supplier', 'suppliers', row => row.name, chooseSupplier);
wireCatalog('product', 'products', row => row.sku + ' · ' + row.name, chooseProduct);

function renderCart() {
    const body = get('lines');
    body.replaceChildren();
    let total = 0n;
    for (const [id, row] of cart) {
        const tr = body.insertRow();
        const amount = BigInt(row.quantity) * BigInt(row.price);
        total += amount;
        [row.sku, row.name, row.quantity, money(row.price), money(amount)].forEach((value, index) => {
            const cell = tr.insertCell();
            cell.textContent = value;
            if (index >= 2) cell.className = 'num';
        });
        const remove = document.createElement('button');
        remove.className = 'secondary';
        remove.textContent = 'Bỏ';
        remove.setAttribute('aria-label', 'Bỏ ' + row.sku);
        remove.addEventListener('click', () => {
            if (saving) return;
            cart.delete(id);
            renderCart();
        });
        tr.insertCell().append(remove);
    }
    if (cart.size === 0) {
        const cell = body.insertRow().insertCell();
        cell.colSpan = 6;
        cell.textContent = 'Chưa có dòng nhập.';
    }
    get('total').textContent = money(total);
}

wire('line-add', (data, form) => {
    if (!product) throw new Error('Hãy chọn mặt hàng cần nhập.');
    if (cart.has(product.id)) throw new Error('Mặt hàng đã có trong phiếu. Bỏ dòng cũ để nhập lại.');
    if (cart.size >= 100) throw new Error('Phiếu chỉ nhận tối đa 100 dòng.');
    const quantity = data.get('quantity');
    const price = data.get('unitPrice');
    if (!/^[0-9]+$/.test(quantity) || Number(quantity) < 1 || Number(quantity) > 1_000_000) {
        throw new Error('Số lượng phải là số nguyên từ 1 đến 1.000.000, nhập bằng chữ số.');
    }
    if (!/^[1-9][0-9]{0,14}$/.test(price)) {
        throw new Error('Đơn giá phải là số nguyên dương, tối đa 15 chữ số.');
    }
    cart.set(product.id, {...product, quantity: Number(quantity), price});
    product = undefined;
    get('product-selection').textContent = 'Chưa chọn mặt hàng';
    form.reset();
    renderCart();
});

get('save').addEventListener('click', async () => {
    if (saving || pending > 0) return;
    if (!supplier || cart.size === 0) {
        error('Cần chọn nhà cung cấp và thêm ít nhất một mặt hàng.');
        return;
    }
    const params = new URLSearchParams({supplierId: supplier.id, requestKey: main.dataset.key});
    for (const row of cart.values()) {
        params.append('productId', row.id);
        params.append('quantity', row.quantity);
        params.append('unitPrice', row.price);
    }
    lockDraft(true);
    try {
        location.assign((await request('receipts', params, 'POST')).invoiceUrl);
    } catch (ex) {
        lockDraft(false);
        error(ex.message);
    }
});
renderCart();
