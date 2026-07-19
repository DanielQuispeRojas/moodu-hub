package com.idastasoft.licencias.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Versionado del sistema MOODU (el instalador completo). El admin del Hub
 * programa versiones acumulando varios cambios; el desktop compara su version
 * local contra versionDisponible y muestra banner de actualizacion.
 */
@Entity
@Table(name = "version_sistema")
public class VersionSistema {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Version del instalador (ej: 2.0.1). */
    @Column(nullable = false, unique = true)
    private String version;

    /** URL de descarga del instalador. */
    @Column(length = 1000)
    private String urlDescarga;

    @Column(length = 2000)
    private String notas;

    @Column(nullable = false)
    private LocalDateTime fecha = LocalDateTime.now();

    @Column(nullable = false)
    private boolean activo = true;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getVersion() { return version; }
    public void setVersion(String version) { this.version = version; }
    public String getUrlDescarga() { return urlDescarga; }
    public void setUrlDescarga(String urlDescarga) { this.urlDescarga = urlDescarga; }
    public String getNotas() { return notas; }
    public void setNotas(String notas) { this.notas = notas; }
    public LocalDateTime getFecha() { return fecha; }
    public void setFecha(LocalDateTime fecha) { this.fecha = fecha; }
    public boolean isActivo() { return activo; }
    public void setActivo(boolean activo) { this.activo = activo; }
}
