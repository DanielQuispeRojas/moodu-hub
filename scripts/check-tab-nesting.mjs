// ─────────────────────────────────────────────────────────────────────────────
// check-tab-nesting.mjs — comprueba que los bloques de pestana del panel son HERMANOS
//
// Por que existe: el panel ya sufrio 3 veces la misma clase de fallo estructural
// (§11.446 `emitir`/`abrirCardex`, §11.452 `resumenDe`, y el `</div>` perdido que
// dejaba Valoraciones/Temas/Soporte dentro del bloque de Catalogo). Los dos fallos
// anteriores los caza `check-vue-template.mjs`, pero ese script revisa IDENTIFICADORES,
// no la ESTRUCTURA del DOM: un `</div>` de mas o de menos no lo detecta, y el sintoma
// es el peor posible -> "la pestana esta en blanco" sin error en consola.
//
// Que comprueba:
//   1) Todos los `v-if="tab==='...'"` deben tener la MISMA cadena de ancestros.
//      Si uno cuelga de otro (que es lo que hace un `</div>` sin cerrar), Vue lo oculta
//      entero: la pestana queda en blanco y no hay error que lo explique.
//   2) Balance de etiquetas del documento (delta 0). Un delta != 0 significa que hay
//      un cierre de mas o de menos; el "delta 1" que arrastraba el panel era ESTE bug.
//
// Uso:  node scripts/check-tab-nesting.mjs [archivo ...]
//       (sin argumentos: admin-web/ y su espejo en static/)
//
// REGLA (de §11.446): un checker que nunca falla no prueba nada -> reintroduce el bug
// en una copia y confirma que lo detecta antes de confiar en el verde.
// ─────────────────────────────────────────────────────────────────────────────
import fs from 'node:fs';
import path from 'node:path';

const RAIZ = path.resolve(path.dirname(new URL(import.meta.url).pathname).replace(/^\/([A-Za-z]:)/, '$1'), '..');
const POR_DEFECTO = [
  'admin-web/index.html',
  'src/main/resources/static/admin/index.html',
];
const archivos = process.argv.slice(2).length ? process.argv.slice(2) : POR_DEFECTO;

const SIN_CUERPO = new Set(['input', 'img', 'br', 'hr', 'meta', 'link', 'source',
  'area', 'base', 'col', 'embed', 'param', 'track', 'wbr', 'area']);

/** Quita comentarios y <script> para no contar sus etiquetas ni sus textos. */
function limpiar(html) {
  return html
    .replace(/<!--[\s\S]*?-->/g, '')
    .replace(/<script[\s\S]*?<\/script>/g, '');
}

/**
 * Recorre el HTML con una pila real. A diferencia de un regex ingenuo, respeta
 * las comillas de los atributos: un `>` dentro de `:disabled="a>b"` NO cierra la
 * etiqueta (ese fue el falso positivo que hacia inservible un chequeo previo).
 */
function analizar(s) {
  const pila = [];
  const bloques = [];   // v-if="tab==='...'"
  let abiertos = 0, cerrados = 0, i = 0;

  while (i < s.length) {
    const lt = s.indexOf('<', i);
    if (lt < 0) break;

    if (s[lt + 1] === '/') {                      // etiqueta de cierre
      const gt = s.indexOf('>', lt);
      if (gt < 0) break;
      const nombre = s.slice(lt + 2, gt).trim().toLowerCase();
      cerrados++;
      for (let k = pila.length - 1; k >= 0; k--) {
        if (pila[k] === nombre) { pila.length = k; break; }
      }
      i = gt + 1;
      continue;
    }

    const m = /^<([a-zA-Z][a-zA-Z0-9-]*)/.exec(s.slice(lt, lt + 40));
    if (!m) { i = lt + 1; continue; }             // <!DOCTYPE> y compañía
    const tag = m[1].toLowerCase();

    // fin de etiqueta respetando comillas
    let j = lt + 1 + m[1].length, q = null;
    while (j < s.length) {
      const c = s[j];
      if (q) { if (c === q) q = null; }
      else if (c === '"' || c === "'") q = c;
      else if (c === '>') break;
      j++;
    }
    const attrs = s.slice(lt, j + 1);
    const linea = s.slice(0, lt).split('\n').length;

    const cond = attrs.match(/v-if\s*=\s*["']\s*tab\s*===\s*['"]([A-Za-z0-9_]+)['"]/);
    if (cond) {
      bloques.push({ clave: cond[1], linea, cadena: pila.join('>') });
    }

    if (!/\/\s*>$/.test(attrs) && !SIN_CUERPO.has(tag)) {
      abiertos++;
      pila.push(tag);
    }
    i = j + 1;
  }
  return { bloques, delta: abiertos - cerrados, pila };
}

let fallos = 0;
console.log('--- estructura de pestanas del panel (bloques hermanos + balance) ---');

for (const rel of archivos) {
  const abs = path.resolve(RAIZ, rel);
  if (!fs.existsSync(abs)) {
    console.log(`   AVISO  ${rel}: no existe (omitido)`);
    continue;
  }
  const { bloques, delta } = analizar(limpiar(fs.readFileSync(abs, 'utf8')));
  const problemas = [];

  // 1) todos los bloques de pestana al mismo nivel
  if (bloques.length) {
    const ref = bloques[0];
    const colgados = bloques.filter(b => b.cadena !== ref.cadena);
    if (colgados.length) {
      for (const b of colgados) {
        problemas.push(
          `pestana "${b.clave}" (linea ${b.linea}) esta colgando de otro bloque ` +
          `(cadena ${b.cadena} en vez de ${ref.cadena}): se quedara EN BLANCO. ` +
          `Falta un </div> antes de ella.`);
      }
    }
  }

  // 2) balance del documento
  if (delta !== 0) {
    problemas.push(
      `documento desbalanceado (delta ${delta}): ${delta > 0
        ? 'sobra(n) ' + delta + ' apertura(s) sin cerrar ->'
        : 'sobra(n) ' + (-delta) + ' cierre(s) de mas ->'} el navegador anida el resto ` +
      `dentro de la etiqueta mal cerrada.`);
  }

  if (problemas.length) {
    console.log(`   FALLA  ${rel}`);
    problemas.forEach(p => console.log(`            - ${p}`));
    fallos++;
  } else {
    console.log(`   OK     ${rel}: ${bloques.length} pestanas al mismo nivel, balance 0`);
  }
}

console.log(fallos ? '--- se encontraron problemas estructurales ---' : '--- sin problemas ---');
process.exit(fallos ? 1 : 0);