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

## Desarrollo

```powershell
.\gradlew.bat build
```

El artefacto remapeado se genera en `build/libs/`.
