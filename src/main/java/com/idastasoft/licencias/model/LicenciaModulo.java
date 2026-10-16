package com.idastasoft.licencias.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Termino de licencia POR MODULO (REQ-HUB-MODULOS-01): un cliente puede tener
 * un modulo permanente y otro en plan mensual/anual dentro de la misma
 * licencia. La licencia global (vigenciaUnidad/Valor) sigue mandando como
 * techo: un modulo solo habilita si la licencia esta vigente Y su termino
 * esta vigente.
 */
@Entity
@Table(name = "licencia_modulo_termino",
        uniqueConstraints = @UniqueConstraint(columnNames = {"licencia_id", "modulo_clave"}))
public class LicenciaModulo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "licencia_id", nullable = false)
    @com.fasterxml.jackson.annotation.JsonIgnore
    private Licencia licencia;

    @Column(name = "modulo_clave", nullable = false, length = 100)
    private String moduloClave;

    /** PERMANENTE (sin vencimiento), MENSUAL o ANUAL (periodos desde el inicio). */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PlanModulo plan = PlanModulo.MENSUAL;

    /** Cantidad de periodos del plan (MENSUAL/ANUAL). 1 = un mes / un anio. */
    @Column(nullable = false)
    private int periodos = 1;

    @Column(nullable = false)
    private LocalDateTime fechaInicio = LocalDateTime.now();

    public enum PlanModulo { PERMANENTE, MENSUAL, ANUAL }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Licencia getLicencia() { return licencia; }
    public void setLicencia(Licencia licencia) { this.licencia = licencia; }
    public String getModuloClave() { return moduloClave; }
    public void setModuloClave(String moduloClave) { this.moduloClave = moduloClave; }
    public PlanModulo getPlan() { return plan; }
    public void setPlan(PlanModulo plan) { this.plan = plan; }
    public int getPeriodos() { return periodos; }
    public void setPeriodos(int periodos) { this.periodos = periodos; }
    public LocalDateTime getFechaInicio() { return fechaInicio; }
    public void setFechaInicio(LocalDateTime fechaInicio) { this.fechaInicio = fechaInicio; }
}
