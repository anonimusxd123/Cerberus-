# AdBlock Android

Aplicación Android nativa, escrita en Kotlin, que implementa una primera versión de bloqueo basado en DNS mediante `VpnService`. No usa root, no crea una VPN remota ni envía el tráfico a un servidor propio.

## Qué funciona realmente

Al activar la protección, Android crea una interfaz VPN **local** que anuncia un servidor DNS virtual (`10.67.0.2`). Solo la ruta a esa dirección se captura en el túnel: el tráfico web normal continúa usando la red habitual del teléfono. La app analiza las consultas DNS IPv4/UDP recibidas, compara el dominio con una lista local y:

- devuelve `NXDOMAIN` para dominios bloqueados;
- reenvía las consultas permitidas a Cloudflare DNS (`1.1.1.1`) usando un socket protegido para que no vuelva a entrar en la VPN;
- guarda únicamente contadores locales, no el historial de dominios.

La lista inicial es deliberadamente pequeña y de prueba. Se pueden añadir dominios manuales a la lista blanca o negra; si la VPN ya está activa, desactívala y actívala para recargar esas reglas. Incluye interfaz Material 3, servicio en primer plano, estadísticas persistentes, listado de aplicaciones con lanzador y pruebas unitarias del motor de reglas.

## Limitaciones actuales

No es un reenvío IP completo ni un filtro de paquetes. Esta versión solo procesa DNS IPv4 sobre UDP; no filtra DNS cifrado (DoH/DoT), IPv6, TCP DNS ni solicitudes resueltas desde caché. Algunas aplicaciones pueden usar sus propios resolvers o dominios compartidos con contenido legítimo. Por ello no promete bloquear todos los anuncios ni contenido específico de YouTube, Facebook u otras apps.

El interruptor de inicio automático se guarda como preferencia, pero no inicia aún un servicio tras reiniciar Android: hacerlo correctamente requiere una estrategia explícita para las restricciones de arranque y foreground services actuales. Las excepciones por aplicación también quedan para un motor de paquetes posterior; la pantalla solo muestra las apps identificables a través del lanzador.

## Compilar sin Android Studio

Requisitos: JDK 17 y Android SDK Platform 35. El repositorio incorpora Gradle Wrapper; no es necesario instalar Android Studio.

```bash
./gradlew testDebugUnitTest
./gradlew assembleDebug
```

En Windows PowerShell:

```powershell
.\gradlew.bat testDebugUnitTest
.\gradlew.bat assembleDebug
```

El APK queda en `app/build/outputs/apk/debug/app-debug.apk`.

## GitHub Actions

Al subir el proyecto a GitHub, el flujo `.github/workflows/build.yml` se ejecuta en cada `push` y `pull request`, instala JDK 17 y el SDK, ejecuta pruebas, compila el APK debug y lo publica como artifact **adblock-android-debug-apk**. Descárgalo desde la ejecución del workflow, sin claves ni keystores en el repositorio.

## Instalar y probar

1. Copia el APK al teléfono e instala la variante debug (autoriza la instalación desde esa fuente cuando Android lo solicite).
2. Abre **AdBlock Android** y pulsa **Activar protección**.
3. Acepta el diálogo oficial de Android para la VPN. La notificación persistente confirma que el filtro DNS está activo.
4. Para una prueba controlada, añade `ads.example.com` a la lista negra y consulta ese dominio desde una herramienta DNS o app que use el DNS del sistema. Debe recibir una respuesta de dominio inexistente.

## Arquitectura y privacidad

- `vpn/`: interfaz VPN local y codec mínimo de DNS/IP.
- `filtering/`: matcher de dominios basado en `HashSet`, lista local y listas blanca/negra.
- `statistics/`: contadores en `SharedPreferences`.
- `settings/`: preferencias locales de interfaz.

No hay analítica, publicidad, cuenta de usuario ni backend propio. La consulta DNS permitida se envía al resolver configurado para obtener su respuesta; no se persisten los nombres de dominio consultados.

## Próximas mejoras

1. Añadir fuentes de blocklists públicas con licencia revisada, descarga verificable y actualización atómica.
2. Implementar DNS TCP, IPv6 y políticas para DoH/DoT cuando sea técnicamente y legalmente apropiado.
3. Sustituir el codec mínimo por un motor de reenvío IP/UDP/TCP local robusto, con reglas por aplicación y excepciones reales.
4. Incorporar pruebas instrumentadas en un dispositivo físico y monitorización de errores local y opt-in.

Versiones: Android Gradle Plugin 8.7.3, Kotlin 2.0.21, Gradle 8.9 y JDK 17.
