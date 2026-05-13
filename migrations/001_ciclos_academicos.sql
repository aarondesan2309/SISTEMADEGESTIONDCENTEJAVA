-- ============================================================
-- Migration 001: Ciclos Académicos y Tabuladores
-- Fecha: 2026-05-13
-- Objetivo: Abstraer el concepto de "ciclo académico" para
--           soportar múltiples semestres sin re-deploy.
-- ============================================================

-- 1. Tabla de ciclos
CREATE TABLE IF NOT EXISTS ciclo_academico (
    id              SERIAL PRIMARY KEY,
    nombre          VARCHAR(80)  NOT NULL,
    nombre_corto    VARCHAR(30),
    fecha_inicio    DATE         NOT NULL,
    fecha_fin       DATE         NOT NULL,
    fecha_contrato  DATE         NOT NULL,
    mes1_nombre     VARCHAR(30)  NOT NULL,
    mes2_nombre     VARCHAR(30)  NOT NULL,
    mes3_nombre     VARCHAR(30)  NOT NULL,
    mes4_nombre     VARCHAR(30)  NOT NULL,
    periodo_txt     VARCHAR(60)  NOT NULL,
    duracion_txt    VARCHAR(30)  DEFAULT '4 meses',
    activo          BOOLEAN      DEFAULT false,
    creado          TIMESTAMP    DEFAULT NOW()
);

-- 2. Tabla de tabuladores (editable por ADM)
CREATE TABLE IF NOT EXISTS tabulador_pago (
    id              SERIAL PRIMARY KEY,
    ciclo_id        INT NOT NULL REFERENCES ciclo_academico(id) ON DELETE CASCADE,
    nivel           VARCHAR(20)  NOT NULL,
    monto_por_hora  NUMERIC(8,2) NOT NULL,
    UNIQUE(ciclo_id, nivel)
);

-- 3. Relaciones con asignaciones y contratos
ALTER TABLE asignacion       ADD COLUMN IF NOT EXISTS ciclo_id INT REFERENCES ciclo_academico(id);
ALTER TABLE contrato_emitido ADD COLUMN IF NOT EXISTS ciclo_id INT REFERENCES ciclo_academico(id);

-- 4. Insertar los 2 ciclos del año escolar 2025-2026
INSERT INTO ciclo_academico (nombre, nombre_corto, fecha_inicio, fecha_fin, fecha_contrato,
    mes1_nombre, mes2_nombre, mes3_nombre, mes4_nombre, periodo_txt, activo) VALUES
('Primer Semestre 2025-2026',  'Sem 1 2025-2026',
    '2025-09-01', '2026-02-28', '2025-09-01',
    'Septiembre 2025', 'Octubre 2025', 'Noviembre 2025', 'Febrero 2026',
    'Septiembre 2025 - Febrero 2026',
    false),
('Segundo Semestre 2025-2026', 'Sem 2 2025-2026',
    '2026-03-01', '2026-08-31', '2026-03-01',
    'Marzo 2026', 'Abril 2026', 'Mayo 2026', 'Junio 2026',
    'Marzo 2026 - Junio 2026',
    true);

-- 5. Tabuladores del Sem 2 (vigentes, los actuales hardcoded en ContratoController)
INSERT INTO tabulador_pago (ciclo_id, nivel, monto_por_hora) VALUES
(2, 'Técnico',      199.30),
(2, 'Licenciatura', 419.58),
(2, 'Maestría',     734.27),
(2, 'Doctorado',    1048.95);

-- 6. Asociar datos existentes al Segundo Semestre (ciclo activo actual)
UPDATE asignacion       SET ciclo_id = 2 WHERE ciclo_id IS NULL;
UPDATE contrato_emitido SET ciclo_id = 2 WHERE ciclo_id IS NULL;
