#!/usr/bin/env node
/**
 * smoke-setup-admin.mjs — ejecuta el setup() del panel admin con stubs minimos de Vue y
 * comprueba (a) que no revienta con ReferenceError y (b) que devuelve TODO lo que el
 * template usa. Un identificador declarado pero no expuesto NO aparece aqui: por eso el
 * bug "X is not a function" solo se ve en el navegador (el build y node --check pasan).
 *
 * Uso: node scripts/smoke-setup-admin.mjs [admin-web/index.html]
 */
import { readFileSync } from 'node:fs'
import { resolve, dirname } from 'node:path'
import { fileURLToPath } from 'node:url'

const aqui = dirname(fileURLToPath(import.meta.url))
const raiz = resolve(aqui, '..')
const archivo = process.argv.slice(2)[0] || 'admin-web/index.html'
const src = readFileSync(resolve(raiz, archivo), 'utf8')

const bloques = [...src.matchAll(/<script[^>]*>([\s\S]*?)<\/script>/g)]
const js = bloques[bloques.length - 1][1]

// ── stubs de Vue: createApp no monta nada, solo guarda las opciones del componente.
let captured = null
const ref = (v) => ({ value: v, __ref: true })
const computed = (fn) => ({ get value() { return fn() } })
const Vue = {
  createApp(opts) {
    captured = opts
    return { mount() { return {} }, use() { return this }, component() {}, directive() {} }
  },
  ref, computed,
  watch() {}, onMounted() {}, onUnmounted() {}, onBeforeUnmount() {}, watchEffect() {}, nextTick() { return Promise.resolve() },
  reactive: (o) => o,
}
// stubs de navegador
const storage = { getItem: () => null, setItem() {}, removeItem() {} }
globalThis.Vue = Vue
globalThis.window = { location: { origin: 'http://localhost:9090' }, addEventListener() {}, matchMedia: () => ({ matches: false }) }
globalThis.document = { getElementById: () => null, querySelector: () => null, querySelectorAll: () => [] }
globalThis.localStorage = storage
globalThis.sessionStorage = storage
globalThis.location = window.location
globalThis.fetch = async () => ({ ok: true, status: 200, json: async () => ({ ok: true, mensaje: 'ok', payload: [] }) })

// El codigo del panel termina en `.mount('#app')`: basta con_NO_ montarlo.
const cuerpo = js.replace(/\.mount\(\s*['"]#app['"]\s*\)\s*;?\s*$/, '')

let exposed
try {
  // eslint-disable-next-line no-new-func
  new Function(cuerpo)()
  if (!captured || typeof captured.setup !== 'function') throw new Error('no se encontro setup() en el componente')
  exposed = captured.setup()
} catch (ex) {
  console.log('   FALLO al ejecutar el setup(): ' + ex.message)
  process.exitCode = 1
  throw ex
}

const nombres = Object.keys(exposed)
const faltan = nombres.length === 0
console.log(`   setup() ejecutado: ${nombres.length} valores expuestos (archivo ${archivo})`)
if (faltan) { console.log('   FALLO: el setup no devuelve nada'); process.exitCode = 1 }
