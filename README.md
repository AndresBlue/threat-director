# Threat Director

Threat Director es un mod de servidor para Minecraft 1.20.1 que convierte mobs
hostiles en hordas coordinadas, acotadas y adaptativas. La dificultad
proviene del posicionamiento, los roles y el ritmo del encuentro; no de aumentar
arbitrariamente la vida o el daño.

## Plataforma

- Minecraft 1.20.1
- Fabric Loader 0.18.4
- Fabric API 0.92.11+1.20.1
- Yarn mappings 1.20.1+build.10
- Java 17

El núcleo funciona sin mods de contenido. Las futuras integraciones con claims,
accesorios, magia y Prominence II se cargan mediante una API opcional y no son
dependencias obligatorias.

## Versión 1.1

Esta entrega se publica como `threat-director-mc1.20.1-1.1.jar`. Las próximas versiones continuarán
la numeración según el alcance del cambio: las correcciones pequeñas incrementan la revisión y las
funcionalidades grandes abren una nueva versión de funcionalidad.

- Adventure Intensity explicable, suavizada y calculada por jugador/grupo.
- El tiempo de juego persistente y el nivel de XP añaden presión progresiva aunque el jugador retrase
  su equipamiento: seis horas alcanzan la contribución máxima de tiempo y el nivel 30 la de XP.
- Threat Budget entero de por vida: base más reserva de refuerzos, sin reciclar el coste de las bajas.
- Tres escalones deliberados: Easy conserva habilidades vanilla y no inicia encuentros automáticos;
  Normal aplica una versión parcial; Hard activa el modo Apocalypse completo.
- Presión contextual reforzada por noche, oscuridad y cuevas. En Hard, el cap por defecto pasa de
  `64` en superficie a `96` con peligro completo; los mínimos son `23/34`, los slots melee `23/34`
  y las oleadas son de `13/20` mobs, hasta diez, cada `100/80` ticks respectivamente.
- Scan automático cada 40 ticks. La probabilidad calculada de Hard es 30 % en superficie y 60 % con
  peligro completo; la elegibilidad nocturna/oscura configurada sigue siendo una salvaguarda aparte.
- Hard cap por defecto de 96, `hardMaxMobs` validable hasta 128 y cap local de 160 hostiles cercanos.
- En threat extremo, la población inicial más diez reposiciones puede hacer pasar cientos de mobs
  por un mismo asedio. El límite concurrente de 96 evita mantener cientos de pathfinders activos a
  la vez, pero desde una aldea la presión llega como una masa continua mientras exista budget.
- Escalado multijugador con rendimientos decrecientes, objetivos distribuidos y propiedad exclusiva
  de cada mob por encuentro.
- Roles Vanguard, Flanker, Harasser e Interceptor sobre goals y navegación vanilla.
- Fases Stalking, Positioning, Engaging, Searching, Climax, Relief, Retreating y cierre seguro.
- Sectores, Pinza, Cierre de salida y rotación de atacantes cuerpo a cuerpo.
- PerformanceGovernor con siete niveles de degradación.
- Salvaguardas por salud, muerte reciente, distancia máxima, spawn y dificultad.
- Configuración de servidor `config/threatdirector.json` y preset `VANILLA_PLUS`.
- BreachPlanner exclusivamente simulado. Por separado, un zombie Hard con herramienta puede romper
  una obstrucción compatible solo si `mobGriefing`, claims, chunks, world border y spawn seguro lo
  permiten; `UNKNOWN` de claims falla cerrado.
- API transaccional para adapters opcionales y claims con fallo cerrado.
- Telemetría NDJSON local, acotada y desactivada por defecto.
- Barra HUD opcional sin texto, sincronizada por paquete y visible en el hueco entre XP y hotbar.
- Autoridad del servidor integrado: en LAN solo el host puede ser ancla de un inicio automático.

El schema 3 permite creepers creados o reclutados por el director. Sus explosiones siguen las reglas
vanilla y `mobGriefing`; continúan sujetos a coste, cap, spawn seguro y Threat Budget.

## Habilidades por dificultad

- **Esqueletos:** Normal escala de 24 a 64 bloques y Hard de 32 a 128 según el threat individual.
  Las flechas predicen el movimiento y se corrigen en vuelo; Hard mejora la precisión, permite dash
  defensivo y aplica lentitud, veneno, náusea o ceguera durante 1–30 segundos (media aproximada de 8).
  Easy conserva el tiro vanilla.
- **Creepers:** Normal acelera la aproximación por la espalda y acorta parcialmente la mecha. Hard
  añade pasos silenciosos, dash, mecha más impredecible y encendido ante paredes bajo autoridad
  vanilla de `mobGriefing`.
- **Zombies:** Normal y Hard escalan su LOS/aggro individual hasta 48/96 bloques. Sobre 35 de threat,
  la detección puede llamar un rayo y 3–5 (Normal) o 5–10 (Hard) zombies con el objetivo fijado;
  golpear a uno alerta a la manada cercana y el aggro se conserva hasta la muerte. También hay
  herramientas melee, escudos, refuerzos y rotura selectiva bajo el guard destructivo del servidor.
- **Arañas:** Normal recibe Speed I y Jump Boost I. Hard sube esos buffs, añade regeneración y
  fuerza, genera hasta dos cave spiders al morir y coloca telaraña muy rara vez (cooldown de 20 s y
  1–6 % según threat), sin recursión de crías.
- **Endermen:** Normal puede alertar hasta dos aliados cercanos. Hard amplía la alerta, adopta postura
  de combate de dos bloques y responde a pilares bajos con teleport acotado.
- **Targeting común:** todos los `Monster` reciben un objetivo server-side por jugador, con rangos
  dinámicos y visión compleja; no compite con mobs administrados por un encuentro. En LAN el servidor
  integrado mantiene la autoridad y cada jugador conserva su propio threat.
- **Otros hostiles vanilla:** reciben además presión temática server-side por familias. Brujas/illagers,
  voladores de fuego, guardianes, drowned/husks, enjambres y brutos tienen reglas acotadas; la familia
  residual continúa genérica, sin una habilidad especial inventada.

## Comandos

Las mutaciones del director y las operaciones sobre otro jugador requieren nivel de permiso 2.
`status`, `intensity`, `threat`, `roles` y `budget` conservan sus consultas libres para el propio
jugador. El HUD se muestra a OP por defecto; sus comandos `on/off` son OP2 y pueden habilitar jugadores
concretos.

```text
/threatdirector status [player]
/threatdirector intensity [player]
/threatdirector threat [player]
/threatdirector threat set <0..100> [player]
/threatdirector threat clear [player]
/threatdirector hud [on|off] [player]
/threatdirector start [player]
/threatdirector cancel [player|all]
/threatdirector phase <state> [player]
/threatdirector scene <state> [player]
/threatdirector roles
/threatdirector sectors
/threatdirector budget
/threatdirector breach simulate
/threatdirector config reload
/threatdirector integrations
/threatdirector performance
/threatdirector performance reset
/threatdirector preset
/threatdirector telemetry
```

`threat set` mantiene un override en memoria hasta ejecutar `threat clear`, reiniciar el servidor o
podar el estado de un jugador desconectado; el cálculo adaptativo continúa en segundo plano.
`scene` crea un encuentro seguro si hace falta y fuerza una de estas escenas:
`stalking`, `positioning`, `engaging`, `searching`, `climax`, `relief` o `retreating`.
`intensity` conserva el diagnóstico adaptativo individual; `threat` coincide con el valor efectivo
del HUD y, durante un encuentro grupal, con la intensidad compartida.

La [guía completa de comandos](docs/COMMANDS.md) detalla sintaxis, permisos, uso desde consola,
salvaguardas, diferencias entre `phase` y `scene`, diagnóstico y flujos reproducibles de prueba.
`performance reset` es una herramienta OP temporal: reinicia el nivel y los contadores del gobernador
y concede 100 ticks sin degradación ordinaria. No modifica la carga real ni oculta una emergencia:
dos muestras críticas consecutivas pueden llevarlo a nivel 6 incluso durante ese warmup.

Ejemplo rápido para OP:

```text
/threatdirector threat set 90
/threatdirector scene engaging
/threatdirector scene climax
/threatdirector threat clear
```

Los comandos de prueba siguen respetando Peaceful, salud mínima, muerte reciente, spawn protegido,
chunks cargados, hard caps y el gobernador de rendimiento.
Un override explícito también se usa para calcular la población: valores inferiores a los necesarios
para formar el mínimo contextual rechazan la escena con una causa clara, en vez de elevarse en silencio.

## HUD de amenaza

Cada operador conectado que no esté en modo espectador recibe una barra segmentada sin texto, colocada
en un espacio dedicado debajo de la barra de experiencia y encima de la hotbar. XP, vida, armadura,
hambre y montura se elevan ligeramente para reservar esa franja. La barra usa la misma textura base
de la XP y cambia de verde a amarillo, rojo y púrpura según el threat efectivo. Un OP puede habilitarla para cualquier jugador con
`/threatdirector hud on <player>` u ocultarla con `hud off`; la preferencia es individual. El servidor
sincroniza únicamente progreso, color y visibilidad mediante un paquete pequeño; la lógica de threat
continúa siendo server-sided. Para ver el overlay el cliente debe tener el JAR, aunque la IA del
servidor sigue funcionando para clientes sin el mod.

Los adapters concretos de Prominence II, élites y patrones adicionales quedan para iteraciones
posteriores. El núcleo actual funciona sin ninguno de ellos.

## Instalación

1. Preparar Minecraft 1.20.1 con Fabric Loader 0.18.4 y Java 17.
2. Instalar Fabric API 0.92.11+1.20.1.
3. Copiar `threat-director-mc1.20.1-1.1.jar` a la carpeta `mods` del servidor o de la instancia integrada.
4. Iniciar una vez para crear `config/threatdirector.json` y ajustar el preset si es necesario.

La configuración actual usa schema 3. Un archivo schema 1 o 2 que conserve exactamente su preset
original migra a todos los defaults de 1.1. Un archivo personalizado conserva sus secciones y
valores y actualiza `schemaVersion`; si ya no satisface una invariante de seguridad, la recarga lo
rechaza y mantiene la última snapshot válida.

En un mundo abierto por LAN basta con instalar Threat Director y Fabric API en la instancia del
anfitrión. En la selección automática, solo un host elegible puede ser ancla; un invitado nunca abre
por sí solo un encuentro automático. Un OP todavía puede dirigir `start` o `scene` explícitamente a
otro jugador. Los invitados pueden conectarse sin el mod porque no se registran contenidos de juego;
solo el invitado que quiera ver el overlay necesita el JAR cliente. Otros mods instalados en esa
instancia sí pueden imponer requisitos propios.

## Desarrollo

```powershell
.\gradlew.bat build
```

El artefacto remapeado se genera en `build/libs/`.

La arquitectura y los escenarios manuales están en `docs/ARCHITECTURE.md` y
`docs/MANUAL_TESTS.md`.
