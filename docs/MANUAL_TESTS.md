# Threat Director — pruebas manuales

## Alcance del documento

Esta matriz define la aceptación manual del MVP vanilla ya implementado. Configuración, runtime,
comandos y controlador están conectados; los casos de Prominence II y adapters concretos se marcan
explícitamente como fases posteriores.

## Preparación

1. Ejecutar `gradlew.bat clean build`.
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

Esperado: se crea JSON UTF-8 legible con schema 2 y preset `VANILLA_PLUS`; el servidor continúa sin
errores. Entre los defaults figuran `maxMobs=8`, `hardMaxMobs=12`, 32 candidatos, cuatro intentos de
path por mob, replan base de 30 ticks y 48 operaciones globales de planner por tick.

### CFG-02 — JSON malformado

1. Conservar una configuración válida activa.
2. Romper la sintaxis del archivo.
3. Ejecutar `/threatdirector config reload`.

Esperado: la recarga falla con ubicación o causa clara, el archivo no se sobrescribe y el servidor
continúa usando la última configuración válida.

### CFG-03 — valores incoherentes

Establecer `minMobs: 9`, `maxMobs: 8`, invertir las distancias de spawn y hacer que un batch de
telemetría exceda la cola.

Esperado: un solo error informa todas las infracciones relevantes y no activa parcialmente ninguna
sección.

### CFG-04 — límites destructivos del MVP

Cambiar `breachSimulation.simulationOnly` a `false` y habilitar adapters externos bajo
`VANILLA_PLUS`.

Esperado: la validación rechaza ambos cambios. No se modifica ningún bloque y no se intenta cargar
ninguna clase externa.

### CFG-05 — reload válido

1. Reducir `encounter.maxMobs` de 8 a 7 manteniendo `minMobs <= maxMobs <= hardMaxMobs`.
2. Recargar durante un encuentro activo.

Esperado: la nueva configuración se publica completa, sin estado parcial ni error. El controlador
activo conserva sus parámetros de construcción para FSM y táctica; seguridad, refuerzos,
finalización/cooldown y el gobernador consultan la configuración vigente. Su población no se reduce
retroactivamente. El siguiente encuentro nace por completo con el nuevo máximo normal.

### CFG-06 — identifiers y costes

Introducir una dimensión con mayúsculas, duplicar otra y agregar un mob permitido sin threat cost.

Esperado: las tres condiciones se reportan y el JSON anterior continúa activo.

### CFG-07 — migración del preset v1 intacto

1. Con el servidor detenido, reemplazar el archivo por el JSON exacto generado por el preset
   `VANILLA_PLUS` schema 1 de la versión anterior.
2. Iniciar el servidor y volver a leer el archivo.

Esperado: se migra y reescribe atómicamente como schema 2 con todos los defaults nuevos, incluidos
8/12 mobs, 32 candidatos, cuatro paths, replan 30 y 48 operaciones. No queda archivo temporal ni se
requiere editarlo a mano.

### CFG-08 — migración v1 personalizada y schema desconocido

1. Partir del JSON schema 1 anterior, cambiar valores válidos distintivos —por ejemplo
   `maxMobs=5`, `hardMaxMobs=7` y una probabilidad personalizada— y arrancar.
2. Confirmar la reescritura, cambiar `schemaVersion` a 99 con el servidor activo y ejecutar reload.

Esperado: en el primer arranque solo cambia a schema 2 y los valores personalizados se preservan.
El schema 99 se rechaza, no se reescribe y la última configuración válida en memoria continúa
activa.

## Adventure Intensity

### HUD-01 — barra adaptativa

Entrar con dos jugadores equipados de forma diferente.

Esperado: cada cliente ve una barra propia de diez segmentos; porcentaje, texto y color reflejan su
threat individual y no el del otro jugador. Al entrar ambos en el mismo encuentro, el secundario
muestra el threat compartido del encuentro con la marca `GRUPO`.

### HUD-02 — override OP

Ejecutar `/threatdirector threat set 90`, observar la barra y luego ejecutar
`/threatdirector threat clear`.

Esperado: cambia inmediatamente a `THREAT 90% · EXTREMO · FORZADO`; al limpiar desaparece la marca y
regresa al último cálculo adaptativo, que continuó actualizándose en segundo plano.

### HUD-03 — lifecycle

Desconectarse, reconectar, morir/respawnear y desactivar el mod desde configuración.

Esperado: no se duplican barras; el respawn conserva una sola barra y al desactivar el runtime se
retira del HUD.

### HUD-04 — preferencia personal

Ejecutar `/threatdirector hud off` y `/threatdirector hud on` sin permisos OP. Después, como OP,
repetir ambos comandos indicando otro jugador.

Esperado: cada jugador puede ocultar o recuperar su barra; modificar la preferencia ajena requiere
nivel de permiso 2 y no altera el cálculo de threat.

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
Budget ni el máximo difícil 12; ningún spawn aparece en visión inmediata. La ruta normal completa
Stalking → Positioning → Engaging → Relief, o pasa por Climax → Retreating cuando se cumplen sus
condiciones, sin transiciones ilegales.

### ENC-02 — composición vanilla

Repetir ENC-01 varias veces.

Esperado: solo zombies, esqueletos, arañas y creepers; cada entidad consume su coste. Si los creepers
generados están desactivados, se usa otro tipo permitido. Con `mobGriefing=true`, un creeper natural
tampoco se recluta sin opt-in; con `mobGriefing=false`, sí puede participar.

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

Esperado MVP: BreachPlanner puede informar el candidato, coste y ruta hipotética, pero todos los
BlockState, BlockEntity e inventarios permanecen idénticos.

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
táctica. El bioma bosque no añade una etiqueta propia: la zona depende de dificultad, noche, luz,
cielo y altura. Se mantienen visión, chunks, población y Threat Budget seguros.

### ENC-12 — Hard en cueva oscura

1. En Hard y a medianoche, comparar una posición de superficie con otra bajo el nivel del mar, sin
   visión del cielo y con luz local 7 o menor.
2. Mantener equipo, intensidad y grupo idénticos y forzar un encuentro en cada posición.

Esperado: la cueva reporta `hard:night+dark+cave` y peligro 1.0, limitado sin overflow. Frente a la
superficie, la presión contextual no disminuye: puede acercar el cap a 12, elevar el mínimo hasta 5,
acelerar replans y habilitar refuerzos mayores. Ninguna de esas ventajas omite reglas vanilla de
spawn o límites de seguridad.

### ENC-13 — oleadas de refuerzo acotadas

1. Usar Hard en cueva oscura, intensidad alta, telemetría activa y espacio de spawn cargado.
2. Mantener el encuentro en Engaging, Searching o Climax con al menos dos mobs vivos y liberar
   espacio de cap/presupuesto entre intervalos derrotando miembros de forma controlada.
3. Registrar todos los eventos `encounter.reinforced` hasta terminar el encuentro.

Esperado: pueden completarse como máximo cuatro oleadas de refuerzo; nunca aparece una quinta. Cada
evento incrementa `wave`, informa zona/peligro y añade solo lo permitido por tamaño contextual,
Threat Budget, cap del encuentro, máximo difícil 12, ownership y presupuesto global del planner. Si
no existe población segura o quedan menos de dos mobs, se difiere o cancela la oleada sin forzarla.

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

### MP-04 — host LAN preferido y cliente sin mod

1. El host inicia un mundo integrado con Fabric API y Threat Director y lo abre a LAN.
2. Un segundo cliente Minecraft 1.20.1 sin Threat Director entra, equipa más potencia que el host y
   permanece en Survival dentro de `groupRadius`.
3. Esperar un scan automático elegible —no usar inicio forzado— y observar HUD y target primario.

Esperado: el cliente remoto conecta sin handshake, canal ni paquete del mod y ve su bossbar vanilla.
El host LAN es el target primario preferido aunque el invitado tenga mayor potencia; el invitado
participa como miembro del grupo y su barra muestra `GRUPO`. Al salir del encuentro desaparece la
marca compartida; al desconectarse o cerrar el mundo se limpian barras y referencias retenidas.

## Rendimiento y degradación

### PERF-01 — límites de planner

Visualizar candidatos y provocar replans desplazándose rápidamente.

Esperado: a nivel 0 nunca se superan 32 candidatos por snapshot ni 48 operaciones globales por tick,
compartidas entre población inicial, refuerzos y controladores activos. Se permiten cuatro intentos
base de path por mob y solo un quinto contextual cuando `danger >= 0.70`. El replan base es 30 ticks
antes de ajustes de presión y gobernador.

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
ejecuta el servidor; HUD, IA y configuración siguen siendo autoritativos del servidor.

### COMP-02 — mob externo próximo

En una instancia de compatibilidad futura, colocar un mob externo junto al jugador manteniendo
adapters desactivados.

Esperado: el mob no se recluta ni provoca `ClassNotFoundException`; el encuentro vanilla continúa.

### COMP-03 — adapter defectuoso

Caso de fase posterior: instalar un adapter de prueba que lance una excepción al registrarse.

Esperado: solo ese adapter se deshabilita y el core retorna al comportamiento vanilla.

### COMP-04 — claims desconocidos

Caso de fase posterior: simular respuesta `UNKNOWN` de claims.

Esperado: toda acción destructiva se deniega. La simulación explica que no pudo demostrar permiso.

## Criterio de cierre del MVP

- Build y JUnit pasan desde checkout limpio; los GameTests están planificados como siguiente capa.
- Cliente integrado y servidor dedicado deben arrancar sin mods externos; ambos son escenarios de
  aceptación manual, no una afirmación cubierta por JUnit.
- Un cliente remoto 1.20.1 sin Threat Director conecta a dedicado y LAN, recibe la bossbar vanilla
  y en LAN el host es el target automático preferido del grupo.
- Se reproducen ENC-01, ENC-03, ENC-04 y ENC-11 a ENC-13 con máximo normal 8, máximo difícil 12 y no
  más de cuatro oleadas de refuerzo.
- No existe ninguna modificación de bloques causada por BreachPlanner.
- Un fallo de config, telemetría o integración no sustituye la última configuración válida ni
  detiene el servidor.
- No se observan chunks forzados, persecución infinita ni órdenes tácticas retenidas tras finalizar.
