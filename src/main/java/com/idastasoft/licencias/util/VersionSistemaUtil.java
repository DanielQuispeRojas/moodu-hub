package com.idastasoft.licencias.util;

import java.util.regex.Pattern;

/**
 * Validacion canonica de la version del instalador MOODU (H-03).
 *
 * <p>El feed {@code /api/catalogo/version-sistema} lo consume el desktop para
 * decidir si muestra el banner de actualizacion. Una fila con version vacia
 * (en produccion habia una) hace que la comparacion semver sea imprevisible.
 * Por eso la validacion vive en un util puro: la usan el alta, la edicion y el
 * endpoint publico, y se prueba sin Spark ni BD.</p>
 */
public final class VersionSistemaUtil {

    /** Semver estricto X.Y.Z (sin prefijo `v`, sin guiones). */
    private static final Pattern SEMVER = Pattern.compile("^\\d+\\.\\d+\\.\\d+$");

    private VersionSistemaUtil() { }

    public static boolean esValida(String version) {
        return version != null && SEMVER.matcher(version.trim()).matches();
    }

    /** Valor seguro para el desktop: una version corrupta se lee como "sin version". */
    public static String segura(String version) {
        return esValida(version) ? version.trim() : "0.0.0";
    }

    /** Comparacion numerica simple de X.Y.Z (robusta ante 2.10 > 2.9). */
    public static int comparar(String a, String b) {
        String[] x = segura(a).split("\\.");
        String[] y = segura(b).split("\\.");
        for (int i = 0; i < 3; i++) {
            int cmp = Integer.compare(Integer.parseInt(x[i]), Integer.parseInt(y[i]));
            if (cmp != 0) return cmp;
        }
        return 0;
    }
}
