async (page) => {
  const fixtures = __UI_FIXTURES__;
  const context = await page.context().browser().newContext({
    viewport: { width: 1440, height: 1000 },
    deviceScaleFactor: 1,
    reducedMotion: 'reduce'
  });
  const errors = [];
  const requests = [];
  const missing = [];
  await context.addInitScript((profile) => {
    localStorage.setItem('token', 'isolated-ui-test-token');
    localStorage.setItem('profile', JSON.stringify(profile));
    localStorage.removeItem('ai-learning-assistant:sidebar-collapsed');
  }, fixtures['/api/user/profile'].data);
  await context.route('http://127.0.0.1:5173/api/**', async route => {
    const request = route.request();
    const address = request.url().split('?');
    const pathname = address[0].replace(/^https?:\/\/[^/]+/, '');
    const params = Object.fromEntries((address[1] || '').split('&').filter(Boolean).map(pair => {
      const [key, value = ''] = pair.split('=');
      return [decodeURIComponent(key), decodeURIComponent(value.replace(/\+/g, ' '))];
    }));
    const url = { pathname, search: address[1] ? '?' + address[1] : '', searchParams: { get: key => params[key], has: key => Object.prototype.hasOwnProperty.call(params, key) } };
    requests.push({ method: request.method(), path: url.pathname, query: url.search });
    const fixture = fixtures[url.pathname];
    if (!fixture || request.method() !== 'GET') {
      missing.push({ method: request.method(), path: url.pathname });
      await route.fulfill({ status: 501, contentType: 'application/json', body: JSON.stringify({ code: 501, message: '未配置的隔离 UI 测试接口', data: null }) });
      return;
    }
    const body = JSON.parse(JSON.stringify(fixture));
    if (body.data?.records) {
      let records = body.data.records;
      const keyword = url.searchParams.get('keyword')?.trim().toLowerCase();
      if (keyword) records = records.filter(row => Object.values(row).some(value => typeof value === 'string' && value.toLowerCase().includes(keyword)));
      for (const key of ['materialType', 'parseStatus', 'materialId', 'summaryType']) {
        const value = url.searchParams.get(key);
        if (value) records = records.filter(row => String(row[key]) === value);
      }
      if (keyword || ['materialType', 'parseStatus', 'materialId', 'summaryType'].some(key => url.searchParams.has(key))) body.data.total = records.length;
      const current = Number(url.searchParams.get('current') || 1);
      const size = Number(url.searchParams.get('size') || 10);
      body.data.current = current;
      body.data.size = size;
      body.data.pages = Math.ceil(body.data.total / size);
      body.data.records = records.slice((current - 1) * size, current * size);
    }
    await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(body) });
  });
  const app = await context.newPage();
  app.on('pageerror', error => errors.push(error.message));
  await app.goto('http://127.0.0.1:5173/dashboard', { waitUntil: 'networkidle' });
  page.context().__uiQa = { context, app, errors, requests, missing, fixtures };
  await app.screenshot({ path: 'C:/Users/雪/Desktop/AI-Intelligent-Learning-Assistant-System/output/playwright/ui-redesign/dashboard-desktop.png', fullPage: true, animations: 'disabled' });
  return { title: await app.title(), snapshot: await app.locator('body').ariaSnapshot(), errors, missing, overflow: await app.evaluate(() => ({ width: innerWidth, scroll: document.documentElement.scrollWidth })) };
}
