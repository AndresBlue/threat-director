# Threat Director — arquitectura del MVP

## Estado y plataforma

El repositorio apunta a Minecraft 1.20.1 con Fabric Loader 0.18.4, Yarn
1.20.1+build.10, Fabric API 0.92.11+1.20.1 y bytecode Java 17. La máquina de
desarrollo inspeccionada dispone de Temurin 21, que compila con `--release 17`.
El MVP es autoritativo del servidor: no necesita entrypoint cliente, paquetes personalizados ni
una interfaz gráfica. En dedicado y en LAN basta con instalarlo en el lado que aloja el servidor;
los clientes remotos de Minecraft 1.20.1 pueden conectarse sin Threat Director y reciben el HUD
mediante la bossbar vanilla. En una partida integrada, el host sí necesita el mod porque también
ejecuta el servidor.

La configuración descrita aquí ya tiene modelo, validación y carga en
`com.andresblue.threatdirector.config`. El enlace de `ConfigManager` con
`ThreatDirectorMod` se realiza en el bootstrap del runtime; se mantiene fuera del paquete de
configuración para que el cargador pueda probarse sin iniciar Minecraft.

## Objetivo del MVP

El primer hito jugable coordina grupos pequeños de zombies, esqueletos, arañas y creepers sin
modificar sus estadísticas. Incluye:

- Adventure Intensity básica y suavizada.
- Threat Budget y poblaciones contextuales: máximo normal 8 y máximo difícil 12 por defecto.
- Roles Vanguard, Flanker, Harasser e Interceptor.
- Sectores terrestres, Pinza y Cierre de salida.
- Fases Stalking, Positioning, Engaging, Searching, Climax, Retreating y Relief.
- Límites estrictos de tiempo, entidades y pathfinding.
- BreachPlanner estrictamente simulado.
- Comandos administrativos y telemetría local opcional.

No forman parte del MVP la rotura real de bloques, claims, mobs externos, Prominence II, élites,
vuelo, combate acuático, overlay cliente, PvPvE ni reconocimiento avanzado de estructuras.

## Capas

```text
Fabric lifecycle/events
        ↓
ThreatDirectorRuntime
        ↓                       ↓
PlayerObservationService ── ThreatHudService ── ServerBossBar vanilla
        ↓
PerformanceGovernor
        ↓
EncounterManager ── EncounterController
        ↓
PopulationService ── GroupBlackboard ── planners tácticos
        ↓
MobOrderStore
        ↓
Goals temporales ── navegación y ataques vanilla
```

Las dependencias avanzan hacia abajo. Los evaluadores, patrones y adapters publican resultados
mediante interfaces; no conocen el runtime que los consume.

## Componentes y responsabilidades

### Runtime

`ThreatDirectorRuntime` existe una vez por `MinecraftServer`. Posee la configuración activa, las
observaciones, el HUD, el gobernador, los encuentros y la telemetría. Se crea en
`SERVER_STARTED` y se cierra en `SERVER_STOPPING`; no debe sobrevivir como singleton a un segundo
servidor integrado.

`EncounterManager` rota el primer controlador atendido y comparte entre todos los encuentros el
presupuesto de operaciones y la deadline best-effort de cada tick. Ningún acceso a entidades,
mundos, chunks o navegación se ejecuta fuera del hilo del servidor.

### Director e intensidad

`PlayerObservationService` captura una snapshot inmutable cada 100 ticks. El pipeline puntúa:

```text
defensa       0..35
ofensa        0..30
sostenimiento 0..15
grupo         0..10
contexto      0..10
penalización  0..30
```

La intensidad cruda se limita a 0..100 y se suaviza con EMA. Una banda no cambia hasta superar la
histéresis. En el MVP, equipo, salud, absorción, inventario y contexto se capturan cada 100 ticks;
una caché invalidada por eventos de equipo es una optimización posterior.

`PlayerDirectorState` mantiene intensidad, banda, memoria reciente, muerte y cooldown; el mapa de
encuentros activos vive en `EncounterManager`. Los cooldowns usan ticks del servidor, no hora del
sistema ni hora del día. El estado desconectado se conserva durante una ventana acotada para evitar
que una reconexión elimine inmediatamente sus salvaguardas.

`EncounterPressure` combina dificultad y zona en un peligro normalizado. Easy, Normal y Hard parten
de 0.25, 0.50 y 0.75; noche, oscuridad y cueva aportan 0.08, 0.10 y 0.12, con límite 1.0. Esa señal
ajusta de forma coherente intensidad adaptativa, probabilidad de encuentro, mínimo y cap de mobs,
atacantes cuerpo a cuerpo, cadencia táctica, refuerzos y cooldown. Un override explícito conserva
su intensidad solicitada, pero no desactiva las demás salvaguardas contextuales.

Un override administrativo vive separado de la intensidad adaptativa. `intensity()` expone el valor
efectivo al director y al HUD, mientras el muestreo continúa actualizando `adaptiveIntensity()`; al
limpiar el override no hay que reconstruir ni falsear el historial observado.

`ThreatHudService` mantiene un `ServerBossBar` individual por UUID no espectador. Reconcilia join,
disconnect y respawn desde el hilo del servidor, y elimina todas las barras al desactivar o cerrar el
runtime. Fuera de combate muestra la intensidad individual; los participantes secundarios de un
encuentro muestran la intensidad efectiva del objetivo primario con la marca `GRUPO`. El cliente
recibe paquetes bossbar vanilla: no existe entrypoint gráfico ni protocolo personalizado.

### Encuentros

`EncounterManager` garantiza como máximo un encuentro por grupo cercano y que una entidad no
pertenezca a dos controladores. Los controladores almacenan UUID y resuelven entidades en el mundo;
no conservan referencias después de unload.

En un servidor integrado abierto a LAN, el host es el objetivo primario preferido durante el scan
automático cuando comparte grupo con otros jugadores elegibles. Fuera de ese caso se prefiere el
jugador con mayor potencia observada y se usa el UUID como desempate determinista.

La máquina de estados principal es:

```text
DORMANT → STALKING → POSITIONING → ENGAGING → CLIMAX → RETREATING → COOLDOWN
                         │            │  ↑                         ↑
                         └→ SEARCHING ┘  └→ RELIEF ───────────────┘
                         └──────────────→ ABORTED ────────────────┘
```

Cada transición registra estado anterior, nuevo, causa, target, encuentro, tick, intensidad y
presupuesto restante. Cambio de dimensión, logout, grupo inviable, distancia excesiva, pérdida de
contacto y nivel de rendimiento 6 terminan el encuentro de forma segura.

Durante `ENGAGING`, `SEARCHING` o `CLIMAX`, la presión puede solicitar hasta cuatro oleadas de
refuerzo. Cada oleada vuelve a pasar por Threat Budget, ownership global, población segura, cap
contextual, límite difícil, presupuesto de planner y gobernador; necesita al menos dos miembros
vivos y nunca garantiza que aparecerá si no existe una población válida. Telemetría y logs incluyen
número de oleada, zona y peligro.

`GroupBlackboard` contiene solamente información parcial: última posición vista, confianza,
velocidad reciente, sectores reservados, slots cuerpo a cuerpo y fallos de ruta. La confianza
decae si ningún miembro ve al jugador.

### Población y presupuesto

Se reclutan primero mobs naturales sin nombre, no persistentes, libres de otro encuentro y sin un
combate ajeno. Si faltan miembros, se buscan posiciones entre 28 y 48 bloques que:

- estén en chunks cargados y dentro del world border;
- cumplan restricciones vanilla de spawn, luz, suelo y colisión;
- no estén en visión directa ni demasiado cerca del jugador;
- respeten Peaceful, `doMobSpawning` y el límite local de hostiles.

Costes iniciales: zombie 1.0, skeleton 1.3, spider 1.1 y creeper 1.8. Reclutados y generados
consumen el mismo presupuesto. La generación de creepers por el director está desactivada de forma
predeterminada. Cuando `mobGriefing=true`, tampoco se reclutan creepers naturales salvo opt-in
explícito; con `mobGriefing=false` sí son elegibles porque su explosión vanilla no rompe terreno.

El schema 2 configura `minMobs=3`, `maxMobs=8`, `hardMaxMobs=12` y
`maxNearbyHostiles=24`. La presión interpola el cap entre esos límites; el escalado multijugador y
las oleadas siguen sujetos al Threat Budget y al máximo difícil. La validación permite personalizar
`hardMaxMobs` hasta un máximo absoluto de 16.

### Táctica e IA vanilla

El entorno inmediato se representa mediante diez sectores relativos a la mirada, la altura, la
ruta de escape, la cobertura y la reserva. Una snapshot produce como máximo 32 candidatos en un
anillo configurable de 10 a 18 bloques por defecto. Primero se hacen comprobaciones baratas y
luego se permiten cuatro intentos base de path por mob; con peligro de 0.70 o más puede autorizarse
un quinto intento contextual, siempre dentro del presupuesto global. La cadencia base de replan es
30 ticks y la presión la acelera o ralentiza; también se recalcula tras desplazamiento significativo
o fallo de ruta.

La predicción de escape combina velocidad reciente y mirada. Pinza requiere sectores opuestos;
Cierre de salida requiere un Interceptor y un punto adelantado viable. Si el plan falla, el grupo
retorna a ataque vanilla distribuido.

El único mixin previsto para el MVP es un accessor de `MobEntity.goalSelector` y
`MobEntity.targetSelector`. Al reclutar una entidad se instalan dos goals:

- movimiento/observación temporal con prioridad inferior a emergencias vanilla;
- compuerta temporal del target durante Stalking, Positioning y Relief.

En Engaging se libera la compuerta, se establece el target y los goals vanilla realizan el ataque.
Al retirar la orden, los goals añadidos quedan inactivos. No se reemplaza el árbol de IA, no se
teletransporta y no se recalcula navegación cada tick.

## Eventos Fabric

- `ServerLifecycleEvents.SERVER_STARTED` y `SERVER_STOPPING`: runtime y cierre.
- `ServerTickEvents.END_SERVER_TICK`: observación, gobernador, scheduler y encuentros.
- `CommandRegistrationCallback`: comandos.
- `ServerLivingEntityEvents.AFTER_DEATH`: bajas y memoria reciente.

Logout, cambio de dimensión, unload y cambios de salud se detectan al resolver UUID y validar el
estado vivo en cada ciclo del controlador. Fabric API 1.20.1 no dispone de
`ServerLivingEntityEvents.AFTER_DAMAGE`; el MVP infiere daño sin cancelarlo mediante diferencias de
salud/absorción y mantiene una comprobación directa de salud crítica en el scheduler.

## Configuración

`ConfigManager` usa `FabricLoader.getInstance().getConfigDir()` y el archivo
`threatdirector.json`. Un constructor que recibe `Path` mantiene las pruebas aisladas.

El archivo ausente se crea con JSON UTF-8 formateado mediante escritura temporal y movimiento
atómico cuando el sistema de archivos lo soporta. La carga sigue este orden:

1. Parsear a una nueva snapshot.
2. Si es schema 1, migrar: el preset legado exacto recibe todos los defaults v2; un archivo v1
   personalizado conserva sus secciones y valores y solo cambia `schemaVersion`.
3. Validar todas las secciones y relaciones como schema 2.
4. Reescribir atómicamente el archivo cuando hubo migración.
5. Publicar la snapshot completa en una asignación `volatile`.
6. Mantener la última snapshot válida si parseo, migración, validación o escritura fallan.

Las secciones del schema 2 son:

| Sección | Responsabilidad |
| --- | --- |
| `director` | elegibilidad, probabilidad, dimensiones y cooldown |
| `intensity` | muestreo, EMA, histéresis y caps de contribución |
| `encounter` | mobs, distancias, fases, población y threat costs |
| `tactical` | anillo, candidatos, paths y atacantes simultáneos |
| `multiplayer` | agrupación y escalado con rendimiento decreciente |
| `performance` | presupuesto por tick y umbrales de degradación |
| `breachSimulation` | análisis no destructivo de bloques |
| `telemetry` | cola local, batch y privacidad |
| `integrations` | límites del preset vanilla |
| `debug` | logs y duración de partículas |

Invariantes de `VANILLA_PLUS`:

- `simulationOnly` siempre es `true`;
- adapters externos están desactivados;
- solo se seleccionan mobs vanilla;
- el default difícil es 12 mobs y el límite configurable absoluto es 16;
- cada mob permitido posee un threat cost positivo;
- distancias, fases y umbrales de rendimiento mantienen orden coherente.

La publicación de una recarga es atómica, pero un encuentro activo no representa una snapshot
íntegra y aislada del runtime. Su controlador conserva la configuración de construcción para FSM,
táctica y órdenes; el manager aplica la configuración vigente a seguridad, presupuesto global,
refuerzos, finalización/cooldown, observación, HUD y gobernador. Los encuentros nuevos sí nacen por
completo con la configuración recargada.

## Persistencia y telemetría

En el MVP, intensidad, memoria reciente y cooldown viven en memoria y se limpian al reiniciar el
servidor. Los controladores tampoco se serializan: tras un reinicio sus mobs quedan completamente
en manos de vanilla. Persistir el resumen por UUID mediante `PersistentState` es trabajo de Fase 2.

La telemetría, desactivada por defecto, usa NDJSON local bajo el directorio del mundo. Una cola
acotada evita bloquear ticks; un error de archivo queda contenido y contabilizado sin afectar al director. No existe
ninguna salida de red.

## Gobernador de rendimiento

`MinecraftServer#getTickTime()` alimenta siete niveles. El sistema degrada tras carga sostenida y
se recupera más lentamente:

```text
0 completo
1 menos replans
2 menos candidatos
3 predicción simplificada
4 menos encuentros
5 órdenes mínimas
6 retorno temporal a vanilla
```

Además de los umbrales de tick, la configuración reserva un presupuesto global de microsegundos. El
default v2 comparte 48 operaciones de planner por tick entre población, refuerzos y controladores,
además de la deadline best-effort y los límites de candidatos y frecuencia. Una consulta de
pathfinding ya iniciada no puede interrumpirse a mitad de llamada. Nunca se carga un chunk para
completar una táctica.

El gobernador ignora transitorios durante los primeros 100 ticks del runtime. Llegar de inmediato a
nivel 6 requiere dos muestras de emergencia consecutivas; una sola no basta. El comando
`/threatdirector performance reset` vuelve a nivel 0, limpia contadores y abre otros 100 ticks de
warm-up, pero no deshabilita la degradación posterior si la carga sostenida continúa.

## Compatibilidad futura

El core publica el entrypoint `threatdirector` mediante `ThreatDirectorIntegration`. Los adapters
que importen clases de otro mod vivirán en jars separados y declararán esa dependencia en su propio
`fabric.mod.json`. Cada integración registra evaluadores, traits, costes, claims y plantillas dentro
de un bloque aislado; una excepción deshabilita solo ese adapter.

Los claims devuelven `UNPROTECTED`, `PROTECTED` o `UNKNOWN`. Cualquier función destructiva interpreta
`UNKNOWN` como denegación. Prominence II se identificará por componentes y versiones reales, no por
asumir un ID del modpack.

## Estrategia de pruebas

- El MVP ejecuta JUnit puro para config, intensidad, smoothing, budget, roles, patrones, FSM,
  compatibilidad, telemetría, simulación de breach y gobernador.
- Los GameTests de goals, spawn seguro y lifecycle quedan como siguiente nivel de cobertura.
- El arranque dedicado se prueba con solo Fabric API y Threat Director.
- Un cliente remoto vanilla, sin el mod, se prueba contra dedicado y contra un host LAN modificado.
- Escenarios manuales reproducibles descritos en `MANUAL_TESTS.md`.
- Cada incremento debe ejecutar `gradlew.bat build`; no se afirma compatibilidad de runtime sin
  arrancar cliente y servidor.

## Puntos de extensión

- `PowerEvaluationPipeline`: evaluadores de atributos, accesorios, hechizos o componentes.
- `MobTraitRegistry`: providers de traits para entidades externas.
- `ThreatCostRegistry`: costes conocidos o estimaciones conservadoras.
- `ThreatDirectorIntegration`: entrypoint Fabric transaccional para jars de compatibilidad.
- `ClaimProtectionService`: adapters trivaluados; un fallo nunca concede permiso destructivo.
- `PatternSelector`: nuevos patrones sin modificar el controlador de población.
- `TelemetryRecorder`: sink local reemplazable; la API no contempla transporte de red.

Los adapters que importen clases de otro mod deben vivir fuera del jar core. El registrar transaccional
solo publica sus extensiones si `register` termina por completo; una excepción descarta el staging.

## Riesgos de compatibilidad

| Riesgo | Mitigación del MVP |
| --- | --- |
| Conflicto con IA de otro mod | Solo cuatro tipos vanilla exactos; goals inactivos sin orden |
| Pathfinding costoso | Cuatro paths base por mob, uno contextual extra con peligro alto, 48 operaciones globales y planificación espaciada |
| Spawn injusto | Distancia 28–48, chunks cargados, cono frontal excluido y reglas vanilla |
| Saturación de entidades | Threat Budget, cap local y máximo de controladores |
| Creepers/griefing | Sin spawn dirigido; con `mobGriefing=true` tampoco se reclutan sin opt-in |
| Claim desconocido | `UNKNOWN` falla cerrado; breaching real no existe en el MVP |
| Adapter roto o ausente | Carga aislada, staging transaccional y fallback vanilla |
| Reinicio durante encuentro | No se persiste el controlador; los goals quedan sin órdenes y ceden a vanilla |
| Modpack con JDK/toolchain distinto | JAR Java 17 y dependencias Fabric fijadas por versión |

Prominence II todavía requiere inventariar sus componentes reales, versiones, atributos, accesorios,
claims, dimensiones y mobs. No se asume que el modpack posea un único mod ID.

## Plan incremental por commits

1. `chore: scaffold Fabric 1.20.1 server mod` — wrapper, metadata y entrypoint mínimo.
2. `feat: add validated Vanilla Plus server config` — JSON atómico, schema y documentación.
3. `chore: align Fabric 1.20.1 toolchain` — Loader/API/Yarn/Loom/Gradle verificados.
4. `feat: implement adaptive tactical core` — intensidad, budget, FSM, roles, sectores y patrones.
5. `feat: add optional integration API and local telemetry` — SPI, claims y NDJSON.
6. `feat: make vanilla encounters playable` — población, goals, controller, comandos y runtime.

Después del MVP:

1. Fase 2: GameTests, memoria persistente, breaching real protegido, más mobs/patrones y élites.
2. Fase 3: adapters genéricos de equipo, accesorios, magia, claims y entidades desconocidas.
3. Fase 4: integración opcional específica de Prominence II y pruebas dentro del modpack completo.
