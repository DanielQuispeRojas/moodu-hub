package com.idastasoft.licencias.model;

import jakarta.persistence.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Plan del marketplace Comprape de UNA licencia (docs/comprape.md 4.2, R8/R9).
 *
 * <p>Comprape tiene dos modos y el modo es lo que decide cuanto puede publicar la
 * empresa y cada cuanto puede corregir su lista:</p>
 * <ul>
 *   <li><b>GRATUITO</b>: 20 items de su mercaderia y la lista publicada se cambia
 *       <b>cada 15 dias</b>. El reloj lo ancla cada CAMBIO de la lista, no el alta
 *       de la cuenta: la primera eleccion es inmediata.</li>
 *   <li><b>PLUS</b>: <b>solo mensual</b> (decision de producto deliberada: no hay
 *       version permanente) y el cambio de la lista es <b>en tiempo real</b>.</li>
 * </ul>
 *
 * <p><b>Lo que NO guarda aqui:</b> el limite de items se DERIVA del plan
 * ({@link #LIMITE_GRATUITO} en gratuito, sin tope en plus) y el Hub no guarda datos
 * del negocio: solo la licencia y su plan.</p>
 *
 * <p><b>Que congela la ventana de 15 dias:</b> SOLO la lista de items (agregar o
 * quitar). Cambiar el precio mayorista de un item ya publicado es libre: corregir un
 * error de tipeo o reaccionar a costos no debe esperar dos semanas.</p>
 */
@Entity
@Table(name = "plan_comprape",
        uniqueConstraints = @UniqueConstraint(name = "uq_plan_comprape_licencia", columnNames = "licencia_id"))
public class PlanComprape {

    /** Items publicables con plan gratuito (decision del usuario, 2026-10-16). */
    public static final int LIMITE_GRATUITO = 20;

    /** Dias que se congela la lista de items en plan gratuito. */
    public static final int VENTANA_DIAS_GRATUITO = 15;

    public enum NivelComprape { GRATUITO, PLUS }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "licencia_id", nullable = false)
    @com.fasterxml.jackson.annotation.JsonIgnore
    private Licencia licencia;

    /** Plan contratado. Sin fila = TODA licencia es GRATUITO (fail-safe). */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private NivelComprape plan = NivelComprape.GRATUITO;

    /** Vence del PLUS (mensual). Obligatorio si plan = PLUS; null en gratuito. */
    private LocalDateTime vigenciaHasta;

    /**
     * Items publicados/consumidos del cupo. Lo escribe la publicacion (Fase 3):
     * MOODU solo lo muestra, jamas lo decide.
     */
    @Column(nullable = false)
    private int itemsPublicados = 0;

    /**
     * Proxima fecha en que la empresa puede volver a cambiar su lista de items.
     * Solo aplica en gratuito (en PLUS es null: el cambio es en tiempo real).
     */
    private LocalDate proximaActualizacion;

    @Column(nullable = false)
    private LocalDateTime fechaCreacion = LocalDateTime.now();

    @Column(nullable = false)
    private LocalDateTime fechaActualizacion = LocalDateTime.now();

    public PlanComprape() {
    }

    public PlanComprape(Licencia licencia, NivelComprape plan, LocalDateTime vigenciaHasta) {
        this.licencia = licencia;
        this.plan = plan != null ? plan : NivelComprape.GRATUITO;
        this.vigenciaHasta = vigenciaHasta;
    }

    /** PLUS solo mientras no expire: al vencer cae a GRATUITO (nunca queda colgado). */
    @Transient
    public NivelComprape planEfectivo() {
        if (plan == NivelComprape.PLUS && vigenciaHasta != null && vigenciaHasta.isBefore(LocalDateTime.now())) {
            return NivelComprape.GRATUITO;
        }
        return plan;
    }

    /** Limite de items segun el plan efectivo (null = sin tope). */
    @Transient
    public Integer limiteEfectivo() {
        return planEfectivo() == NivelComprape.PLUS ? null : LIMITE_GRATUITO;
    }

    @Transient
    public boolean plusVigente() {
        return planEfectivo() == NivelComprape.PLUS;
    }

    /** Dias que faltan para poder cambiar la lista (0 = se puede ya). */
    @Transient
    public long diasParaCambiar() {
        if (plusVigente() || proximaActualizacion == null) return 0L;
        long d = java.time.temporal.ChronoUnit.DAYS.between(LocalDate.now(), proximaActualizacion);
        return Math.max(0L, d);
    }

    @PrePersist
    private void alCrear() {
        if (plan == null) plan = NivelComprape.GRATUITO;
        if (itemsPublicados < 0) itemsPublicados = 0;
        if (fechaCreacion == null) fechaCreacion = LocalDateTime.now();
        fechaActualizacion = fechaCreacion;
        if (plan == NivelComprape.GRATUITO) {
            //El gratuito no tiene vencimiento: si selando con el modo anterior, se limpia.
            vigenciaHasta = null;
        } else {
            proximaActualizacion = null;
        }
    }

    @PreUpdate
    private void alActualizar() {
        if (plan == null) plan = NivelComprape.GRATUITO;
        if (itemsPublicados < 0) itemsPublicados = 0;
        fechaActualizacion = LocalDateTime.now();
        if (plan == NivelComprape.GRATUITO) {
            vigenciaHasta = null;
        } else {
            proximaActualizacion = null;
        }
    }

    //GETTERS/SETTERS
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Licencia getLicencia() { return licencia; }
    public void setLicencia(Licencia licencia) { this.licencia = licencia; }
    public NivelComprape getPlan() { return plan; }
    public void setPlan(NivelComprape plan) { this.plan = plan; }
    public LocalDateTime getVigenciaHasta() { return vigenciaHasta; }
    public void setVigenciaHasta(LocalDateTime vigenciaHasta) { this.vigenciaHasta = vigenciaHasta; }
    public int getItemsPublicados() { return itemsPublicados; }
    public void setItemsPublicados(int itemsPublicados) { this.itemsPublicados = itemsPublicados; }
    public LocalDate getProximaActualizacion() { return proximaActualizacion; }
    public void setProximaActualizacion(LocalDate proximaActualizacion) { this.proximaActualizacion = proximaActualizacion; }
    public LocalDateTime getFechaCreacion() { return fechaCreacion; }
    public void setFechaCreacion(LocalDateTime fechaCreacion) { this.fechaCreacion = fechaCreacion; }
    public LocalDateTime getFechaActualizacion() { return fechaActualizacion; }
    public void setFechaActualizacion(LocalDateTime fechaActualizacion) { this.fechaActualizacion = fechaActualizacion; }
}