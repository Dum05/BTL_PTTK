const main = document.querySelector('#warehouse');
const get = id => document.getElementById(id);
const cart = new Map();
let supplier;
let product;

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
    const response = await fetch('/warehouse/' + path + (method === 'GET' ? '?' + data : ''),
        {method, body: method === 'POST' ? data : undefined});
    if (response.redirected) {
        location.assign(response.url);
        throw new Error('Phiên làm việc đã kết thúc.');
    }
    const payload = await response.json();
    if (!response.ok) throw new Error(payload.error || 'Dữ liệu không hợp lệ hoặc phiên đã hết hạn.');
    return payload;
}

function wire(formId, run) {
    const form = get(formId);
    form.addEventListener('submit', async event => {
        event.preventDefault();
        error('');
        const button = form.querySelector('button');
        button.disabled = true;
        try {
            await run(new FormData(form), form);
        } catch (ex) {
            error(ex.message);
        } finally {
            button.disabled = false;
        }
    });
}

function chooseSupplier(row) {
    supplier = row;
    get('supplier-selection').textContent = 'Đã chọn: ' + row.name;
}

function chooseProduct(row) {
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

wire('supplier-search', async data => {
    renderResults(get('supplier-results'), await request('suppliers', data), row => row.name, chooseSupplier);
});
wire('product-search', async data => {
    if (!supplier) throw new Error('Chọn nhà cung cấp trước khi tìm mặt hàng.');
    renderResults(get('product-results'), await request('products', data), row => row.sku + ' · ' + row.name, chooseProduct);
});
wire('supplier-add', async (data, form) => {
    chooseSupplier(await request('suppliers', data, 'POST'));
    form.reset();
    form.closest('details').open = false;
});
wire('product-add', async (data, form) => {
    if (!supplier) throw new Error('Chọn nhà cung cấp trước khi thêm mặt hàng.');
    const row = await request('products', data, 'POST');
    form.reset();
    form.closest('details').open = false;
    chooseProduct(row);
});

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
    cart.set(product.id, {...product, quantity: data.get('quantity'), price: data.get('unitPrice')});
    product = undefined;
    get('product-selection').textContent = 'Chưa chọn mặt hàng';
    form.reset();
    renderCart();
});

get('save').addEventListener('click', async () => {
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
    get('save').disabled = true;
    try {
        location.assign((await request('receipts', params, 'POST')).invoiceUrl);
    } catch (ex) {
        error(ex.message);
    } finally {
        get('save').disabled = false;
    }
});
renderCart();
