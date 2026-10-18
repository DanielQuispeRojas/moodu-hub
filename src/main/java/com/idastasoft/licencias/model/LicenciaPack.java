package com.idastasoft.licencias.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;

import java.time.LocalDateTime;

/**
 * Pack de dispositivos ADQUIRIDO por una licencia (REQ-HUB-PACK-DISP-01).
 *
 * <p><b>Una sola fila por licencia</b> (pack activo): comprar otro lo reemplaza.
 * Se suma al cupo base de la licencia mientras siga vigente. Al vencer un pack
 * MENSUAL la licencia vuelve a su cupo base solo (nada queda a medias).</p>
 *
 * <p>No es un termino por modulo porque no es un modulo: el beneficio es del
 * equipo entero (dispositivos), no de una funcionalidad.</p>
 */
@Entity
@Table(name = "licencia_pack_dispositivos")
public class LicenciaPack {

    public enum Plan { MENSUAL, PERMANENTE }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "licencia_id", nullable = false, unique = true)
    private Licencia licencia;

    /** Codigo del pack comprado (ver {@link PackDispositivos}). */
    @Column(length = 12, nullable = false)
    private String codigo;

    @Enumerated(EnumType.STRING)
    @Column(length = 12, nullable = false)
    private Plan plan = Plan.MENSUAL;

    /** null = PERMANENTE (no caduca). */
    private LocalDateTime vigenciaHasta;

    @Column(nullable = false)
    private boolean activo = true;

    private LocalDateTime fechaCompra = LocalDateTime.now();

    @Transient
    private transient Integer dispositivos;

    /** Un pack MENSUAL esta vigente mientras la fecha no haya pasado. */
    public boolean vigente() {
        if (!activo) return false;
        return plan == Plan.PERMANENTE || (vigenciaHasta != null && vigenciaHasta.isAfter(LocalDateTime.now()));
    }

    /** Codigo del plan como texto para el JSON (sin depender del enum en el cliente). */
    @Transient
    public String getPlan() {
        return plan == null ? null : plan.name();
    }

    public void setPlan(String plan) {
        if (plan == null || plan.isBlank()) {
            this.plan = Plan.MENSUAL;
            return;
        }
        try {
            this.plan = Plan.valueOf(plan.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Forma de pago del pack invalida: " + plan
                + " (use MENSUAL o PERMANENTE)");
        }
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Licencia getLicencia() { return licencia; }
    public void setLicencia(Licencia licencia) { this.licencia = licencia; }
    public String getCodigo() { return codigo; }
    public void setCodigo(String codigo) { this.codigo = codigo; }
    public Plan getPlanEnum() { return plan; }
    public void setPlanEnum(Plan plan) { this.plan = plan; }
    public LocalDateTime getVigenciaHasta() { return vigenciaHasta; }
    public void setVigenciaHasta(LocalDateTime vigenciaHasta) { this.vigenciaHasta = vigenciaHasta; }
    public boolean isActivo() { return activo; }
    public void setActivo(boolean activo) { this.activo = activo; }
    public LocalDateTime getFechaCompra() { return fechaCompra; }
    public void setFechaCompra(LocalDateTime fechaCompra) { this.fechaCompra = fechaCompra; }
    public Integer getDispositivos() { return dispositivos; }
    public void setDispositivos(Integer dispositivos) { this.dispositivos = dispositivos; }
}