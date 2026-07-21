# Threat Director

Threat Director es un mod de servidor para Minecraft 1.20.1 que convierte grupos
pequeños de mobs hostiles en encuentros coordinados y adaptativos. La dificultad
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

## MVP implementado

- Adventure Intensity explicable, suavizada y calculada por jugador/grupo.
- Threat Budget entero y composiciones limitadas de zombies, esqueletos, arañas y creepers.
- Escalado multijugador con rendimientos decrecientes, objetivos distribuidos y propiedad exclusiva
  de cada mob por encuentro.
- Roles Vanguard, Flanker, Harasser e Interceptor sobre goals y navegación vanilla.
- Fases Stalking, Positioning, Engaging, Searching, Relief y Cooldown.
- Sectores, Pinza, Cierre de salida y rotación de atacantes cuerpo a cuerpo.
- PerformanceGovernor con siete niveles de degradación.
- Salvaguardas por salud, muerte reciente, distancia máxima, spawn y dificultad.
- Configuración de servidor `config/threatdirector.json` y preset `VANILLA_PLUS`.
- BreachPlanner exclusivamente simulado: no existe ninguna llamada de rotura de bloques.
- API transaccional para adapters opcionales y claims con fallo cerrado.
- Telemetría NDJSON local, acotada y desactivada por defecto.

Los creepers creados por el director están desactivados por defecto. Los creepers naturales tampoco
se reclutan mientras `mobGriefing` permita dañar bloques, salvo activación explícita del mismo
interruptor; con `mobGriefing=false` pueden participar sin destruir terreno.

## Comandos

Las mutaciones requieren nivel de permiso 2.

```text
/threatdirector status [player]
/threatdirector intensity [player]
/threatdirector start [player]
/threatdirector cancel [player|all]
/threatdirector phase <state> [player]
/threatdirector roles
/threatdirector sectors
/threatdirector budget
/threatdirector breach simulate
/threatdirector config reload
/threatdirector integrations
/threatdirector performance
/threatdirector preset
/threatdirector telemetry
```

La rotura real, adapters de Prominence II, claims concretos, élites y el resto de patrones quedan
para fases posteriores. El núcleo actual ya funciona sin ninguno de ellos.

## Instalación

1. Preparar Minecraft 1.20.1 con Fabric Loader 0.18.4 y Java 17.
2. Instalar Fabric API 0.92.11+1.20.1.
3. Copiar `threat-director-0.1.0.jar` a la carpeta `mods` del servidor o de la instancia integrada.
4. Iniciar una vez para crear `config/threatdirector.json` y ajustar el preset si es necesario.

## Desarrollo

```powershell
.\gradlew.bat build
```

El artefacto remapeado se genera en `build/libs/`.

La arquitectura y los escenarios manuales están en `docs/ARCHITECTURE.md` y
`docs/MANUAL_TESTS.md`.
