package com.idastasoft.licencias.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Maquina (PC) registrada contra una licencia. Solo cuentan PCs, no apps moviles.
 * El desktop envia un id de hardware al validar. Si se desactiva, el server marca
 * activa=false y emite un evento para que el desktop cierre sesion remotamente.
 */
@Entity
@Table(name = "maquina", uniqueConstraints = @UniqueConstraint(columnNames = {"licencia_id", "idHardware"}))
public class Maquina {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "licencia_id")
    private Licencia licencia;

    @Column(nullable = false)
    private String idHardware;

    @Column
    private String nombreEquipo;

    @Column(nullable = false)
    private boolean activa = true;

    @Column(nullable = false)
    private LocalDateTime fechaRegistro = LocalDateTime.now();

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Licencia getLicencia() { return licencia; }
    public void setLicencia(Licencia licencia) { this.licencia = licencia; }
    public String getIdHardware() { return idHardware; }
    public void setIdHardware(String idHardware) { this.idHardware = idHardware; }
    public String getNombreEquipo() { return nombreEquipo; }
    public void setNombreEquipo(String nombreEquipo) { this.nombreEquipo = nombreEquipo; }
    public boolean isActiva() { return activa; }
    public void setActiva(boolean activa) { this.activa = activa; }
    public LocalDateTime getFechaRegistro() { return fechaRegistro; }
    public void setFechaRegistro(LocalDateTime fechaRegistro) { this.fechaRegistro = fechaRegistro; }
}
