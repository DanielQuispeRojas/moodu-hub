package com.idastasoft.licencias.model;

import jakarta.persistence.*;

/**
 * Foto del catalogo de un modulo (max 10 por modulo, REQ-HUB-FOTOS-01).
 * Se guarda en BD (base64) para sobrevivir a los redeploys del hosting
 * (el disco de Render es efimero: en filesystem se perderian).
 */
@Entity
@Table(name = "modulo_foto")
public class ModuloFoto {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "modulo_id", nullable = false)
    @com.fasterxml.jackson.annotation.JsonIgnore
    private ModuloCatalogo modulo;

    /** Orden de muestra (0 = primera). */
    @Column(nullable = false)
    private int orden = 0;

    @Column(nullable = false, length = 200)
    private String nombre = "";

    /** MIME validado al subir (image/png, image/jpeg, image/webp). */
    @Column(nullable = false, length = 50)
    private String mime = "image/png";

    /** Contenido en base64 (tope 1 MB origen, ver controller). */
    @Column(nullable = false, columnDefinition = "TEXT")
    private String datos = "";

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public ModuloCatalogo getModulo() { return modulo; }
    public void setModulo(ModuloCatalogo modulo) { this.modulo = modulo; }
    public int getOrden() { return orden; }
    public void setOrden(int orden) { this.orden = orden; }
    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }
    public String getMime() { return mime; }
    public void setMime(String mime) { this.mime = mime; }
    public String getDatos() { return datos; }
    public void setDatos(String datos) { this.datos = datos; }
}
