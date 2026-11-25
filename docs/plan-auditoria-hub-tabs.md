# HUB-AUD-01 — Plan de auditoría tab por tab del MOODU Hub

> **Estado:** plan listo, ejecución pendiente de autorización (KANBAN `HUB-12`, `[ ]`).
> **Fecha de registro:** 2026-11-25 10:40 -05:00 (America/Lima).
> **Fuente:** pedido del usuario 2026-11-25 — *"revisa tab por tab, verifica que todo funcione,
> busca defectos, errores, conflictos, inconsistencias, bugs y errores visuales; sé minucioso,
> especialista en UX, ciberseguridad, inspector de integridad de datos, abogado y especialista
> en comercio; una vez analizado, no solo corrijas bugs sino plantéame mejoras"*.
> **Repo:** `moodu-hub/` (subrepo propio, remote `moodu-hub`, rama `main`).

---

## 0. Norma aplicable (por qué este plan y no "ir arreglando")

| Norma | Cómo se cumple aquí |
|---|---|
| **ISO/IEC/IEEE 29148** | El pedido se convierte en requisitos verificables **antes** de codificar; cada hallazgo lleva criterio de aceptación y caso de prueba. |
| **ISO/IEC/IEEE 12207** | Proceso de **verificación** (olas 0-4), **mantenimiento** (ola 5) y **mejora** (ola 6), con trazabilidad de qué actividad cubre cada tarea. |
| **ISO/IEC/IEEE 15288** | Se audita el **sistema completo**: Hub + panel admin + panel cliente + API pública + core que consume el espejo + BD de producción + hosting (Render/Neon). |
| **ISO/IEC/IEEE 25010** | Usabilidad (9241-110), seguridad, fiabilidad/integridad, mantenibilidad y adequately comercial como adecuación funcional. |
| **REQ-TC-01** | Los casos `TC-HUB-12-*` nacen **antes** de tocar código (`casos-prueba-kamban.md` §66). |
| **REQ-FECHAS-01 / REQ-ESTADO-01** | Registro ISO-8601 con zona; flujo `[ ] → [~] → [?] → [x]` con comprobación de la licenciada. |
| **REQ-LEGAL-01** | Si un cambio toca privacidad, servicio externo, estándar nuevo o flujo de negocio, se revisan los Términos **en la misma sesión**. |
| **§7 AGENTS.md** | **No se empaqueta JAR/.exe del core ni se regenera el instalador sin orden explícita.** El Hub se compila con su Dockerfile en Render; `target/*.jar` no se commitea. |
| **§11.51 AGENTS.md** | Toda edición de `admin-web/index.html` o `cliente-web/index.html` se re-copia a `src/main/resources/static/<x>/index.html` (Render sirve solo `src/`). Los scripts `check-vue-template.mjs`, `smoke-setup-admin.mjs` y `check-tab-nesting.mjs` son gate obligatorio. |

**Regla de oro del agente:** *no arreglar lo que no se puede demostrar*. Todo hallazgo de este plan tiene evidencia en código, en `curl` a producción o en la ejecución aislada. Lo que no se pudo verificar queda marcado como **A VERIFICAR**, no como bug.

---

## 1. Lentes de auditoría (los cinco roles)

| Lente | Preguntas que hace | Norma / referencia |
|---|---|---|
| **UX/UI** | ¿Se entiende de un vistazo? ¿Las columnas coinciden con sus cabeceras? ¿La moneda es la misma? ¿El modal se cierra con Escape? ¿Cabe en 390 px? | ISO 9241-110 (autodescripción, idoneidad), 25010 usabilidad |
| **Ciberseguridad** | ¿Quién puede llamar cada endpoint? ¿Basta un correo para desligar una máquina? ¿Hay CSP, rate-limit, secretos por defecto, SSRF, XSS en SVG? | OWASP Top 10, 25010 seguridad |
| **Integridad de datos** | ¿El precio que ve el admin es el que cobra la Tienda? ¿La versión publicada es válida? ¿Hay espejos desactualizados? ¿Los planes y el campo legacy divergen? | 25010 fiabilidad, contabilidad (NIC 2 / NIIF 15 donde aplique) |
| **Legal / Términos** | ¿Se.expone correo personal sin consentimiento? ¿El precio está en moneda clara? ¿Los Términos cubren lo que el Hub hace hoy? | Ley 29733 (datos personales), Ley 29571 (consumidor), REQ-LEGAL-01 |
| **Comercio** | ¿Lo que se vende se entrega? ¿GRATIS/PLUS/PRO están bien modelados? ¿Los add-ons contradicen "no hay permanente"? ¿Comprape está registrado? | Modelo de negocio aprobado (§11.91, §11.450, §11.517) |

---

## 2. Alcance

### 2.1 Panel admin — 7 pestañas (verificadas en código y en producción)
`Licencias` (`lic`) · `Paquetes` (`paq`) · `Dispositivos` (`disp`) · `Catálogo` (`cat`) · `Valoraciones` (`val`) · `Temas` (`temas`) · `Soporte` (`cfg`).

> El script `scripts/check-tab-nesting.mjs` confirma **7 pestañas hermanas y balance 0** en fuente y espejo.
> `scripts/check-vue-template.mjs` confirma **157 identificadores expuestos** en admin y **13** en cliente (0 sin exponer).

### 2.2 Panel cliente (2 estados)
Visitante: paquetes + login + registro. Logueado: licencia + máquinas (desligar/eliminar).

### 2.3 API
Pública (`/api/catalogo/**`, `/api/licencias/validar|estado|activar-gratuito`, `/api/dispositivos/**`, `/api/valoraciones`, `/api/cliente/**`) y privada (`/api/admin/**`, JWT).

### 2.4 Periférico
`application.properties` / `application-prod.properties`, `hub-config.json`, `HubParametro` (BD), `render.yaml`, `Dockerfile`, espejos `static/admin` y `static/cliente`, y el consumidor (core + desktop).

---

## 3. Estado del pre-vuelo (solo lectura, ya ejecutado 2026-11-25)

| Comprobación | Resultado |
|---|---|
| `/actuator/health` (Render) | `UP` |
| `/api/catalogo/modulos` | 10 módulos activos |
| `/api/catalogo/hub` | `normativasOrigen: EMBEBIDO`, `comprapeUrl: null`, mudanza inactiva |
| `/api/catalogo/version-sistema` | **`version: ""` con fecha** → hallazgo H-03 |
| `/api/valoraciones?destino=moodu` | 1 reseña, **correo completo expuesto** → H-05 |
| `/api/catalogo/contacto` | whatsapp/correo/telefono visibles; `redes:[]`, `portada:[]` |
| `/api/dispositivos/catalogo` | `packs:[]` (sin packs con precio) |
| `/admin/index.html` desplegado | 7 pestañas, **2.575 líneas** vs **2.803 locales** → AYU-02 (guías) aún sin push |
| Espejos fuente ↔ `static/` | AUDITAR en la ola 0 (el local tiene cambios sin commitear de AYU-02) |
| `check-vue-template` / `smoke-setup-admin` / `check-tab-nesting` | 0 hallazgos (gate verde en fuente) |
| Cabeceras HTTP | HSTS y `nosniff` presentes; **sin CSP** → H-07 |

---

## 4. Olas de ejecución

| Ola | Objetivo | Entorno | Salida |
|---|---|---|---|
| **0 — Pre-vuelo** | Confirmar inventario, espejos, secretos, propiedades y estado real de BD | Solo lectura (producción + repo) | Inventario cerrado; ningún dato tocado |
| **1 — Seguridad** | IDOR del panel cliente, CSP, rate-limit, JWT en rutas públicas, secretos, SVG | Hub propio en H2 aislada + `curl` + Playwright | Hallazgos H-02, H-07, H-08, H-15, H-16 confirmados/cerrados |
| **2 — Integridad de datos** | Versión vacía, divergencia de precios, plan PERMANENTE residual, espejos | H2 aislada + **BD de producción solo lectura** para contrastar | H-03, H-04, H-06, H-17 cerrados |
| **3 — UX tab por tab** | 7 pestañas en 1440×900, 768×1024 y 390×844; modales, foco, Escape, moneda | Playwright sobre Hub propio | H-01, H-09, H-11, H-12, H-13, H-14 cerrados |
| **4 — Legal / comercio** | PII en valoraciones, moneda, Términos del desktop, modelo GRATIS/PLUS/PRO y Comprape | Revisión de código + Términos en `ProfileView.vue` | H-05, H-10, H-18 + `REQ-LEGAL-01` resuelto |
| **5 — Correcciones** | Tests de regresión por hallazgo + `mvn clean test` + scripts de gate | Repo local | `OK` → `[?]` (re-prueba de la licenciada) |
| **6 — Mejoras** | MEJ-01..10 priorizadas por valor/coste | Repo local | Propuesta de siguiente KANBAN |

**Regla de entorno aislado:** todo smoke corre con JAR copiado a `/tmp/opencode/hub-audit-<fecha>/`, `cwd` propio, H2 propia y puerto propio (ej. `:9097`). **La BD de producción y el `:8080` del usuario no se tocan.** Matar el java propio por PID/`Win32_Process.CommandLine`, nunca por puerto (devuelve PID 0 fantasma).

---

## 5. Revisión tab por tab — checklist

### 5.1 Licencias (`lic`)
- [ ] Tabla: **9 cabeceras vs 10 celdas** (H-01). Verificar que "Clave" no se repita y que Modulos/Maq./Vigencia/Activa caigan en su columna.
- [ ] Alta: correo válido, clave autogenerada visible, `modulos[]` con claves canónicas, INDEFINIDO oculta cantidad.
- [ ] Edición: `terminoDe(clave)` debe devolver el objeto persistido (no uno nuevo por render) o el `v-model` no guarda. **A VERIFICAR con Playwright.**
- [ ] Plan TRIENAL usa `numeroAnios()` del Hub; PERMANENTE solo si el módulo es GRATIS (H-06).
- [ ] Comprape: plan/cupo/congelamiento y botón Guardar con validación real (H-10).
- [ ] Revocar: confirmación y efecto inmediato en `/validar`. **A VERIFICAR.**

### 5.2 Paquetes (`paq`)
- [ ] Alta/edición/eliminación; precio con `step="0.01"` (H-11).
- [ ] `modulosExtra` (texto libre) — mismo riesgo de clave inventada que ya se corrigió en Licencias; verificar normalización en backend.
- [ ] Paquete inactivo NO se ofrece al cliente.

### 5.3 Dispositivos (`disp`)
- [ ] Modos GRATIS/PLUS/PRO se deducen de `modulos[]`, no se editan aquí (texto correcto).
- [ ] `v-model.number="licSelId"` con `<option :value="null">`: verificar que el placeholder no se convierta en `0/NaN` y rompa el selector. **A VERIFICAR.**
- [ ] Asignar pack: si no hay packs con precio, el `<select>` queda vacío y `plan` puede enviarse `undefined`. **A VERIFICAR.**
- [ ] Desligar/Eliminar máquina: **H-02** (solo correo) — gatear en backend.
- [ ] "Permanente" en packs vs "no hay permanente" en módulos (H-18).

### 5.4 Catálogo (`cat`)
- [ ] Moneda `$` vs `S/` (H-09).
- [ ] Precio legacy vs `planes[]` divergente (H-04): mostrar aviso si no coinciden.
- [ ] `version-sistema`: validación semver y una sola activa (H-03).
- [ ] Guía (AYU-02): semilla por módulo, no sobrescribe edición del admin, 400 limpio con manual inválido. **Verificar tras push.**
- [ ] Fotos/icono: MIME, tope 10/1 MB/512 KB, SVG con script (H-07).
- [ ] Valoración por móduloVisible en tarjeta (ya implementado); verificar promedio con 0 reseñas.

### 5.5 Valoraciones (`val`)
- [ ] **H-05**: correo completo expuesto en API pública. Enmascarar en servidor.
- [ ] Ocultar/Mostrar/Eliminar; ocultar baja el promedio visible pero conserva la fila.
- [ ] Filtro por destino y orden por último cambio.

### 5.6 Temas (`temas`)
- [ ] Crear con JSON válido/inválido, clave duplicada con sufijo, BOM.
- [ ] `esFabrica` oculta de la Tienda; detalle y descarga 404 para los de fábrica.
- [ ] `temaMsg` como texto inline (H-13).
- [ ] La Tienda aplica temas del Hub sin pisar fondos/nav locales (contrato ya verificado en desktop).

### 5.7 Soporte (`cfg`)
- [ ] Medios de contacto, redes libres (≤12) y portada (≤8, 1.5 MB) con base64 en BD.
- [ ] Parámetros `NORMATIVAS_*`, `ANUAL_FACTOR`, `MAX_ANIOS_LICENCIA`, `COMPRAPE_URL`.
- [ ] **H-14**: texto "melhora" (portugués) en la ayuda.
- [ ] Vista previa pública sin base64; admin con base64 (contrato AYU/HUB-10).

### 5.8 Panel cliente y API pública
- [ ] Login/registro con BCrypt; **sin rate-limit** (H-08).
- [ ] `mis-dispositivos`/`desactivar`/`delete` con solo correo (H-02).
- [ ] `/api/licencias/validar` consume cupo; `/estado` es consulta pura (regla §11.64) — verificar que siga así.
- [ ] `/api/licencias/activar-gratuito`: solo módulos GRATIS y precio 0; sin rate-limit.
- [ ] `/core/trayectorias`, `/core/responsabilidades` del core consumen el espejo del Hub — **test de contrato** (MEJ-10).

---

## 6. Hallazgos confirmados (evidencia + corrección propuesta)

| ID | Sev | Lente | Hallazgo | Evidencia | Corrección |
|---|---|---|---|---|---|
| **H-01** | P0 | UX / Integridad | Tabla Licencias: 9 `<th>` vs 10 `<td>`. La columna "Modulos" repite la clave de activación; "Maq." muestra badges; "Vigencia" muestra el número; "Activa" muestra la vigencia; sobra una columna sin cabecera. | `admin-web/index.html:134-164`; contador `th=9 td=10` | Quitar el `<td>` duplicado; timeline de columnas 1:1. Test de balance `th/td` en el script de gate. |
| **H-02** | P0 | Seguridad / Integridad | `/api/cliente/mis-dispositivos`, `/maquinas/{id}/desactivar` y `DELETE /maquinas/{id}` se autorizan solo con `?correo=`. Cualquiera que conozca el correo puede desligar/borrar máquinas (DoS del cupo). | `ClienteController.java:79-117` | Exigir clave BCrypt o token de sesión cliente en **mutaciones**; idealmente también en lectura; rate-limit por IP. Caso `TC-HUB-12-SEG-*`. |
| **H-03** | P0 | Integridad / Comercio | `/api/catalogo/version-sistema` devuelve `version:""` en producción. No hay validación de formato ni control de "una sola activa". El desktop compara esa cadena. | `curl` producción; `CatalogoController.java:267-284`; `AdminGestionController.java:592-601` | Validar `^\d+\.\d+\.\d+$`, rechazar vacío, desactivar las demás al publicar, y devolver `0.0.0` si la fila activa es inválida (fail-safe). |
| **H-04** | P0 | Integridad / Comercio | `precio` legacy y `planes[]` divergen: `almacen` muestra S/3000 en el panel pero 120/1200 en la Tienda. | `curl /api/catalogo/modulos` | Una sola fuente: calcular el campo público desde `planes`, o eliminarlo; aviso en el panel si hay divergencia. |
| **H-05** | P1 | Legal / Privacidad | `/api/valoraciones` público expone el correo completo del valorador. El desktop lo enmascara solo en UI. | `curl` producción → `da12328.dq@gmail.com` | Enmascarar en servidor (`da***@dominio`) o documentar consentimiento explícito en Términos §8. |
| **H-06** | P1 | Comercio / Integridad | `moodu_plus` sigue con plan `PERMANENTE` (1200) pese a HUB-11 ("no hay permanente de pago"). | `curl` producción | Ejecutar/verificar el runner de conversión en BD real; rechazar PERMANENTE para planes de pago en admin y en service. |
| **H-07** | P1 | Seguridad | Sin `Content-Security-Policy` en `/admin` ni en API; `spring.h2.console.enabled=true` en el perfil por defecto. | `curl -I` producción; `application.properties` | CSP (`script-src 'self'`, `img-src data: https:`), `X-Frame-Options`, h2-console solo con perfil dev + flag explícito. |
| **H-08** | P1 | Seguridad | Sin rate-limit en `/api/admin/login`, `/api/licencias/validar`, `/activar-gratuito`, `/api/valoraciones` POST ni `/api/cliente/*`. | Revisión de filtros (solo `JwtAuthFilter`, sin throttle) | Throttle por IP+clave con 429; reutilizar el patrón del core (§11.526). |
| **H-09** | P1 | UX / Legal | Moneda inconsistente: Catálogo `$`; Paquetes y Dispositivos `S/`. | `admin-web/index.html:398, 522, 730` | Unificar a `S/` (o `USD` explícito), con `Intl.NumberFormat`. |
| **H-10** | P1 | Comercio | `comprapeUrl:null` y sin módulo `comprape` en el catálogo; la tarjeta Comprape del modal no tiene enlace. | `curl /api/catalogo/hub` y `/modulos` | Registrar el módulo y fijar `COMPRAPE_URL` como parámetro del Hub (pendiente del usuario). |
| **H-11** | P2 | UX | Inputs de precio sin `step="0.01"` en Licencias/Paquetes/precio legacy del Catálogo. | `admin-web/index.html` (3 sitios) | Añadir `step="0.01"` a todos los precios. |
| **H-12** | P2 | UX / Accesibilidad | Modales del panel con `class="modal d-block"` sin Escape, sin foco inicial y sin `aria-label` en botones solo-icono. | Revisión de los 7 bloques | Patrón de cierre por teclado + foco inicial + `aria-label`. |
| **H-13** | P2 | UX | `temaMsg`, `contactoMsg`, `manualMsg`, `fotoMsg` como texto inline poco visible. | Bloques Temas/Soporte/Catálogo | Alerta consistente o `AvisoFlotante` (patrón §11.326 del desktop). |
| **H-14** | P2 | UX / Marca | Texto "melhora" (portugués) en Soporte; nombre comercial `Recepcion_taller` con guion bajo. | `admin-web/index.html:1219`, `curl` catálogo | Corrección de texto y nombre legible, sin tocar claves técnicas. |
| **H-15** | P2 | Seguridad | `JwtAuthFilter` del Hub responde 401 con token inválido **incluso en rutas públicas** (el core ya lo corrigió en §11.230). | `config/JwtAuthFilter.java` (sin `RUTAS_PUBLICAS`) | Espejo de la solución del core: token inválido en pública = anónimo. |
| **H-16** | P2 | Seguridad | Cliente axios del Hub en el desktop fija `Content-Type: application/json` por defecto (misma clase de bug de §11.230). | `ElectronVue/src/api/licencias.js:26` | Quitar el header default; axios lo infiere para FormData. |
| **H-17** | P2 | Despliegue | `tieneManual` no aparece en el Hub desplegado (AYU-02 sin push); admin local 2.803 líneas vs 2.575 desplegadas. | `curl` + `wc -l` | Condición de cierre: push del subrepo cuando se autorice. No es bug de código. |
| **H-18** | P2 | Comercio | Packs siguen ofreciendo "Permanente" mientras los módulos ya no lo ofrecen (HUB-11). | `admin-web/index.html` bloque Dispositivos | Confirmar si es decisión deliberada (add-on) o alinear a TRIENAL. |

> **No se ha tocado código todavía.** Estos hallazgos son el resultado del pre-vuelo; H-01 a H-04 se consideran **bloqueantes** para dar por buena la pestaña correspondiente.

---

## 7. Mejoras propuestas (no son bugs)

| ID | Mejora | Valor de negocio | Coste | Prioridad |
|---|---|---|---|---|
| **MEJ-01** | Card **"Salud comercial"** en el panel: detecta precio legacy≠planes, planes sin precio, módulo sin guía/foto, licencia vencida con módulos activos y packs sin oferta. | Un vistazo antes de publicar; evita inconsistencias como H-04 | B | P1 |
| **MEJ-02** | **Auditoría del Hub**: tabla `hub_auditoria` con quién cambió licencia, plan, precio, clave y dispositivo (hoy solo audita el core). | Trazabilidad para soporte y disputas comerciales | M | P1 |
| **MEJ-03** | **Exportar/importar licencias** (JSON ofuscado) para mudanza de hosting, con preview y verificación post-import. | Conecta con HUB-10 y el plan de migración; sin perder cupos | M | P2 |
| **MEJ-04** | **Simulador de validación**: probar `validar` con correo+id de prueba sin consumir cupo, con resultado explicado. | Soporte puede diagnosticar sin quemar licencias | B | P2 |
| **MEJ-05** | **Historial de clave de activación**: cada regeneración con fecha/usuario; la clave vieja queda como vencida. | Evita confusión con `regenerar-clave` (§11.301) | B | P2 |
| **MEJ-06** | **Paginación y búsqueda** en Licencias y Valoraciones (hoy cargan todo). | Escalabilidad y usabilidad con cientos de licencias | B | P2 |
| **MEJ-07** | **Confirmación reforzada** (clave admin) para revocar licencia, eliminar módulo y borrar dispositivo. | Control interno proporcional | M | P2 |
| **MEJ-08** | **Control de Términos** visible en el panel: versión, fecha de última revisión y qué gatillo la originó (REQ-LEGAL-01). | Cumplimiento y defensa documental | B | P1 |
| **MEJ-09** | `/api/catalogo/hub` suma `contactoResumen` y `comprapeUrl` resuelta, para que el desktop haga una sola llamada. | Menos latencia con Render dormido | B | P2 |
| **MEJ-10** | **Test de contrato Hub ↔ core ↔ desktop**: planes, `dispositivosPermitidos`, `modulos[]`, `version`, `tieneManual`. Se corre en CI. | Detecta drift entre "verdad comercial" y "espejo operativo" | M | P1 |

---

## 8. Criterios de aceptación / GO-NO-GO

**GO para dar por auditada una pestaña solo si:**
1. Sus casos `TC-HUB-12-*` positivos y negativos están en verde (REQ-TC-01).
2. No quedan hallazgos P0/P1 sin corregir o sin decisión explícita del usuario de diferirlos.
3. `th/td` balanceados, moneda única, sin desbordes en 390/768/1440, modal accesible con Escape.
4. Todo endpoint mutante tiene gate de autorización verificable (no solo ofuscación en UI).
5. Fuente y espejo `static/*` son byte-idénticos (`sha256`) y pasan los 3 scripts del Hub.
6. `mvn clean test` del Hub en verde (los errores preexistentes ajenos se documentan, no se ocultan).

**NO-GO si:** queda un IDOR, un precio divergente, una versión inválita publicada o una tabla desalineada.

**Entrega:** pase a `[?]` **EN RE-PRUEBA**; la licenciada decide `OK` → `[x]` o `BUG` → `[~]` con motivo (REQ-ESTADO-01). El `push` del subrepo y el empaquetado solo con orden explícita.

---

## 9b. Addendum del mapa profundo (2026-11-25, mismo día)

Confirmaciones nuevas tras el barrido completo del árbol (58 archivos Java, 88 endpoints, 20 entidades, 15 services):

- **Espejos byte-idénticos hoy**: `admin-web/index.html` ↔ `static/admin/index.html` (SHA-256 `5861c915…50c3538`) y `cliente-web` ↔ `static/cliente` (`5e02b2b9…b19abe`). `DEP01/DEP02` ya están en verde **en la fuente**; se revalidan tras cada corrección P0.
- **H-19 (P2, comercio)**: el endpoint `PUT /api/admin/gestion/config/anual-factor` existe pero **no tiene UI** en el panel Soporte. Hoy el factor anual solo se cambia por API; añadir el input cierra HUB-11 de extremo a extremo.
- **H-20 (P2, seguridad)**: `JwtAuthFilter` del Hub otorga `ROLE_ADMIN` a **cualquier JWT válido** sin comprobar el claim `rol`. No es explotable hoy (solo existen tokens admin), pero **impide reutilizar el mismo esquema JWT para el panel cliente** sin escalada de privilegios. Por eso H-02 se resuelve con un token opaco de cliente, **nunca** con un JWT firmado con el mismo secreto.
- **H-21 (P2, integridad)**: no hay Flyway; `ddl-auto=update` + un único SQL aditivo (`docs/migracion-hubpaq-sp1.sql`). Las columnas que pasaron a `nullable` requieren SQL manual (patrón ya documentado en el proyecto padre). Conecta con MEJ-03/MEJ-10.
- `anyRequest().permitAll()` hace públicos también `/api/valoraciones/**`, `/api/dispositivos/**`, `/api/licencias/estado`, `/comprape/estado` y `/activar-gratuito`; confirmado (H-05/H-08).
- `frameOptions.disable()` + `/h2-console/**` en `permitAll` solo en el perfil por defecto; en `prod` está desactivado (H-07 queda como endurecimiento).

**Decisión de ejecución del usuario (2026-11-25):** alcance **solo P0** (H-01, H-02, H-03, H-04). H-05..H-21 y MEJ-01..10 quedan registrados para otra ronda.

---

## 9. Trazabilidad

- **12207:** verificación (olas 0-4) → mantenimiento (ola 5) → mejora continua (ola 6).
- **29148:** requisitos verificables por hallazgo y por mejora, con casos antes del código.
- **15288:** Hub + panel admin + panel cliente + core + BD + hosting como un solo sistema.
- **25010:** usabilidad (H-01/H-09/H-11/H-12), seguridad (H-02/H-07/H-08/H-15/H-16), fiabilidad (H-03/H-04/H-06/H-17), mantenibilidad (MEJ-02/MEJ-10), adecuación comercial (H-10/H-18).

---

## 10. Ejecución P0 (2026-11-25, autorizada por el usuario)

**Alcance autorizado:** solo P0 (H-01 a H-04). H-05..H-23 y MEJ-01..10 quedan para otra ronda. No se empaquetó `.exe` ni nada del core (regla §7); no se tocó la BD de producción ni el `:8080` del usuario. Se respetaron los cambios sin commitear de AYU-02 en el subrepo.

| Hallazgo | Corrección aplicada | Verificación |
|---|---|---|
| **H-01** | Se quitó el `<td>` duplicado de clave en la tabla Licencias (fuente + espejo). El checker `check-tab-nesting.mjs` ahora valida `th/td` por tabla (tolera la fila de estado vacío) y **se validó reintroduciendo el bug**: lo detecta. | Playwright 1440×900: 9 cabeceras / 9 celdas, la clave aparece una sola vez, "Modulos" muestra `almacen`, "Maq." 3. A 390×844: `scrollWidth=clientWidth=390` (sin desbordes). |
| **H-02** | Nuevo `ClienteSesionService` con **token opaco** (nunca JWT: H-20 impedía reutilizar el secreto admin). `/login` emite token; `/logout` lo revoca; `mis-dispositivos`, `desactivar` y `eliminar` exigen `X-Cliente-Token` que coincida con el correo. Panel cliente actualizado (fuente + espejo). | Smoke real: sin token 401 en los 3 endpoints; con token 200; tras logout 401. 6 tests nuevos. |
| **H-03** | `VersionSistemaUtil` (semver `X.Y.Z`, `segura()→0.0.0`, `comparar`). Alta/edición validan y dejan **una sola activa**; el feed público nunca devuelve cadena vacía y suma `versionValida`. | Smoke: `""`→400, `2.0.1`/`2.0.2`→200, feed `2.0.2` con `versionValida:true`, solo una activa. 4 tests nuevos. |
| **H-04** | `ModuloPlanService.precioDeEntrada()` deriva el precio público del plan de entrada; `/api/catalogo/modulos` expone `precio`, `precioLegacy` y `precioConcuerda`. El panel deshabilita el precio legacy cuando hay planes (con `title` explicativo). | Smoke: `almacen` → `precio=120`, `precioLegacy=3000`, `precioConcuerda=false`, 2 planes. 3 tests nuevos. |
| **H-22** (nuevo, P1, destapado por el smoke) | `Map.of("licencia", null, …)` en `mis-dispositivos` daba **500** para un cliente recién registrado sin licencia. Se cambió por `LinkedHashMap`. | Smoke: con token y sin licencia → 200 `{licencia:null, maquinas:[]}`. |

**Hallazgo nuevo registrado para la próxima ronda:** **H-23 (P2, UX)** — a 390 px las 7 pestañas del panel envuelven en 3 filas en vez de scrollear horizontalmente (ver captura). No se corrige por estar fuera del alcance P0; el caso `TC-HUB-12-UX02` queda como pendiente.

**Verificación global:** Hub `mvn clean test` **111 tests, 0 fallos, 0 errores** (98 previos + 13 nuevos); `check-vue-template` 0 hallazgos (157/13 expuestos); `smoke-setup-admin` 157 expuestos; `check-tab-nesting` 0 problemas con el nuevo control de columnas; espejos fuente↔`static/*` con SHA-256 idéntico; **smoke API 12/12 PASS** sobre JAR real en H2 aislada (`:9097`), scratch y proceso detenidos.

**Estado:** KANBAN `HUB-12` pasa a `[?]` **EN RE-PRUEBA**. Queda la comprobación de la licenciada (`OK` → `[x]` o `BUG` → `[~]` con motivo) antes de cualquier push del subrepo.

---

## 11. Ejecución P1 + quick wins P2 (2026-11-25, continuación autorizada)

El usuario indicó que haría la re-prueba después y pidió *"prosigue"*. Se ejecutó la siguiente ola sin sacar HUB-12 de `[?]`.

| ID | Corrección | Verificación |
|---|---|---|
| **H-05** | `ValoracionService.mascaraCorreo()`: la **lista pública** muestra `da***@gmail.com`; `mia` y el panel admin conservan el correo completo (el interesado lo aporta y lo necesita para editar). | Smoke: pública enmascarada, `mia` completa, admin completa. 3 tests nuevos. |
| **H-06** | `TipoMoodu.admiteAlta()`: PLUS lee PERMANENTE legacy pero ya no lo puede **crear** (solo TRIENAL); el mensaje de error usa `periodicidadesAlta()`. | Smoke: PERMANENTE → 400 "use TRIENAL", TRIENAL → 200 con 1095 días. Test nuevo. |
| **H-07** | CSP con `script-src 'self' https://unpkg.com 'unsafe-inline' 'unsafe-eval'` (el compilador de Vue CDN exige `eval`), `style-src` con jsdelivr, `img-src data: https:`, `frame-ancestors 'none'`. H2 console **desactivada por defecto** (`H2_CONSOLE_ENABLED`) y `frameOptions.disable()` solo si se habilita. | `curl -I`: CSP presente, `/h2-console` → 404. Playwright: panel monta, **0 errores de consola** tras login. **Compromiso documentado:** `'unsafe-eval'`/`'unsafe-inline'` siguen necesarios mientras los paneles sean Vue-CDN con template inline. |
| **H-08** | `RateLimitFilter` (`@Order(1)`): cuenta **solo fallos** por IP (`X-Forwarded-For`) y grupo; admin login 5/5 min, validar 20, valoraciones/cliente 10; 429 + `Retry-After`; un éxito limpia. | Smoke: 4 fallos + éxito → 200; 5 fallos → 6º 429. 4 tests nuevos. |
| **H-09** | Catálogo usa `S/` en vez de `$` (fuente + espejo). | Playwright: celdas "S/ 1200" y "S/ 299". |
| **H-10** | Runner idempotente `registrarModuloComprape`: si falta, crea el módulo OFICIAL/GRATIS `comprape` (plan GRATUITO/PLUS sigue siendo `PlanComprape`, no un módulo aparte). | Smoke: `comprape` en `/api/catalogo/modulos`, activo y GRATIS. |
| **H-11** | `step="0.01"` en los precios de paquetes y catálogo (fuente + espejo). | Verificado en HTML. |
| **H-12** | Escape cierra cualquier modal del panel (listener único en `onMounted`). | Playwright: abrir "+ Nuevo pack" → Escape → modal cerrado. *(El foco inicial queda como mejora menor; no se automatizó.)* |
| **H-14** | Texto "melhora" → "mejoras" en Soporte (fuente + espejo). El nombre `Recepcion_taller` del catálogo queda como dato editable del admin. | Verificado en HTML. |
| **H-15** | `JwtService.esAdmin()` exige claim `rol=ADMIN`; el filtro lo usa además de `tokenValido`. | Smoke: JWT firmado con `rol=USER` → 401; JWT admin → 200. |
| **H-16** | `ElectronVue/src/api/licencias.js`: se quitó el `Content-Type: application/json` por defecto (misma clase de bug de §11.230). | `npm run build` OK; vitest **659 verdes** (2 unhandled preexistentes de `hub.spec.ts`, §11.468). |
| **H-19** | UI del factor anual en Soporte (input 1–24 + Guardar, carga con los parámetros). | Smoke API: default 10, guardar 12, fuera de rango 400. Playwright: input con valor 12/min 1/max 24. |

**Verificación global de esta ola:** Hub `mvn clean test` **119 tests, 0 fallos, 0 errores** (+8 sobre la ola P0); `check-vue-template` 161/13 expuestos sin hallazgos; `check-tab-nesting` 0; espejos SHA-256 idénticos; smoke JAR real `:9098` **17/17 PASS** sumando ambas olas; Playwright 1440×900 sin errores de consola nuevos. Scratch y proceso detenidos; BD y `:8080` del usuario intactas. **Sin push ni empaquetado** (regla §7).

**Pendiente de la ola:** H-13 (mensajes inline tenues), H-17 (guías AYU-02 ya pusheadas pero `tieneManual` sigue pendiente de verificar en Render), H-18 (packs con PERMANENTE — confirmar decisión comercial), H-20 ya cubierto por H-15, H-21 (Flyway) y MEJ-01..10.

---

## 12. Ejecución final (2026-11-25, autorizada "procede")

| ID | Corrección | Verificación |
|---|---|---|
| **H-13** | Aviso flotante único (`.hub-toast`, `role=status`, auto-oculto a los 6 s) que refleja los mensajes de tema/contacto/parámetros/manual/fotos/Comprape mediante un `watch` único. | Playwright: al cambiar el factor a 99 aparece "El factor anual debe estar entre 1 y 24." con `role=status`. |
| **H-17** | **A VERIFICAR, no corregido**: Render no respondió (timeout 60 s) al consultar `/api/catalogo/modulos`. No se afirma nada sobre `tieneManual` en producción. | `curl` HTTP=000. Queda como condición de despliegue. |
| **H-23** | `.nav-tabs` con `flex-wrap:nowrap` + `overflow-x:auto` sin barra en ≤640 px. | Playwright 390×844: `wrap=nowrap`, `scrollW=839`/`clientW=366`, **documento sin desborde** (`390=390`). |
| **MEJ-01** | Card "Salud comercial" en Licencias: precio divergente, sin precio, sin guía, sin fotos y packs sin tarifa, leídos del **catálogo público** (una sola verdad), con botón que salta a la pestaña correspondiente. | Playwright: "Sin guía: 3", "Sin fotos: 3", "Packs sin tarifa: 3" con datos reales del JAR. |
| **MEJ-02** | Entidad `HubAuditoria` + repo + service + `GET /api/admin/gestion/auditoria`; hooks en crear/actualizar/eliminar licencia y módulo y en publicar/actualizar versión. Sin secretos en el detalle. Nueva pestaña **Auditoría** (8ª). | Smoke: `CREAR` y `PUBLICAR_VERSION` con `usuario=root`, sin `claveActivacion` en el detalle; pestaña renderiza su tabla. |
| **MEJ-08** | Controles de versión/revisión de Términos en Soporte + defaults en `ParametroService.mapa()` (`1.0` / `2026-11-25`), se guardan con "Guardar parámetros". | Smoke: GET devuelve los defaults, PUT guarda 1.1; UI muestra ambos campos. |
| **MEJ-10** | `ContratoCatalogoTest`: exige las claves públicas (`precioLegacy`, `precioConcuerda`, `planes`, `tieneManual`, `valoracion`, `dispositivosIncluidos`) y feed sin versión → `0.0.0`. | 2 tests nuevos en verde. **Lección:** Mockito inline no puede mockear clases concretas en este JDK; se instancian reales con repos mockeados (interfaces). |

**Bug propio atrapado por el checker:** al añadir la 8ª pestaña quedó colgada dentro de Soporte (`html>body>div>div>div` vs `html>body>div>div`): se movió un `</div>` y el checker volvió a 8 pestañas hermanas, balance 0. Es la misma clase de §11.452; el gate funcionó.

**Verificación global final:** Hub `mvn clean test` **121 tests, 0 fallos, 0 errores**; `check-vue-template` 167/13 sin hallazgos; `check-tab-nesting` 8 pestañas balance 0; espejos SHA-256 idénticos; smoke JAR real `:9099` 8/9 (la única expectativa fallida era **del arnés**: esperaba defaults de Términos en BD, que ahora `mapa()` expone y se reverificó); Playwright 390 px y 1440 px sin desbordes ni errores nuevos. Scratch y proceso detenidos; BD y `:8080` intactas. **Sin push ni empaquetado** (regla §7).

**Queda para el usuario / otra ronda:** H-17 (verificar Render cuando responda), H-18 (¿PACK con PERMANENTE es decisión comercial?), H-21 (Flyway — cambio arquitectónico), MEJ-03..07 y MEJ-09. HUB-12 sigue en `[?]` a la espera de la comprobación de la licenciada.
- **REQ-LEGAL-01:** evaluado en el pre-vuelo; H-05 obliga a revisar Términos §8 **en la misma sesión** en que se corrija. El resto no añade dato personal, servicio externo ni estándar nuevo.
