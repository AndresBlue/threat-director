# Threat Director — descripción para CurseForge

## Una noche que aprende de ti

Threat Director convierte la supervivencia de Minecraft 1.20.1 en una campaña de presión adaptativa.
El servidor observa a cada jugador, estima su preparación y peligro ambiental, y dirige encuentros con
roles, sectores, flanqueos y oleadas. Una aldea puede parecer tranquila al principio y convertirse en
el punto de convergencia de una horda cuando el jugador ya está bien equipado.

El mod está diseñado para ser server-sided: no registra bloques, objetos ni entidades de contenido.
La IA, el threat y los comandos se ejecutan en el servidor integrado; el único paquete adicional es el
canal opcional que transmite el valor del HUD al cliente que quiera visualizarlo. Los invitados pueden
jugar sin el mod, pero necesitan el JAR para ver su propia barra.

## Requisitos

- Minecraft Java Edition 1.20.1
- Fabric Loader 0.18.4
- Fabric API 0.92.11+1.20.1
- Java 17

## Threat por jugador

El threat es un valor de 0 a 100 calculado a partir de equipo, salud, absorción, inventario, entorno,
preparación y carga reciente. Se suaviza para evitar saltos artificiales y se consulta de forma
individual: un jugador nuevo no hereda automáticamente el castigo de su compañero equipado.

Las habilidades extraordinarias comienzan a aparecer sobre 35 de threat. Por debajo de ese umbral el
comportamiento sigue siendo muy cercano a vanilla. En Hard, el progreso completo alcanza los rangos
publicitados: esqueletos hasta 128 bloques y zombis hasta 96. El servidor conserva todos los límites de
seguridad aunque el threat esté forzado por un OP.

## Director de encuentros

- Adventure Intensity explicable, con bandas, histéresis y diagnóstico por jugador/grupo.
- Threat Budget entero: las bajas no reciclan el coste; las reservas de refuerzos son acotadas.
- Presión contextual por noche, oscuridad, cuevas y zonas peligrosas.
- Fases `STALKING`, `POSITIONING`, `ENGAGING`, `SEARCHING`, `CLIMAX`, `RELIEF` y `RETREATING`.
- Roles `VANGUARD`, `FLANKER`, `HARASSER` e `INTERCEPTOR`, navegación vanilla y rotación melee.
- Sectores de pinza, cierres de salida y oleadas continuas. En Hard el cap contextual por defecto es
  64 en superficie y 96 en peligro completo, con un cap local de 160 hostiles cercanos.
- Performance Governor de siete niveles para cortar oleadas o planes cuando el servidor está bajo carga.
- Salvaguardas de salud mínima, muerte reciente, distancia, chunks cargados, world border, spawn y
  `mobGriefing`; claims desconocidos fallan cerrado.

## Habilidades de los mobs

### Esqueletos: fuego que corrige su trayectoria

En Normal el alcance crece de 24 a 64 bloques; en Hard llega de 32 a 128 según el threat del jugador.
La puntería predice velocidad y dirección, y cada flecha mejorada recibe guiado server-side mientras
vuela. Hard reduce la divergencia, permite retroceso defensivo y dash para tomar distancia. Las flechas
especiales pueden aplicar `Slowness`, `Poison`, `Nausea` o `Blindness`; duran entre 1 y 30 segundos,
con una media aproximada de 8 segundos. Easy conserva el tiro vanilla.

### Zombies: una señal y llega la manada

Cuando un jugador supera el umbral de threat, la detección inicial tiene una probabilidad progresiva de
invocar un rayo y una horda de 3–5 zombies en Normal o 5–10 en Hard. Los recién llegados nacen con el
objetivo marcado. Un golpe a un zombie alerta a los zombis cercanos dentro de un radio que escala con
threat. El marcador de aggro se conserva hasta que el zombie muere: no se pierde por distancia ni por
línea de visión, por lo que las paredes solo compran tiempo.

Hard añade equipo de hierro, escudos ocasionales, refuerzos acotados y herramientas capaces de romper
un bloque compatible (pala, hacha, pico o espada) solo cuando las reglas de mundo, claims y guardas lo
permiten. Easy mantiene el comportamiento vanilla.

### Creepers: silencio, presión y mechas impredecibles

Normal acelera el acercamiento por la espalda y acorta parcialmente la mecha. En Hard, con threat alto,
los pasos son silenciosos, el creeper puede hacer dash, encenderse al encontrar una pared que puede
romper y escoger una mecha aleatoria entre 30 % y 100 % de la vanilla. La explosión sigue `mobGriefing`
y las salvaguardas del servidor.

### Arañas

Normal ofrece Speed I y Jump Boost I. Hard añade regeneración y fuerza, y al morir puede generar hasta
dos cave spiders sin recursión. La telaraña al golpear es deliberadamente rara: cooldown de 20 segundos
y probabilidad de 1–6 % según threat, siempre sujeta a `mobGriefing` y claims.

### Endermen

Al recibir daño un enderman puede alertar aliados cercanos. En Hard adopta una postura agachada de dos
bloques y puede teletransportarse de forma acotada al bloque de un jugador que intenta hacer un pilar.
Estas respuestas se activan y escalan con el threat individual.

### Otros hostiles vanilla

Brujas e illagers, blaze/ghast/phantom, guardianes, drowned/husks, silverfish/endermites/slimes y brutos
reciben presión temática server-side: proyectiles, acercamiento, aliados, estados y empujes según familia
y dificultad. El resto conserva el objetivo común adaptativo sin inventar entidades registradas.

## Dificultad

| Dificultad | Experiencia |
| --- | --- |
| Easy | Vanilla; no habilidades extraordinarias ni encuentros automáticos. |
| Normal | Presión parcial, rangos moderados y hordas pequeñas sobre 35 de threat. |
| Hard | Perfil Apocalypse completo: visión amplia, horda, pathfinding agresivo y habilidades avanzadas. |

## Comandos para OP (permiso 2)

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
/threatdirector performance [reset]
/threatdirector preset
/threatdirector config reload
/threatdirector integrations
/threatdirector breach simulate
/threatdirector telemetry
```

`threat set` fuerza el valor efectivo en memoria para probar escenas y escalados; `threat clear`
devuelve el cálculo adaptativo. `scene` crea o reutiliza un encuentro y salta directamente a una fase.
El HUD es una barra segmentada sin texto, situada en un espacio dedicado debajo de la XP y sobre la
hotbar. La XP y los indicadores vanilla inferiores se elevan ligeramente para reservar la franja; la
barra reutiliza la estética de la XP y conserva la paleta verde, amarilla, roja y morada. Los OP la ven
por defecto y pueden habilitarla para un jugador concreto con `hud on <player>` o esconderla con `hud off`.
El overlay requiere el JAR en el cliente que lo visualiza; la IA y el cálculo de threat siguen siendo
server-sided.

## Instalación y compatibilidad

1. Instala Fabric Loader, Fabric API y Java 17.
2. Coloca `threat-director-0.3.0.jar` en `mods` del servidor o de la instancia integrada.
3. Inicia el mundo y ajusta `config/threatdirector.json` si quieres cambiar caps o preset.

El gameplay no requiere contenido adicional en el cliente, pero el cliente que quiera ver el overlay
debe tener el mismo JAR. Es compatible con mundos dedicados y LAN; integraciones de claims, telemetría
y otros contenidos son opcionales. Las explosiones, roturas y telarañas respetan las reglas del servidor.
Para pruebas controladas, usa `threat set`, `scene engaging` o `scene climax`, y luego `threat clear`.

## Créditos y estado

Threat Director 0.3.x se apoya en entidades y navegación vanilla, con lógica adicional aislada en el
servidor. El objetivo es que la amenaza se perciba como una inteligencia colectiva, no como simples
bonificadores de vida o daño.
