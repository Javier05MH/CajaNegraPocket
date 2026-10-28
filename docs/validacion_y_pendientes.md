# Validación técnica y pendientes de CajaNegra Pocket

## Comprobación realizada

Se compiló de forma aislada `UtmConverter.kt` con el compilador Kotlin disponible y se compararon dos resultados con PROJ/pyproj:

| Latitud, longitud | EPSG de salida | Este UTM (m) | Norte UTM (m) |
|---|---:|---:|---:|
| 42.8467, -2.6727 | EPSG:32630 | 526743.920975 | 4743843.267171 |
| -33.9, 151.2 | EPSG:32756 | 333568.941011 | 6247473.336788 |

Las diferencias respecto al cálculo PROJ fueron inferiores a 0,3 mm en estos dos puntos. Esto solo verifica la conversión numérica para esas coordenadas; no certifica el funcionamiento integral de la app ni la exactitud GNSS del teléfono.

## Pendientes antes de presentar como entrega final

1. Compilar el proyecto Android en Android Studio (JDK 17, Android SDK Platform 35) e instalarlo en el teléfono que se va a demostrar. No se ha generado APK en este entorno.
2. Validar permisos, disponibilidad de sensores, columnas, tiempos de registro y exportación CSV en un móvil real.
3. Integrar un modelo de geoide apropiado (por ejemplo, una rejilla mundial EGM96 o una rejilla regional de EGM2008), interpolar N por coordenada y registrar modelo/versión. La app de partida solo acepta N manual y deja H vacío si falta.
4. Ejecutar un recorrido real y seguro en vehículo, con el móvil sujeto y una persona acompañante manejando la app. Adjuntar exactamente el CSV exportado por la aplicación, sin retocar.
5. Verificar UTM y las alturas con fuentes independientes y documentar la discrepancia observada en el dispositivo elegido.

## Estado de la muestra

`sample_data/DEMO_SINTETICA_NO_ES_TRAYECTO_REAL.csv` contiene datos inventados para enseñar el formato. Su primera columna es `DEMO_SINTETICA`, no es evidencia experimental ni cumple el requisito del trayecto real.
