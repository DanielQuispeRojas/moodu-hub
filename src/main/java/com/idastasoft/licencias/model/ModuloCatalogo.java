package com.idastasoft.licencias.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Catalogo unificado de MOODU Hub: modulos oficiales y de terceros.
 * Cada modulo publicado tiene imagen, descripcion, desarrollador, version
 * y link de descarga. Los oficiales apuntan al instalador MOODU; los de
 * terceros apuntan a su repositorio. El precio aplica a oficiales (licencia)
 * y a terceros de paga.
 */
@Entity
@Table(name = "modulo_catalogo")
public class ModuloCatalogo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Clave unica del modulo (ej: "recepcion", "caja", "mi-plugin"). */
    @Column(nullable = false, unique = true)
    private String clave;

    @Column(nullable = false)
    private String nombre;

    @Column(length = 2000)
    private String descripcion;

    /** URL de la imagen del modulo en el catalogo. */
    @Column(length = 1000)
    private String imagen;

    /**
     * Icono del modulo (data-URL PNG/JPEG/WebP <= 512 KB, o http URL).
     * Se muestra en la Tienda junto a las fotos (REQ-HUB-FOTOS-01).
     */
    @Column(columnDefinition = "TEXT")
    private String iconoUrl;

    /** Ids de fotos para la galeria (no se serializan los bytes). */
    @Transient
    private java.util.List<Long> fotoIds;

    /**
     * Planes de pago del modulo (REQ-HUB-PLANES-01): cada plan su precio.
     * Vive en {@code modulo_plan}; aqui solo viaja el resumen para la Tienda
     * y el panel. Se rellena por el controller; la entidad no lo persiste.
     */
    @Transient
    private java.util.List<java.util.Map<String, Object>> planes = new java.util.ArrayList<>();

    /** Desarrollador que publica (MOODU para oficiales, o un DESARROLLADOR). */
    @Column(nullable = false)
    private String desarrollador = "MOODU";

    /** Version del modulo (configurada antes de publicar). */
    @Column(nullable = false)
    private String version = "1.0.0";

    /** Fecha de publicacion del modulo (se muestra en la Tienda). */
    private LocalDate fechaPublicacion;

    /** OFICIAL o TERCERO. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrigenModulo origen = OrigenModulo.OFICIAL;

    /** GRATIS o PAGA. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TipoLicencia tipo = TipoLicencia.GRATIS;

    @Column(nullable = false)
    private BigDecimal precio = BigDecimal.ZERO;

    /**
     * Dispositivos que regala la adquisicion de este modulo (REQ-HUBPAQ-01).
     * Nullable por compatibilidad con BDs existentes: se lee como 0.
     */
    @Column(nullable = true)
    private Integer dispositivosIncluidos = 0;

    /** Descuento de temporada en % (null = sin oferta). 1..70. */
    @Column(nullable = true)
    private Integer descuentoPct;

    /** Inicio de la vigencia del descuento (null = desde siempre). */
    @Column(nullable = true)
    private LocalDate descuentoInicio;

    /** Fin de la vigencia del descuento (null = sin fin). */
    @Column(nullable = true)
    private LocalDate descuentoFin;

    /** Link de descarga/instalacion. Oficial -> instalador MOODU; tercero -> repo. */
    @Column(length = 1000)
    private String linkDescarga;

    /** Link al repositorio (para terceros). */
    @Column(length = 1000)
    private String linkRepositorio;

    /**
     * Guia de uso del modulo (REQ-AYU-02). JSON con el MISMO contrato que ya
     * consume el visor del desktop: {@code secciones[{titulo,pasos[]}]},
     * {@code desarrollo} y {@code faq[]}. Las imagenes viajan como data-URL
     * dentro del JSON porque el disco de Render es efimero (igual que
     * {@code ModuloFoto.datos}). TEXT sin {@code @Lob}: en Postgres {@code @Lob}
     * mapea a oid y la lectura fuera de sesion falla con "Unable to access lob
     * stream" (AGENTS 11.195/11.438). Null = sin manual (el desktop cae al
     * manual del info.json del modulo).
     */
    @Column(columnDefinition = "TEXT")
    private String manual;

    /** Vigencia de la licencia para terceros de paga (dias). 0 = indefinido. */
    @Column(nullable = false)
    private int vigenciaDias = 0;

    @Column(nullable = false)
    private boolean activo = true;

    public enum OrigenModulo { OFICIAL, TERCERO }
    public enum TipoLicencia { GRATIS, PAGA }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getClave() { return clave; }
    public void setClave(String clave) { this.clave = clave; }
    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }
    public String getDescripcion() { return descripcion; }
    public void setDescripcion(String descripcion) { this.descripcion = descripcion; }
    public String getImagen() { return imagen; }
    public void setImagen(String imagen) { this.imagen = imagen; }
    public String getIconoUrl() { return iconoUrl; }
    public void setIconoUrl(String iconoUrl) { this.iconoUrl = iconoUrl; }
    public java.util.List<Long> getFotoIds() { return fotoIds; }
    public void setFotoIds(java.util.List<Long> fotoIds) { this.fotoIds = fotoIds; }
    public java.util.List<java.util.Map<String, Object>> getPlanes() { return planes; }
    public void setPlanes(java.util.List<java.util.Map<String, Object>> planes) { this.planes = planes; }
    public String getDesarrollador() { return desarrollador; }
    public void setDesarrollador(String desarrollador) { this.desarrollador = desarrollador; }
    public String getVersion() { return version; }
    public void setVersion(String version) { this.version = version; }
    public LocalDate getFechaPublicacion() { return fechaPublicacion; }
    public void setFechaPublicacion(LocalDate fechaPublicacion) { this.fechaPublicacion = fechaPublicacion; }
    public OrigenModulo getOrigen() { return origen; }
    public void setOrigen(OrigenModulo origen) { this.origen = origen; }
    public TipoLicencia getTipo() { return tipo; }
    public void setTipo(TipoLicencia tipo) { this.tipo = tipo; }
    public BigDecimal getPrecio() { return precio; }
    public void setPrecio(BigDecimal precio) { this.precio = precio; }
    public Integer getDispositivosIncluidos() { return dispositivosIncluidos; }
    public void setDispositivosIncluidos(Integer dispositivosIncluidos) { this.dispositivosIncluidos = dispositivosIncluidos; }
    public Integer getDescuentoPct() { return descuentoPct; }
    public void setDescuentoPct(Integer descuentoPct) { this.descuentoPct = descuentoPct; }
    public LocalDate getDescuentoInicio() { return descuentoInicio; }
    public void setDescuentoInicio(LocalDate descuentoInicio) { this.descuentoInicio = descuentoInicio; }
    public LocalDate getDescuentoFin() { return descuentoFin; }
    public void setDescuentoFin(LocalDate descuentoFin) { this.descuentoFin = descuentoFin; }

    /** Dispositivos incluidos nunca negativos (legacy null = 0). */
    public int dispositivosIncluidosEfectivos() {
        return dispositivosIncluidos == null ? 0 : Math.max(0, dispositivosIncluidos);
    }

    /** El descuento solo aplica dentro de su rango de fechas (hoy inclusive). */
    public boolean descuentoVigente(LocalDate hoy) {
        if (descuentoPct == null || descuentoPct <= 0 || hoy == null) return false;
        return (descuentoInicio == null || !hoy.isBefore(descuentoInicio))
                && (descuentoFin == null || !hoy.isAfter(descuentoFin));
    }

    /** Precio con el descuento de temporada ya aplicado (2 decimales HALF_UP). */
    public BigDecimal precioConDescuento(BigDecimal base, LocalDate hoy) {
        BigDecimal b = base == null ? BigDecimal.ZERO : base;
        if (!descuentoVigente(hoy)) return b;
        return b.multiply(BigDecimal.valueOf(100 - descuentoPct))
                .divide(BigDecimal.valueOf(100), 2, java.math.RoundingMode.HALF_UP);
    }
    public String getLinkDescarga() { return linkDescarga; }
    public void setLinkDescarga(String linkDescarga) { this.linkDescarga = linkDescarga; }
    public String getLinkRepositorio() { return linkRepositorio; }
    public void setLinkRepositorio(String linkRepositorio) { this.linkRepositorio = linkRepositorio; }
    public String getManual() { return manual; }
    public void setManual(String manual) { this.manual = manual; }
    /** El manual existe solo si hay contenido parseable (no cadena vacia). */
    public boolean tieneManual() { return manual != null && !manual.isBlank(); }
    public int getVigenciaDias() { return vigenciaDias; }
    public void setVigenciaDias(int vigenciaDias) { this.vigenciaDias = vigenciaDias; }
    public boolean isActivo() { return activo; }
    public void setActivo(boolean activo) { this.activo = activo; }
}
