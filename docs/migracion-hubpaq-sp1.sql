-- SP-1 (REQ-HUBPAQ-01 / REQ-HUBDISP-01) — campos ADITIVOS del Hub.
-- Correr ANTES del primer arranque del backend nuevo sobre una BD existente.
-- Todas las columnas son NULLABLE a proposito: ddl-auto=update las crea, pero
-- este script deja el backfill explicito y documentado. NO hay DROP ni UPDATE
-- destructivo. Se puede ejecutar dos veces (IF NOT EXISTS).

-- PostgreSQL --------------------------------------------------------------
ALTER TABLE modulo_catalogo ADD COLUMN IF NOT EXISTS dispositivos_incluidos INTEGER;
ALTER TABLE modulo_catalogo ADD COLUMN IF NOT EXISTS descuento_pct INTEGER;
ALTER TABLE modulo_catalogo ADD COLUMN IF NOT EXISTS descuento_inicio DATE;
ALTER TABLE modulo_catalogo ADD COLUMN IF NOT EXISTS descuento_fin DATE;
ALTER TABLE licencia ADD COLUMN IF NOT EXISTS dispositivos_permitidos INTEGER;

UPDATE modulo_catalogo SET dispositivos_incluidos = 0 WHERE dispositivos_incluidos IS NULL;
-- El snapshot se recalcula solo al guardar terminos/packs; el backfill usa el
-- cupo legacy para que el panel no muestre un hueco mientras tanto.
UPDATE licencia SET dispositivos_permitidos = maquinas_permitidas
 WHERE dispositivos_permitidos IS NULL;

-- H2 2.x (mismo efecto; H2 acepta ADD COLUMN IF NOT EXISTS) ----------------
-- ALTER TABLE modulo_catalogo ADD COLUMN IF NOT EXISTS dispositivos_incluidos INTEGER;
-- ALTER TABLE modulo_catalogo ADD COLUMN IF NOT EXISTS descuento_pct INTEGER;
-- ALTER TABLE modulo_catalogo ADD COLUMN IF NOT EXISTS descuento_inicio DATE;
-- ALTER TABLE modulo_catalogo ADD COLUMN IF NOT EXISTS descuento_fin DATE;
-- ALTER TABLE licencia ADD COLUMN IF NOT EXISTS dispositivos_permitidos INTEGER;
-- UPDATE modulo_catalogo SET dispositivos_incluidos = 0 WHERE dispositivos_incluidos IS NULL;
-- UPDATE licencia SET dispositivos_permitidos = maquinas_permitidas WHERE dispositivos_permitidos IS NULL;

-- Verificacion -------------------------------------------------------------
-- SELECT column_name, is_nullable FROM information_schema.columns
--  WHERE table_name IN ('modulo_catalogo','licencia')
--    AND column_name IN ('dispositivos_incluidos','descuento_pct','descuento_inicio',
--                        'descuento_fin','dispositivos_permitidos');
