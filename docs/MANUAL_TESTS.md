# Threat Director — pruebas manuales

## Alcance del documento

Esta matriz define la aceptación manual de Threat Director 0.3.5. Configuración, runtime, comandos,
habilidades y controlador están conectados; los casos de Prominence II y adapters concretos se marcan
explícitamente como fases posteriores.

## Preparación

1. Ejecutar `gradlew.bat clean build` y comprobar `build/libs/threat-director-0.3.5.jar`.
2. Iniciar una instancia Fabric 1.20.1 con solo Fabric API y Threat Director en el lado servidor.
   Preparar además un cliente remoto 1.20.1 sin Threat Director para los casos LAN/dedicado.
3. Crear un mundo de pruebas en dificultad Normal, cheats activos y clima despejado.
4. Conservar una copia del mundo y del JSON antes de cada bloque de escenarios.
5. Activar logs detallados solo durante diagnóstico para no alterar mediciones normales.
6. Registrar versión del mod, preset, seed, número de jugadores y tick time medio.

Para escenarios aleatorios se recomienda un comando administrativo de inicio forzado. Forzar un
encuentro evita esperar la probabilidad natural, pero no debe omitir seguridad de spawn, límites de
entidades ni validación de posiciones.

## Configuración

### CFG-01 — primera creación

1. Eliminar únicamente `config/threatdirector.json` con el juego detenido.
2. Iniciar el servidor.

Esperado: se crea JSON UTF-8 legible con schema 3 y preset `VANILLA_PLUS`; el servidor continúa sin
errores. Entre los defaults figuran `minMobs=4`, `maxMobs=32`, `hardMaxMobs=96`,
`maxNearbyHostiles=160`, 64 candidatos, cinco intentos de path por mob, replan base de 20 ticks y
192 operaciones/20 000 microsegundos globales de planner por tick.

### CFG-02 — JSON malformado

1. Conservar una configuración válida activa.
2. Romper la sintaxis del archivo.
3. Ejecutar `/threatdirector config reload`.

Esperado: la recarga falla con ubicación o causa clara, el archivo no se sobrescribe y el servidor
continúa usando la última configuración válida.

### CFG-03 — valores incoherentes

Establecer `minMobs: 33`, `maxMobs: 32`, `hardMaxMobs: 129`, invertir las distancias de spawn y hacer
que un batch de telemetría exceda la cola.

Esperado: un solo error informa todas las infracciones relevantes y no activa parcialmente ninguna
sección.

### CFG-04 — invariantes destructivas

Cambiar `breachSimulation.simulationOnly` a `false` y habilitar adapters externos bajo
`VANILLA_PLUS`.

Esperado: la validación rechaza ambos cambios. No se modifica ningún bloque y no se intenta cargar
ninguna clase externa.

### CFG-05 — reload válido

1. Reducir `encounter.maxMobs` de 32 a 31 manteniendo `minMobs <= maxMobs <= hardMaxMobs`.
2. Recargar durante un encuentro activo.

Esperado: la nueva configuración se publica completa, sin estado parcial ni error. El controlador
activo conserva sus parámetros de construcción para FSM y táctica; seguridad, refuerzos,
finalización/cooldown y el gobernador consultan la configuración vigente. Su población no se reduce
retroactivamente. El siguiente encuentro nace por completo con el nuevo máximo normal.

### CFG-06 — identifiers y costes

Introducir una dimensión con mayúsculas, duplicar otra y agregar un mob permitido sin threat cost.

Esperado: las tres condiciones se reportan y el JSON anterior continúa activo.

### CFG-07 — migración de presets v1/v2 intactos

1. Con el servidor detenido, reemplazar el archivo por el JSON exacto generado por el preset
   `VANILLA_PLUS` schema 1 legado.
2. Iniciar el servidor, verificar la migración, detenerlo y repetir con el preset schema 2 intacto.

Esperado: ambos se migran y reescriben atómicamente como schema 3 con todos los defaults nuevos,
incluidos 32/96 mobs, nearby cap 160, 64 candidatos, cinco paths, replan 20 y 192 operaciones. No
queda archivo temporal ni se requiere editarlo a mano.

### CFG-08 — migración v1 personalizada y schema desconocido

1. Partir de un JSON schema 1 o 2, cambiar valores válidos distintivos —por ejemplo
   `maxMobs=5`, `hardMaxMobs=7` y una probabilidad personalizada— y arrancar.
2. Confirmar la reescritura, cambiar `schemaVersion` a 99 con el servidor activo y ejecutar reload.

Esperado: en el primer arranque solo cambia a schema 3 y los valores personalizados se preservan.
El schema 99 se rechaza, no se reescribe y la última configuración válida en memoria continúa
activa.

## Adventure Intensity

### HUD-01 — barra adaptativa

Entrar con dos jugadores equipados de forma diferente, uno OP2 y otro sin permisos.

Esperado: solo el OP2 ve una barra propia segmentada, sin texto, ubicada entre XP y hotbar; el color
refleja su threat. El no operador no la recibe hasta que un OP ejecuta `/threatdirector hud on <player>`.
Al habilitarla durante el mismo encuentro, el secundario muestra el threat compartido.

### HUD-02 — override OP

Como OP2, ejecutar `/threatdirector threat set 90`, observar la barra y luego ejecutar
`/threatdirector threat clear`.

Esperado: la barra cambia inmediatamente al nivel extremo; al limpiar el override regresa al último
cálculo adaptativo, que continuó actualizándose en segundo plano.

### HUD-03 — lifecycle

Desconectarse, reconectar, morir/respawnear y desactivar el mod desde configuración.

Esperado: no se duplica la barra; el respawn conserva una sola y al desactivar el runtime se retira
del HUD.

### HUD-04 — permisos y preferencia

Intentar `/threatdirector hud`, `/threatdirector hud off` y `/threatdirector hud on` sin permisos.
Después, como OP2, repetirlos para sí mismo y para otro operador conectado.

Esperado: el jugador sin OP2 no ejecuta ninguna rama `hud`. El operador puede ocultar o recuperar su
barra y habilitar la de cualquier jugador; la preferencia no altera el cálculo de threat. Los
espectadores nunca reciben la barra.

### INT-01 — jugador débil

Jugador sin armadura, espada de madera, salud completa y sin consumibles durante la noche.

Esperado: el desglose es bajo y no recibe una cacería avanzada de forma inmediata.

### INT-02 — diamante completo

Jugador con diamante completo, espada o arco, munición, escudo y comida durante la noche.

Esperado: intensidad claramente superior a INT-01 y elegibilidad para encuentro; el desglose explica
defensa, ofensa y sostenimiento.

### INT-03 — netherite y encantamientos

Repetir INT-02 con netherite y encantamientos altos.

Esperado: sube hasta los caps sin exceder 100 ni producir `NaN`/infinito.

### INT-04 — suavizado e histéresis

Alternar rápidamente entre equipo fuerte y débil.

Esperado: el valor converge gradualmente y no inicia/cancela encuentros en cada cambio de slot.

### INT-05 — daño y recuperación

Recibir daño fuerte y quedar por debajo de media salud durante la preparación.

Esperado: la presión efectiva disminuye o el encuentro aborta conforme a config; curarse no produce
un salto instantáneo a máxima intensidad.

## Formación y justicia del encuentro

### ENC-00 — comando de escena

Como OP fuera del spawn protegido, ejecutar `/threatdirector threat set 90` y después
`/threatdirector scene engaging` sin un encuentro previo.

Esperado: se forma una población mediante las reglas seguras normales, se crea el encuentro y entra
en Engaging. El feedback informa threat, mobs y budget. Repetir con las demás escenas admitidas.

### ENC-01 — cacería nocturna reproducible

Forzar un encuentro con jugador full diamond en campo abierto y noche cerrada.

Esperado: la población alcanza el mínimo contextual y no supera el `mobCap` reportado, el Threat
Budget ni el máximo difícil 96; ningún spawn aparece en visión inmediata. La ruta normal completa
Stalking → Positioning → Engaging → Relief, o pasa por Climax → Retreating cuando se cumplen sus
condiciones, sin transiciones ilegales.

### ENC-02 — composición vanilla

Repetir ENC-01 varias veces.

Esperado: solo zombies, esqueletos, arañas y creepers; cada entidad consume su coste. Schema 3 permite
creepers generados y reclutados. Sus explosiones siguen las reglas vanilla de `mobGriefing`, mientras
su incorporación continúa limitada por coste, budget, cap y spawn seguro.

### ENC-03 — Pinza

Usar terreno plano con al menos cuatro miembros y dos alas viables.

Esperado: se ocupan sectores opuestos antes de atacar; no hay teleport y cada destino posee path
real. Si un lado falla, el patrón cancela o degrada a vanilla.

### ENC-04 — Cierre de salida

Correr de forma sostenida por un camino despejado.

Esperado: un Interceptor busca una posición adelantada mientras el resto presiona desde atrás; deja
contrajuego lateral y abandona destinos inviables.

### ENC-05 — casa de tierra

Refugiarse detrás de tierra y madera simple.

Esperado: `BreachPlanner` puede informar el candidato, coste y ruta hipotética, pero el comando no
modifica BlockState, BlockEntity ni inventarios. Ejecutar este caso en Normal o sin zombies Hard para
no confundirlo con la habilidad destructiva independiente probada en ABIL-04.

### ENC-06 — spawn protegido

Repetir ENC-05 dentro del radio de protección configurado.

Esperado: la simulación marca protección y no propone una acción segura.

### ENC-07 — pérdida de visión

Romper línea de visión y permanecer oculto más que `contactLossTicks`.

Esperado: la posición compartida pierde confianza; el grupo abandona o entra en Relief, sin conocer
permanentemente la posición exacta.

### ENC-08 — cambio de dimensión

Entrar al Nether durante Engaging.

Esperado: encuentro abortado, órdenes retiradas y mobs restantes devueltos a IA vanilla. No se
transfieren mobs ni referencias entre mundos.

### ENC-09 — persecución limitada

Huir más allá de `maxPursuitDistance`.

Esperado: Relief/abort y cooldown; no hay persecución infinita.

### ENC-10 — muerte o desconexión

Morir y, en otra repetición, desconectarse durante cada fase.

Esperado: limpieza idempotente, cooldown correcto, telemetría con causa y ninguna excepción por
UUID ausente.

### ENC-11 — Hard en bosque nocturno

1. Activar telemetría local, fijar el mismo equipo/intensidad y situarse en la superficie de un
   bosque a medianoche.
2. Forzar una ejecución en Normal y otra en Hard desde la misma zona, sin cambiar luz ni grupo.

Esperado: Hard reporta mayor `danger` que Normal, una zona `hard:night` que puede añadir `dark` si
la luz local es 7 o menor, y presión no inferior en cap, mínimo contextual, atacantes y frecuencia
táctica. Para un contexto `hard:surface` la política pura reporta chance 30 % por scan de 40 ticks,
cap 64, mínimo 23, melee 23, oleada 13 cada 100 ticks y hasta diez. El requisito nocturno/oscuro
para auto-inicio sigue siendo independiente. El bioma bosque no añade una etiqueta propia.

### ENC-12 — Hard en cueva oscura

1. En Hard y a medianoche, comparar una posición de superficie con otra bajo el nivel del mar, sin
   visión del cielo y con luz local 7 o menor.
2. Mantener equipo, intensidad y grupo idénticos y forzar un encuentro en cada posición.

Esperado: la cueva reporta `hard:night+dark+cave` y peligro 1.0, limitado sin overflow. La política
reporta chance 60 % por scan de 40 ticks, cap 96, mínimo 34, melee 34, replan 10 ticks y oleada de 20
cada 80 ticks, hasta diez. Ninguna de esas ventajas omite reglas vanilla de spawn, nearby cap 160,
Threat Budget, chunks o límites de seguridad.

### ENC-13 — oleadas de refuerzo acotadas

1. Usar Hard en cueva oscura, intensidad alta, telemetría activa y espacio de spawn cargado.
2. Mantener el encuentro en Engaging, Searching o Climax con al menos dos mobs vivos y liberar
   espacio de cap/presupuesto entre intervalos derrotando miembros de forma controlada.
3. Registrar todos los eventos `encounter.reinforced` hasta terminar el encuentro.

Esperado: pueden completarse como máximo diez oleadas de refuerzo; nunca aparece una undécima. Cada
evento incrementa `wave`, informa zona/peligro y añade solo lo permitido por tamaño contextual,
Threat Budget, cap del encuentro, máximo difícil 96, ownership y presupuesto global del planner. Si
no existe población segura o quedan menos de dos mobs, se difiere o cancela la oleada sin forzarla.

## Perfiles de dificultad y habilidades

Estas habilidades se aplican a hostiles vanilla cargados aunque no pertenezcan a un encuentro. Al
cambiar dificultad, generar entidades nuevas para verificar buffs/equipamiento aplicados durante
`initialize`; no reutilizar una araña ya buffeada para afirmar que Easy la desactiva.

### ABIL-01 — Easy vanilla y sin inicio automático

1. Poner el mundo en Easy, fuera del spawn protegido, de noche y con un jugador por encima de la
   intensidad mínima.
2. Esperar al menos 100 scans de 40 ticks sin usar `start` ni `scene`.
3. Generar zombies, esqueletos, arañas, creepers y endermen nuevos en un recinto seguro.

Esperado: no se inicia ningún encuentro automático porque la chance Easy es exactamente cero. Los
mobs conservan habilidades vanilla: sin targeting ampliado, equipo adicional del mod, buffs de
araña, flechas especiales, dash o alertas añadidas. Un OP todavía puede forzar una escena Easy y esa
ruta conserva health, spawn, cap, chunks y demás salvaguardas.

### ABIL-02 — targeting común y ownership

1. En Normal, colocar distintos `Monster` vanilla con visión a un Survival a 26–28 bloques; repetir
   en Hard a 40–48 y en Easy a la misma distancia.
2. Crear después un encuentro mediante `scene engaging` y observar un mob con el tag administrado.
3. En LAN, poner al invitado más cerca que el host y repetir con ambos elegibles.

Esperado: por debajo de 35 de threat el objetivo extra permanece dormido. Sobre ese umbral el rango
crece por jugador; genéricos alcanzan 48, esqueletos Hard 128 y zombis Hard 96 a threat máximo. Hard
puede conservar memoria sin visión. Easy no añade este goal. Mientras el mob está manejado por un
encuentro, el targeting común se apaga y manda la compuerta del director.

### ABIL-03 — esqueletos predictivos

1. Construir una pista lateral abierta y comparar esqueletos recién generados en Easy, Normal y Hard
   contra un jugador que corre perpendicularmente.
2. Medir tiros cerca del límite de alcance y observar tags/efectos durante una muestra amplia.
3. En Hard, acercarse a menos de siete bloques con línea de visión y espacio detrás del esqueleto.

Esperado: Easy conserva alcance 15 y flecha vanilla. Normal escala de 24 a 64 según threat; Hard de
32 a 128, lanza a 2.2, predice el movimiento y corrige el proyectil en vuelo. Las flechas especiales
duran 20–600 ticks, con media aproximada de ocho segundos; Hard hace kiting y puede ejecutar dash
hacia atrás con cooldown. En una muestra suficiente,
32 % de las flechas Hard se reparten entre Slowness II, Poison I, Nausea I y Blindness I con efectos
vanilla. No aparece entidad ni paquete no vanilla.

### ABIL-04 — zombies, refuerzos y guard destructivo

1. Generar 100 zombies por dificultad y registrar equipo inicial.
2. En Hard, recibir golpes de zombies y dañarlos de forma repetida fuera del cooldown, contando
   refuerzos en un radio de 24 bloques.
3. Dar a un zombie Hard un pico/hacha/pala apropiado, colocar al jugador tras un único bloque
   compatible y repetir estas variantes: `mobGriefing=false`; dentro del spawn protegido; claim
   `PROTECTED`; claim `UNKNOWN`; y finalmente fuera de spawn con `mobGriefing=true` y respuesta
   `UNPROTECTED` o ausencia confirmada de sistema de claims.

Esperado: Easy no recibe equipo del mod. Normal equipa herramienta/espada aproximadamente 40 % y no
llama refuerzos ni rompe. Hard equipa aproximadamente 85 %, incluye pico y puede llevar escudo en
30 % de los equipados; cada evento elegible tiene 20 % de llamada, máximo dos refuerzos, cooldown 200
ticks y cap de ocho zombies cercanos. La obstrucción no cambia en las cuatro variantes denegadas.
Solo la última puede romper un bloque adecuado, cargado, sin block entity, dureza `<=5`, sin drops y
como máximo uno por intento de 30 ticks. Contenedores, bloques sin herramienta adecuada, world border
y chunks no cargados permanecen intactos.

### ABIL-05 — creepers parciales y Apocalypse

1. En Normal, acercarse y alejar la mirada de un creeper a menos de 18 bloques; medir velocidad y
   duración de mecha en varias repeticiones.
2. En Hard, repetir escuchando pasos, luego provocar un dash visible entre 4 y 10 bloques.
3. Con protección contra explosión, dejarlo bloqueado 20 ticks tras una pared, sin visión y a 4.5
   bloques; repetir con `mobGriefing=false`.

Esperado: Normal solo acelera la aproximación trasera `×1.35` y elige una mecha entre 70–100 % de la
vanilla. Hard usa `×2`, mecha 30–100 %, silencia pasos y puede hacer dash con cooldown 60 ticks. El
encendido de pared solo ocurre con `mobGriefing=true`; la explosión, daño, sonido y sincronización son
vanilla. Easy carece de todos estos cambios.

### ABIL-06 — arañas, telaraña y crías

1. Generar arañas nuevas en cada dificultad y consultar sus efectos.
2. En Hard permitir golpes separados por 100 ticks sobre posiciones reemplazables, primero con
   `mobGriefing=false` o claim protegido y luego en zona permitida.
3. Matar una araña Hard y después matar las cave spiders producidas.

Esperado: Easy no añade buffs. Normal aplica Speed I y Jump Boost I. Hard aplica Speed II, Jump Boost
II, Regeneration I y Strength I; solo en zona autorizada coloca como máximo una telaraña por golpe y
cooldown. Al morir intenta generar dos cave spiders vanilla con tag
`threatdirector.spider_hatchling`; sus muertes no generan otra camada. Fallos de chunk, border o
colisión reducen la cantidad sin forzar spawn.

### ABIL-07 — endermen coordinados y anti-pillar

1. Rodear otro hostile con endermen y hacerle daño como Survival.
2. Repetir en Normal y Hard, contando aliados alertados y distancias.
3. En Hard subir a un pilar con al menos 2.25 bloques de ventaja, soporte sólido y tres lados libres,
   manteniendo al enderman a cuatro bloques horizontales o menos.

Esperado: Normal intenta alertar hasta dos endermen dentro de 12 bloques, con 50 % por candidato.
Hard alerta hasta seis dentro de 24 con 100 %. Creative/Spectator no provocan la alerta. En combate
Hard adopta postura de 1.95 bloques y, tras 30 ticks de ventaja válida, intenta teleport al objetivo
con cooldown 80; geometría que no cumpla el detector no lo dispara.

### ABIL-08 — otras familias vanilla

Probar al menos una entidad de cada familia: witch/illager, blaze/ghast/phantom, guardian,
drowned/husk, silverfish/endermite/slime/magma cube y ravager/piglin brute.

Esperado: además del targeting común, Normal/Hard aplican reglas acotadas y con cooldown: debilidad o
coordinación de raiders, impulso de aproximación aérea, fatiga de guardian/drowned, hambre de husk,
rally de enjambres y carga de brutos solo en Hard. Las ventanas de distancia, visión, agua, máximo de
aliados y caps de velocidad impiden spam. Una entidad de la familia residual `OTHER` recibe targeting
genérico dinámico, pero ninguna habilidad temática inventada.

## Multijugador

### MP-01 — dos jugadores juntos

Dos jugadores equipados a menos de `groupRadius` fuerzan inicio simultáneo.

Esperado: un solo encuentro, presupuesto escalado con rendimiento decreciente y límite global de
mobs respetado.

### MP-02 — separación del grupo

Separarse durante Positioning y superar el radio de grupo.

Esperado: no se duplica inmediatamente el encuentro; el controlador conserva un target válido o
aborta de forma explícita.

### MP-03 — jugador ajeno ataca un miembro

Un tercer jugador golpea un mob mientras este acecha.

Esperado: el mob puede defenderse o se libera del director; la compuerta táctica no lo vuelve
incapaz de responder indefinidamente.

### MP-04 — host LAN como única ancla automática y cliente sin mod

1. El host inicia un mundo integrado con Fabric API y Threat Director y lo abre a LAN.
2. Un segundo cliente Minecraft 1.20.1 sin Threat Director entra, equipa más potencia que el host y
   permanece en Survival dentro de `groupRadius`.
3. Esperar un scan automático elegible —no usar inicio forzado— y observar target primario y HUD.
4. Finalizar el encuentro, hacer inelegible al host y dejar al invitado en Survival durante varios
   scans de 40 ticks.
5. Como OP2, ejecutar `/threatdirector start <invitado>`; después otorgar OP2 al invitado.

Esperado: el cliente remoto conecta sin depender del canal del mod. Mientras el host es elegible, es
siempre el target automático aunque el invitado tenga más potencia; el invitado nunca lo sustituye
cuando el host queda inelegible. El inicio administrativo explícito sí puede elegir al invitado y un OP
puede ejecutar `hud on <invitado>`; si el cliente tiene el JAR, aparece la barra. Al salir del encuentro
desaparece la presión compartida; al desconectarse o cerrar el mundo se limpian referencias retenidas.

## Rendimiento y degradación

### PERF-01 — límites de planner

Visualizar candidatos y provocar replans desplazándose rápidamente.

Esperado: a nivel 0 nunca se superan 64 candidatos por snapshot, cinco intentos de path por mob ni
192 operaciones globales por tick, compartidas entre población inicial, refuerzos y controladores
activos. El trabajo total del planner queda además limitado a 20 000 microsegundos por tick. El
replan base es 20 ticks antes de ajustes de presión y gobernador.

### PERF-02 — servidor bajo carga

Generar carga controlada hasta superar progresivamente los umbrales de tick time.

Esperado: los niveles aumentan solo tras carga sostenida, reducen replans/candidatos/encuentros y el
nivel 6 devuelve temporalmente a vanilla.

### PERF-03 — recuperación

Retirar la carga después de llegar a nivel alto.

Esperado: recuperación lenta, de un nivel por ventana; no oscila entre niveles cada tick.

### PERF-04 — chunks no cargados

Huir hacia el borde de simulación durante Positioning.

Esperado: el director no fuerza carga de chunks y descarta candidatos fuera del área cargada.

### PERF-05 — warm-up y reset administrativo

1. Al arrancar, provocar picos de carga durante los primeros 100 ticks y observar el nivel.
2. Después del warm-up, mantener carga suficiente para degradar el gobernador.
3. Tras comprobar la causa, ejecutar `/threatdirector performance reset` y continuar observando
   durante más de 100 ticks.

Esperado: los picos iniciales no degradan. El reset devuelve a nivel 0, limpia muestras acumuladas y
abre un nuevo warm-up de 100 ticks; no amplía caps ni borra encuentros por sí mismo. Si la carga
sostenida persiste después, el gobernador vuelve a degradar normalmente.

### PERF-06 — emergencia de dos muestras

Después del warm-up, generar de forma controlada una muestra por encima del umbral de nivel 6,
recuperar la siguiente ventana de 20 ticks y repetir; después generar dos muestras críticas
consecutivas.

Esperado: una muestra crítica aislada no activa nivel 6 y una ventana no crítica reinicia la cuenta.
Dos muestras críticas consecutivas sí llevan a nivel 6 y devuelven temporalmente los encuentros a
IA vanilla.

## Compatibilidad y fallo seguro

### COMP-01 — instalación mínima

Arrancar un host integrado y un servidor dedicado con solo Fabric API y Threat Director en el lado
servidor. Conectar a cada uno un cliente remoto Minecraft 1.20.1 sin el mod.

Esperado: carga completa sin referencias opcionales, comandos disponibles, preset Vanilla Plus y
conexión de los clientes sin instalar Threat Director. El host integrado sí lleva el mod porque
ejecuta el servidor; HUD, IA y configuración siguen siendo autoritativos del servidor. Un cliente sin
el JAR puede jugar, pero no renderiza el overlay; con el JAR y una habilitación OP sí lo muestra.

### COMP-02 — mob externo próximo

En una instancia de compatibilidad futura, colocar un mob externo junto al jugador manteniendo
adapters desactivados.

Esperado: el mob no se recluta ni provoca `ClassNotFoundException`; el encuentro vanilla continúa.

### COMP-03 — adapter defectuoso

Caso de fase posterior: instalar un adapter de prueba que lance una excepción al registrarse.

Esperado: solo ese adapter se deshabilita y el core retorna al comportamiento vanilla.

### COMP-04 — claims desconocidos

Caso de fase posterior: simular respuesta `UNKNOWN` de claims.

Esperado: toda acción destructiva real del módulo zombie o de telarañas se deniega. BreachPlanner no
modifica bloques en ningún caso y su simulación explica que no pudo demostrar permiso.

## Criterio de cierre de 0.3.5

- Build y JUnit pasan desde checkout limpio; los GameTests están planificados como siguiente capa.
- Cliente integrado y servidor dedicado deben arrancar sin mods externos; ambos son escenarios de
  aceptación manual, no una afirmación cubierta por JUnit.
- Un cliente remoto 1.20.1 sin Threat Director conecta a dedicado y LAN, pero no renderiza el overlay.
  En LAN el host es la única ancla automática; los clientes con el JAR pueden recibir una barra
  habilitada individualmente.
- Easy no genera encuentros automáticos ni altera habilidades vanilla. Normal conserva presión
  parcial. Hard reproduce caps 64/96, mínimos 23/34, melee 23/34, oleadas 13/20 de hasta diez cada
  100/80 ticks y chances 30 %/60 % por scan de 40 ticks según superficie/peligro completo.
- El targeting dinámico alcanza a todos los `Monster`, se apaga mientras el mob lleva el tag de gestión
  de un encuentro y mantiene la autoridad automática del host en LAN.
- Se reproducen las habilidades específicas de skeleton, creeper, zombie, spider y enderman, y al
  menos una muestra de cada familia temática restante, sin cambios de habilidad en Easy.
- BreachPlanner no modifica bloques. La rotura zombie real solo ocurre con `mobGriefing=true`, chunk
  cargado, dentro del world border, fuera del spawn protegido y permiso de claims demostrado.
- Un fallo de config, telemetría o integración no sustituye la última configuración válida ni
  detiene el servidor.
- No se observan chunks forzados, persecución infinita ni órdenes tácticas retenidas tras finalizar.
