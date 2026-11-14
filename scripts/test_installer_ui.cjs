const {test} = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const html = fs.readFileSync('installer/index.html', 'utf8');
const scripts = [...html.matchAll(/<script>([\s\S]*?)<\/script>/g)].map(m => m[1]);
async function run(fw, release, fail = false, v = 'pc') {
  const activate = {disabled: true};
  const elements = {
    'install-btn': {querySelector: () => activate, setAttribute(k, value) {this[k] = value;}},
    'firmware-status': {}, 'stand-apk-ja': {}, 'stand-apk-en': {}, 'engine-link': {}
  };
  const loaded = [];
  const document = {
    documentElement: {dataset: {fw, v, lang: 'en'}},
    querySelectorAll: () => [], getElementById: id => elements[id],
    createElement: () => ({}), head: {appendChild: script => loaded.push(script)}
  };
  vm.runInNewContext(scripts.at(-1), {document, URLSearchParams, fetch: async () => {
    if (fail) throw Error('offline');
    return {ok: true, json: async () => release};
  }});
  await new Promise(resolve => setImmediate(resolve));
  return {activate, elements, loaded};
}
test('every firmware selects its manifest and waits for engine load', async () => {
  for (const fw of ['standalone', 'companion', 'headphone-stand']) {
    const r = await run(fw, {version: 'v1.1', firmware: {[fw]: true}});
    assert.equal(r.elements['install-btn'].manifest, `./manifest-${fw}.json`);
    assert.equal(r.activate.disabled, true);
    assert.equal(r.loaded.length, 1);
    r.loaded[0].onload();
    assert.equal(r.activate.disabled, false);
    assert.equal(r.elements['stand-apk-en'].href, 'https://github.com/Cespresso/CLumo/releases/tag/v1.1');
  }
});
test('unpublished firmware stays disabled without loading an engine', async () => {
  const r = await run('headphone-stand', {version: 'v1.0', firmware: {companion: true}});
  assert.equal(r.activate.disabled, true);
  assert.equal(r.loaded.length, 0);
  assert.match(r.elements['firmware-status'].textContent, /not published/);
});
test('network failure stays disabled and explains retry', async () => {
  const r = await run('headphone-stand', null, true);
  assert.equal(r.activate.disabled, true);
  assert.equal(r.loaded.length, 0);
  assert.match(r.elements['firmware-status'].textContent, /Reload/);
});
test('mobile engine error stays disabled', async () => {
  const r = await run('headphone-stand', {version: 'v1.1', firmware: {'headphone-stand': true}}, false, 'mobile');
  assert.match(r.loaded[0].src, /tasmota/);
  r.loaded[0].onerror();
  assert.equal(r.activate.disabled, true);
  assert.match(r.elements['firmware-status'].textContent, /Could not load/);
});
