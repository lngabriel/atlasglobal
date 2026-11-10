import assert from 'node:assert/strict'
import { spawn } from 'node:child_process'
import { mkdtempSync, mkdirSync, readFileSync, writeFileSync } from 'node:fs'
import { rm } from 'node:fs/promises'
import { tmpdir } from 'node:os'
import { join, resolve } from 'node:path'

// Run against a built application with the documented provider imports loaded.
// Uses Chrome's DevTools protocol and Node's built-in APIs; no browser SDK needed.
const base = new URL(process.env.ATLAS_BASE_URL ?? 'http://127.0.0.1:3000')
assert.ok(['http:', 'https:'].includes(base.protocol), 'ATLAS_BASE_URL must use HTTP or HTTPS')
const screenshotDirectory = process.env.ATLAS_SCREENSHOT_DIR ? resolve(process.env.ATLAS_SCREENSHOT_DIR) : null
if (screenshotDirectory) mkdirSync(screenshotDirectory, { recursive: true })
const profile = mkdtempSync(join(tmpdir(), 'atlas-browser-check-'))
const downloadDirectory = join(screenshotDirectory ?? profile, 'downloads')
mkdirSync(downloadDirectory, { recursive: true })
const browser = spawn(process.env.CHROME_BIN ?? 'google-chrome', [
  '--headless', '--no-sandbox', '--disable-gpu', '--no-first-run', '--no-default-browser-check',
  '--disable-background-networking', '--disable-component-update', '--disable-sync',
  '--remote-debugging-port=0', `--user-data-dir=${profile}`, 'about:blank',
], { stdio: ['ignore', 'ignore', 'pipe'] })
let browserError
let browserLog = ''
browser.on('error', error => { browserError = error })
browser.stderr.on('data', data => { browserLog = (browserLog + data).slice(-4000) })
const pause = milliseconds => new Promise(resolve => setTimeout(resolve, milliseconds))
const url = path => new URL(path, base).href
const passes = []
function pass(name) { passes.push(name); console.log(`PASS ${name}`) }
async function api(path) {
  const response = await fetch(url(`/api/v1${path}`), { signal: AbortSignal.timeout(15_000) })
  assert.equal(response.status, 200, `API fixture unavailable: ${path}. Start Compose and import the documented dataset.`)
  return response.json()
}
const tableExpression = `Array.from(document.querySelectorAll('table')).find(table => table.caption?.textContent.includes('Dados anuais'))`
let websocket
let call
let evaluate
let lastPage = ''
const exceptions = []
const cspErrors = []
const unexpectedHttpErrors = []
const requestedComparisons = []
const requests = []
const downloads = []
const completedDownloads = new Set()
let interceptionMode = null
const pausedRequests = []

try {
  let port
  for (let attempt = 0; attempt < 100; attempt++) {
    if (browserError) throw browserError
    try { port = Number(readFileSync(join(profile, 'DevToolsActivePort'), 'utf8').split('\n')[0]); if (port) break } catch { /* Chrome is starting. */ }
    if (browser.exitCode !== null) throw new Error(`Chrome exited before startup: ${browserLog}`)
    await pause(100)
  }
  assert.ok(port, `Chrome DevTools was not available: ${browserLog}`)
  const pages = await (await fetch(`http://127.0.0.1:${port}/json/list`)).json()
  const page = pages.find(item => item.type === 'page')
  assert.ok(page, 'Chrome did not create a page')
  websocket = new WebSocket(page.webSocketDebuggerUrl)
  await new Promise((resolve, reject) => {
    websocket.addEventListener('open', resolve, { once: true })
    websocket.addEventListener('error', reject, { once: true })
  })
  let nextId = 0
  const pending = new Map()
  const loadListeners = new Set()
  websocket.addEventListener('message', event => {
    const message = JSON.parse(event.data)
    if (message.method === 'Page.loadEventFired') for (const listener of [...loadListeners]) listener()
    if (message.method === 'Fetch.requestPaused') {
      const requestId = message.params.requestId
      if (interceptionMode === 'pause') pausedRequests.push(requestId)
      else if (interceptionMode === 'fail') void call('Fetch.failRequest', { requestId, errorReason: 'Failed' }).catch(() => { /* An obsolete query may already be cancelled. */ })
      else void call('Fetch.continueRequest', { requestId }).catch(() => { /* Navigation may have cancelled this request. */ })
    }
    if (message.method === 'Runtime.exceptionThrown') exceptions.push(message.params.exceptionDetails)
    if (message.method === 'Network.requestWillBeSent') requests.push(message.params.request.url)
    if (message.method === 'Browser.downloadWillBegin') downloads.push(message.params)
    if (message.method === 'Browser.downloadProgress' && message.params.state === 'completed') completedDownloads.add(message.params.guid)
    if (message.method === 'Network.requestWillBeSent' && /\/api\/v1\/indicators\/[^/]+\/history(?:\?|$)/.test(message.params.request.url)) requestedComparisons.push(message.params.request.url)
    if (message.method === 'Log.entryAdded' && /Content Security Policy|Refused to (?:load|apply)|violates.*policy/i.test(message.params.entry.text)) cspErrors.push(message.params.entry.text)
    if (message.method === 'Network.responseReceived') {
      const response = message.params.response
      if (response.url.includes('/api/v1/') && response.status >= 400 && !/\/countries\/ZZZ(?:[/?]|$)/.test(response.url)) unexpectedHttpErrors.push(`${response.status} ${response.url}`)
    }
    const request = pending.get(message.id)
    if (request) {
      clearTimeout(request.timeout)
      pending.delete(message.id)
      if (message.error) request.reject(new Error(JSON.stringify(message.error)))
      else request.resolve(message.result)
    }
  })
  call = (method, parameters = {}) => new Promise((resolve, reject) => {
    const id = ++nextId
    const timeout = setTimeout(() => { pending.delete(id); reject(new Error(`DevTools timeout: ${method}`)) }, 10_000)
    pending.set(id, { resolve, reject, timeout })
    websocket.send(JSON.stringify({ id, method, params: parameters }))
  })
  evaluate = async expression => {
    const result = await call('Runtime.evaluate', { expression, returnByValue: true, awaitPromise: true })
    if (result.exceptionDetails) throw new Error(JSON.stringify(result.exceptionDetails))
    return result.result.value
  }
  async function waitFor(expression, description = expression) {
    for (let attempt = 0; attempt < 160; attempt++) {
      if (await evaluate(expression)) return
      await pause(100)
    }
    throw new Error(`Timed out waiting for ${description}. URL: ${await evaluate('location.href')}. Page: ${await evaluate('document.body.innerText.slice(0, 2000)')}`)
  }
  async function navigate(path) {
    lastPage = path
    await loadDocument('Page.navigate', { url: url(path) })
  }
  async function loadDocument(method, parameters = {}) {
    let listener
    let timeout
    const loaded = new Promise((resolve, reject) => {
      listener = () => { clearTimeout(timeout); loadListeners.delete(listener); resolve() }
      timeout = setTimeout(() => { loadListeners.delete(listener); reject(new Error(`Document load timed out: ${lastPage}`)) }, 20_000)
      loadListeners.add(listener)
    })
    try { await call(method, parameters); await loaded }
    finally { clearTimeout(timeout); loadListeners.delete(listener) }
  }
  async function click(expression) {
    const point = await evaluate(`(() => { const element = ${expression}; if (!element) throw new Error('Missing click target'); element.scrollIntoView({ block: 'center' }); const box = element.getBoundingClientRect(); if (!box.width || !box.height) throw new Error('Hidden click target'); return { x: box.x + box.width / 2, y: box.y + box.height / 2 }; })()`)
    await call('Input.dispatchMouseEvent', { type: 'mouseMoved', ...point })
    await call('Input.dispatchMouseEvent', { type: 'mousePressed', button: 'left', clickCount: 1, ...point })
    await call('Input.dispatchMouseEvent', { type: 'mouseReleased', button: 'left', clickCount: 1, ...point })
  }
  async function input(selector, text) {
    await click(`document.querySelector(${JSON.stringify(selector)})`)
    await call('Input.dispatchKeyEvent', { type: 'keyDown', key: 'a', code: 'KeyA', windowsVirtualKeyCode: 65, modifiers: 2, commands: ['selectAll'] })
    await call('Input.dispatchKeyEvent', { type: 'keyUp', key: 'a', code: 'KeyA', windowsVirtualKeyCode: 65, modifiers: 2 })
    await call('Input.insertText', { text })
    await waitFor(`document.querySelector(${JSON.stringify(selector)})?.value === ${JSON.stringify(text)}`, `typing into ${selector}`)
  }
  async function select(selector, value) {
    await evaluate(`(() => { const element = document.querySelector(${JSON.stringify(selector)}); Object.getOwnPropertyDescriptor(HTMLSelectElement.prototype, 'value').set.call(element, ${JSON.stringify(value)}); element.dispatchEvent(new Event('change', { bubbles: true })); })()`)
  }
  async function screenshot(name) {
    if (!screenshotDirectory) return
    const shot = await call('Page.captureScreenshot', { format: 'png', captureBeyondViewport: true })
    writeFileSync(join(screenshotDirectory, `${name}.png`), Buffer.from(shot.data, 'base64'))
  }
  async function readyDashboard() {
    await waitFor(`!!document.querySelector('#history-title') && !!document.querySelector('.data-table tbody tr') && !!document.querySelector('.recharts-surface')`, 'country dashboard and rendered chart')
  }
  function csvRecords(content) {
    const rows = [], row = []
    let cell = '', quoted = false
    for (let index = 0; index < content.length; index++) {
      const character = content[index]
      if (character === '"') {
        if (quoted && content[index + 1] === '"') { cell += '"'; index++ } else quoted = !quoted
      } else if (!quoted && character === ',') { row.push(cell); cell = '' }
      else if (!quoted && character === '\r' && content[index + 1] === '\n') {
        row.push(cell); rows.push([...row]); row.length = 0; cell = ''; index++
      } else cell += character
    }
    assert.equal(quoted, false, 'CSV must have balanced RFC4180 quoting')
    if (cell || row.length) { row.push(cell); rows.push([...row]) }
    const header = rows.shift()
    assert.ok(header?.length)
    return rows.map(values => { assert.equal(values.length, header.length); return Object.fromEntries(header.map((key, index) => [key, values[index]])) })
  }
  async function downloadCsv() {
    const previous = downloads.length
    await click(`Array.from(document.querySelectorAll('button')).find(button => button.textContent.trim() === 'Exportar CSV' && !button.disabled)`)
    for (let attempt = 0; attempt < 160; attempt++) {
      const download = downloads[previous]
      if (download && completedDownloads.has(download.guid)) {
        const bytes = readFileSync(join(downloadDirectory, download.suggestedFilename))
        assert.deepEqual([...bytes.subarray(0, 3)], [239, 187, 191], 'Downloaded CSV must retain UTF-8 BOM')
        return csvRecords(bytes.toString('utf8').replace(/^\uFEFF/, ''))
      }
      await pause(100)
    }
    throw new Error('CSV download did not finish')
  }
  async function noOverflow(name) {
    assert.ok(await evaluate('document.documentElement.scrollWidth <= innerWidth + 1'), `${name}: page must not overflow horizontally`)
    pass(name)
  }

  await call('Page.enable')
  await call('Runtime.enable')
  await call('Network.enable')
  await call('Network.setCacheDisabled', { cacheDisabled: true })
  await call('Log.enable')
  await call('Browser.setDownloadBehavior', { behavior: 'allow', downloadPath: downloadDirectory, eventsEnabled: true })
  await call('Emulation.setDeviceMetricsOverride', { width: 1440, height: 1000, deviceScaleFactor: 1, mobile: false })
  const [brazil, summaries] = await Promise.all([api('/countries/BRA'), api('/countries/BRA/indicators?size=100')])
  assert.ok(summaries.content.some(item => item.latestAvailable), 'Import the documented World Bank dataset before running browser checks')
  const featuredCodes = ['ARG', 'AUS', 'BRA', 'CAN', 'CHL', 'CHN', 'FRA', 'HKG', 'ISR', 'ITA', 'JPN', 'PRY', 'POL', 'PRT', 'RUS', 'SGP', 'KOR', 'ESP', 'CHE', 'TWN', 'UKR', 'GBR', 'USA', 'VEN', 'VNM']
  const overview = await api('/overview')
  assert.equal(overview.scope.code, 'WLD')
  assert.ok(overview.indicators.some(item => item.latestAvailable), 'Import the documented world aggregate data before running browser checks')
  await navigate('/')
  await waitFor(`document.querySelectorAll('#featured-countries a[data-country]').length === 25`, 'the complete curated country selection')
  const featured = await evaluate(`Array.from(document.querySelectorAll('#featured-countries a[data-country]')).map(link => ({ code: link.dataset.country, path: new URL(link.href).pathname }))`)
  assert.deepEqual(featured.map(item => item.code), featuredCodes)
  for (const item of featured) assert.equal(item.path, `/paises/${item.code}`)
  assert.equal(await evaluate(`/[\\u{1F1E6}-\\u{1F1FF}]/u.test(document.body.innerText)`), false, 'The interface must not use emoji country flags')
  pass('homepage displays exactly the 25 requested countries with direct profile links and no emoji flags')
  for (const item of overview.indicators.filter(entry => entry.latestAvailable)) {
    const value = item.latestAvailable.value
    const amount = new Intl.NumberFormat('pt-BR', { maximumFractionDigits: item.series.unit === 'PERSONS' ? 0 : 2 }).format(value)
    const expected = item.series.unit === 'USD_CURRENT' ? `US$ ${amount}`
      : item.series.unit === 'PERSONS' ? `${amount} pessoas`
        : item.series.unit === 'YEARS' ? `${amount} anos`
          : item.series.unit.startsWith('PERCENT_') ? `${amount}%` : amount
    await waitFor(`!!document.querySelector('#overview [data-indicator=${JSON.stringify(item.indicator.code)}]')`, `world indicator ${item.indicator.code}`)
    assert.ok(await evaluate(`(() => { const metric = document.querySelector('#overview [data-indicator=${JSON.stringify(item.indicator.code)}]'); return Array.from(metric.querySelectorAll('[title]')).some(element => element.title === ${JSON.stringify(expected)}) && metric.textContent.includes(${JSON.stringify(String(item.latestAvailable.period))}) && metric.textContent.includes(${JSON.stringify(item.series.source.name)}); })()`), `World metric must match the provider aggregate, reference year and source: ${item.indicator.code}`)
  }
  pass('global highlights match the real WLD aggregate with their source and individual reference years')
  await noOverflow('homepage desktop has no horizontal overflow')
  await screenshot('home-desktop-light')
  await input('#home-country-query', 'Brasil')
  await waitFor(`document.querySelectorAll('#featured-countries a[data-country]').length === 1 && document.querySelector('#featured-countries a[data-country]')?.dataset.country === 'BRA'`, 'curated country search')
  await click(`document.querySelector('#featured-countries a[data-country="BRA"]')`)
  await waitFor(`location.pathname === '/paises/BRA'`)
  await readyDashboard()
  pass('homepage search opens the matching individual country profile')
  await navigate('/explorar')
  await readyDashboard()
  assert.ok(await evaluate(`document.querySelector('article')?.getAttribute('aria-label').includes(${JSON.stringify(brazil.name)})`))
  const rows = await evaluate(`Array.from(document.querySelectorAll('.data-table tbody tr')).map(row => ({ name: row.querySelector('button')?.textContent, year: row.querySelectorAll('td')[1]?.textContent, title: row.querySelector('td')?.title }))`)
  assert.equal(rows.length, summaries.content.length)
  for (const item of summaries.content) {
    const row = rows.find(candidate => candidate.name === item.indicator.name)
    assert.ok(row, `Missing indicator row: ${item.indicator.name}`)
    assert.equal(row.year, String(item.latestAvailable?.period ?? '—'), `Incorrect reference year: ${item.indicator.name}`)
    if (item.indicator.code === 'NY.GDP.MKTP.CD' && item.latestAvailable) {
      const expected = `US$ ${new Intl.NumberFormat('pt-BR', { maximumFractionDigits: 2 }).format(item.latestAvailable.value)}`
      assert.equal(row.title, expected, 'Displayed GDP must match the real API value')
      assert.ok(await evaluate(`Array.from(document.querySelectorAll('.metric [title]')).some(element => element.title === ${JSON.stringify(expected)})`), 'Headline GDP must match the real API value')
    }
  }
  pass('full catalog explorer preserves real API values and individual reference years')
  await select('#indicator-category', 'ECONOMY')
  await waitFor(`document.querySelector('#indicator-category')?.value === 'ECONOMY' && document.querySelectorAll('.data-table tbody tr').length === ${summaries.content.filter(item => item.indicator.category === 'ECONOMY').length}`, 'indicator category filter')
  assert.equal(await evaluate('document.querySelector("#history-title")?.textContent'), summaries.content.find(item => item.indicator.code === 'NY.GDP.MKTP.CD').indicator.name, 'Category filtering must preserve the selected history')
  await select('#indicator-category', '')
  await waitFor(`document.querySelectorAll('.data-table tbody tr').length === ${summaries.content.length}`)
  pass('indicator category filter updates the table without changing the history')
  await evaluate(`document.querySelector('.recharts-surface').focus()`)
  await call('Input.dispatchKeyEvent', { type: 'keyDown', key: 'ArrowRight', code: 'ArrowRight', windowsVirtualKeyCode: 39 })
  await call('Input.dispatchKeyEvent', { type: 'keyUp', key: 'ArrowRight', code: 'ArrowRight', windowsVirtualKeyCode: 39 })
  await waitFor(`(() => { const tooltip = document.querySelector('.recharts-tooltip-wrapper'); return tooltip && getComputedStyle(tooltip).visibility === 'visible' && /\\d{4}/.test(tooltip.textContent) && tooltip.textContent.includes('US$'); })()`, 'keyboard-accessible chart tooltip')
  pass('chart keyboard navigation exposes a year and formatted value')
  await noOverflow('desktop has no horizontal overflow')
  await screenshot('desktop-light')

  await navigate('/explorar?pais=&pagina=10000')
  await readyDashboard()
  assert.ok(await evaluate(`document.querySelector('article')?.getAttribute('aria-label').includes(${JSON.stringify(brazil.name)})`), 'An empty country parameter must fall back to Brazil')
  await waitFor(`Array.from(document.querySelectorAll('#country-search button')).some(element => element.textContent.trim() === 'Voltar à primeira página')`, 'out-of-range country page recovery')
  await click(`Array.from(document.querySelectorAll('#country-search button')).find(element => element.textContent.trim() === 'Voltar à primeira página')`)
  await waitFor(`!new URLSearchParams(location.search).has('pagina') && document.querySelectorAll('#country-search li').length > 0`)
  pass('empty country and out-of-range pagination recover to a usable workspace')

  await input('#country-query', brazil.name)
  await waitFor(`new URLSearchParams(location.search).get('q') === ${JSON.stringify(brazil.name)} && document.querySelectorAll('#country-search li').length === 1`, 'debounced country search')
  await click(`document.querySelector('#country-search li button')`)
  await waitFor(`new URLSearchParams(location.search).get('pais') === 'BRA'`)
  pass('country search and selection update the shared URL')
  await click(`Array.from(document.querySelectorAll('button')).find(element => element.textContent.trim() === 'Limpar filtros')`)
  await waitFor(`!new URLSearchParams(location.search).has('q') && document.querySelector('#country-query')?.value === '' && !document.querySelector('#country-region').disabled`)
  await select('#country-region', brazil.region)
  const regionFiltered = await api(`/countries?region=${encodeURIComponent(brazil.region)}&size=8`)
  await waitFor(`new URLSearchParams(location.search).get('regiao') === ${JSON.stringify(brazil.region)} && document.querySelector('#country-region')?.value === ${JSON.stringify(brazil.region)} && Number(document.querySelector('#country-search [role="status"]')?.textContent.trim().split(' ')[0]) === ${regionFiltered.totalElements}`, 'committed region filter and matching API count')
  await select('#country-income', brazil.incomeLevel)
  await waitFor(`new URLSearchParams(location.search).get('renda') === ${JSON.stringify(brazil.incomeLevel)} && document.querySelector('#country-income')?.value === ${JSON.stringify(brazil.incomeLevel)} && document.querySelector('#country-region')?.value === ${JSON.stringify(brazil.region)}`)
  const filtered = await api(`/countries?region=${encodeURIComponent(brazil.region)}&incomeLevel=${encodeURIComponent(brazil.incomeLevel)}&size=8`)
  await waitFor(`Number(document.querySelector('#country-search [role="status"]')?.textContent.trim().split(' ')[0]) === ${filtered.totalElements}`, 'filtered country count from API')
  pass('region and income filters match the API result count')
  await click(`Array.from(document.querySelectorAll('button')).find(element => element.textContent.trim() === 'Limpar filtros')`)
  const unfiltered = await api('/countries?size=8')
  await waitFor(`['q', 'regiao', 'renda', 'pagina'].every(key => !new URLSearchParams(location.search).has(key)) && Number(document.querySelector('#country-search [role="status"]')?.textContent.trim().split(' ')[0]) === ${unfiltered.totalElements} && !!document.querySelector('button[aria-label="Próxima página de países"]:not(:disabled)')`, 'complete unfiltered country page before pagination')
  await click(`document.querySelector('button[aria-label="Próxima página de países"]')`)
  await waitFor(`new URLSearchParams(location.search).get('pagina') === '1' && document.querySelectorAll('#country-search li').length > 0`)
  pass('country pagination is usable')

  const currentYear = new Date().getUTCFullYear()
  const internet = summaries.content.find(item => item.indicator.code === 'IT.NET.USER.ZS')
  assert.ok(internet, 'The documented internet series must be imported')
  const history = await api(`/countries/BRA/indicators/IT.NET.USER.ZS?seriesId=${encodeURIComponent(internet.series.id)}&from=2015&to=${currentYear}&size=100`)
  await navigate(`/paises/BRA?indicador=IT.NET.USER.ZS&de=2015&ate=${currentYear}`)
  await readyDashboard()
  await waitFor(`document.querySelector('#history-title')?.textContent === ${JSON.stringify(internet.indicator.name)}`)
  await click(`Array.from(document.querySelectorAll('summary')).find(element => element.textContent.includes('Ver dados em tabela'))`)
  const historyRows = await evaluate(`Array.from((${tableExpression}).tBodies[0].rows).map(row => ({ year: Number(row.cells[0].textContent), value: row.cells[1].textContent, status: row.cells[2].textContent }))`)
  assert.equal(historyRows.length, currentYear - 2015 + 1)
  for (const row of historyRows) {
    const observation = history.observations.content.find(item => item.period === row.year)
    if (!observation) { assert.equal(row.status, 'Ano sem registro'); assert.equal(row.value, '—') }
    else if (observation.status === 'MISSING') { assert.equal(row.status, 'Não informado pela fonte'); assert.equal(row.value, '—') }
    else assert.equal(row.status, 'Disponível')
  }
  pass('direct country URL renders history with missing and absent years preserved')
  for (const years of [10, 20, null]) {
    const first = years ? currentYear - years + 1 : 1960
    await click(`Array.from(document.querySelectorAll('button')).find(element => element.textContent.trim() === ${JSON.stringify(years ? `${years} anos` : 'Desde 1960')})`)
    await waitFor(`document.querySelector('#period-from')?.value === '${first}' && new URLSearchParams(location.search).get('de') === '${first}' && !!(${tableExpression}) && (${tableExpression}).tBodies[0].rows.length === ${currentYear - first + 1}`, `complete ${years ?? 'all'}-year history`)
  }
  pass('historical period presets update complete chart and table intervals')
  await input('#period-from', '2024')
  await input('#period-to', '2024')
  await click(`Array.from(document.querySelectorAll('button')).find(element => element.textContent.trim() === 'Aplicar')`)
  await waitFor(`new URLSearchParams(location.search).get('de') === '2024' && new URLSearchParams(location.search).get('ate') === '2024' && !!(${tableExpression}) && (${tableExpression}).tBodies[0].rows.length === 1`)
  pass('custom single-year period renders without an exception')
  await click(`document.querySelector('button[aria-label*="modo"]')`)
  await pause(100)
  const theme = await evaluate('localStorage.getItem("atlas-theme")')
  assert.ok(theme === 'light' || theme === 'dark')
  await loadDocument('Page.reload')
  await readyDashboard()
  assert.equal(await evaluate('document.documentElement.classList.contains("dark")'), theme === 'dark')
  pass('theme preference survives reload')
  await screenshot('country-theme')

  interceptionMode = 'pause'
  await call('Fetch.enable', { patterns: [{ urlPattern: `${base.origin}/api/v1/countries/BRA/indicators/NY.GDP.MKTP.CD*`, requestStage: 'Request' }] })
  await navigate('/paises/BRA?de=2010&ate=2020')
  await waitFor(`document.body.innerText.includes('Carregando o histórico completo')`, 'pending historical query')
  assert.equal(await evaluate('document.querySelectorAll(".recharts-surface").length'), 0, 'Loading must not display a stale history chart')
  assert.ok(pausedRequests.length > 0, 'The historical request must be paused by Chrome')
  pass('pending historical request displays loading without a stale chart')
  interceptionMode = 'fail'
  for (const requestId of pausedRequests.splice(0)) {
    try { await call('Fetch.failRequest', { requestId, errorReason: 'Failed' }) } catch { /* StrictMode may have cancelled an earlier request. */ }
  }
  await waitFor(`document.querySelector('[role="alert"]')?.textContent.includes('Não foi possível conectar ao serviço de dados')`, 'controlled history network error')
  assert.equal(await evaluate('document.querySelectorAll(".recharts-surface").length'), 0, 'A failed history must not display stale values')
  pass('failed historical request displays a recoverable network error')
  interceptionMode = null
  await call('Fetch.disable')
  await click(`document.querySelector('[role="alert"] button')`)
  await readyDashboard()
  pass('retry restores the historical chart after a network failure')

  const gdp = summaries.content.find(item => item.indicator.code === 'NY.GDP.MKTP.CD')
  assert.ok(gdp, 'The GDP series must be imported before comparing countries')
  const comparison = await api(`/indicators/NY.GDP.MKTP.CD/history?countries=BRA,USA&seriesId=${encodeURIComponent(gdp.series.id)}&from=2020&to=2024&size=100`)
  const availableByCountry = ['BRA', 'USA'].map(code => comparison.observations.content.filter(item => item.countryCode === code && item.status === 'AVAILABLE' && item.value !== null))
  const commonYear = availableByCountry[0].map(item => item.period).filter(period => availableByCountry[1].some(item => item.period === period)).sort((a, b) => b - a)[0]
  assert.ok(commonYear, 'The imported GDP series must have a common BRA/USA observation year for browser verification')
  await navigate(`/comparar?paises=BRA,USA&indicador=NY.GDP.MKTP.CD&serie=${encodeURIComponent(gdp.series.id)}&de=2020&ate=2024`)
  await waitFor(`!!document.querySelector('#comparison-reference-title') && !!document.querySelector('.recharts-surface')`, 'source-specific comparison and chart')
  assert.ok(await evaluate(`document.querySelector('#comparison-reference-title').parentElement.textContent.includes('${commonYear}')`), 'Comparison headline values must use a common reference year')
  const comparisonValues = await evaluate(`Array.from(document.querySelector('#comparison-reference-title').closest('section').querySelectorAll('dl > div')).map(item => ({ name: item.querySelector('dt').textContent, value: item.querySelector('dd[title]').title }))`)
  for (const country of comparison.countries) {
    const observation = comparison.observations.content.find(item => item.countryCode === country.iso3Code && item.period === commonYear)
    const expected = `US$ ${new Intl.NumberFormat('pt-BR', { maximumFractionDigits: 2 }).format(observation.value)}`
    assert.equal(comparisonValues.find(item => item.name === country.name)?.value, expected)
  }
  await click(`Array.from(document.querySelectorAll('summary')).find(element => element.textContent.includes('Ver comparação em tabela'))`)
  const comparisonRows = await evaluate(`Array.from(Array.from(document.querySelectorAll('table')).find(table => table.caption?.textContent.includes('Comparação anual')).tBodies[0].rows).map(row => ({ year: Number(row.cells[0].textContent), values: Array.from(row.cells).slice(1).map(cell => cell.querySelector('span')?.textContent) }))`)
  assert.equal(comparisonRows.length, 5)
  for (const row of comparisonRows) for (const [index, code] of ['BRA', 'USA'].entries()) {
    const observation = comparison.observations.content.find(item => item.countryCode === code && item.period === row.year)
    const expected = observation?.status === 'AVAILABLE' && observation.value !== null ? `US$ ${new Intl.NumberFormat('pt-BR', { maximumFractionDigits: 2 }).format(observation.value)}` : '—'
    assert.equal(row.values[index], expected)
  }
  pass('comparison uses one source series, aligns actual API values by year and identifies a common reference year')
  await screenshot('comparison-desktop')
  for (const [index, code] of ['CHN', 'JPN', 'FRA'].entries()) {
    await select('#comparison-country', code)
    await waitFor(`document.querySelectorAll('ul[aria-label="Países selecionados para comparação"] li').length === ${index + 3}`, 'country selection within the comparison limit')
  }
  assert.equal(await evaluate('document.querySelector("#comparison-country").disabled'), true)
  await waitFor(`!!document.querySelector('.recharts-surface') && document.querySelectorAll('ul[aria-label="Legenda dos países"] li').length === 5`, 'five-country comparison')
  pass('comparison supports up to five selected countries without mixing series')

  const gini = await api('/indicators/SI.POV.GINI')
  assert.ok(gini.series[0]?.comparison?.note, 'Gini must publish its comparability caveat')
  await navigate(`/comparar?paises=BRA,USA&indicador=SI.POV.GINI&serie=${encodeURIComponent(gini.series[0].id)}`)
  await waitFor(`document.querySelector('#comparison-title')?.textContent === ${JSON.stringify(gini.indicator.name)} && document.body.innerText.includes(${JSON.stringify(gini.series[0].comparison.note)})`, 'Gini methodology caveat')
  pass('Gini comparison identifies the income and consumption methodology caveat')

  const wages = await api('/indicators/OECD.WAGE.AVERAGE.PPP')
  const wageSeries = wages.series.find(series => /^USD_PPP_\d{4}_ANNUAL$/.test(series.unit))
  assert.ok(wageSeries, 'Import the documented OECD wage dataset before running browser checks')
  const usIndicators = await api('/countries/USA/indicators?size=100')
  const usWages = usIndicators.content.find(item => item.series.id === wageSeries.id)
  assert.ok(usWages?.latestAvailable, 'The US wage series must have an actual OECD observation')
  await navigate(`/paises/USA?indicador=OECD.WAGE.AVERAGE.PPP&serie=${encodeURIComponent(wageSeries.id)}`)
  await readyDashboard()
  const priceBase = /^USD_PPP_(\d{4})_ANNUAL$/.exec(wageSeries.unit)[1]
  const expectedWage = `US$ PPC ${new Intl.NumberFormat('pt-BR', { maximumFractionDigits: 2 }).format(usWages.latestAvailable.value)}`
  assert.ok(await evaluate(`document.querySelector('#history-title')?.textContent === ${JSON.stringify(wages.indicator.name)} && document.body.innerText.includes(${JSON.stringify(`preços constantes de ${priceBase}`)}) && Array.from(document.querySelectorAll('[title]')).some(element => element.title === ${JSON.stringify(expectedWage)}) && document.body.innerText.includes('OECD')`), 'Wage profile must identify the actual annual PPP value, provider and price base')
  assert.equal(usWages.latestAvailable.sourceUpdatedAt, null, 'The HTTP resource date must not become a statistical update date')
  pass('OECD wage profile preserves the real annual PPP value, source and reported price base')

  const nationalPoverty = await api('/indicators/SI.POV.NAHC')
  assert.equal(nationalPoverty.series[0]?.comparison?.allowed, false)
  await navigate(`/comparar?paises=BRA,USA&indicador=SI.POV.NAHC&serie=${encodeURIComponent(nationalPoverty.series[0].id)}`)
  await waitFor(`document.body.innerText.includes('A comparação entre países está indisponível devido à definição ou à cobertura da série')`, 'national poverty comparison restriction')
  assert.equal(await evaluate('document.querySelectorAll(".recharts-surface").length'), 0)
  assert.equal(requestedComparisons.some(request => request.includes('/indicators/SI.POV.NAHC/history')), false, 'Methodologically incompatible national poverty must not trigger a comparison history request')
  pass('national poverty comparison is blocked with its source methodology and no history request')

  const nationalSpecifications = [
    { code: 'IBGE.GINI.HOUSEHOLD.PER.CAPITA', source: 'IBGE', unit: /^GINI_COEFFICIENT$/ },
    { code: 'IBGE.HOUSEHOLD.INCOME.PER.CAPITA', source: 'IBGE', unit: /^BRL_REAL_\d{4}_MONTHLY_PER_PERSON$/ },
    { code: 'BCB.HOUSEHOLD.DEBT.DECEMBER', source: 'BCB', unit: /^PERCENT_HOUSEHOLD_RNDBF_12M$/ },
  ]
  for (const specification of nationalSpecifications) {
    const definition = await api(`/indicators/${specification.code}`)
    const series = definition.series.filter(item => specification.unit.test(item.unit))
      .sort((first, second) => second.unit.localeCompare(first.unit))[0]
    assert.ok(series, `Import the documented ${specification.source} dataset before running browser checks`)
    const entry = summaries.content.find(item => item.series.id === series.id)
    assert.ok(entry?.latestAvailable, `Missing actual Brazil observation: ${specification.code}`)
    assert.equal(series.comparison.allowed, false)
    assert.equal(series.source.code, specification.source)
    assert.equal(entry.latestAvailable.sourceUpdatedAt, null, 'Catalog metadata dates must not become statistical revision dates')
    const digits = series.unit === 'GINI_COEFFICIENT' ? 3 : 2
    const amount = new Intl.NumberFormat('pt-BR', { maximumFractionDigits: digits, minimumFractionDigits: digits === 3 ? 3 : 0 }).format(entry.latestAvailable.value)
    const expected = series.unit === 'GINI_COEFFICIENT' ? amount : series.unit.startsWith('BRL_REAL_') ? `R$ ${amount}` : `${amount}%`
    await navigate(`/paises/BRA?indicador=${specification.code}&serie=${encodeURIComponent(series.id)}`)
    await readyDashboard()
    await waitFor(`document.querySelector('#history-title')?.textContent === ${JSON.stringify(definition.indicator.name)}`, 'selected national history')
    assert.ok(await evaluate(`Array.from(document.querySelectorAll('.data-table tbody tr')).some(row => row.querySelector('button')?.textContent === ${JSON.stringify(definition.indicator.name)} && row.querySelector('td[title]')?.title === ${JSON.stringify(expected)})`), 'National value must match the stored official observation without changing its scale')
    assert.ok(await evaluate(`document.body.innerText.includes(${JSON.stringify(series.comparison.note)})`), 'National coverage must remain visible in the profile')
    if (series.unit.startsWith('BRL_REAL_')) {
      const priceBase = /^BRL_REAL_(\d{4})_/.exec(series.unit)[1]
      assert.ok(await evaluate(`document.body.innerText.includes(${JSON.stringify(`preços constantes de ${priceBase}`)})`))
    }
    if (specification.source === 'BCB') {
      assert.ok(entry.latestAvailable.period < currentYear, 'December-only observations must not invent the current unfinished year')
      assert.match(definition.indicator.name, /dezembro/i)
    }
    pass(`national ${specification.code} history preserves the real value, scale, year, source and coverage`)
  }
  const nationalGini = await api('/indicators/IBGE.GINI.HOUSEHOLD.PER.CAPITA')
  await navigate(`/comparar?paises=BRA,USA&indicador=${nationalGini.indicator.code}&serie=${encodeURIComponent(nationalGini.series[0].id)}`)
  await waitFor(`document.body.innerText.includes('A comparação entre países está indisponível devido à definição ou à cobertura da série')`, 'national source comparison restriction')
  assert.equal(await evaluate('document.querySelectorAll(".recharts-surface").length'), 0)
  assert.ok(await evaluate(`Array.from(document.querySelectorAll('a')).some(link => link.textContent.includes('Consultar Brasil') && new URL(link.href).pathname === '/paises/BRA')`))
  assert.equal(requestedComparisons.some(request => request.includes('/indicators/IBGE.GINI.HOUSEHOLD.PER.CAPITA/history')), false)
  pass('national source comparison explains Brazil-only coverage and offers its individual profile without requesting a comparison')
  await navigate(`/comparar?paises=BRA,TWN&indicador=NY.GDP.MKTP.CD&serie=${encodeURIComponent(gdp.series.id)}`)
  await waitFor(`document.querySelector('#comparison-reference-title') && document.body.innerText.includes('Sem valor no período')`, 'country without source observations in a comparison')
  const taiwanComparison = await evaluate(`Array.from(document.querySelector('#comparison-reference-title').closest('section').querySelectorAll('dl > div')).map(item => ({ name: item.querySelector('dt').textContent, value: item.querySelector('dd[title]').textContent }))`)
  assert.equal(taiwanComparison.find(item => item.name.includes('Taiwan'))?.value, '—')
  pass('an unavailable Taiwan series remains missing in comparison rather than becoming zero')

  await navigate('/paises/BRA?de=2020&ate=2024')
  await readyDashboard()
  const countryCsv = await downloadCsv()
  assert.equal(countryCsv.length, 5)
  assert.ok(countryCsv.every(row => row.country_code === 'BRA' && row.indicator_code === 'NY.GDP.MKTP.CD' && row.source_code === 'WORLD_BANK'))
  pass('country CSV download preserves every observation, selected years, unit and source')
  await navigate('/comparar?paises=BRA,USA&de=2020&ate=2024')
  await waitFor(`!!document.querySelector('#comparison-reference-title') && !!document.querySelector('.recharts-surface')`)
  const comparisonCsv = await downloadCsv()
  assert.equal(comparisonCsv.length, comparison.observations.content.length)
  for (const row of comparisonCsv) {
    const original = comparison.observations.content.find(item => item.countryCode === row.country_code && item.period === Number(row.period))
    assert.ok(original)
    assert.equal(row.value === '' ? null : Number(row.value), original.value)
  }
  pass('comparison CSV matches the actual API across both countries without mixing reference years')

  const ranking = await api(`/indicators/NY.GDP.MKTP.CD/ranking?seriesId=${encodeURIComponent(gdp.series.id)}&direction=desc&size=20`)
  await navigate('/rankings')
  await waitFor(`document.querySelectorAll('#ranking-table tbody tr').length === ${ranking.ranking.content.length}`, 'ranking table')
  assert.ok(await evaluate(`document.querySelector('#ranking-coverage').textContent.includes('Cobertura parcial') && document.querySelector('#ranking-coverage').textContent.includes('${ranking.coverage.withData} de ${ranking.coverage.totalCountries}')`))
  const ranked = await evaluate(`Array.from(document.querySelectorAll('#ranking-table tbody tr')).map(row => ({ rank: Number(row.querySelector('td').textContent), path: new URL(row.querySelector('a').href).pathname, value: row.querySelector('td[title]').title }))`)
  for (const [index, entry] of ranking.ranking.content.entries()) {
    assert.equal(ranked[index].rank, entry.rank)
    assert.equal(ranked[index].path, `/paises/${entry.country.iso3Code}`)
    assert.equal(ranked[index].value, `US$ ${new Intl.NumberFormat('pt-BR', { maximumFractionDigits: 2 }).format(entry.value)}`)
  }
  pass('ranking matches one source and reference year and explicitly identifies incomplete coverage')
  await screenshot('rankings-desktop')
  if (ranking.ranking.totalPages > 1) {
    await click(`document.querySelector('button[aria-label="Próxima página do ranking"]')`)
    await waitFor(`new URLSearchParams(location.search).get('pagina') === '1' && !!document.querySelector('#ranking-table tbody tr')`)
    const next = await api(`/indicators/NY.GDP.MKTP.CD/ranking?seriesId=${encodeURIComponent(gdp.series.id)}&direction=desc&size=20&page=1`)
    await waitFor(`Number(document.querySelector('#ranking-table tbody tr td')?.textContent) === ${next.ranking.content[0].rank}`)
    pass('ranking pagination preserves absolute positions across pages')
  }
  await select('#ranking-direction', 'asc')
  await waitFor(`new URLSearchParams(location.search).get('ordem') === 'asc' && !new URLSearchParams(location.search).has('pagina') && !!document.querySelector('#ranking-table tbody tr')`)
  const ascending = await api(`/indicators/NY.GDP.MKTP.CD/ranking?seriesId=${encodeURIComponent(gdp.series.id)}&direction=asc&size=20`)
  await waitFor(`document.querySelector('#ranking-table tbody tr a')?.getAttribute('href').includes('/paises/${ascending.ranking.content[0].country.iso3Code}')`)
  await select('#ranking-year', '2020')
  await waitFor(`new URLSearchParams(location.search).get('ano') === '2020' && document.querySelector('#ranking-coverage')?.textContent.includes('em 2020')`)
  pass('ranking direction and selected year update the query without carrying data from another year')
  const beforeBlockedRanking = requests.length
  await navigate('/rankings?indicador=IBGE.GINI.HOUSEHOLD.PER.CAPITA')
  await waitFor(`document.body.innerText.includes('Esta definição não permite ranking entre países')`)
  assert.equal(requests.slice(beforeBlockedRanking).some(request => request.includes('/IBGE.GINI.HOUSEHOLD.PER.CAPITA/ranking')), false)
  pass('national policy restrictions prevent misleading country rankings and their API requests')

  const imfDefinition = await api('/indicators/IMF.GOVERNMENT.DEBT.GDP')
  const imfSeries = imfDefinition.series.filter(item => item.source.code === 'IMF').sort((a, b) => b.datasetCode.localeCompare(a.datasetCode))[0]
  assert.ok(imfSeries, 'Import the documented IMF dataset before browser checks')
  const imfYear = new Date().getUTCFullYear()
  const imfHistory = await api(`/countries/BRA/indicators/IMF.GOVERNMENT.DEBT.GDP?seriesId=${imfSeries.id}&from=${imfYear - 2}&to=${imfYear}&size=100`)
  assert.ok(imfHistory.observations.content.some(row => row.providerStatus === 'IMF_PROJECTION'))
  await navigate(`/paises/BRA?indicador=IMF.GOVERNMENT.DEBT.GDP&serie=${imfSeries.id}&de=${imfYear - 2}&ate=${imfYear}`)
  await readyDashboard()
  await waitFor(`document.querySelector('#history-title')?.closest('section').textContent.includes('Projeção do FMI')`)
  await click(`Array.from(document.querySelectorAll('summary')).find(element => element.textContent.includes('Ver dados em tabela'))`)
  assert.ok(await evaluate(`${tableExpression}.textContent.includes('Projeção do FMI')`))
  const imfCsv = await downloadCsv()
  assert.ok(imfCsv.some(row => row.provider_status === 'IMF_PROJECTION' && Number(row.period) === imfYear))
  pass('IMF profiles, history tables and CSV distinguish official current-year projections from historical estimates')
  await screenshot('imf-country-desktop')
  await navigate(`/comparar?paises=BRA,USA&indicador=IMF.GOVERNMENT.DEBT.GDP&serie=${imfSeries.id}&de=${imfYear - 2}&ate=${imfYear}`)
  await waitFor(`document.querySelector('#comparison-reference-title')?.closest('section').textContent.includes('Projeção do FMI') && !!document.querySelector('.recharts-surface')`)
  await navigate(`/rankings?indicador=IMF.GOVERNMENT.DEBT.GDP&serie=${imfSeries.id}&ano=${imfYear}`)
  await waitFor(`document.querySelector('#ranking-table')?.textContent.includes('Projeção do FMI')`)
  pass('country comparisons and rankings visibly retain IMF projection classifications')
  await screenshot('imf-ranking-desktop')
  const taiwanImf = await api('/countries/TWN/indicators?source=IMF&size=100')
  assert.ok(taiwanImf.content.some(item => item.latestAvailable?.providerStatus === 'IMF_PROJECTION'))
  await navigate(`/paises/TWN?indicador=IMF.GOVERNMENT.DEBT.GDP&serie=${imfSeries.id}`)
  await readyDashboard()
  await waitFor(`document.querySelector('#history-title')?.closest('section').textContent.includes('Projeção do FMI')`)
  pass('Taiwan exposes native IMF coverage without inventing unavailable World Bank observations')

  const hdiDefinition = await api('/indicators/UNDP.HDI')
  const hdiSeries = hdiDefinition.series.filter(item => item.source.code === 'UNDP').sort((a, b) => b.datasetCode.localeCompare(a.datasetCode))[0]
  assert.ok(hdiSeries, 'Import the documented UNDP dataset before browser checks')
  const hdiHistory = await api(`/countries/BRA/indicators/UNDP.HDI?seriesId=${hdiSeries.id}&size=100`)
  const hdiLatest = hdiHistory.observations.content.filter(row => row.value !== null).sort((a, b) => b.period - a.period)[0]
  await navigate(`/paises/BRA?indicador=UNDP.HDI&serie=${hdiSeries.id}&de=${hdiLatest.period - 3}&ate=${hdiLatest.period}`)
  await readyDashboard()
  const hdiValue = new Intl.NumberFormat('pt-BR', { minimumFractionDigits: 3, maximumFractionDigits: 3 }).format(hdiLatest.value)
  await waitFor(`document.querySelector('#history-title')?.closest('section').textContent.includes(${JSON.stringify(hdiValue)})`)
  assert.ok(await evaluate(`document.querySelector('#history-title').closest('section').textContent.includes('índice (0–1)')`))
  const hdiCsv = await downloadCsv()
  assert.ok(hdiCsv.some(row => Number(row.period) === hdiLatest.period && Number(row.value) === hdiLatest.value && row.source_code === 'UNDP'))
  pass('UNDP indices preserve their actual reference year, 0–1 scale, three-decimal values and original CSV provenance')
  await screenshot('undp-country-desktop')

  const energyDefinition = await api('/indicators/EUROSTAT.RENEWABLE.ENERGY.SHARE')
  const energySeries = energyDefinition.series.find(item => item.source.code === 'EUROSTAT')
  assert.ok(energySeries, 'Import the documented Eurostat dataset before browser checks')
  const energyHistory = await api(`/countries/FRA/indicators/EUROSTAT.RENEWABLE.ENERGY.SHARE?seriesId=${energySeries.id}&size=100`)
  const energyLatest = energyHistory.observations.content.filter(row => row.value !== null).sort((a, b) => b.period - a.period)[0]
  await navigate(`/paises/FRA?indicador=EUROSTAT.RENEWABLE.ENERGY.SHARE&serie=${energySeries.id}&de=${energyLatest.period - 2}&ate=${energyLatest.period}`)
  await readyDashboard()
  const energyValue = `${new Intl.NumberFormat('pt-BR', { maximumFractionDigits: 3 }).format(energyLatest.value)}%`
  await waitFor(`document.querySelector('#history-title')?.closest('section').textContent.includes(${JSON.stringify(energyValue)})`)
  assert.ok(await evaluate(`document.querySelector('#history-title').closest('section').textContent.includes('Provisório')`))
  const energyCsv = await downloadCsv()
  assert.ok(energyCsv.some(row => Number(row.period) === energyLatest.period && Number(row.value) === energyLatest.value && row.provider_status === energyLatest.providerStatus))
  pass('Eurostat renewable-energy values retain native precision and provisional flags in profiles and CSV')
  await screenshot('eurostat-country-desktop')

  const policy = await api('/policy-rates?countries=BRA,USA')
  assert.ok(policy.countries.every(item => item.latestAvailable), 'Import the official policy-rate data before browser checks')
  const rateTo = policy.asOf
  const rateFrom = `${Number(rateTo.slice(0, 4)) - 1}${rateTo.slice(4)}`
  await navigate(`/juros?paises=BRA,USA&de=${rateFrom}&ate=${rateTo}`)
  await waitFor(`document.querySelectorAll('#rate-latest [data-rate-country]').length === 2 && !!document.querySelector('.recharts-surface')`, 'dated policy rates and chart')
  for (const item of policy.countries) {
    const expected = `${new Intl.NumberFormat('pt-BR', { maximumFractionDigits: 6 }).format(item.latestAvailable.value)}%`
    assert.ok(await evaluate(`document.querySelector('#rate-latest [data-rate-country="${item.country.iso3Code}"] [title]')?.title === ${JSON.stringify(expected)}`))
  }
  await screenshot('policy-rates-desktop')
  await click(`Array.from(document.querySelectorAll('summary')).find(element => element.textContent.includes('Ver taxas em tabela'))`)
  await waitFor(`!!document.querySelector('#rate-history-table tbody tr')`)
  const rateHistory = await api(`/policy-rates/history?countries=BRA,USA&from=${rateFrom}&to=${rateTo}`)
  const rateCsv = await downloadCsv()
  assert.equal(rateCsv.length, rateHistory.observations.length)
  assert.ok(rateCsv.every(row => row.frequency === 'DAILY' && /^\d{4}-\d{2}-\d{2}$/.test(row.period)))
  pass('policy rates retain actual daily dates and source-specific latest values in charts, tables and CSV')
  await click(`Array.from(document.querySelectorAll('button')).find(button => button.textContent.trim() === '5 anos')`)
  await waitFor(`new URLSearchParams(location.search).get('de')?.startsWith('${Number(rateTo.slice(0, 4)) - 5}') && !!document.querySelector('.recharts-surface')`)
  pass('policy-rate period filters retrieve a dated multi-year history')
  await navigate('/juros?paises=SGP')
  await waitFor(`document.querySelector('#rate-latest')?.textContent.includes('câmbio')`)
  assert.equal(await evaluate('document.querySelector("#rate-latest [title]").title'), '—')
  pass('a jurisdiction without a comparable interest-rate target remains unavailable rather than showing a market proxy')
  await navigate('/juros?paises=UKR')
  await waitFor(`document.querySelector('#rate-latest')?.textContent.includes('National Bank of Ukraine')`)
  const ukraineRate = await api('/policy-rates?countries=UKR')
  assert.equal(ukraineRate.countries[0].series.source.code, 'NBU')
  assert.ok(await evaluate(`document.querySelector('#rate-latest')?.textContent.includes(${JSON.stringify(new Intl.NumberFormat('pt-BR', { maximumFractionDigits: 6 }).format(ukraineRate.countries[0].latestAvailable.value))})`))
  pass('the official NBU fallback exposes persisted Ukrainian rates and their source')
  await navigate('/juros?paises=ARG')
  await waitFor(`document.querySelector('#rate-latest')?.textContent.includes('encerrado em 2025')`)
  assert.ok(await evaluate(`document.querySelector('#rate-latest')?.textContent.includes('não representa uma taxa vigente')`))
  pass('discontinued Argentine history is explicitly distinguished from a current policy rate')

  const mapRequestStart = requests.length
  await navigate('/mapa?pais=USA')
  await waitFor(`!!document.querySelector('path[data-country="BRA"]') && document.querySelector('#map-country-title')?.textContent`, 'interactive world map')
  await click(`document.querySelector('path[data-country="BRA"]')`)
  await waitFor(`new URLSearchParams(location.search).get('pais') === 'BRA' && document.querySelector('#map-country-title')?.textContent === ${JSON.stringify(brazil.name)}`)
  const population = summaries.content.find(item => item.indicator.code === 'SP.POP.TOTL')
  const populationText = `${new Intl.NumberFormat('pt-BR', { maximumFractionDigits: 0 }).format(population.latestAvailable.value)} pessoas`
  await waitFor(`document.querySelector('[data-map-indicator="SP.POP.TOTL"] dd[title]')?.title === ${JSON.stringify(populationText)}`)
  pass('world map polygon selection shows real country indicators with reference years and profile access')
  await evaluate(`document.querySelector('path[data-country="CAN"]').focus()`)
  await call('Input.dispatchKeyEvent', { type: 'keyDown', key: 'Enter', code: 'Enter', windowsVirtualKeyCode: 13 })
  await call('Input.dispatchKeyEvent', { type: 'keyUp', key: 'Enter', code: 'Enter', windowsVirtualKeyCode: 13 })
  await waitFor(`new URLSearchParams(location.search).get('pais') === 'CAN'`)
  const shape = await evaluate(`document.querySelector('path[data-country="BRA"]').getAttribute('d')`)
  await click(`document.querySelector('.leaflet-control-zoom-in')`)
  await waitFor(`document.querySelector('path[data-country="BRA"]').getAttribute('d') !== ${JSON.stringify(shape)}`, 'map zoom')
  await click(`Array.from(document.querySelectorAll('button')).find(button => button.textContent.trim() === 'Visão mundial')`)
  pass('world map supports keyboard country selection, zoom controls and world reset')
  await select('#map-country', 'HKG')
  await waitFor(`new URLSearchParams(location.search).get('pais') === 'HKG' && document.body.innerText.includes('não possui uma área individual')`)
  await select('#map-country', 'TWN')
  await waitFor(`new URLSearchParams(location.search).get('pais') === 'TWN' && document.querySelector('[data-map-indicator="NY.GDP.MKTP.CD"] dd[title]')?.title === '—'`)
  assert.equal(requests.slice(mapRequestStart).filter(request => /^https?:/.test(request) && new URL(request).origin !== base.origin).length, 0)
  pass('small territories remain selectable without invented map values or external tile requests')
  await screenshot('world-map-desktop')

  for (const width of [768, 390, 320]) {
    await call('Emulation.setDeviceMetricsOverride', { width, height: 844, deviceScaleFactor: 1, mobile: true })
    await navigate('/')
    await waitFor(`document.querySelectorAll('#featured-countries a[data-country]').length === 25`, 'responsive curated country grid')
    await noOverflow(`homepage ${width}px has no horizontal overflow`)
    await screenshot(`home-${width}`)
    await navigate('/comparar?paises=BRA,USA&de=2020&ate=2024')
    await waitFor(`!!document.querySelector('#comparison-reference-title') && !!document.querySelector('.recharts-surface')`, 'responsive comparison')
    await noOverflow(`comparison ${width}px has no horizontal overflow`)
    await navigate('/rankings')
    await waitFor(`!!document.querySelector('#ranking-table tbody tr')`)
    await noOverflow(`ranking ${width}px has no horizontal overflow`)
    await screenshot(`rankings-${width}`)
    await navigate(`/rankings?indicador=IMF.GOVERNMENT.DEBT.GDP&serie=${imfSeries.id}&ano=${imfYear}`)
    await waitFor(`document.querySelector('#ranking-table')?.textContent.includes('Projeção do FMI')`)
    await noOverflow(`IMF projection ranking ${width}px has no horizontal overflow`)
    await screenshot(`imf-ranking-${width}`)
    await navigate(`/paises/BRA?indicador=UNDP.HDI&serie=${hdiSeries.id}`)
    await readyDashboard()
    await noOverflow(`UNDP index profile ${width}px has no horizontal overflow`)
    await screenshot(`undp-country-${width}`)
    await navigate('/mapa')
    await waitFor(`!!document.querySelector('path[data-country="BRA"]')`)
    await noOverflow(`world map ${width}px has no horizontal overflow`)
    await screenshot(`world-map-${width}`)
    await navigate('/juros?paises=BRA,USA')
    await waitFor(`document.querySelectorAll('#rate-latest [data-rate-country]').length === 2 && !!document.querySelector('.recharts-surface')`)
    await noOverflow(`policy rates ${width}px has no horizontal overflow`)
    await screenshot(`policy-rates-${width}`)
    await navigate('/explorar')
    await readyDashboard()
    await noOverflow(`mobile ${width}px has no horizontal overflow`)
    await screenshot(`mobile-${width}`)
    await click(`document.querySelector('button[aria-controls="country-search"]')`)
    await input('#country-query', brazil.name)
    await waitFor(`document.querySelectorAll('#country-search li').length === 1 && new URLSearchParams(location.search).get('q') === ${JSON.stringify(brazil.name)}`)
    await click(`document.querySelector('#country-search li button')`)
    assert.equal(await evaluate('document.activeElement?.getAttribute("aria-controls")'), 'country-search', 'Focus should remain on a visible control after mobile country selection')
    pass(`mobile ${width}px country search preserves keyboard focus`)
  }

  await navigate('/paises/AFG')
  await waitFor(`document.querySelector('article') && document.body.innerText.includes('Nenhum valor disponível')`, 'known country without observations')
  assert.equal(await evaluate(`Array.from(document.querySelectorAll('.data-table tbody td:first-of-type')).every(cell => cell.textContent === '—')`), true)
  pass('known country without imported observations does not invent zeros')
  await navigate('/paises/ZZZ')
  await waitFor(`document.querySelector('[role="alert"]')?.textContent.includes('não foi encontrado')`, 'unknown country error')
  assert.equal(await evaluate('document.querySelectorAll(".metric").length'), 0)
  pass('unknown country displays a recoverable error without stale values')
  await navigate('/does-not-exist')
  await waitFor(`document.querySelector('h1')?.textContent.includes('Página não encontrada')`)
  pass('unknown route uses the application fallback')
  assert.equal(exceptions.length, 0, `JavaScript exceptions: ${JSON.stringify(exceptions)}`)
  assert.equal(cspErrors.length, 0, `Content policy violations: ${JSON.stringify(cspErrors)}`)
  assert.equal(unexpectedHttpErrors.length, 0, `Unexpected API errors: ${JSON.stringify(unexpectedHttpErrors)}`)
  pass('no JavaScript exceptions, CSP violations or unexpected API errors')
  console.log(`\n${passes.length} browser checks passed against ${base.origin}.`)
} catch (error) {
  console.error(`FAIL ${lastPage || 'browser startup'}: ${error.message}`)
  if (screenshotDirectory && call) {
    try {
      const shot = await call('Page.captureScreenshot', { format: 'png', captureBeyondViewport: true })
      writeFileSync(join(screenshotDirectory, 'failure.png'), Buffer.from(shot.data, 'base64'))
    } catch { /* Preserve the original test failure. */ }
  }
  process.exitCode = 1
} finally {
  const exited = browser.exitCode !== null || browser.signalCode !== null
    ? Promise.resolve() : new Promise(resolve => browser.once('exit', resolve))
  if (websocket?.readyState === WebSocket.OPEN && call) {
    try { await call('Browser.close') } catch { /* A clean browser shutdown may close DevTools before replying. */ }
  }
  websocket?.close()
  await Promise.race([exited, pause(3000)])
  if (browser.exitCode === null && browser.signalCode === null) {
    browser.kill('SIGTERM')
    await Promise.race([exited, pause(3000)])
    if (browser.exitCode === null && browser.signalCode === null) { browser.kill('SIGKILL'); await Promise.race([exited, pause(1000)]) }
  }
  try { await rm(profile, { recursive: true, force: true, maxRetries: 10, retryDelay: 200 }) }
  catch (error) {
    console.error(`Chrome profile cleanup failed: ${error.message}`)
    process.exitCode = 1
  }
}
