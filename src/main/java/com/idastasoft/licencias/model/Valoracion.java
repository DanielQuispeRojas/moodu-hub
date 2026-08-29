package com.idastasoft.licencias.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Valoracion + comentario de un usuario/correo sobre MOODU (sistema) o sobre un
 * modulo del catalogo. Un mismo correo solo puede tener UNA valoracion por
 * destino: el destino es el sistema (moodu) o un modulo (clave). Guarda
 * fechaCreacion y fechaModificacion; el ordenamiento siempre usa fechaModificacion
 * ("ordenado por ultimo cambio").
 */
@Entity
@Table(name = "valoracion")
public class Valoracion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Destino de la valoracion: "moodu" (sistema) o una clave de modulo. */
    @Column(nullable = false, length = 100)
    private String destino;

    @Column(nullable = false, length = 200)
    private String correo;

    /** Estrellas 1..5. */
    @Column(nullable = false)
    private int puntaje;

    @Column(length = 2000)
    private String comentario;

    @Column(nullable = false)
    private LocalDateTime fechaCreacion = LocalDateTime.now();

    @Column(nullable = false)
    private LocalDateTime fechaModificacion = LocalDateTime.now();

    /** false = ocultada por moderacion (admin) o rechazada. */
    @Column(nullable = false)
    private boolean visible = true;

    // Getters y setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getDestino() { return destino; }
    public void setDestino(String destino) { this.destino = destino; }
    public String getCorreo() { return correo; }
    public void setCorreo(String correo) { this.correo = correo; }
    public int getPuntaje() { return puntaje; }
    public void setPuntaje(int puntaje) { this.puntaje = puntaje; }
    public String getComentario() { return comentario; }
    public void setComentario(String comentario) { this.comentario = comentario; }
    public LocalDateTime getFechaCreacion() { return fechaCreacion; }
    public void setFechaCreacion(LocalDateTime fechaCreacion) { this.fechaCreacion = fechaCreacion; }
    public LocalDateTime getFechaModificacion() { return fechaModificacion; }
    public void setFechaModificacion(LocalDateTime fechaModificacion) { this.fechaModificacion = fechaModificacion; }
    public boolean isVisible() { return visible; }
    public void setVisible(boolean visible) { this.visible = visible; }
}