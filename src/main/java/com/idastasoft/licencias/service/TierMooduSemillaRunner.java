package com.idastasoft.licencias.service;

import com.idastasoft.licencias.model.ModuloCatalogo;
import com.idastasoft.licencias.model.ModuloPlan;
import com.idastasoft.licencias.model.TipoMoodu;
import com.idastasoft.licencias.repository.ModuloCatalogoRepo;
import com.idastasoft.licencias.repository.ModuloPlanRepo;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Siembra en el catalogo los dos MODOS de MOODU que se venden como licencia
 * (REQ-HUB-TIER-01): {@code moodu_plus} y {@code moodu_pro}.
 *
 * <p><b>Por que van en el catalogo y no en una tabla aparte:</b> el tipo de
 * MOODU de una licencia se deduce de su lista de modulos
 * ({@link TipoMoodu#deLicencia}), asi que PLUS y PRO tienen que ser modulos
 * que el admin pueda agregar a una licencia como cualquier otro. No son
 * modulos que el escritorio instale: son el alcance del SISTEMA.</p>
 *
 * <p><b>Precios del tarifario §11.91</b> (el administrador los edita despues
 * en el Hub &gt; Catalogo &gt; planes; manda la tabla, nunca esta semilla):
 * <ul>
 *   <li>{@code moodu_plus}: PERMANENTE S/ 1.200.</li>
 *   <li>{@code moodu_pro}: MENSUAL S/ 299 y ANUAL S/ 2.990 (10 meses).</li>
 * </ul>
 *
 * <p><b>Idempotente y sin resucitar lo borrado</b> (§11.438): si ya existe
 * CUALQUIERA de las dos claves no se siembra nada. Asi el admin puede ajustar
 * precios o borrar el que no quiera vender y no vuelve solo al arrancar.</p>
 *
 * <p>Los packs de dispositivos ({@link PackDispositivosSemillaRunner}) si se
 * re-siembran si no queda ninguno: ahi el borrado no es una decision de
 * negocio, es un reset de catalogo.</p>
 */
@Service
public class TierMooduSemillaRunner {

    /** PLUS es permanente (§11.91). */
    private static final BigDecimal PRECIO_PLUS = new BigDecimal("1200.00");
    /** PRO mensual y anual (10 mensualidades). */
    private static final BigDecimal PRECIO_PRO_MENSUAL = new BigDecimal("299.00");
    private static final BigDecimal PRECIO_PRO_ANUAL = new BigDecimal("2990.00");

    private final ModuloCatalogoRepo moduloRepo;
    private final ModuloPlanRepo planRepo;

    public TierMooduSemillaRunner(ModuloCatalogoRepo moduloRepo, ModuloPlanRepo planRepo) {
        this.moduloRepo = moduloRepo;
        this.planRepo = planRepo;
    }

    @jakarta.annotation.PostConstruct
    @Transactional
    public void sembrarSiNoHay() {
        try {
            if (moduloRepo.findByClave(TipoMoodu.CLAVE_PLUS) != null
                || moduloRepo.findByClave(TipoMoodu.CLAVE_PRO) != null) {
                return; // ya existe (o el admin lo borro): no se siembra
            }
            ModuloCatalogo plus = modulo(TipoMoodu.PLUS, PRECIO_PLUS, 0);
            ModuloCatalogo pro = modulo(TipoMoodu.PRO, PRECIO_PRO_MENSUAL, 30);
            moduloRepo.saveAll(java.util.List.of(plus, pro));
            planRepo.saveAll(java.util.List.of(
                plan(plus, ModuloPlan.TipoPlan.PERMANENTE, PRECIO_PLUS, 0),
                plan(pro, ModuloPlan.TipoPlan.MENSUAL, PRECIO_PRO_MENSUAL, 0),
                plan(pro, ModuloPlan.TipoPlan.ANUAL, PRECIO_PRO_ANUAL, 1)));
            planRepo.flush();
        } catch (RuntimeException e) {
            // La semilla jamas tumba el arranque del Hub (§11.415).
            System.err.println("[Hub] No se pudo sembrar el catalogo de modos de MOODU (PLUS/PRO): " + e.getMessage());
        }
    }

    private ModuloCatalogo modulo(TipoMoodu t, BigDecimal precio, int vigenciaDias) {
        ModuloCatalogo m = new ModuloCatalogo();
        m.setClave(t.claveCatalogo());
        m.setNombre(t.etiqueta());
        m.setDescripcion(t.descripcion());
        m.setDesarrollador("MOODU");
        m.setVersion("2.0.0");
        m.setFechaPublicacion(LocalDate.now());
        m.setOrigen(ModuloCatalogo.OrigenModulo.OFICIAL);
        m.setTipo(ModuloCatalogo.TipoLicencia.PAGA);
        m.setPrecio(precio);
        // Campos legacy: si alguien borrara los planes, la Tienda sintetiza
        // uno coherente con el tipo (PERMANENTE para PLUS, MENSUAL para PRO).
        m.setVigenciaDias(vigenciaDias);
        m.setActivo(true);
        return m;
    }

    private ModuloPlan plan(ModuloCatalogo m, ModuloPlan.TipoPlan tipo, BigDecimal precio, int orden) {
        ModuloPlan p = new ModuloPlan();
        p.setModulo(m);
        p.setTipo(tipo);
        p.setPrecio(precio);
        p.setPeriodos(1);
        p.setOrden(orden);
        p.setActivo(true);
        return p;
    }
}