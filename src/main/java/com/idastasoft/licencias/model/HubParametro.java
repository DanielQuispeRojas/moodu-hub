package com.idastasoft.licencias.model;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * Parametro editable del Hub en caliente (clave/valor).
 *
 * <p>Nace para lo que el admin tiene que poder cambiar SIN redesplegar:
 * la URL publica del Hub, la URL de la tienda Comprape y el aviso de
 * mudanza de hosting. Antes eso vivia en {@code hub-config.json}, que esta
 * dentro del JAR: editarlo exigia recompilar, y un aviso de mudanza no
 * puede depender de un redesplegue (para entonces el Hub viejo ya cayo).</p>
 *
 * <p>Los valores son texto; los booleanos se guardan como "true"/"false"
 * (lo parsea el service). Fila por clave.</p>
 */
@Entity
@Table(name = "hub_parametro", uniqueConstraints = {
        @UniqueConstraint(columnNames = "clave")
})
public class HubParametro {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 100)
    private String clave;

    @Column(columnDefinition = "TEXT")
    private String valor;

    @Column(length = 100)
    private String actualizadoPor;

    @Column(nullable = false)
    private LocalDateTime fechaActualizacion = LocalDateTime.now();

    public HubParametro() {}

    public HubParametro(String clave, String valor) {
        this.clave = clave;
        this.valor = valor;
    }

    @PrePersist
    @PreUpdate
    private void tocar() {
        if (this.clave != null) this.clave = this.clave.trim();
        if (this.clave != null) this.clave = this.clave.toUpperCase();
        this.fechaActualizacion = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public String getClave() { return clave; }
    public void setClave(String clave) { this.clave = clave; }
    public String getValor() { return valor; }
    public void setValor(String valor) { this.valor = valor; }
    public String getActualizadoPor() { return actualizadoPor; }
    public void setActualizadoPor(String actualizadoPor) { this.actualizadoPor = actualizadoPor; }
    public LocalDateTime getFechaActualizacion() { return fechaActualizacion; }
    public void setFechaActualizacion(LocalDateTime fechaActualizacion) { this.fechaActualizacion = fechaActualizacion; }
}