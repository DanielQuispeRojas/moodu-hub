package com.idastasoft.licencias.model;

import jakarta.persistence.*;
import java.math.BigDecimal;

/**
 * Plan de pago de un modulo del catalogo (REQ-HUB-PLANES-01).
 *
 * <p>No todos los modulos ofrecen los mismos planes: un modulo puede ser
 * solo mensual, o tener mensual + anual, o los tres. El admin define
 * cuales existen y con que precio <b>cada uno</b> (el precio NO se hereda
 * del modulo: es propio de cada plan).</p>
 *
 * <p>Si un modulo no tiene filas aqui, la Tienda sintetiza un plan unico
 * desde los campos legacy ({@code tipo}/{@code precio}/{@code vigenciaDias})
 * para que los modulos ya cargados sigan funcionando sin migracion.</p>
 *
 * <p>PERMANENTE no lleva caducidad (vigenciaDias 0).</p>
 */
@Entity
@Table(name = "modulo_plan")
public class ModuloPlan {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "modulo_id", nullable = false)
    @com.fasterxml.jackson.annotation.JsonIgnore
    private ModuloCatalogo modulo;

    /** MENSUAL | ANUAL | PERMANENTE. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TipoPlan tipo = TipoPlan.MENSUAL;

    /** Precio de ESTE plan (cada plan tiene precio propio). */
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal precio = BigDecimal.ZERO;

    /** Periodos que cubre (1 = 1 mes / 1 anio). Solo para MENSUAL/ANUAL. */
    @Column(nullable = false)
    private int periodos = 1;

    /** Orden de presentacion en la Tienda (0 = primero). */
    @Column(nullable = false)
    private int orden = 0;

    @Column(nullable = false)
    private boolean activo = true;

    public enum TipoPlan { MENSUAL, ANUAL, PERMANENTE }

    /**
     * Vigencia en dias derivada del tipo y los periodos.
     * PERMANENTE = 0 (no caduca). Se guarda en el mapa que ve la Tienda,
     * no en la entidad: asi cambiar un periodo recalcula solo.
     */
    public int vigenciaDias() {
        if (tipo == TipoPlan.PERMANENTE) return 0;
        int p = Math.max(1, periodos);
        return tipo == TipoPlan.ANUAL ? p * 365 : p * 30;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public ModuloCatalogo getModulo() { return modulo; }
    public void setModulo(ModuloCatalogo modulo) { this.modulo = modulo; }
    public TipoPlan getTipo() { return tipo; }
    public void setTipo(TipoPlan tipo) { this.tipo = tipo; }
    public BigDecimal getPrecio() { return precio; }
    public void setPrecio(BigDecimal precio) { this.precio = precio; }
    public int getPeriodos() { return periodos; }
    public void setPeriodos(int periodos) { this.periodos = periodos; }
    public int getOrden() { return orden; }
    public void setOrden(int orden) { this.orden = orden; }
    public boolean isActivo() { return activo; }
    public void setActivo(boolean activo) { this.activo = activo; }
}