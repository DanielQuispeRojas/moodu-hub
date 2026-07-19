package com.idastasoft.licencias.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Licencia de un cliente. Asociada a un correo y una clave de activacion que el
 * administrador genera manualmente. Permite un numero de maquinas (PCs) y una
 * lista de modulos oficiales de MOODU. La vigencia se calcula desde fechaInicio
 * sumando la unidad de tiempo configurada.
 */
@Entity
@Table(name = "licencia")
public class Licencia {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String nombre;

    @Column(nullable = false)
    private String apellido;

    @Column(nullable = false, unique = true)
    private String correo;

    @Column(nullable = false, unique = true)
    private String claveActivacion;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "licencia_modulo", joinColumns = @JoinColumn(name = "licencia_id"))
    @Column(name = "modulo")
    private List<String> modulos = new ArrayList<>();

    @Column(nullable = false)
    private int maquinasPermitidas = 3;

    // Vigencia: DIAS, MESES, ANIO, INDEFINIDO
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UnidadVigencia vigenciaUnidad = UnidadVigencia.MESES;

    @Column(nullable = false)
    private int vigenciaValor = 12;

    @Column(nullable = false)
    private LocalDateTime fechaInicio = LocalDateTime.now();

    @Column(nullable = false)
    private boolean activa = true;

    public enum UnidadVigencia { DIAS, MESES, ANIO, INDEFINIDO }

    // Getters y setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }
    public String getApellido() { return apellido; }
    public void setApellido(String apellido) { this.apellido = apellido; }
    public String getCorreo() { return correo; }
    public void setCorreo(String correo) { this.correo = correo; }
    public String getClaveActivacion() { return claveActivacion; }
    public void setClaveActivacion(String claveActivacion) { this.claveActivacion = claveActivacion; }
    public List<String> getModulos() { return modulos; }
    public void setModulos(List<String> modulos) { this.modulos = modulos; }
    public int getMaquinasPermitidas() { return maquinasPermitidas; }
    public void setMaquinasPermitidas(int maquinasPermitidas) { this.maquinasPermitidas = maquinasPermitidas; }
    public UnidadVigencia getVigenciaUnidad() { return vigenciaUnidad; }
    public void setVigenciaUnidad(UnidadVigencia vigenciaUnidad) { this.vigenciaUnidad = vigenciaUnidad; }
    public int getVigenciaValor() { return vigenciaValor; }
    public void setVigenciaValor(int vigenciaValor) { this.vigenciaValor = vigenciaValor; }
    public LocalDateTime getFechaInicio() { return fechaInicio; }
    public void setFechaInicio(LocalDateTime fechaInicio) { this.fechaInicio = fechaInicio; }
    public boolean isActiva() { return activa; }
    public void setActiva(boolean activa) { this.activa = activa; }
}
