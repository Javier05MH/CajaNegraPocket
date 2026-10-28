# CajaNegra Pocket — KineTrace Labs

Prototipo académico de aplicación Android para registrar sensores y posición durante ensayos dinámicos. Proyecto Kotlin nativo, sin dependencias de bibliotecas externas.

## Qué incluye

- Pantalla inicial con velocidad GPS destacada.
- Descubrimiento en tiempo de ejecución de todos los sensores Android ofrecidos por el dispositivo (`Sensor.TYPE_ALL`), incluidos acelerómetro, magnetómetro e intensidad de luz si existen.
- Captura de una fila cada 100 ms (10 Hz), leyendo el último valor disponible de cada sensor.
- Posición GPS/red: latitud, longitud, velocidad, precisión, rumbo y altura elipsoidal que proporcione Android.
- Conversión WGS84 geodésica a UTM implementada en `UtmConverter.kt`.
- Exportación CSV mediante el selector de archivos del sistema.
- Corrección ortométrica opcional `H = h - N` si la persona técnica introduce una ondulación geoidal conocida.

## Limitaciones que hay que resolver antes de una entrega final

1. El prototipo **no incorpora la rejilla de ningún modelo de geoide**. `N` debe introducirse manualmente con el modelo indicado. Si no se conoce, la aplicación deja esa altura vacía; no inventa un valor. Integrar una rejilla global EGM2008/EGM96 e interpolación bilineal offline es un requisito pendiente para afirmar cumplimiento completo de altitud ortométrica automática.
2. El programa no incluye APK compilado ni está validado en todos los fabricantes. Abrir la carpeta en Android Studio, sincronizar Gradle, compilar e instalar en un dispositivo Android real.
3. La grabación solo está diseñada para la pantalla de la aplicación en primer plano. Para sesiones largas o pantalla apagada habría que añadir un servicio foreground y gestionar su ciclo de vida.
4. La frecuencia real de cada sensor depende del hardware y del sistema operativo. Las filas del CSV se generan a 10 Hz y repiten el último valor recibido; esta frecuencia no implica que cada sensor esté midiendo exactamente a 10 Hz.
5. El CSV de demostración se marca como sintético: no sustituye al trayecto real solicitado en la actividad.

## Obtener un APK

Consulta [COMPILAR_APK.md](COMPILAR_APK.md). Se ha añadido el flujo `.github/workflows/build-apk.yml` para compilar el APK de depuración en GitHub Actions. El APK no está generado ni verificado en este entorno porque no hay Android SDK/Gradle ni acceso a la red.

## Compilación

1. Instala Android Studio con JDK 17 y Android SDK Platform 35.
2. Abre esta carpeta como proyecto existente.
3. Permite sincronizar Gradle; usa Android Gradle Plugin 8.6.1 y Kotlin 2.0.20, con JDK 17 y Gradle 8.7. Este paquete no incluye el binario `gradle-wrapper.jar`; si Android Studio lo exige, genera el wrapper desde la raíz con `gradle wrapper --gradle-version 8.7` o selecciona una instalación local de Gradle 8.7.
4. Ejecuta la configuración `app` en un teléfono físico Android 8.0 (API 26) o posterior. Concede permiso de ubicación.
5. Comprueba primero que aparecen acelerómetro, magnetómetro y luz. Si un teléfono no incorpora alguno, el estado lo indicará.
6. Graba un recorrido real seguro con el dispositivo sujeto y una persona acompañante manejándolo. Pulsa **Detener y exportar** y guarda el CSV.

## Estructura

- `app/src/main/java/.../MainActivity.kt`: interfaz, sensores, posición, captura y exportación.
- `app/src/main/java/.../UtmConverter.kt`: conversión UTM WGS84.
- `docs/`: documentación del proyecto.
- `sample_data/`: ejemplo de CSV expresamente sintético.

## Seguridad

No manejar ni observar la aplicación mientras se conduce. Teléfono firmemente sujeto; pruebas realizadas por un conductor atento a la vía y otra persona responsable del registro. No realizar frenadas o maniobras forzadas en vías públicas.
