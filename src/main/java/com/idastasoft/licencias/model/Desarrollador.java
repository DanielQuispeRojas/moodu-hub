package com.idastasoft.licencias.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Cuenta de desarrollador en el MOODU Hub. Puede publicar modulos de terceros
 * (precio, tiempo de licencia, etc.) que adquiere el cliente al comprar. La
 * comision que se queda MOODU por cada venta se configura en hub-config.json.
 */
@Entity
@Table(name = "desarrollador")
public class Desarrollador {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String correo;

    @Column(nullable = false)
    private String nombre;

    @Column(nullable = false)
    private String clave;

    @Column(nullable = false)
    private boolean activo = true;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getCorreo() { return correo; }
    public void setCorreo(String correo) { this.correo = correo; }
    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }
    public String getClave() { return clave; }
    public void setClave(String clave) { this.clave = clave; }
    public boolean isActivo() { return activo; }
    public void setActivo(boolean activo) { this.activo = activo; }
}
