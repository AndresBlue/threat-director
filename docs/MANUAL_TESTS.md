# Threat Director — pruebas manuales

## Alcance del documento

Esta matriz define la aceptación manual del MVP vanilla ya implementado. Configuración, runtime,
comandos y controlador están conectados; los casos de Prominence II y adapters concretos se marcan
explícitamente como fases posteriores.

## Preparación

1. Ejecutar `gradlew.bat clean build`.
2. Iniciar una instancia Fabric 1.20.1 con solo Fabric API y Threat Director.
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

Esperado: se crea JSON UTF-8 legible con schema 1 y preset `VANILLA_PLUS`; el servidor continúa sin
errores y la configuración reportada coincide con los defaults.

### CFG-02 — JSON malformado

1. Conservar una configuración válida activa.
2. Romper la sintaxis del archivo.
3. Ejecutar `/threatdirector config reload`.

Esperado: la recarga falla con ubicación o causa clara, el archivo no se sobrescribe y el servidor
continúa usando exactamente la snapshot anterior.

### CFG-03 — valores incoherentes

Establecer `minMobs: 9`, `maxMobs: 6`, invertir las distancias de spawn y hacer que un batch de
telemetría exceda la cola.

Esperado: un solo error informa todas las infracciones relevantes y no activa parcialmente ninguna
sección.

### CFG-04 — límites destructivos del MVP

Cambiar `breachSimulation.simulationOnly` a `false` y habilitar adapters externos bajo
`VANILLA_PLUS`.

Esperado: la validación rechaza ambos cambios. No se modifica ningún bloque y no se intenta cargar
ninguna clase externa.

### CFG-05 — reload válido

1. Reducir `encounter.maxMobs` de 6 a 5 manteniendo `minMobs <= maxMobs <= hardMaxMobs`.
2. Recargar durante un encuentro activo.

Esperado: el encuentro activo conserva su snapshot; el siguiente respeta el nuevo máximo.

### CFG-06 — identifiers y costes

Introducir una dimensión con mayúsculas, duplicar otra y agregar un mob permitido sin threat cost.

Esperado: las tres condiciones se reportan y el JSON anterior continúa activo.

## Adventure Intensity

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

### ENC-01 — cacería nocturna reproducible

Forzar un encuentro con jugador full diamond en campo abierto y noche cerrada.

Esperado: 3–6 mobs, presupuesto no negativo, ningún spawn en visión inmediata y transición completa
Stalking → Positioning → Engaging → Relief.

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

## Rendimiento y degradación

### PERF-01 — límites de planner

Visualizar candidatos y provocar replans desplazándose rápidamente.

Esperado: nunca se superan 16 candidatos por snapshot, dos paths por mob y el presupuesto global de
operaciones por tick compartido entre formación de población y controladores activos.

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

## Compatibilidad y fallo seguro

### COMP-01 — instalación mínima

Arrancar cliente integrado y servidor dedicado con solo Fabric API y Threat Director.

Esperado: carga completa sin referencias opcionales, comandos disponibles y preset Vanilla Plus.

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
- Se reproducen ENC-01, ENC-03 y ENC-04 con un máximo de seis mobs por defecto.
- No existe ninguna modificación de bloques causada por BreachPlanner.
- Un fallo de config, telemetría o integración no sustituye la última configuración válida ni
  detiene el servidor.
- No se observan chunks forzados, persecución infinita ni órdenes tácticas retenidas tras finalizar.
