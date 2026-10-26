# Guía para correr MemoFlash con 2 emuladores en otro PC 📱⌚

Esta guía explica paso a paso cómo preparar, ejecutar y sincronizar **MemoFlash** en otro ordenador usando **dos emuladores al mismo tiempo** (uno de teléfono Android y otro de reloj Wear OS), tanto desde **Android Studio** como desde la **terminal**, **sin necesidad de iniciar sesión con una cuenta de Google**.

---

## 1. Requisitos previos

1. **JDK 17 o superior** instalado.
2. **Android Studio** (o las herramientas de línea de comandos del **Android SDK**).
3. En el **SDK Manager** de Android Studio (o mediante `sdkmanager`), asegúrate de tener instalados:
   - `Android SDK Platform-Tools` (`adb`)
   - `Android Emulator`
   - Una imagen de sistema de **teléfono** (por ejemplo, **Android 14 / API 34** — `system-images;android-34;google_apis;x86_64`)
   - Una imagen de sistema de **Wear OS** (por ejemplo, **Wear OS 4 / API 34** — `system-images;android-34;android-wear;x86_64`)

> **Nota sobre variables de entorno:** Para ejecutar comandos como `adb`, `emulator` o `avdmanager` desde cualquier terminal, agrega a tu `PATH`:
> - `$ANDROID_HOME/platform-tools`
> - `$ANDROID_HOME/emulator`
> - `$ANDROID_HOME/cmdline-tools/latest/bin`
>
> *(En Linux suele ser `~/Android/Sdk`, en macOS `~/Library/Android/sdk` y en Windows `%LOCALAPPDATA%\Android\Sdk`).*

---

## 2. Crear e iniciar los 2 emuladores

Puedes crearlos visualmente desde Android Studio o directamente por terminal:

### Opción A: Desde Android Studio (Device Manager)
1. Abre **Tools → Device Manager → Create Virtual Device**.
2. **Emulador de Teléfono:**
   - Categoría **Phone** → elige por ejemplo **Pixel 6**.
   - Selecciona la imagen **API 34 (Android 14)** y finaliza con el nombre `Pixel_Phone`.
3. **Emulador de Reloj:**
   - Categoría **Wear OS** → elige **Wear OS Small Round** o **Wear OS Large Round**.
   - Selecciona la imagen **API 34 (Wear OS)** y finaliza con el nombre `WearOS_Round`.
4. Inicia ambos emuladores haciendo clic en el botón **▶ (Play)** de cada uno.

### Opción B: Desde la terminal
```bash
# 1. Descargar las imágenes de sistema (si aún no las tienes)
sdkmanager "system-images;android-34;google_apis;x86_64" "system-images;android-34;android-wear;x86_64"

# 2. Crear los dos emuladores (AVDs)
avdmanager create avd -n Pixel_Phone -k "system-images;android-34;google_apis;x86_64" -d pixel_6 --force
avdmanager create avd -n WearOS_Round -k "system-images;android-34;android-wear;x86_64" -d wearos_small_round --force

# 3. Iniciar ambos emuladores en segundo plano
emulator -avd WearOS_Round &
emulator -avd Pixel_Phone &
```

Verifica que ambos aparezcan conectados ejecutando:
```bash
adb devices -l
```
Verás algo similar a:
```text
List of devices attached
emulator-5554          device product:sdk_gwear_x86_64 model:sdk_gwear_x86_64 ...
emulator-5556          device product:sdk_gphone64_x86_64 model:sdk_gphone64_x86_64 ...
```
*(Anota cuál `emulator-XXXX` corresponde al teléfono `sdk_gphone` y cuál al reloj `sdk_gwear`).*

---

## 3. Activar el puente de sincronización entre emuladores (sin cuenta de Google)

En dispositivos físicos, **MemoFlash** sincroniza por Bluetooth/Wi-Fi mediante la **Wearable Data Layer API**. En emuladores, para evitar tener que iniciar sesión con una cuenta de Google e instalar la app *Wear OS Companion*, [`Sincronizador`](core/src/main/java/com/ralenday/memoflash/core/sync/Sincronizador.kt) incluye un **puente local automático por TCP** (`10.0.2.2`):

- El **teléfono** escucha en el puerto **`19876`**.
- El **reloj** escucha en el puerto **`19877`**.

Con ambos emuladores encendidos, ejecuta estos dos comandos en tu terminal (ajustando `emulator-5556` y `emulator-5554` según te haya mostrado `adb devices -l`):

```bash
# Redirigir el puerto 19876 hacia el emulador de TELÉFONO
adb -s emulator-5556 forward tcp:19876 tcp:19876

# Redirigir el puerto 19877 hacia el emulador de RELOJ
adb -s emulator-5554 forward tcp:19877 tcp:19877
```

> **Importante:** Si reinicias los emuladores en otra sesión, vuelve a ejecutar esos dos comandos `adb forward`.

---

## 4. Instalar y correr MemoFlash en ambos emuladores

Tienes dos formas de instalar la aplicación:

### Opción 1: Usar los APKs precompilados desde GitHub Releases (más rápido)
1. Descarga `mobile-debug.apk` y `wear-debug.apk` desde la sección [**Releases**](https://github.com/Ralenday/MemoFlash/releases) del repositorio.
2. Instálalos en cada emulador:
   ```bash
   # Instalar app de teléfono en el emulador móvil
   adb -s emulator-5556 install -r mobile-debug.apk

   # Instalar app de reloj en el emulador Wear OS
   adb -s emulator-5554 install -r wear-debug.apk
   ```

### Opción 2: Compilar desde el código fuente
1. Clona el repositorio:
   ```bash
   git clone https://github.com/Ralenday/MemoFlash.git
   cd MemoFlash
   ```
2. Compila ambos módulos (en Windows usa `gradlew.bat`):
   ```bash
   ./gradlew assembleDebug
   ```
3. Instala los APKs generados:
   ```bash
   adb -s emulator-5556 install -r mobile/build/outputs/apk/debug/mobile-debug.apk
   adb -s emulator-5554 install -r wear/build/outputs/apk/debug/wear-debug.apk
   ```

### Abrir las aplicaciones desde terminal (o desde el cajón de apps)
```bash
# Abrir MemoFlash en el teléfono
adb -s emulator-5556 shell am start -n com.ralenday.memoflash/com.ralenday.memoflash.mobile.MainActivity

# Abrir MemoFlash en el reloj
adb -s emulator-5554 shell am start -n com.ralenday.memoflash/com.ralenday.memoflash.wear.MainActivity
```

---

## 5. Verificar que la sincronización funciona

1. Al abrir ambas apps (con los puertos `19876` y `19877` redirigidos):
   - En el **teléfono** verás: **`⌚ Reloj conectado: Reloj (emulador)`** y la hora de última sincronización.
   - En el **reloj** verás: **`📱 Teléfono conectado`** y el mazo de ejemplo **“Inglés básico”** listo para estudiar.
2. Si abriste primero un emulador y luego el otro, toca el botón **Sincronizar** en cualquiera de los dos.
3. Entra a **Inglés básico** en el reloj, responde una tarjeta (**Difícil / Regular / Fácil**) y observa cómo a los ~1.5 segundos el contador de tarjetas pendientes en el teléfono se actualiza solo y el repaso suma en la pestaña **Estadísticas → Dónde estudias**.
