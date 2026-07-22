# Threat Director — guía completa de comandos

Esta guía describe el árbol de comandos del MVP para Minecraft 1.20.1. Todos los comandos son
autoritativos del servidor y comienzan con `/threatdirector`.

## Convenciones

- `[player]` es un jugador conectado. Puede ser un nombre o un selector que resuelva exactamente
  un jugador.
- Cuando se omite `[player]`, el emisor debe ser un jugador. La consola debe usar una variante con
  objetivo explícito cuando exista.
- **OP2** significa nivel de permiso 2 o superior.
- Los argumentos escritos entre `<...>` son obligatorios.
- Los estados y escenas no distinguen mayúsculas de minúsculas.
- Los comandos de lectura devuelven éxito con código `1`; los rechazos controlados devuelven `0` y
  muestran el motivo en rojo.

La regla de permisos es: las mutaciones del director y las operaciones sobre otro jugador requieren
OP2. La única mutación disponible para un jugador sin permisos es mostrar u ocultar su propio HUD.

## Resumen de sintaxis y permisos

| Comando | Permiso | Consola | Propósito |
| --- | ---: | --- | --- |
| `/threatdirector status` | Libre | No | Resumen del jugador emisor |
| `/threatdirector status <player>` | OP2 | Sí | Resumen de otro jugador |
| `/threatdirector intensity` | Libre | No | Diagnóstico adaptativo individual |
| `/threatdirector intensity <player>` | OP2 | Sí | Diagnóstico individual de otro jugador |
| `/threatdirector threat` | Libre | No | Threat efectivo que coincide con el HUD |
| `/threatdirector threat <player>` | OP2 | Sí | Threat efectivo de otro jugador o su grupo |
| `/threatdirector threat set <0..100>` | OP2 | No | Fuerza el threat propio o grupal |
| `/threatdirector threat set <0..100> <player>` | OP2 | Sí | Fuerza el threat del objetivo o dueño grupal |
| `/threatdirector threat clear` | OP2 | No | Elimina el override propio o grupal |
| `/threatdirector threat clear <player>` | OP2 | Sí | Elimina el override del objetivo o dueño grupal |
| `/threatdirector hud` | Libre | No | Consulta el HUD propio |
| `/threatdirector hud <player>` | OP2 | Sí | Consulta el HUD de otro jugador |
| `/threatdirector hud on\|off` | Libre | No | Muestra u oculta el HUD propio |
| `/threatdirector hud on\|off <player>` | OP2 | Sí | Cambia el HUD de otro jugador |
| `/threatdirector start` | OP2 | No | Inicia un encuentro forzado en Stalking |
| `/threatdirector start <player>` | OP2 | Sí | Inicia un encuentro para el objetivo |
| `/threatdirector cancel` | OP2 | No | Envía el encuentro propio o grupal a Relief |
| `/threatdirector cancel <player>` | OP2 | Sí | Cancela el encuentro que incluye al objetivo |
| `/threatdirector cancel all` | OP2 | Sí | Envía todos los encuentros a Relief |
| `/threatdirector phase <state>` | OP2 | No | Salta de fase dentro de un encuentro existente |
| `/threatdirector phase <state> <player>` | OP2 | Sí | Salta la fase del encuentro del objetivo |
| `/threatdirector scene <state>` | OP2 | No | Crea, fuerza o reinicia una escena de prueba |
| `/threatdirector scene <state> <player>` | OP2 | Sí | Hace lo mismo para el encuentro del objetivo |
| `/threatdirector roles` | Libre | No | Lista los roles de los mobs del encuentro |
| `/threatdirector sectors` | OP2 | No | Visualiza posiciones tácticas con partículas |
| `/threatdirector budget` | Libre | No | Muestra el ledger de Threat Budget |
| `/threatdirector breach simulate` | OP2 | No | Ejecuta una simulación no destructiva |
| `/threatdirector config reload` | OP2 | Sí | Recarga y valida la configuración JSON |
| `/threatdirector integrations` | Libre | Sí | Resume integraciones y claims |
| `/threatdirector performance` | Libre | Sí | Consulta el PerformanceGovernor |
| `/threatdirector performance reset` | OP2 | Sí | Reinicia temporalmente el governor para pruebas |
| `/threatdirector preset` | Libre | Sí | Muestra el preset activo |
| `/threatdirector telemetry` | Libre | Sí | Muestra los contadores de telemetría local |

`roles`, `sectors`, `budget` y `breach simulate` no tienen argumento `[player]`; por ello deben
ejecutarse dentro del juego. La consola puede usar los demás comandos que indiquen “Sí”, aportando
un jugador explícito cuando sea necesario.

## Estado, intensity y threat

### `/threatdirector status [player]`

Muestra threat efectivo y banda, modo adaptativo o forzado, marca de grupo, **presión de encuentro**
y zona, poder individual, fase, cantidad de mobs y cooldown restante en ticks. En un encuentro
multijugador, el threat, la presión y la fase pertenecen al ancla primaria; poder y cooldown siguen
siendo propios del jugador consultado.

Sin argumento, cualquier jugador puede consultar su estado. Consultar a otro requiere OP2.

### `/threatdirector intensity [player]`

Es el diagnóstico **individual**. No es necesariamente idéntico a la barra de un participante
secundario. Informa intensidad efectiva individual, override, pista adaptativa, objetivo crudo,
poder, entorno, preparación, dominio, estrés y carga. Si el jugador es secundario, añade por separado
el threat del HUD grupal y el nombre del dueño.

Antes de la primera muestra puede indicar que todavía no existe un desglose. El muestreo ocurre según
`intensity.sampleIntervalTicks` —100 ticks en `VANILLA_PLUS`— y no cada frame del HUD.

### `/threatdirector threat [player]`

Consulta el threat efectivo `0..100` que usa el HUD. Las bandas son:

- `LOW`: menos de 40;
- `TACTICAL`: 40 a menos de 65;
- `HIGH`: 65 a menos de 85;
- `EXTREME`: 85 a 100.

El valor adaptativo usa suavizado e histéresis, por lo que la banda puede tardar deliberadamente en
subir o bajar. Un override usa directamente los umbrales anteriores. Durante un encuentro grupal,
un secundario consulta el estado del objetivo primario y recibe `compartido con <nombre>`.

La presión de encuentro no sustituye la barra. Es la intensidad que usa la composición táctica tras
combinar el threat adaptativo con dificultad y entorno. Easy, Normal y Hard son progresivos; noche,
luz `<=7` y cueva aumentan frecuencia, cap, quorum, atacantes simultáneos, replanning y refuerzos.
Un override OP conserva exactamente su número y no recibe esta bonificación de intensidad, aunque
Hard y la zona todavía determinan la forma táctica y el hard cap.

Referencia de los defaults v2 de `VANILLA_PLUS` en superficie, antes de multiplicadores de grupo o
límites de budget:

| Dificultad | Hazard | Ajuste adaptativo | Chance/scan | Cap | Mínimo | Melee | Replan | Refuerzo | Cooldown |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| Easy | .25 | −5 | 3.875 % | 6 | 3 | 3 | 38 t | ninguno | ×1.00 |
| Normal | .50 | 0 | 9.5 % | 8 | 4 | 4 | 30 t | 1/600 t | ×.75 |
| Hard | .75 | +5 | 18.875 % | 10 | 5 | 5 | 23 t | 2/300 t | ×.50 |

Hard + noche + oscuridad + cueva alcanza hazard `1`: ajuste adaptativo `+10`, chance `32 %` por
scan, cap `12`, mínimo `5`, cinco melee, replan cada `15` ticks, reposición de hasta tres cada `240`
ticks y cooldown `×.25`. Son techos de política; el Threat Budget, governor, terreno y cap local
pueden producir menos miembros. El inicio automático todavía exige noche u oscuridad por defecto.

## Overrides de threat

### `/threatdirector threat set <0..100> [player]`

Requiere OP2. Solo acepta un número finito entre 0 y 100; `NaN`, infinito y valores fuera del rango
se rechazan sin modificar estado.

El override sustituye el valor efectivo usado por HUD y director, actualiza inmediatamente la barra,
influye en transiciones y patrones y no detiene el cálculo adaptativo. Si existe antes de crear un
encuentro, también determina presupuesto y población. No recalcula el Threat Budget ni la población
de un encuentro ya creado.

Si el objetivo indicado es un secundario, se modifica al primario porque el encuentro usa threat
compartido. El feedback nombra al dueño realmente modificado.

Un valor bajo no se eleva en silencio. Si no puede financiar el quorum contextual, `start` o `scene`
devuelve `threat budget mob cap <cap> is below the contextual minimum <min>` o
`contextual horde is unaffordable: minimum=<min>, available=<n> milli-threat`.

### `/threatdirector threat clear [player]`

Requiere OP2. Elimina el override —o el del primario si se indicó un secundario— y vuelve al último
valor adaptativo, que continuó actualizándose en segundo plano.

Overrides, intensity, memoria reciente y cooldown viven en memoria. Se pierden al reiniciar. El
override de un jugador desconectado también desaparece cuando su estado supera la ventana de
retención.

## HUD de amenaza

### `/threatdirector hud [player]`

Consulta si el HUD está `activado` u `oculto`. Consultar la preferencia de otro requiere OP2.

### `/threatdirector hud on|off [player]`

Cada jugador puede mostrar u ocultar su barra sin ser OP. Modificar a otro requiere OP2.

Ocultarla retira el `ServerBossBar` del cliente, pero no detiene intensity, threat, encuentros ni IA,
y no afecta a otros miembros del grupo. La preferencia dura la sesión del servidor, incluso tras una
desconexión, pero se restablece al reiniciar.

Los espectadores no reciben barra. Al volver a un modo no espectador reaparece en el siguiente
refresco si la preferencia está activa. El HUD usa paquetes bossbar vanilla, se sincroniza cada 10
ticks y no necesita overlay o paquete personalizado.

## Crear y terminar encuentros

### `/threatdirector start [player]`

Requiere OP2. Construye una población segura y crea un encuentro en Stalking. El feedback incluye
UUID, número de mobs y Threat Budget total de por vida —base más reserva de refuerzos—.

Un inicio forzado omite cooldown normal, mínimo adaptativo, noche/oscuridad, probabilidad aleatoria y
selección automática del miembro preferido. No omite las salvaguardas de la sección siguiente.

Sin override toma `max(55, director.minimumIntensity, intensity actual)` como base y después aplica
presión contextual; Easy puede reducir la cifra final y Hard aumentarla. Con override usa exactamente
el valor solicitado; por eso uno inferior a 35 normalmente no financia el quorum. Con los defaults
v2 de `VANILLA_PLUS`, Hard oscuro puede llegar al hard cap de 12 y mantener presión mediante hasta
cuatro oleadas, de hasta tres integrantes cada una, sin superar ese límite concurrente.

Las oleadas son reposiciones seguras, no mobs adicionales por encima del cap. Solo se intentan en
Engaging o Climax, con al menos dos miembros vivos, governor 0–4, intervalo cumplido, milli-threat y
planner disponibles, chunks cargados y spawn/rutas válidos. Además, en ese mismo ciclo del
controlador al menos un mob dirigido debe estar a 48 bloques o menos y ver al objetivo primario.
Forzar la escena no inventa esa percepción: sin línea de visión confirmada no aparece una oleada.
Cada fallo se difiere sin romper el límite de ownership.

### `/threatdirector cancel [player|all]`

Requiere OP2. Sin argumento cancela el encuentro del emisor; con jugador, el encuentro que lo
incluye; con `all`, todos los activos.

Cancelar envía el encuentro a Relief, borra targets y emite retirada. Al finalizar se liberan roles
y propiedad: los mobs naturales vuelven a IA vanilla y los generados por el director se descartan.

## `phase` frente a `scene`

### `/threatdirector phase <state> [player]`

Requiere OP2 y un encuentro existente. Es un salto administrativo de bajo nivel. Admite Stalking,
Positioning, Engaging, Searching, Climax, Relief y Retreating si no son ya la fase actual. Dormant,
Cooldown y Aborted se rechazan.

`phase` no reinicia el temporizador al pedir la fase actual. Para Relief y Retreating cambia primero
el estado y actualiza las órdenes cuando el controlador se ejecute de nuevo. Bajo degradación alta
ese ciclo puede estar espaciado. Para pruebas reproducibles o retirada inmediata use `scene` o
`cancel`.

### `/threatdirector scene <state> [player]`

Requiere OP2 y es la interfaz recomendada para pruebas. Admite y autocompleta `stalking`,
`positioning`, `engaging`, `searching`, `climax`, `relief` y `retreating`.

Si no existe encuentro, crea primero uno mediante la ruta segura de `start`. También puede reiniciar
la fase actual, aplica inmediatamente Relief/Retreating y actúa sobre el controlador compartido al
dirigirse a un secundario.

Forzar una escena omite requisitos narrativos, pero no las comprobaciones del controlador. En el
siguiente ciclo puede salir por target inválido, dimensión, spawn, distancia, navegación, derrota,
salud crítica o governor nivel 6.

Pedir Relief o Retreating sin encuentro puede crear una población y mandarla de inmediato a retirada.
Sirve para probar la fase, aunque no representa un flujo natural.

## Salvaguardas de `start` y `scene`

Cuando `scene` debe crear un encuentro usa la misma ruta de `start`. Ambos respetan:

- `enabled=true`;
- objetivo vivo en Survival;
- salud mínima y ventana de muerte reciente;
- dificultad distinta de Peaceful;
- dimensión permitida;
- ausencia de otro encuentro propio o grupal cercano;
- protección alrededor del spawn;
- límite global de controladores y hard mob cap;
- permisos del PerformanceGovernor;
- slice administrativa acotada del planner para la formación forzada;
- tipos de mob y costes válidos;
- chunks ya cargados y world border;
- suelo, luz, colisión, ruta y reglas vanilla;
- límite local de hostiles;
- `doMobSpawning` para generación dirigida.

Con defaults, el objetivo debe estar en Overworld, fuera del radio de 128 bloques del spawn, con al
menos 50 % de salud y sin una muerte en los últimos 12 000 ticks.

La formación forzada usa una slice administrativa independiente del allowance y del deadline global
del tick: `max(performance.maxPlannerOperationsPerTick, tactical.maxCandidatesPerSnapshot)`, 48
operaciones con `VANILLA_PLUS`, dentro de una ventana propia acotada de al menos 25 ms. No omite
ninguna salvaguarda de entidades, chunks, rutas o caps. Los inicios automáticos y las oleadas sí
comparten el planner global de `3500 µs/48 ops` por defecto.

El reclutamiento natural ocurre antes de generar el déficit. `doMobSpawning=false` bloquea mobs
nuevos, no entidades elegibles ya existentes. Los creepers dirigidos están desactivados por defecto;
con `mobGriefing=true` tampoco se recluta uno natural sin opt-in.

## Diagnóstico táctico

### `/threatdirector roles`

No requiere OP. Muestra pares como `1234abcd=FLANKER`; el identificador es el prefijo del UUID, no el
tipo de mob.

### `/threatdirector sectors`

Requiere OP2. Genera seis partículas `END_ROD` en cada posición táctica y comunica cuántas visualizó.
Las partículas pueden ser visibles para otros jugadores cercanos.

### `/threatdirector budget`

No requiere OP. Muestra `total` desglosado en `base` y `reserva`, gasto de `mobs activos`, gasto de
`mobs retirados`, reserva de `patrones` y `restante`.

Los valores usan **milli-threat**: `1000` equivale a `1.0` en el JSON. Un skeleton con coste `1.3`
consume `1300`. Los budgets mostrados por `start` y `scene` usan la misma unidad.

El budget es un ledger **de por vida del encuentro**. La base refleja intensity, grupo y governor;
si la presión permite refuerzos, se añade una reserva acotada —desde cero hasta otro 100 % de la
base— para sostener posibles oleadas. `total = base + reserva`; no representa mobs simultáneos y no
se recalcula al cambiar threat después de crear el controlador. La reserva queda bloqueada durante
la población inicial y los patrones tácticos solo pueden reservar saldo de la base: la porción de
refuerzos no puede ser consumida por un patrón.

Cuando un mob dirigido muere o se retira del encuentro durante el combate, su coste pasa
de `mobs activos` a `mobs retirados`. Libera un hueco del cap concurrente, pero esos milli-threat
siguen gastados: una baja no financia gratis a su reemplazo. Una oleada debe pagar sus miembros con
el `restante` del budget vitalicio, que incorpora la reserva. Solo un intento que se revierte antes
de quedar comprometido libera sus asignaciones.

Con los defaults, los caps concurrentes por intensity son `0` bajo 35, `6` desde 35, `9` desde 60 y
`12` desde 85. El grupo puede escalarlos hasta el hard cap; la presión fija su techo contextual, y
costes/rendimiento pueden reducir la población financiable o agotar la reserva antes de cuatro
oleadas.

## BreachPlanner de solo lectura

### `/threatdirector breach simulate`

Requiere OP2 y un jugador. Nunca modifica bloques, block entities ni inventarios. Usa la posición del
jugador como objetivo, un mob del encuentro o un punto cercano como origen, fase activa o Engaging
hipotético, política `SOFT_BLOCKS_ONLY`, rol Breacher y cooldowns hipotéticamente disponibles.

El resultado muestra decisión, motivo, bloques, coste, protección, seguridad y nodos visitados.
Puede denegar por intensity, fase, claim, spawn, dureza, contenedor, block entity, bloque sensible,
lava, presupuesto o ruta insegura.

Claims conocidos sin adapter responden `UNKNOWN` y fallan cerrado. Incluso `WOULD_BREACH` significa
“sería candidato”; el MVP no realiza la acción.

## Configuración y diagnóstico del servidor

### `/threatdirector config reload`

Requiere OP2 y funciona en consola. Lee `config/threatdirector.json`, valida una snapshot completa y
solo entonces la publica. Un error de I/O, JSON o validación conserva la última versión aceptada.

La configuración nueva afecta observación, governor y futuros encuentros en ciclos posteriores. Los
controladores activos retienen principalmente su snapshot; límites globales como salud urgente,
rendimiento y cooldown final siguen la configuración vigente. `enabled=false` limpia encuentros y HUD
en el siguiente tick.

Defaults v2 de `VANILLA_PLUS`: scan `60` ticks, chance base `.04`, cooldown `3600–7200`, población
`3/8/12` (mínimo/cap normal/hard cap), cap local `24`, `32` candidatos, `4` intentos de path por mob,
replan `30`, melee base `3`, planner `3500 µs/48 ops` y umbrales MSPT
`40/48/56/65/80/100`. Threat Budget permite caps por banda de `6/9/12`. Un v1 intacto adopta estos
defaults; un v1 personalizado conserva sus valores y solo migra el schema a 2.

### `/threatdirector integrations`

Muestra IDs cargados, fallos y detección de claims. Los adapters se descubren al arrancar el runtime,
no en cada consulta.

### `/threatdirector performance`

Muestra nivel `0..6`, MSPT, intervalo de decisión, límite de candidatos y encuentros activos. Los
niveles 5 y 6 rechazan nuevos encuentros; el 6 devuelve temporalmente los controladores a vanilla.
También informa si acepta encuentros y los máximos configurados de operaciones/microsegundos; no
muestra cuánto allowance queda en el tick actual.

El governor toma una muestra cada 20 ticks. Al arrancar aplica 100 ticks de warmup que ignoran solo
la degradación ordinaria. La emergencia crítica nunca queda oculta: dos muestras críticas
consecutivas llevan directamente a nivel 6 aun dentro del warmup. Con `VANILLA_PLUS`, crítico
significa MSPT `>=100` o ratio de capacidad `>=1.75`; la degradación ordinaria sostenida requiere
100 ticks y la recuperación de un nivel requiere 600.

### `/threatdirector performance reset`

Requiere OP2 y funciona en consola. Reinicia temporalmente el PerformanceGovernor a nivel 0 y limpia
sus contadores de degradación, recuperación y emergencia para pruebas controladas.

No cambia MSPT real, umbrales del JSON, encuentros, planner ni safeguards. Abre 100 ticks que ignoran
la degradación ordinaria, pero no es un bypass de emergencia: dos muestras críticas consecutivas
pueden devolverlo a nivel 6 durante ese mismo periodo. Al terminar, una carga alta no crítica vuelve
a degradarlo gradualmente.

```text
/threatdirector performance
/threatdirector performance reset
/threatdirector performance
/threatdirector scene engaging <player>
```

Si la escena aún falla, el reset no corrige spawn, salud, muerte reciente, dimensión, población o
presupuesto.

### `/threatdirector preset`

Muestra el preset activo, actualmente `VANILLA_PLUS` en el MVP.

### `/threatdirector telemetry`

Muestra enabled, closed, cola, capacidad, aceptados, escritos, descartes, fallos y último error. No
fuerza un flush ni activa telemetría. La telemetría es local, no usa red y está desactivada por defecto.

## Diagnóstico de errores habituales

| Mensaje o síntoma | Causa probable | Qué comprobar |
| --- | --- | --- |
| `Threat Director runtime is not available` | Runtime aún no iniciado o cerrándose | Esperar el arranque |
| Se esperaba un jugador | Consola usó una variante sin objetivo | Añadir nombre o ejecutar dentro del juego |
| Falta de permisos | Rama OP2 | Usar `/op`, cheats o consola |
| `target is not an eligible survival player` | Modo incorrecto, muerto o removido | Cambiar a Survival y estar vivo |
| `target health is below the configured safety ratio` | Salud baja | Curarse por encima del ratio |
| `target is inside the recent-death safety window` | Muerte reciente | Esperar; `start` no omite esta ventana |
| `world difficulty is Peaceful` | Peaceful | Cambiar dificultad |
| `dimension is not enabled` | Dimensión no permitida | Volver a Overworld o cambiar config |
| `player already has an active encounter` | Ya es primario | Usar `scene`, `phase` o `cancel` |
| `nearby group already has an active encounter` | Otro primario cercano | Dirigir la escena al grupo o cancelarlo |
| `player is inside spawn protection` | Dentro del radio conservador | Alejarse del spawn |
| `performance governor level N is not accepting new encounters` | Governor 5 o 6 | Reducir carga o usar `performance reset` para prueba |
| `active encounter safety cap has been reached` | Cap global o degradado | Terminar otro encuentro |
| `threat budget mob cap N is below the contextual minimum M` | Cap concurrente por intensity/contexto insuficiente | Subir threat o bajar el mínimo/cap contextual |
| `contextual horde is unaffordable: minimum=M, available=N milli-threat` | Budget vitalicio insuficiente para el quorum | Subir threat o revisar costes/config/rendimiento |
| `safe population could not be formed` | Sin mobs/rutas/spawns seguros | Revisar chunks, gamerules, terreno y hostiles |
| `Minimum encounter population was not reached; allocations rolled back` | No se alcanzó el quorum con posiciones y rutas seguras | Alejarse de caps locales o probar terreno cargado más abierto |
| `No hay encuentro activo` | Falta controlador | Ejecutar `start` o `scene` |
| `Fase desconocida` | Nombre inválido | Usar una fase documentada |
| `No se pudo cambiar la fase` | Igual, prohibida o terminada | Usar `scene` o elegir otra fase |
| Configuración rechazada | JSON o invariantes inválidas | Corregir todas las causas; sigue la snapshot previa |
| Breach `target_claim_unknown` | Claim sin adapter confiable | UNKNOWN falla cerrado |
| Breach `intensity_too_low` | Intensity bajo el mínimo | Consultar `intensity` y `threat` |

Los tres mensajes de la captura provenían de los defaults v1 de `VANILLA_PLUS`: cuatro operaciones
globales se consumían antes de que el comando formara la población; los niveles 5/6 bloqueaban incluso
la ruta de prueba; y cualquier intento que terminara con menos de tres miembros revertía lo
reclutado/generado. Con los defaults v2 de `VANILLA_PLUS`, la configuración intacta migra a 48
operaciones, reclutamiento reserva trabajo para el spawn, se contabilizan las operaciones realmente
usadas y los comandos OP reciben una slice acotada propia.
El governor continúa bloqueando niveles 5/6 por seguridad, pero ahora `performance reset` permite
abrir un warmup explícito para degradación ordinaria. La emergencia crítica sigue activa durante ese
periodo. El rollback de quorum continúa deliberadamente: evita crear “media horda” sin rutas y deja
el mundo como estaba.

## Flujos de prueba recomendados

### HUD y cálculo adaptativo

```text
/threatdirector status
/threatdirector intensity
/threatdirector threat
/threatdirector hud off
/threatdirector hud on
```

### Secuencia reproducible de alto threat

En Survival, Overworld, fuera de spawn, con salud suficiente y fuera de Peaceful:

```text
/threatdirector threat set 90
/threatdirector scene stalking
/threatdirector roles
/threatdirector budget
/threatdirector sectors
/threatdirector scene positioning
/threatdirector scene engaging
/threatdirector scene climax
/threatdirector scene retreating
/threatdirector cancel
/threatdirector threat clear
```

### Override bajo

```text
/threatdirector threat set 20
/threatdirector scene engaging
```

Debe rechazarse por presupuesto insuficiente. Termine con `threat clear`.

### Refuerzos y budget de por vida

Después de crear una escena con threat alto, consulte el ledger antes y después de una baja:

```text
/threatdirector threat set 90
/threatdirector scene engaging
/threatdirector budget
```

Mantenga al menos dos mobs dirigidos vivos y permita que uno vea al objetivo primario a 48 bloques o
menos. Derrote uno, permanezca en Engaging o pase a Climax y espere el intervalo contextual. Searching
no genera reposiciones. Tras la baja, `mobs retirados` aumenta sin aumentar `restante`; si se forma una
oleada, `mobs activos` vuelve a crecer y `restante` baja. Con los defaults de `VANILLA_PLUS`, el cap
concurrente nunca supera 12 y la reserva vitalicia puede agotarse antes de completar cuatro oleadas.

### Grupo multijugador

Con dos jugadores dentro de `multiplayer.groupRadius`:

```text
/threatdirector threat set 90 JugadorPrimario
/threatdirector scene engaging JugadorPrimario
```

El secundario debe ver `GRUPO`. En su cliente, `threat` muestra lo compartido, `intensity` su
diagnóstico personal y `hud off` solo oculta su propia barra.

### Safeguards

Repita `scene engaging` en Peaceful, spawn, salud crítica, Spectator, dimensión no permitida y tras
morir. Cada caso debe rechazar con causa explícita. Día y cooldown normal no bloquean un inicio forzado.

### BreachPlanner

```text
/threatdirector threat set 90
/threatdirector breach simulate
```

Repita junto a spawn, lava, cofres y bloques duros. Ningún `BlockState`, block entity o inventario
debe cambiar.

### Governor

```text
/threatdirector performance
/threatdirector performance reset
/threatdirector scene engaging
```

El reset abre 100 ticks contra degradación ordinaria. Una carga crítica no espera: dos muestras
consecutivas pueden llevar el governor a nivel 6 dentro del warmup; una carga alta no crítica vuelve
a degradarlo cuando termina.

## Servidor dedicado, singleplayer y LAN

Threat Director es server-sided:

- En dedicado, instale Threat Director y Fabric API en el servidor.
- En singleplayer, instálelos en la instancia del anfitrión: el mundo usa un servidor integrado.
- Al abrir por LAN, el anfitrión ejecuta el director. Los invitados reciben bossbar y efectos mediante
  protocolo vanilla; Threat Director no exige overlay ni canal cliente propio.
- Dentro de un grupo LAN elegible, el anfitrión tiene prioridad como ancla aunque un invitado tenga
  más equipo. Un host inelegible no salta salvaguardas; entonces otro miembro puede ser primario.
- Esta prioridad solo afecta la selección automática. `start <player>` y `scene <state> <player>`
  respetan el objetivo explícito del OP. En dedicado se conserva el ranking por power y UUID.
- Otros mods del modpack pueden imponer sus propios requisitos de cliente.

Los permisos pertenecen al servidor. En singleplayer/LAN, habilitar cheats concede al anfitrión
acceso a OP2; en dedicado use `/op` o consola.

Ejemplos de consola dedicada, sin `/` inicial:

```text
threatdirector status Jugador
threatdirector threat set 90 Jugador
threatdirector scene climax Jugador
threatdirector cancel Jugador
threatdirector cancel all
threatdirector performance reset
threatdirector config reload
```

Los objetivos deben estar conectados. El MVP no conserva controladores ni overrides tras reiniciar.
