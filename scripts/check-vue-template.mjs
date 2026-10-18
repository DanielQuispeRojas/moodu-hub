#!/usr/bin/env node
/**
 * check-vue-template.mjs — el panel admin/cliente del Hub es un HTML con Vue 3 CDN y un
 * `setup()` EXPLICITO: en el template solo se ve lo que el `return` del setup expone.
 * Un identificador declarado pero NO expuesto se resuelve como `_ctx.nombre` y al
 * invocarlo revienta con "nombre is not a function" -> Vue aborta el render de esa lista
 * y la pantalla "desaparece" (le paso al usuario: la tabla se borraba a los segundos).
 *
 * Aqui se revisa eso: identificadores que el template usa y el return NO expone.
 * Uso: node scripts/check-vue-template.mjs [archivo.html ...]
 *      (sin argumentos: admin-web/index.html y cliente-web/index.html)
 *
 * Reglas de evasion de falsos positivos:
 *  - se descartan palabras clave, metodos globales de JS y los prefijos de Vue ($event, $el...).
 *  - se descartan las propiedades (precedidas de `.`) y los alias de v-for/v-slot declarados
 *    en la propia plantilla (por ejemplo `p in packs` -> `p` no viene del setup).
 *  - se descartan los PARAMETROS de las arrow functions escritas dentro de una expresion
 *    (por ejemplo `x=>planesDePack(x).length` -> `x` es local).
 */
import { readFileSync, existsSync } from 'node:fs'
import { resolve, dirname } from 'node:path'
import { fileURLToPath } from 'node:url'

const aqui = dirname(fileURLToPath(import.meta.url))
const raiz = resolve(aqui, '..')

const RESERVADAS = new Set(`
  if else for while do return typeof instanceof in of new delete void this null undefined true false
  function const let var class extends super import export from as await async try catch finally throw
  switch case default break continue do yield static get set with debugger
`.trim().split(/\s+/))

const GLOBALES = new Set(`
  Math JSON String Number Boolean Array Object Date RegExp Map Set Promise parseInt parseFloat isNaN
  confirm alert prompt window document navigator localStorage sessionStorage fetch console URL URLSearchParams
  atob btoa crypto structuredClone queueMicrotask
`.trim().split(/\s+/))

/** Todo lo que Vue expone por defecto ($event, $el, ...) + los helpers propios de Vue. */
const VUE = /^\$/

function textoTemplate(src) {
  // El template es lo que va ANTES del <script> que crea la app. Ojo: el primero del
  // documento es el CDN de Vue en el <head>, hay que buscar el bloque que CONTIENE createApp.
  let corte = -1
  for (const m of src.matchAll(/<script[^>]*>/g)) {
    const fin = src.indexOf('</script>', m.index)
    const cuerpo = src.slice(m.index, fin === -1 ? src.length : fin)
    if (cuerpo.includes('createApp')) { corte = m.index; break }
  }
  return corte === -1 ? src : src.slice(0, corte)
}

function bloquesScript(src) {
  return [...src.matchAll(/<script[^>]*>([\s\S]*?)<\/script>/g)].map(m => m[1])
}

/** Identificadores que el template usa en interpolaciones y en atributos de Vue. */
function usadosEnTemplate(tpl) {
  const limpio = tpl.replace(/<!--[\s\S]*?-->/g, ' ')
  const usos = new Map() // nombre -> linea (aprox)
  const meter = (nombre, linea) => {
    if (usos.has(nombre)) return
    usos.set(nombre, linea)
  }

  const lineaDe = (idx) => limpio.slice(0, idx).split('\n').length

  // atributos con expresion: v-*, :*, @*
  const reAt = /(?:\s|^)(v-[a-zA-Z0-9:_-]+|:[\w.$-]+|@[\w.$-]+)\s*=\s*"([^"]*)"/g
  for (const m of limpio.matchAll(reAt)) {
    const nombre = m[1]
    if (nombre === 'v-pre' || nombre === 'v-cloak') continue
    const expr = m[2]
    meter('@' + nombre + ' ' + expr.replace(/\s+/g, ' '), lineaDe(m.index))
    // alias declarados por la propia plantilla: v-for="x in y", v-for="(a,i) in y", v-slot="{a}"
    for (const a of expr.matchAll(/[({]?\s*([A-Za-z_$][\w$]*(?:\s*,\s*[A-Za-z_$][\w$]*)*)\s*\)?\s+(?:in|of)\s+/g)) {
      for (const p of a[1].split(',')) ALIAS.add(p.trim())
    }
    for (const a of expr.matchAll(/v-slot\s*=\s*"?\{([^}]*)\}/g)) {
      for (const p of a[1].split(',')) ALIAS.add(p.trim().split(':')[0].trim())
    }
    // parametros de arrow functions escritas dentro de la expresion
    for (const a of expr.matchAll(/\(([^()]*)\)\s*=>/g)) {
      for (const p of a[1].split(',')) ALIAS.add(p.trim())
    }
    for (const a of expr.matchAll(/([A-Za-z_$][\w$]*)\s*=>/g)) ALIAS.add(a[1])
    // identificadores libres de la expresion (SIN los textos: dentro de comillas no es codigo)
    const codigo = sinTextos(expr)
    for (const id of codigo.matchAll(/([A-Za-z_$][\w$]*)/g)) {
      const antes = codigo.slice(0, id.index)
      const previo = antes.trimEnd().slice(-1)
      if (previo === '.') continue                        // es propiedad
      if (/\b(let|const|var|function)\s+$/.test(antes)) continue
      // clave de objeto literal: `{active: expr}` o `c, background: x` -> no es codigo
      if ((previo === '{' || previo === ',') && /^\s*:/.test(codigo.slice(id.index + id[0].length))) continue
      meter(id[1], lineaDe(m.index) + ' (' + expr.trim().slice(0, 60) + ')')
    }
  }
  // interpolaciones: identificadores libres ({{ ... }})
  for (const m of limpio.matchAll(/\{\{([\s\S]*?)\}\}/g)) {
    const codigo = sinTextos(m[1])
    for (const id of codigo.matchAll(/([A-Za-z_$][\w$]*)/g)) {
      const antes = codigo.slice(0, id.index)
      const previo = antes.trimEnd().slice(-1)
      if (previo === '.') continue
      if ((previo === '{' || previo === ',') && /^\s*:/.test(codigo.slice(id.index + id[0].length))) continue
      meter(id[1], lineaDe(m.index) + ' (' + m[1].replace(/\s+/g, ' ').trim() + ')')
    }
  }
  return usos
}

/** Quita cadenas y template literals: su contenido es texto, no codigo. */
function sinTextos(s) {
  return s
    .replace(/`(?:\\.|\$\{[^}]*\}|[^`\\])*`/g, ' ')
    .replace(/'(?:\\.|[^'\\])*'/g, ' ')
    .replace(/"(?:\\.|[^"\\])*"/g, ' ')
}

/**
 * Devuelve el texto entre las llaves del objeto que empieza en `abre` (la llave ya abierta).
 * Cuenta llaves IGNORANDO comentarios, strings y template literals: contar a pelo se desboca
 * con el primer '{' dentro de un texto y devuelve un cuerpo gigante (falsa alarma).
 */
function cuerpoDeObjeto(s, abre) {
  let nivel = 1, i = abre + 1
  while (i < s.length && nivel > 0) {
    const c = s[i]
    if (c === '/' && s[i + 1] === '/') { i = s.indexOf('\n', i); if (i === -1) break; continue }
    if (c === '/' && s[i + 1] === '*') { const f = s.indexOf('*/', i); i = f === -1 ? s.length : f + 2; continue }
    if (c === "'" || c === '"') { i = saltarString(s, i, c); continue }
    if (c === '`') { i = saltarPlantilla(s, i); continue }
    if (c === '{') nivel++
    else if (c === '}') nivel--
    i++
  }
  return { cuerpo: s.slice(abre + 1, i - 1), fin: i }
}

function saltarString(s, i, comilla) {
  i++
  while (i < s.length) {
    if (s[i] === '\\') { i += 2; continue }
    if (s[i] === comilla) return i + 1
    i++
  }
  return i
}

/** Template literal: se salta hasta el cierre, respetando ${...} anidados. */
function saltarPlantilla(s, i) {
  i++
  while (i < s.length) {
    if (s[i] === '\\') { i += 2; continue }
    if (s[i] === '`' ) return i + 1
    if (s[i] === '$' && s[i + 1] === '{') {
      i += 2
      let nivel = 1
      while (i < s.length && nivel > 0) {
        const c = s[i]
        if (c === '{') nivel++
        else if (c === '}') nivel--
        else if (c === "'" || c === '"') { i = saltarString(s, i, c) - 1 }
        else if (c === '`') { i = saltarPlantilla(s, i) - 1 }
        i++
      }
      continue
    }
    i++
  }
  return i
}

/** Lo que el `return { ... }` del setup expone (+ props declaradas). */
function expuestos(src) {
  const expuestos = new Set()
  const scripts = bloquesScript(src)
  for (const s of scripts) {
    for (const p of s.matchAll(/props\s*:\s*\{([^}]*)\}/g)) {
      for (const n of p[1].split(',')) {
        const t = n.split(':')[0].trim()
        if (t) expuestos.add(t)
      }
    }
    // el return MAS grande (por llaves balanceadas) suele ser el del setup
    let mejor = null
    for (const m of s.matchAll(/return\s*\{/g)) {
      const r = cuerpoDeObjeto(s, m.index + m[0].length - 1)
      if (!mejor || r.cuerpo.length > mejor.cuerpo.length) mejor = r
    }
    if (!mejor) continue
    // fuera los comentarios de linea: un "// packs, de cupo" partiria la lista y taparia un nombre real
    const cuerpo = mejor.cuerpo.replace(/\/\/[^\n]*/g, '')
    for (const n of cuerpo.split(',')) {
      const t = n.trim().split(':')[0].trim()
      if (/^[A-Za-z_$][\w$]*$/.test(t)) expuestos.add(t)
    }
  }
  return expuestos
}

function revisar(archivo) {
  if (!existsSync(archivo)) { console.log(`   (no existe: ${archivo})`); return 0 }
  const src = readFileSync(archivo, 'utf8')
  const tpl = textoTemplate(src)
  const usados = usadosEnTemplate(tpl)
  const exp = expuestos(src)

  const faltan = []
  for (const [nombre, donde] of usados) {
    if (nombre.startsWith('@') || nombre.startsWith('__alias__')) continue
    if (RESERVADAS.has(nombre) || GLOBALES.has(nombre) || VUE.test(nombre)) continue
    if (ALIAS.has(nombre)) continue
    if (exp.has(nombre)) continue
    faltan.push([nombre, donde])
  }
  if (faltan.length === 0) {
    console.log(`   OK  ${archivo.replace(raiz + '\\', '')}: ${exp.size} expuestos, ninguno sin exponer`)
    return 0
  }
  console.log(`   FALLA ${archivo.replace(raiz + '\\', '')}: ${faltan.length} identificador(es) que el template usa y el setup NO expone`)
  for (const [n, d] of faltan) console.log(`      - ${n}   [linea ${d}]`)
  return faltan.length
}

let ALIAS = new Set()
const args = process.argv.slice(2)
const archivos = args.length ? args : ['admin-web/index.html', 'cliente-web/index.html']
let total = 0
console.log('--- barrido del template Vue (identificadores no expuestos por el setup)')
for (const a of archivos) {
  ALIAS = new Set()
  total += revisar(resolve(raiz, a))
}
if (total > 0) {
  console.log(`\n   ${total} problema(s). En Vue 3 con setup() explicito, lo que el template ve es SOLO el return.`)
  process.exitCode = 1
} else {
  console.log('--- sin problemas')
}
