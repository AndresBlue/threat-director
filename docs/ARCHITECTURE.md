# Threat Director — arquitectura de la versión 0.3.0

## Estado y plataforma

El repositorio apunta a Minecraft 1.20.1 con Fabric Loader 0.18.4, Yarn
1.20.1+build.10, Fabric API 0.92.11+1.20.1 y bytecode Java 17. La máquina de
desarrollo inspeccionada dispone de Temurin 21, que compila con `--release 17`.
La lógica de la versión 0.3.0 es autoritativa del servidor. En dedicado y en LAN basta con instalarlo
en el lado que aloja el servidor para ejecutar IA, threat y encuentros; los clientes remotos pueden
conectarse sin el mod. El overlay opcional sí requiere el JAR en el cliente que quiera verlo: el host
integrado lo necesita porque también ejecuta el servidor y renderiza su propio HUD.

La configuración descrita aquí ya tiene modelo, validación y carga en
`com.andresblue.threatdirector.config`. El enlace de `ConfigManager` con
`ThreatDirectorMod` se realiza en el bootstrap del runtime; se mantiene fuera del paquete de
configuración para que el cargador pueda probarse sin iniciar Minecraft.

## Alcance de 0.3.0

El runtime coordina grupos vanilla y añade capacidades server-side por dificultad. Incluye:

- Adventure Intensity básica y suavizada.
- Threat Budget y poblaciones contextuales: base configurada 32, cap Hard contextual `64–96`, hard
  cap por defecto 96 y límite validable 128.
- Roles Vanguard, Flanker, Harasser e Interceptor.
- Sectores terrestres, Pinza y Cierre de salida.
- Fases Stalking, Positioning, Engaging, Searching, Climax, Retreating y Relief.
- Límites estrictos de tiempo, entidades y pathfinding.
- BreachPlanner estrictamente simulado y un guard separado para cambios reales de mobs.
- Comandos administrativos y telemetría local opcional.
- Perfiles Easy vanilla, Normal parcial y Hard Apocalypse para familias hostiles vanilla.

No forman parte de 0.3.0 los mobs externos, adapters concretos de Prominence II, élites, PvPvE ni
reconocimiento avanzado de estructuras. `BreachPlanner` no ejecuta roturas, pero
los zombies Hard sí pueden retirar una obstrucción compatible mediante `MobGriefingGuard`; las
telarañas Hard usan el mismo guard.

## Capas

```text
Fabric lifecycle/events
        ↓                         ↓
ThreatDirectorRuntime      módulos de dificultad/mixins
        ↓                       ↓
PlayerObservationService ── ThreatHudService ── paquete HUD opcional ── overlay cliente
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
        ↓
MobGriefingGuard ── gamerule + claims + chunk/border + spawn seguro
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
histéresis. En 0.3.0, equipo, salud, absorción, inventario y contexto se capturan cada 100 ticks;
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

`ThreatHudService` sincroniza por jugador únicamente progreso, color y visibilidad. El cliente registra
un canal opcional y dibuja una barra segmentada sin texto debajo de XP y sobre la hotbar; el servidor
no depende de un overlay para ejecutar la IA. Los operadores se muestran por defecto y una preferencia
explícita permite habilitar la barra para no operadores. Si el cliente no tiene el JAR, simplemente no
renderiza el overlay. Todas las ramas del comando `hud` requieren OP2, pero pueden dirigirse a cualquier
jugador conectado.

### Encuentros

`EncounterManager` garantiza como máximo un encuentro por grupo cercano y que una entidad no
pertenezca a dos controladores. Los controladores almacenan UUID y resuelven entidades en el mundo;
no conservan referencias después de unload.

En un servidor integrado abierto a LAN, el scan automático solo considera al host como posible
ancla. Los clientes remotos pueden integrar su grupo o recibir un objetivo administrativo explícito,
pero nunca originan por sí solos un encuentro automático. En dedicado se prefiere el jugador con
mayor potencia observada y se usa el UUID como desempate determinista.

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

Durante `ENGAGING` o `CLIMAX`, la presión Hard puede solicitar hasta diez oleadas de refuerzo. En
superficie son hasta 13 miembros cada 100 ticks; con peligro completo, hasta 20 cada 80 ticks. Cada
oleada vuelve a pasar por Threat Budget, ownership global, población segura, cap contextual, límite
difícil, presupuesto de planner y gobernador; necesita al menos dos miembros vivos y percepción
confirmada del objetivo, y nunca garantiza que aparecerá si no existe una población válida.
Telemetría y logs incluyen número de oleada, zona y peligro.

`GroupBlackboard` contiene solamente información parcial: última posición vista, confianza,
velocidad reciente, sectores reservados, slots cuerpo a cuerpo y fallos de ruta. La confianza
decae si ningún miembro ve al jugador.

### Población y presupuesto

Se reclutan primero mobs naturales sin nombre, no persistentes, libres de otro encuentro y sin un
combate ajeno. Si faltan miembros, se buscan posiciones entre 32 y 64 bloques que:

- estén en chunks cargados y dentro del world border;
- cumplan restricciones vanilla de spawn, luz, suelo y colisión;
- no estén en visión directa ni demasiado cerca del jugador;
- respeten Peaceful, `doMobSpawning` y el límite local de hostiles.

Costes iniciales: zombie 1.0, skeleton 1.3, spider 1.1 y creeper 1.8. Reclutados y generados
consumen el mismo presupuesto. Schema 3 habilita la generación dirigida y el reclutamiento de
creepers; sus explosiones siguen siendo vanilla y obedecen `mobGriefing`.

El schema 3 configura `minMobs=4`, `maxMobs=32`, `hardMaxMobs=96` y
`maxNearbyHostiles=160`. Hard interpola el cap contextual entre 64 en superficie y 96 con peligro
completo; sus mínimos son 23/34 y sus slots melee 23/34. El escalado multijugador y las oleadas
siguen sujetos al Threat Budget y al máximo difícil. La validación permite personalizar
`hardMaxMobs` hasta un máximo absoluto de 128.

### Táctica e IA vanilla

El entorno inmediato se representa mediante diez sectores relativos a la mirada, la altura, la
ruta de escape, la cobertura y la reserva. Una snapshot produce como máximo 64 candidatos en un
anillo configurable de 12 a 24 bloques por defecto. Primero se hacen comprobaciones baratas y
luego se permiten como máximo cinco intentos de path por mob, siempre dentro del presupuesto global.
La cadencia base de replan es 20 ticks y la presión la acelera o ralentiza; también se recalcula tras
desplazamiento significativo o fallo de ruta.

La predicción de escape combina velocidad reciente y mirada. Pinza requiere sectores opuestos;
Cierre de salida requiere un Interceptor y un punto adelantado viable. Si el plan falla, el grupo
retorna a ataque vanilla distribuido.

El director táctico usa un accessor de `MobEntity.goalSelector` y `MobEntity.targetSelector`. Al
reclutar una entidad se instalan dos goals:

- movimiento/observación temporal con prioridad inferior a emergencias vanilla;
- compuerta temporal del target durante Stalking, Positioning y Relief.

En Engaging se libera la compuerta, se establece el target y los goals vanilla realizan el ataque.
Al retirar la orden, los goals añadidos quedan inactivos. No se reemplaza el árbol de IA, no se
teletransporta y no se recalcula navegación cada tick.

### Habilidades vanilla por dificultad

Las habilidades se aplican en el servidor a hostiles vanilla cargados, no solo a miembros de un
encuentro. No registran entidades, efectos ni atributos de contenido: combinan goals, tags,
equipamiento, efectos y movimiento vanilla. El único canal adicional es el overlay HUD opcional.
`enabled=false` selecciona el comportamiento Easy.

- **Easy:** no añade targeting, buffs ni habilidades y la probabilidad de encuentros automáticos es
  cero. Los comandos OP aún pueden crear una escena, respetando todas las salvaguardas.
- **Targeting común:** todos los `Monster` reciben un `ActiveTargetGoal` server-side con rango por
  jugador. El escalado empieza sobre 35 de threat y llega a 48 para genéricos, 64 para esqueletos
  Normal, 96 para zombis Hard y 128 para esqueletos Hard. En Hard puede ignorar visibilidad y se
  apaga mientras el mob está administrado por un encuentro.
- **Skeletons:** dos `BowAttackGoal` de mayor prioridad escalan el alcance a 24–64 en Normal y
  32–128 en Hard; el original sigue disponible para Easy. El mixin de `attack` conserva proyectil y
  sonido vanilla, predice movimiento y el overlay de flecha aplica guiado progresivo por threat.
  Normal tiene 10 % de lentitud; Hard tiene 32 % total, repartido por igual entre lentitud, veneno,
  náusea y ceguera. El kiting proviene del goal de arco y Hard puede hacer dash hacia atrás con
  cooldown. Los tags son `threatdirector_apocalypse_skeleton` y
  `threatdirector_apocalypse_arrow`.
- **Creepers:** Normal usa multiplicador 1.35 al aproximarse por la espalda y una mecha aleatoria de
  70–100 % de la vanilla. Hard usa multiplicador 2.0, mecha de 30–100 %, pasos silenciosos y dash
  acotado; tras 20 ticks bloqueado contra pared, sin línea de visión y a 4.5 bloques puede encenderse
  solo si `mobGriefing` está activo. La explosión y sus paquetes siguen siendo vanilla.
- **Zombies:** Normal equipa herramienta o espada con 40 % de probabilidad. Hard usa 85 %, añade
  pico al pool y escudo con 30 %, puede llamar hasta dos refuerzos con 20 % por evento de daño,
  cooldown 200 ticks y cap de ocho zombies cercanos. Cada 30 ticks puede intentar retirar un único
  bloque cargado, sin block entity ni drops, dureza `<=5` y apropiado para su herramienta.
- **Spiders:** Normal recibe Speed I y Jump Boost I al generarse. Hard recibe Speed II, Jump Boost
  II, Regeneration I y Strength I; al golpear puede colocar una telaraña cada 100 ticks y al morir
  intenta generar dos cave spiders vanilla. El tag `threatdirector.spider_hatchling` impide recursión.
- **Endermen:** al daño de un hostile por un jugador, Normal alerta hasta dos endermen en 12 bloques
  con 50 % por aliado; Hard alerta hasta seis en 24 bloques. Hard adopta una postura de 1.95 bloques
  durante combate y, tras 30 ticks de ventaja de pilar, intenta teleport con cooldown 80.
- **Otras familias:** además del targeting común, Normal/Hard aplican presión temática:
  brujas/illagers debilitan o coordinan aliados, blazes/ghasts/phantoms reciben impulso de alcance,
  guardianes aplican fatiga en agua, drowned/husks aplican fatiga/hambre, enjambres comparten target
  y ravagers/piglin brutes cargan en Hard. La familia residual `OTHER` permanece genérica y no recibe
  una habilidad temática.

`MobGriefingGuard` es la única autoridad para las roturas de zombie y las telarañas añadidas por el
mod. Exige `mobGriefing`, runtime activo, chunk cargado, world border, respuesta de claims que permita
destrucción y, en Overworld, estar fuera de la protección real del spawn. `PROTECTED`, `UNKNOWN` o
un provider que falla deniegan la acción.

## Eventos Fabric

- `ServerLifecycleEvents.SERVER_STARTED` y `SERVER_STOPPING`: runtime y cierre.
- `ServerTickEvents.END_SERVER_TICK`: observación, gobernador, scheduler y encuentros.
- `ServerEntityEvents.ENTITY_LOAD`: goals de esqueletos y otras familias hostiles, con sets débiles
  para evitar instalaciones duplicadas tras unload/reload.
- `CommandRegistrationCallback`: comandos.
- `ServerLivingEntityEvents.AFTER_DEATH`: bajas y memoria reciente.

Logout, cambio de dimensión, unload y cambios de salud se detectan al resolver UUID y validar el
estado vivo en cada ciclo del controlador. Fabric API 1.20.1 no dispone de
`ServerLivingEntityEvents.AFTER_DAMAGE`; 0.3.0 infiere daño sin cancelarlo mediante diferencias de
salud/absorción y mantiene una comprobación directa de salud crítica en el scheduler.

## Configuración

`ConfigManager` usa `FabricLoader.getInstance().getConfigDir()` y el archivo
`threatdirector.json`. Un constructor que recibe `Path` mantiene las pruebas aisladas.

El archivo ausente se crea con JSON UTF-8 formateado mediante escritura temporal y movimiento
atómico cuando el sistema de archivos lo soporta. La carga sigue este orden:

1. Parsear a una nueva snapshot.
2. Si es schema 1 o 2, migrar: un preset legado exacto recibe todos los defaults schema 3; un archivo
   personalizado conserva sus secciones y valores y solo cambia `schemaVersion`.
3. Validar todas las secciones y relaciones como schema 3.
4. Reescribir atómicamente el archivo cuando hubo migración.
5. Publicar la snapshot completa en una asignación `volatile`.
6. Mantener la última snapshot válida si parseo, migración, validación o escritura fallan.

Las secciones del schema 3 son:

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
- el default difícil es 96 mobs y el límite configurable absoluto es 128;
- cada mob permitido posee un threat cost positivo;
- distancias, fases y umbrales de rendimiento mantienen orden coherente.

La publicación de una recarga es atómica, pero un encuentro activo no representa una snapshot
íntegra y aislada del runtime. Su controlador conserva la configuración de construcción para FSM,
táctica y órdenes; el manager aplica la configuración vigente a seguridad, presupuesto global,
refuerzos, finalización/cooldown, observación, HUD y gobernador. Los encuentros nuevos sí nacen por
completo con la configuración recargada.

## Persistencia y telemetría

En 0.3.0, intensidad, memoria reciente y cooldown viven en memoria y se limpian al reiniciar el
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
default schema 3 comparte 192 operaciones y 20 000 microsegundos de planner por tick entre
población, refuerzos y controladores,
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

- La versión 0.3.0 ejecuta JUnit puro para config, intensidad, smoothing, budget, roles, patrones,
  FSM, compatibilidad, telemetría, simulación de breach, gobernador y políticas de habilidades.
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

| Riesgo | Mitigación en 0.3.0 |
| --- | --- |
| Conflicto con IA de otro mod | Goals acotados por dificultad; la capa táctica queda inactiva sin orden |
| Pathfinding costoso | Cinco paths base por mob, uno contextual extra con peligro alto, 192 operaciones globales y planificación espaciada |
| Spawn injusto | Distancia 32–64, chunks cargados, cono frontal excluido y reglas vanilla |
| Saturación de entidades | Threat Budget, cap local y máximo de controladores |
| Creepers/griefing | Explosión vanilla bajo `mobGriefing`; coste, cap, spawn seguro y budget siguen activos |
| Cambios de bloque de mobs | `MobGriefingGuard` centraliza gamerule, claims, chunk/border y spawn seguro |
| Claim desconocido | `UNKNOWN` falla cerrado para toda acción destructiva añadida por el mod |
| Adapter roto o ausente | Carga aislada, staging transaccional y fallback vanilla |
| Reinicio durante encuentro | No se persiste el controlador; los goals quedan sin órdenes y ceden a vanilla |
| Modpack con JDK/toolchain distinto | JAR Java 17 y dependencias Fabric fijadas por versión |

Prominence II todavía requiere inventariar sus componentes reales, versiones, atributos, accesorios,
claims, dimensiones y mobs. No se asume que el modpack posea un único mod ID.

## Evolución posterior

1. Añadir GameTests y pruebas de arranque dedicadas para mixins, goals y guards destructivos.
2. Persistir memoria/cooldowns resumidos mediante `PersistentState` sin serializar entidades vivas.
3. Incorporar adapters genéricos de equipo, accesorios, magia y claims en jars separados.
4. Inventariar Prominence II por componentes/versiones reales antes de crear una integración opcional.
