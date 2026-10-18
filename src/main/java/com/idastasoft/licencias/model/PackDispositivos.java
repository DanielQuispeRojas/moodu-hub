package com.idastasoft.licencias.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;

/**
 * Pack de dispositivos adicionales del catalogo del Hub (REQ-HUB-PACK-DISP-01).
 *
 * <p>Catalogo cerrado con los packs que el administrador define (5 / 10 / 15 por
 * defecto; puede agregar otros). Cada pack tiene DOS formas de pago:
 * <b>por mensualidad</b> o <b>permanente</b>. Los precios se editan en el Hub
 * y se muestran en la Tienda de MOODU (menu Dispositivos).</p>
 *
 * <p>Precio nulo = ese pack no se ofrece todavia (la Tienda lo oculta). Es el
 * default seguro: un pack sin precio nunca se puede "comprar" gratis.</p>
 *
 * <p>NO va ligado a una licencia: es del Hub. Lo que se compra por licencia es
 * {@link LicenciaPack}.</p>
 */
@Entity
@Table(name = "pack_dispositivos")
public class PackDispositivos {

    /** Codigo estable del pack (D5, D10, D15...). */
    @Id
    @Column(length = 12, nullable = false)
    private String codigo;

    @Column(length = 60)
    private String nombre;

    /** Cuantos dispositivos EXTRA otorga (se suman al cupo base). */
    @Column(nullable = false)
    private int dispositivos = 5;

    /** Precio por mes (null = no se ofrece por mensualidad). */
    @Column(precision = 12, scale = 2)
    private BigDecimal precioMensual;

    /** Precio de compra permanente (null = no se ofrece en perpetuo). */
    @Column(precision = 12, scale = 2)
    private BigDecimal precioPermanente;

    @Column(nullable = false)
    private boolean activo = true;

    @Column(length = 400)
    private String descripcion;

    public PackDispositivos() { }

    public PackDispositivos(String codigo, String nombre, int dispositivos,
                            BigDecimal precioMensual, BigDecimal precioPermanente) {
        this.codigo = codigo;
        this.nombre = nombre;
        this.dispositivos = dispositivos;
        this.precioMensual = precioMensual;
        this.precioPermanente = precioPermanente;
    }

    /** Se ofrece si tiene al menos un precio puesto y esta activo. */
    public boolean offered() {
        return activo && (precioMensual != null || precioPermanente != null);
    }

    public boolean tienePrecio(String plan) {
        return "MENSUAL".equalsIgnoreCase(plan) ? precioMensual != null : precioPermanente != null;
    }

    public BigDecimal precioDe(String plan) {
        return "MENSUAL".equalsIgnoreCase(plan) ? precioMensual : precioPermanente;
    }

    public String getCodigo() { return codigo; }
    public void setCodigo(String codigo) { this.codigo = codigo; }
    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }
    public int getDispositivos() { return dispositivos; }
    public void setDispositivos(int dispositivos) { this.dispositivos = dispositivos; }
    public BigDecimal getPrecioMensual() { return precioMensual; }
    public void setPrecioMensual(BigDecimal precioMensual) { this.precioMensual = precioMensual; }
    public BigDecimal getPrecioPermanente() { return precioPermanente; }
    public void setPrecioPermanente(BigDecimal precioPermanente) { this.precioPermanente = precioPermanente; }
    public boolean isActivo() { return activo; }
    public void setActivo(boolean activo) { this.activo = activo; }
    public String getDescripcion() { return descripcion; }
    public void setDescripcion(String descripcion) { this.descripcion = descripcion; }
}