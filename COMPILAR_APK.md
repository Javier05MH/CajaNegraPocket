# Cómo obtener `CajaNegraPocket.apk`

El proyecto contiene una compilación automática con GitHub Actions. El entorno en el que se preparó este paquete no dispone de Android SDK ni de Gradle y no tiene acceso a la red, así que aquí no se ha podido generar ni comprobar un APK real.

## Opción sencilla: compilar en GitHub

1. Descomprime el ZIP del proyecto en tu ordenador.
2. Crea un repositorio en GitHub y sube el contenido de esta carpeta, incluida la carpeta oculta `.github/workflows`.
3. En GitHub, abre **Actions**. La compilación se inicia automáticamente al hacer `push`; también se puede abrir **Construir APK Android** y pulsar **Run workflow**.
4. Cuando el trabajo termine en verde, entra en esa ejecución y descarga el artefacto **CajaNegraPocket-APK-debug**.
5. Descomprime el artefacto: dentro estará `app-debug.apk`. Pásalo al teléfono Android y ábrelo para instalarlo. Si Android lo pide, permite instalar aplicaciones desde esa fuente.

El APK de depuración queda firmado automáticamente con la clave de depuración de Gradle; sirve para pruebas académicas y no es una versión de publicación comercial.

## Compilación local con Android Studio

1. Instala Android Studio con JDK 17 y Android SDK Platform 35.
2. Abre la carpeta del proyecto `CajaNegraPocket`.
3. Configura Gradle 8.7, sincroniza el proyecto y ejecuta **Build > Build Bundle(s) / APK(s) > Build APK(s)**.
4. El APK de depuración se genera en `app/build/outputs/apk/debug/app-debug.apk`.

## Antes de presentarlo

Instálalo y prueba permisos, GPS, sensores y exportación en el móvil que usarás en la demostración. La columna de altura ortométrica solo se calcula si se introduce una ondulación geoidal conocida; el modelo de geoide no está integrado todavía. El CSV sintético de la carpeta `sample_data` no sustituye al trayecto real exigido por la actividad.
