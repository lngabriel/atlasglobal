import assert from 'node:assert/strict'
import { spawn } from 'node:child_process'
import { mkdtempSync, mkdirSync, readFileSync, writeFileSync } from 'node:fs'
import { rm } from 'node:fs/promises'
import { tmpdir } from 'node:os'
import { join, resolve } from 'node:path'

// Run against a built application with the documented World Bank import loaded.
// Uses Chrome's DevTools protocol and Node's built-in APIs; no browser SDK needed.
const base = new URL(process.env.ATLAS_BASE_URL ?? 'http://127.0.0.1:3000')
assert.ok(['http:', 'https:'].includes(base.protocol), 'ATLAS_BASE_URL must use HTTP or HTTPS')
const screenshotDirectory = process.env.ATLAS_SCREENSHOT_DIR ? resolve(process.env.ATLAS_SCREENSHOT_DIR) : null
if (screenshotDirectory) mkdirSync(screenshotDirectory, { recursive: true })
const profile = mkdtempSync(join(tmpdir(), 'atlas-browser-check-'))
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
    throw new Error(`Timed out waiting for ${description}. Page: ${await evaluate('document.body.innerText.slice(0, 2000)')}`)
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
  async function noOverflow(name) {
    assert.ok(await evaluate('document.documentElement.scrollWidth <= innerWidth + 1'), `${name}: page must not overflow horizontally`)
    pass(name)
  }

  await call('Page.enable')
  await call('Runtime.enable')
  await call('Network.enable')
  await call('Network.setCacheDisabled', { cacheDisabled: true })
  await call('Log.enable')
  await call('Emulation.setDeviceMetricsOverride', { width: 1440, height: 1000, deviceScaleFactor: 1, mobile: false })
  const [brazil, summaries] = await Promise.all([api('/countries/BRA'), api('/countries/BRA/indicators?size=100')])
  assert.ok(summaries.content.some(item => item.latestAvailable), 'Import the documented World Bank dataset before running browser checks')
  await navigate('/')
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
  pass('homepage renders real API values and individual reference years')
  const usa = await api('/countries/USA')
  await waitFor(`document.querySelectorAll('nav[aria-label="Países em destaque"] button').length === 3`, 'featured countries from the real catalog')
  await click(`Array.from(document.querySelectorAll('nav[aria-label="Países em destaque"] button')).find(element => element.textContent === ${JSON.stringify(usa.name)})`)
  await waitFor(`new URLSearchParams(location.search).get('pais') === 'USA' && document.querySelector('article')?.getAttribute('aria-label').includes(${JSON.stringify(usa.name)})`, 'featured country selection')
  await readyDashboard()
  await click(`Array.from(document.querySelectorAll('nav[aria-label="Países em destaque"] button')).find(element => element.textContent === ${JSON.stringify(brazil.name)})`)
  await waitFor(`new URLSearchParams(location.search).get('pais') === 'BRA' && document.querySelector('article')?.getAttribute('aria-label').includes(${JSON.stringify(brazil.name)})`)
  await readyDashboard()
  pass('featured country links use catalog names and switch the dashboard')
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

  await navigate('/?pais=&pagina=10000')
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
  await waitFor(`!new URLSearchParams(location.search).has('regiao') && !!document.querySelector('button[aria-label="Próxima página de países"]:not(:disabled)')`)
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

  for (const width of [390, 320]) {
    await call('Emulation.setDeviceMetricsOverride', { width, height: 844, deviceScaleFactor: 1, mobile: true })
    await navigate('/')
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
