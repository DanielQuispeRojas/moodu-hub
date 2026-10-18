package com.idastasoft.licencias.model;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * Theme de MOODU del catalogo del Hub (aplicable desde Tienda > Temas).
 * El archivo .json viaja DENTRO de la BD (columna contenido): el disco del
 * hosting es efimero y un theme en filesystem se perderia en cada redeploy.
 * El contenido ya viene NORMALIZADO por TemaCatalogoService (mismo contrato
 * que el validador del desktop), asi que el desktop solo lo revalida al aplicar.
 */
@Entity
@Table(name = "tema_catalogo")
public class TemaCatalogo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Clave tecnica unica y segura para URL (Ej: azul-moodu). */
    @Column(nullable = false, unique = true, length = 80)
    private String clave;

    /** Nombre comercial que ve el cliente en la Tienda. */
    @Column(nullable = false, length = 100)
    private String nombre;

    @Column(length = 500)
    private String descripcion;

    /** Grupo visual (claros/intensos/oscuros/pastel). Informativo: el Hub lo publica. */
    @Column(length = 30)
    private String grupo = "claros";

    @Column(length = 20)
    private String version = "1.0.0";

    /** Base efectiva del theme: light | dark (derivada del JSON al guardar). */
    @Column(nullable = false, length = 10)
    private String base = "light";

    /**
     * Archivo .json del theme, ya validado y normalizado.
     * SIN @Lob a proposito: en Postgres @Lob+TEXT mapea a "oid" (large object) y
     * leerlo fuera de sesion tira "Unable to access lob stream" (bug 2026-10-07
     * del core). El Hub corre en Neon Postgres, asi que el texto viaja en la fila.
     */
    @Column(nullable = false, columnDefinition = "TEXT")
    private String contenido;

    @Column(nullable = false)
    private boolean activo = true;

    @Column(length = 100)
    private String creadoPor;

    /**
     * Tema de FABRICA (los presets que trae MOODU de serie): NO se ofrecen en la
     * Tienda, solo se ven en el panel admin. Wrapper Boolean a proposito: las filas
     * viejas tienen NULL y deben leerse como "no es de fabrica" (= visible).
     * La siembra de fabrica lo marca, y hace un backfill idempotente de las que ya
     * estaban (por creadoPor = "sistema") para no pedir migracion manual.
     */
    @Column(name = "es_fabrica")
    private Boolean esFabrica;

    @Column(nullable = false)
    private LocalDateTime fechaCreacion = LocalDateTime.now();

    @Column
    private LocalDateTime fechaModificacion;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getClave() { return clave; }
    public void setClave(String clave) { this.clave = clave; }
    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }
    public String getDescripcion() { return descripcion; }
    public void setDescripcion(String descripcion) { this.descripcion = descripcion; }
    public String getGrupo() { return grupo; }
    public void setGrupo(String grupo) { this.grupo = grupo; }
    public String getVersion() { return version; }
    public void setVersion(String version) { this.version = version; }
    public String getBase() { return base; }
    public void setBase(String base) { this.base = base; }
    public String getContenido() { return contenido; }
    public void setContenido(String contenido) { this.contenido = contenido; }
    public boolean isActivo() { return activo; }
    public void setActivo(boolean activo) { this.activo = activo; }
    public String getCreadoPor() { return creadoPor; }
    public void setCreadoPor(String creadoPor) { this.creadoPor = creadoPor; }
    public Boolean getEsFabrica() { return esFabrica; }
    public void setEsFabrica(Boolean esFabrica) { this.esFabrica = esFabrica; }
    /** null (fila vieja) = false = se ofrece en la Tienda. */
    public boolean esDeFabrica() { return Boolean.TRUE.equals(esFabrica); }
    public LocalDateTime getFechaCreacion() { return fechaCreacion; }
    public void setFechaCreacion(LocalDateTime fechaCreacion) { this.fechaCreacion = fechaCreacion; }
    public LocalDateTime getFechaModificacion() { return fechaModificacion; }
    public void setFechaModificacion(LocalDateTime fechaModificacion) { this.fechaModificacion = fechaModificacion; }
}