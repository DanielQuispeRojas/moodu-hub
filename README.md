# MOODU · Servidor de Licencias

Servidor central de validación de licencias para **MOODU ERP**. Todas las instalaciones
desktop de MOODU se conectan aquí para validar licencias, consultar módulos
habilitados, controlar el número de máquinas (PCs) permitidas y gestionar
paquetes. Pensado para desplegarse en el hosting propio de idastaSoft
(como servicio compartido por todos los clientes).

---

## Características

- **Validación de licencias** desde el desktop MOODU: correo + clave de activación + ID de hardware.
- **Control de máquinas (PCs)**: la licencia permite N máquinas; al superar el tope se rechaza (no cuentan apps móviles).
- **Vigencia configurable**: días, meses, año o indefinido.
- **Desligado de máquina**: el administrador (o el cliente desde su cuenta) puede desactivar un equipo robado/vendido; el desktop, al revalidar, cierra sesión automáticamente.
- **Reactivación**: una máquina desligada solo se reactiva si el usuario vuelve a ingresar correo + clave de activación.
- **Dashboard admin** (Vue separado): crear licencias, paquetes, ver y desligar dispositivos.
- **Web cliente** (Vue separado): visitante ve precios/paquetes; cliente registrado ve sus dispositivos y los gestiona.
- **CORS abierto** para pruebas en localhost (restringir al dominio real antes de producción).

---

## Tecnologías

| Tecnología | Versión | Propósito |
|--------------|---------|-----------|
| Java (Temurin) | 21 | Lenguaje del servidor |
| Spring Boot | 3.4.6 | Framework / REST API |
| Spring Data JPA + Hibernate | — | Persistencia |
| Spring Security | — | CORS / filtrado (auth por token en memoria) |
| H2 Database | — | Base de datos embebida (archivo) |
| BCrypt | — | Hash de claves de cliente |
| Vue 3 (CDN) | 3 | Dashboards admin y cliente (runtime, sin build) |

> El frontend admin/cliente usa **Vue 3 por CDN** (un solo `index.html` cada uno)
> para facilitar la iteración en localhost. Para producción conviene convertirlos
> a proyectos Vue compilados y embeberlos en el JAR (ver sección Despliegue).

---

## Estructura

```
licencias-server/
├── pom.xml                      # Build Maven (Spring Boot)
├── src/main/java/com/idastasoft/licencias/
│   ├── LicenciasServerApplication.java   # Main + SecurityFilterChain (CORS)
│   ├── model/
│   │   ├── Licencia.java          # correo, claveActivacion, modulos[], maquinas, vigencia
│   │   ├── Maquina.java          # idHardware, equipo, activa (solo PCs)
│   │   ├── Paquete.java         # nombre, precio, modulos[]
│   │   └── ClienteUsuario.java  # registro web de cliente
│   ├── repository/               # LicenciaRepo, MaquinaRepo, PaqueteRepo, ClienteUsuarioRepo
│   ├── service/
│   │   ├── LicenciaService.java    # validación, vigencia, tope de máquinas, reactivación
│   │   ├── AdminService.java      # carga credenciales admin desde JSON
│   │   └── AdminTokenService.java # tokens de sesión admin (en memoria)
│   └── controller/
│       ├── LicenciaController.java    # /api/licencias/validar (desktop)
│       ├── AdminAuthController.java   # /api/admin/login, /logout
│       ├── AdminGestionController.java # CRUD licencias/paquetes, desligar máquina
│       └── ClienteController.java    # visitante, registro, mis-dispositivos
├── src/main/resources/
│   ├── application.properties    # puerto 9090, datasource H2
│   └── admin-credentials.json  # usuarios admin (EDITABLE POR TI)
├── admin-web/index.html         # Dashboard admin (Vue CDN)
└── cliente-web/index.html       # Web cliente (Vue CDN)
```

---

## Manual de Uso

### 1. Levantar el servidor (localhost)

```bash
cd licencias-server
./mvnw.cmd spring-boot:run        # Windows
./mvnw spring-boot:run            # Linux/Mac
```

El servidor queda en `http://localhost:9090`. Con H2 la BD se crea sola
en `licencias-server/licencias_data/`.

### 2. Credenciales de administrador

Edita `src/main/resources/admin-credentials.json` (antes de compilar o dentro del JAR):

```json
{
  "usuarios": [
    { "usuario": "admin", "clave": "admin123", "nombre": "Administrador MOODU" }
  ]
}
```

Luego en `admin-web/index.html` → login con esas credenciales → obtienes un token.

### 3. Crear una licencia (admin)

En el dashboard admin, pestaña **Licencias**:
- Nombre, Apellido, Correo del cliente.
- Clave de activación: la generas tú y se la das al cliente (o se autogenera).
- Módulos oficiales (separados por coma): `recepcionTallerMec`, etc.
- Máquinas permitidas (default 3).
- Vigencia: DIAS / MESES / ANIO / INDEFINIDO + valor.

Al guardar, la licencia queda activa. Le entregas **correo + clave de activación**
al cliente.

### 4. El cliente activa MOODU (desktop)

En el primer arranque de MOODU, pantalla de licencia:
- Marca "Cuento con una licencia" → ingresa correo + clave → Validar.
- Si es válida, el desktop registra la máquina en el servidor y guarda la licencia.
- Si el tope de máquinas se supera, el server responde con mensaje de error
  (el cliente debe desligar una máquina o ampliar la licencia).

### 5. Desligar una máquina (robo / venta)

- **Como admin**: dashboard → Licencias → "Ver disp." → Desligar.
- **Como cliente**: `cliente-web/index.html` → login → Mis dispositivos → Desligar.

En ambos casos, la máquina queda `activa=false`. La próxima vez que el
desktop revalide, el server dirá "no válida" y MOODU cerrará sesión.
Para reactivar, el usuario debe volver a ingresar correo + clave.

### 6. Web cliente (visitante)

`cliente-web/index.html` muestra los **paquetes y precios** (configurados por ti
en el dashboard admin) a cualquiera. Un visitante puede registrarse para luego
gestionar sus propias máquinas.

---

## API (OpenAPI / Swagger-style)

Base URL: `http://localhost:9090`

### Licencias (desktop)

```
POST /api/licencias/validar
  Body: { "correo", "claveActivacion", "idHardware", "nombreEquipo", "forzarActivacion"?: bool }
  Resp: { "ok": bool, "mensaje", "modulos": [], "maquinasPermitidas",
          "maquinasActivas", "expiracion", "vigente" }

GET  /api/licencias/estado?correo=&claveActivacion=
  Resp: igual que validar (sin registrar máquina)
```

### Admin (requiere header X-Admin-Token)

```
POST /api/admin/login        { "usuario", "clave" } -> { "token", "nombre" }
POST /api/admin/logout       (header X-Admin-Token)

GET  /api/admin/gestion/licencias
POST /api/admin/gestion/licencias        { nombre, apellido, correo, claveActivacion?,
                                          modulos[], maquinasPermitidas, vigenciaUnidad, vigenciaValor }
PUT  /api/admin/gestion/licencias/{id}
DEL  /api/admin/gestion/licencias/{id}
GET  /api/admin/gestion/licencias/{id}/maquinas
POST /api/admin/gestion/licencias/{id}/maquinas/{idHardware}/desactivar

GET  /api/admin/gestion/paquetes
POST /api/admin/gestion/paquetes        { nombre, descripcion, modulos[], precio }
PUT  /api/admin/gestion/paquetes/{id}
DEL  /api/admin/gestion/paquetes/{id}
```

### Cliente (público / registrado)

```
GET  /api/cliente/paquetes               # paquetes activos (visitante)
POST /api/cliente/registro             { nombre, correo, clave }
POST /api/cliente/login                { correo, clave }
GET  /api/cliente/mis-dispositivos?correo=   # licencia + máquinas del cliente
POST /api/cliente/maquinas/{idHardware}/desactivar?correo=   # desligar propia
```

### Modelo de respuesta de validación (éxito)

```json
{
  "ok": true,
  "mensaje": "Licencia valida",
  "correo": "juan@taller.com",
  "modulos": ["recepcionTallerMec"],
  "maquinasPermitidas": 2,
  "maquinasActivas": 1,
  "expiracion": "2027-01-18T17:52:36",
  "vigente": true
}
```

---

## Despliegue en producción (pendiente)

1. **Restringir CORS** en `LicenciasServerApplication` al dominio real de MOODU
   (hoy está abierto `*`).
2. **Mover credenciales admin** de `admin-credentials.json` a variables de entorno / BD.
3. **Convertir los dashboards** (`admin-web`, `cliente-web`) de Vue-CDN a proyectos
   Vue compilados y embeberlos en `src/main/resources/static` del JAR, para no
   depender de unpkg/jsdelivr en runtime.
4. Cambiar H2 por PostgreSQL si se espera alto volumen.
5. El desktop MOODU apunta hoy a `http://localhost:9090`; al subir el hosting,
   exponer la URL configurable (ya existe `localStorage['moodu-lic-server']` y
   `setLicServerUrl()` en el cliente).

---

## Notas de seguridad

- El token de admin vive **en memoria** del servidor; al reiniciar se invalidan las sesiones.
- Las claves de cliente se guardan con **BCrypt**.
- La clave de activación de licencia es un texto plano compartido por ti con el cliente
  (modelo temporal; en el futuro será correo+contraseña del sitio al comprar).
- No expongas `admin-credentials.json` ni dejes CORS abierto en producción.
