# Threat Director — arquitectura del MVP

## Estado y plataforma

El repositorio apunta a Minecraft 1.20.1 con Fabric Loader 0.18.4, Yarn
1.20.1+build.10, Fabric API 0.92.6+1.20.1 y Java 17. El MVP es autoritativo del
servidor: no necesita entrypoint cliente, paquetes personalizados ni una interfaz gráfica.

La configuración descrita aquí ya tiene modelo, validación y carga en
`com.andresblue.threatdirector.config`. El enlace de `ConfigManager` con
`ThreatDirectorMod` se realiza en el bootstrap del runtime; se mantiene fuera del paquete de
configuración para que el cargador pueda probarse sin iniciar Minecraft.

## Objetivo del MVP

El primer hito jugable coordina grupos pequeños de zombies, esqueletos, arañas y creepers sin
modificar sus estadísticas. Incluye:

- Adventure Intensity básica y suavizada.
- Threat Budget y composiciones de 3 a 6 mobs.
- Roles Vanguard, Flanker, Harasser e Interceptor.
- Sectores terrestres, Pinza y Cierre de salida.
- Fases Stalking, Positioning, Engaging y Relief.
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
        ↓
DirectorService ── PlayerObservation/Intensity
        ↓
EncounterManager ── EncounterController
        ↓
Blackboard ── SectorAnalyzer/PatternPlan
        ↓
MobOrderStore
        ↓
Goals temporales ── navegación y ataques vanilla
```

Las dependencias avanzan hacia abajo. Los evaluadores, patrones y adapters publican resultados
mediante interfaces; no conocen el runtime que los consume.

## Componentes y responsabilidades

### Runtime

`ThreatDirectorRuntime` existe una vez por `MinecraftServer`. Posee la configuración activa, el
scheduler, el director, los encuentros, la persistencia y la telemetría. Se crea en
`SERVER_STARTED` y se cierra en `SERVER_STOPPING`; no debe sobrevivir como singleton a un segundo
servidor integrado.

`TickWorkScheduler` distribuye perfiles y controladores entre ticks. Ningún acceso a entidades,
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
histéresis configurada. Un cambio de equipo invalida el caché; salud, absorción, inventario y
contexto se observan periódicamente.

`DirectorTrack` mantiene `DORMANT`, la referencia al encuentro activo y `COOLDOWN`. Los cooldowns
usan tiempo total del mundo, no hora del sistema ni hora del día.

### Encuentros

`EncounterManager` garantiza como máximo un encuentro por grupo cercano y que una entidad no
pertenezca a dos controladores. Los controladores almacenan UUID y resuelven entidades en el mundo;
no conservan referencias después de unload.

La máquina inicial es:

```text
DORMANT → STALKING → POSITIONING → ENGAGING → RELIEF → COOLDOWN
                     ↘ ABORTED ←───────────────↗
```

Cada transición registra estado anterior, nuevo, causa, target, encuentro, tick, intensidad y
presupuesto restante. Cambio de dimensión, logout, grupo inviable, distancia excesiva, pérdida de
contacto y nivel de rendimiento 6 terminan el encuentro de forma segura.

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
predeterminada; un segundo esqueleto es el fallback seguro.

### Táctica e IA vanilla

El entorno inmediato se representa mediante ocho sectores horizontales relativos a la mirada del
jugador. Una snapshot produce como máximo 16 candidatos en un anillo de 10 a 18 bloques. Primero se
hacen comprobaciones baratas y luego, como máximo, dos intentos de path por mob. Se recalcula cada
40 ticks, tras desplazamiento significativo o fallo de ruta.

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
- `ServerTickEvents.END_SERVER_TICK`: scheduler.
- `CommandRegistrationCallback`: comandos.
- `ServerEntityEvents.EQUIPMENT_CHANGE`: invalidación del power cache.
- `ServerEntityEvents.ENTITY_UNLOAD`: limpieza de miembros.
- `ServerLivingEntityEvents.AFTER_DEATH`: bajas y memoria reciente.
- `ServerPlayConnectionEvents.DISCONNECT`: recalcular o abortar.
- `ServerEntityWorldChangeEvents.AFTER_PLAYER_CHANGE_WORLD`: cancelar persecución.
- `ServerPlayerEvents.AFTER_RESPAWN`: cooldown y limpieza de presión.

Fabric API 1.20.1 no dispone de `ServerLivingEntityEvents.AFTER_DAMAGE`. El MVP puede observar el
daño entrante sin cancelarlo y combinarlo con diferencias de salud/absorción; no debe compilar
contra un evento de versiones posteriores.

## Configuración

`ConfigManager` usa `FabricLoader.getInstance().getConfigDir()` y el archivo
`threatdirector.json`. Un constructor que recibe `Path` mantiene las pruebas aisladas.

El archivo ausente se crea con JSON UTF-8 formateado mediante escritura temporal y movimiento
atómico cuando el sistema de archivos lo soporta. La carga sigue este orden:

1. Parsear a una nueva snapshot.
2. Validar todas las secciones y relaciones.
3. Publicar la snapshot completa en una asignación `volatile`.
4. Mantener la última snapshot válida si parseo o validación fallan.

Las secciones del schema 1 son:

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
- el límite absoluto no supera 10 mobs;
- cada mob permitido posee un threat cost positivo;
- distancias, fases y umbrales de rendimiento mantienen orden coherente.

Los encuentros activos deben conservar la snapshot con la que nacieron. Una recarga solo afecta
nuevos encuentros.

## Persistencia y telemetría

Un `PersistentState` del Overworld guarda intensidad suavizada, cooldown y resumen reciente por
UUID. Los controladores y mobs activos no se serializan: tras un reinicio se liberan a vanilla.

La telemetría, desactivada por defecto, usa NDJSON local bajo el directorio del mundo. Una cola
acotada evita bloquear ticks; un error de archivo deshabilita el sink, no el director. No existe
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

Además de los umbrales de tick, cada tick posee un presupuesto de microsegundos y de operaciones de
planner. El trabajo restante se aplaza; nunca se carga un chunk para completar una táctica.

## Compatibilidad futura

El core publicará un entrypoint `threatdirector` mediante `ThreatDirectorIntegration`. Los adapters
que importen clases de otro mod vivirán en jars separados y declararán esa dependencia en su propio
`fabric.mod.json`. Cada integración registra evaluadores, traits, costes, claims y plantillas dentro
de un bloque aislado; una excepción deshabilita solo ese adapter.

Los claims devuelven `ALLOW`, `DENY` o `UNKNOWN`. Cualquier función destructiva interpreta
`UNKNOWN` como denegación. Prominence II se identifica por componentes y versiones reales, no por
asumir un ID del modpack.

## Estrategia de pruebas

- JUnit puro para config, intensidad, smoothing, budget, roles, patrones, FSM y gobernador.
- GameTests para goals, spawn seguro, lifecycle y garantía de cero cambios de bloques.
- Arranque dedicado con solo Fabric API y Threat Director.
- Escenarios manuales reproducibles descritos en `MANUAL_TESTS.md`.
- Cada incremento debe ejecutar `gradlew.bat build`; no se afirma compatibilidad de runtime sin
  arrancar cliente y servidor.
