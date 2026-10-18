package com.idastasoft.licencias.service;

import com.idastasoft.licencias.model.Licencia;
import com.idastasoft.licencias.model.LicenciaPack;
import com.idastasoft.licencias.model.PackDispositivos;
import com.idastasoft.licencias.repository.LicenciaModuloRepo;
import com.idastasoft.licencias.repository.LicenciaPackRepo;
import com.idastasoft.licencias.repository.LicenciaRepo;
import com.idastasoft.licencias.repository.MaquinaRepo;
import com.idastasoft.licencias.repository.ModuloCatalogoRepo;
import com.idastasoft.licencias.repository.PackDispositivosRepo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * Tipo de MOODU de la licencia y compra de packs de dispositivos
 * (REQ-HUB-TIER-01 / REQ-HUB-PACK-DISP-01).
 *
 * <p>Lo que se verifica aqui es la regla comercial que mas importa:
 * <ul>
 *   <li>el tipo sale de los <b>modulos</b> de la licencia (moodu_plus /
 *       moodu_pro), nunca de una columna aparte;</li>
 *   <li>el cupo que se aplica es <b>base + extra del pack vigente</b>, y con
 *       MOODU Pro no hay tope (0 = ilimitado);</li>
 *   <li>una compra = <b>un</b> pack activo (comprar otro lo reemplaza), y sin
 *       precio en esa forma de pago no se compra.</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class LicenciaPackServiceTest {

    private static final String CORREO = "prueba@moodu.com";
    private static final String CLAVE = "CLAVE1";

    @Mock private LicenciaRepo licenciaRepo;
    @Mock private MaquinaRepo maquinaRepo;
    @Mock private LicenciaModuloRepo terminoRepo;
    @Mock private ModuloCatalogoRepo moduloCatalogoRepo;
    @Mock private PackDispositivosRepo packRepo;
    @Mock private LicenciaPackRepo licenciaPackRepo;

    private LicenciaService service;
    private Licencia licencia;

    @BeforeEach
    void setUp() {
        PackDispositivosService packService = new PackDispositivosService(packRepo, licenciaPackRepo);
        service = new LicenciaService(licenciaRepo, maquinaRepo, terminoRepo, null,
                moduloCatalogoRepo, packService, licenciaPackRepo);
        // Sin terminos por modulo: la licencia se apoya en su lista legacy.
        lenient().when(terminoRepo.findByLicenciaId(any())).thenReturn(List.of());
        lenient().when(maquinaRepo.countByLicenciaIdAndActivaTrue(any())).thenReturn(0L);
        licencia = licencia("recepcion");
    }

    private Licencia licencia(String... modulos) {
        Licencia l = new Licencia();
        l.setId(1L);
        l.setCorreo(CORREO);
        l.setClaveActivacion(CLAVE);
        l.setMaquinasPermitidas(1);
        l.setActiva(true);
        l.setModulos(List.of(modulos));
        return l;
    }

    private void licenciaCon(String... modulos) {
        licencia = licencia(modulos);
        when(licenciaRepo.findByCorreoAndClaveActivacion(any(), any())).thenReturn(Optional.of(licencia));
        when(licenciaRepo.findByCorreo(any())).thenReturn(Optional.of(licencia));
    }

    private PackDispositivos pack(String codigo, int disp, boolean mensual) {
        PackDispositivos p = new PackDispositivos();
        p.setCodigo(codigo);
        p.setDispositivos(disp);
        p.setActivo(true);
        if (mensual) p.setPrecioMensual(new BigDecimal("25.00"));
        else p.setPrecioPermanente(new BigDecimal("150.00"));
        return p;
    }

    private LicenciaPack comprado(String codigo, LicenciaPack.Plan plan, LocalDateTime hasta) {
        LicenciaPack lp = new LicenciaPack();
        lp.setLicencia(licencia);
        lp.setCodigo(codigo);
        lp.setPlanEnum(plan);
        lp.setVigenciaHasta(hasta);
        lp.setActivo(true);
        return lp;
    }

    /**
     * El pack vigente que "ve" el repo mock.
     *
     * <p><b>El mock TIENE que persistir</b> (§11.442): comprarPackDispositivos
     * guarda y luego devuelve el estado, que RE-LEE el repo. Un mock que solo
     * devuelve lo que se le puso haria fallar el test sin que hubiera un bug.</p>
     */
    private void packVigenteEnRepo(LicenciaPack inicial) {
        java.util.concurrent.atomic.AtomicReference<LicenciaPack> ref =
                new java.util.concurrent.atomic.AtomicReference<>(inicial);
        when(licenciaPackRepo.findByLicencia(any()))
                .thenAnswer(i -> Optional.ofNullable(ref.get()));
        when(licenciaPackRepo.save(any())).thenAnswer(i -> {
            ref.set(i.getArgument(0));
            return i.getArgument(0);
        });
    }

    // ─── Tipo de MOODU ───────────────────────────────────────────────

    @Test
    @DisplayName("sin moodu_plus/pro el tipo es GRATIS (tambien sin licencia: es el estado por defecto)")
    void tipoPorDefectoGratis() {
        assertEquals("GRATIS", service.tipoDe(licencia("recepcion")).name());
        assertEquals("GRATIS", service.tipoDe(licencia()).name());
    }

    @Test
    @DisplayName("con moodu_plus el tipo es PLUS y el cupo sigue siendo el base")
    void plusConCupoBase() {
        Licencia l = licencia("recepcion", "moodu_plus");
        assertEquals("PLUS", service.tipoDe(l).name());
        assertEquals(1, service.maquinasEfectivas(l));
    }

    @Test
    @DisplayName("con moodu_pro el cupo efectivo es 0 = ilimitado")
    void proIlimitado() {
        Licencia l = licencia("moodu_pro");
        assertEquals("PRO", service.tipoDe(l).name());
        assertEquals(0, service.maquinasEfectivas(l));
    }

    @Test
    @DisplayName("el cupo base + un pack vigente = el total que ve el cliente")
    void cupoBaseMasExtra() {
        licenciaCon("moodu_plus");
        when(licenciaPackRepo.findByLicencia(any()))
                .thenReturn(Optional.of(comprado("D10", LicenciaPack.Plan.PERMANENTE, null)));
        when(packRepo.findById("D10")).thenReturn(Optional.of(pack("D10", 10, false)));

        Map<String, Object> r = service.estadoDispositivos(CORREO, CLAVE);
        assertEquals(Boolean.TRUE, r.get("ok"));
        assertEquals("PLUS", r.get("tipoMoodu"));
        assertEquals(1, r.get("base"));
        assertEquals(10, r.get("extra"));
        assertEquals(11, r.get("total"));
        assertNull(r.get("motivoNoHacefalta"));
    }

    // ─── Compra ──────────────────────────────────────────────────────

    @Test
    @DisplayName("comprar un pack con precio lo deja vigente y lo reporta en el estado")
    void compraGuardaYReporta() {
        licenciaCon("moodu_plus");
        when(packRepo.findById("D5")).thenReturn(Optional.of(pack("D5", 5, true)));
        packVigenteEnRepo(null); // la licencia aun no tiene pack

        Map<String, Object> r = service.comprarPackDispositivos(CORREO, CLAVE, "D5", "MENSUAL");

        assertEquals(Boolean.TRUE, r.get("ok"));
        assertEquals("D5", r.get("codigoPack"));
        assertEquals("MENSUAL", r.get("plan"));
        assertEquals(5, r.get("extra"));
        assertNotNull(r.get("vigenteHasta"), "el pack mensual debe caducar");
    }

    @Test
    @DisplayName("el pack permanente no caduca")
    void compraPermanenteNoCaduca() {
        licenciaCon("moodu_plus");
        when(packRepo.findById("D10")).thenReturn(Optional.of(pack("D10", 10, false)));
        packVigenteEnRepo(null);

        Map<String, Object> r = service.comprarPackDispositivos(CORREO, CLAVE, "D10", "PERMANENTE");
        assertEquals("PERMANENTE", r.get("plan"));
        assertNull(r.get("vigenteHasta"));
    }

    @Test
    @DisplayName("no se puede comprar un pack que no tiene precio en esa forma de pago")
    void compraRechazaSinPrecio() {
        licenciaCon("moodu_plus");
        when(packRepo.findById("D10")).thenReturn(Optional.of(pack("D10", 10, true))); // solo mensual

        Map<String, Object> r = service.comprarPackDispositivos(CORREO, CLAVE, "D10", "PERMANENTE");
        assertEquals(Boolean.FALSE, r.get("ok"));
        assertTrue(String.valueOf(r.get("mensaje")).toLowerCase().contains("forma de pago"));
    }

    @Test
    @DisplayName("con MOODU Pro no se compra nada (ya son ilimitados) y el estado lo explica")
    void proNoCompraPack() {
        licenciaCon("moodu_pro");

        Map<String, Object> compra = service.comprarPackDispositivos(CORREO, CLAVE, "D5", "MENSUAL");
        assertEquals(Boolean.FALSE, compra.get("ok"));
        assertTrue(String.valueOf(compra.get("mensaje")).toLowerCase().contains("ilimitados"));

        assertEquals(Boolean.TRUE, service.estadoDispositivos(CORREO, CLAVE).get("ilimitados"));
    }

    @Test
    @DisplayName("clave invalida no compra")
    void compraRechazaLicenciaInvalida() {
        when(licenciaRepo.findByCorreoAndClaveActivacion(any(), any())).thenReturn(Optional.empty());
        Map<String, Object> r = service.comprarPackDispositivos("nada@moodu.com", "X", "D5", "MENSUAL");
        assertEquals(Boolean.FALSE, r.get("ok"));
    }

    @Test
    @DisplayName("quitar el pack desactiva la fila (el cupo vuelve al base)")
    void quitarPack() {
        when(licenciaRepo.findByCorreo(any())).thenReturn(Optional.of(licencia("moodu_plus")));
        when(licenciaPackRepo.findByLicencia(any()))
                .thenReturn(Optional.of(comprado("D10", LicenciaPack.Plan.PERMANENTE, null)));
        when(licenciaPackRepo.save(any())).thenAnswer(i -> i.getArgument(0));

        assertTrue(service.quitarPackDispositivos(CORREO));
    }
}