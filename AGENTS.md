# AGENTS.md — StorageHeart (mod de Hytale)

> Cofre central estilo Magic Storage (Terraria): un Corazón con acceso
> unificado a toda la red de almacenamiento. Llave + F para vincular,
> interfaz propia con buscador.

## 1. Estado actual (v1.0.0, server 0.7.2) — 17 oct 2026, PUBLICADO

- v1.0.0 inglés global + MIT + build.ps1 + README EN. Commit `152ca7a` en
  `main`, subido a https://github.com/Cosmar43/storage-heart. Tag `v1.0.0`.
- Verificado en capturas: nombres ES→EN resuelven (mesa), llave dorada 3D,
  UI inglesa abre bien.
- PENDIENTE: `docs/` con capturas (usuario las guarda en screenshots/).

- v1.0.0 inglés global + MIT + build.ps1 + README EN. Commit `152ca7a` en
  `main`, subido a https://github.com/Cosmar43/storage-heart.
- SSH con `~/.ssh/github` (`GIT_SSH_COMMAND='ssh -i ~/.ssh/github'`).
- PENDIENTE: captura UI inglesa en juego; release con jar (vía web).

- i18n mods imposible en 0.7.2 con evidencia completa (bytecode + logs):
  `.lang` cargan pero en mapa muerto; evento solo vía comando dev.
  Nombres directos ES (display OK); búsqueda ES del creativo = límite vanilla.
- Llave 3D con textura propia oro/esmeralda (modelo Coffer probado).
- PENDIENTE EN JUEGO: nombres ES en mesa/creativo/mano, llave en mano.

- Bytecode: `GenerateDefaultLanguageEvent` solo lo dispara el comando dev
  `GenerateI18nCommand` → fuera listener; `.lang` es/en restaurados.
- Llave 3D con textura propia (recolor oro/esmeralda sobre UV Coffer).
- PENDIENTE EN JUEGO: `Loaded 4 entries` en log, nombres ES, llave en mano.

- 0.23.0: icono/texturas Terraria del Corazón (aceptado).
- 0.24.0: nombres por `GenerateDefaultLanguageEvent` es+en (los `.lang`
  del pack no registraban); icono de llave propio (modelo 3D pendiente).
- PENDIENTE EN JUEGO: nombres ES en mesa/creativo, búsqueda ES creativo,
  icono de llave.

- 0.21.0: sin comandos, llave propia, recetas (verificadas en juego).
- 0.22.0: nombres por claves + `Server/Languages/es-ES|en-US/
  StorageHeart.lang`; texturas propias 32x32 del Corazón (generadas por
  script `mktex.ps1`, gotcha PS: parentizar restas en `@()`).
- PENDIENTE EN JUEGO: nombres visibles ES, búsqueda ES en creativo,
  aspecto del Corazón colocado (captura).

- Punto 1 VERIFICADO (buscador localizado). Punto 2 cerrado (botones-only).
- UI: doble columna RED+INVENTARIO, Uno+Todo, buscador, botones con marco,
  filas con separador, panel ajustado.
- 0.21.0: `/storage` ELIMINADO (código y mensajes sin comandos);
  `StorageHeart_Key` propia + recetas en mesa (Corazón 10 hierro+15
  tablones; Llave 3 cobre+1 hierro); creativo por Categories.
- REPO GITHUB EN ESPERA: no crear hasta que el usuario dé el visto bueno a
  la v1 (incluirá LICENSE por elegir + wrapper Gradle recomendado).
- PENDIENTE EN JUEGO: carga sin errores de assets, creativo muestra ambos,
  recetas en mesa, crafteo, vínculo con llave NUEVA (Coffer_Key ya no vale).

- Punto 1 VERIFICADO (buscador localizado). Punto 2 cerrado (botones-only).
- 0.18.x–0.19.x: botones con marco+hover, filas tarjeta alternas, cantidad
  dorada, aire entre elementos (capturas OK).
- 0.20.0: RED + INVENTARIO lado a lado (panel 1000x740, buscador común,
  paginador por columna, 6 filas/columna, filas compactas). 0.20.1: panel a
  620 de alto (captura: sobraba hueco).
- PENDIENTE EN JUEGO: captura con filas uniformes + separador (0.20.2).

- Punto 1 VERIFICADO. Punto 2 cerrado (botones-only). Vista conmutable
  Red/Inventario con `Uno`+`Todo` en ambas (Uno de red = 1 unidad).
- 0.18.0: cabecera por zonas, modo RED/INVENTARIO explícito, pie con truco.
- PENDIENTE EN JUEGO: captura de la nueva interfaz (¿cabe todo en 700?).

- Punto 1 VERIFICADO. Punto 2 cerrado (botones-only). Vista conmutable
  Red/Inventario con `Uno`+`Todo` en ambas (Uno de red = 1 unidad).

- Punto 1 VERIFICADO. Punto 2 cerrado (botones-only). Nuevo: vista
  conmutable Red/Inventario (`Ver inventario`, Uno+Todo por pila,
  almacén+mochila sin llave/corazón, buscador en ambas).
- PENDIENTE EN JUEGO: conmutar vistas, Uno/Todo desde inventario, que el
  botón extra quepa en la fila (captura).

- Punto 1 (buscador localizado) VERIFICADO en juego. Punto 2 CERRADO:
  ItemGrid de muestra = mudo en 0.7.2 (8 tipos enlazados + codec numérico +
  5 gestos → cero eventos servidor; genéricos tumban la página).
- 0.16.0: sin bandeja/espejo/sondas/ventana compuesta; página sola, 8
  filas, buscador ES sin tildes, Sacar/Todo, todo/repetidos/mano.
  PENDIENTE EN JUEGO: la página abre y todo sigue funcionando.

- 0.14.0: buscador localizado VERIFICADO en juego por el usuario.
- Ronda 3 resolvió el misterio: `RAW={"Action":"probe","SlotIndex":3}`
  llegaba pero moría en el decode (codec todo-STRING vs números del
  cliente; claves desconocidas como `ShiftHeld` SÍ se ignoran bien).
  0.15.3 declara INTEGER/BOOLEAN reales; `Index` e `ItemStackId` STRING.
- PENDIENTE EN JUEGO: pulsar un item del espejo → ¿`sonda grid=` + depósito?

- 0.14.0: buscador localizado VERIFICADO en juego por el usuario.
- Ronda 3 al modelo superior respondida con experimentos: `RAW=` pre-codec
  (¿números vs codec STRING?), sonda `SlotMouseEntered` (hover), stack real
  del cierre E2 (`withCause`). Su punto 4 no aplica (bindings con datos) y
  su E3 es la vía retirada en 0.9.x.
- PENDIENTE EN JUEGO: líneas `RAW=` al pasar el ratón/pulsar/arrastrar en
  espejo/bandeja + stack de `ventana compuesta cerrada`.

- 0.14.0: buscador localizado VERIFICADO en juego por el usuario.
- 0.15.0 tumbaba la página: el log CLIENTE lo dijo exacto (`... has no
  compatible RightClicking event. Selector: #DepGrid`) → 0.15.1 solo con
  los 6 slot/arrastre. Lección: un binding incompatible tumba TODA la
  página (no solo ese binding).
- PENDIENTE EN JUEGO: ¿llega `sonda grid=` / `arrastre=` al pulsar o
  arrastrar en espejo/bandeja?

- 0.14.0: buscador localizado VERIFICADO en juego por el usuario.
- Sonda punto 2: 8 tipos extra enlazados a bandeja+espejo (`probe`, solo
  log). PENDIENTE EN JUEGO: ¿llega `sonda grid=` / `arrastre=` / `arrastre
  marcado=` al hacer clic/doble-clic/clic-derecho/arrastrar en el espejo o
  la bandeja?

- Buscador filtra por id O nombre visible: clave de
  `ItemStack.getDisplayName().getMessageId()` resuelta con
  `I18nModule.getMessage(PlayerRef.getLanguage(), key)` (+ fallback en-US y
  texto crudo); comparación sin tildes. Filtro `@mod` intacto. Log
  `idioma=X` una vez por página. PENDIENTE VERIFICAR EN JUEGO (buscar
  término ES visible, p. ej. sin tildes).

- Página custom ÚNICA y en buen estado (foto 23:35): nombres ES, iconos,
  cantidades, 6 págs, `3/3 cofres OK`, buscador, Sacar/Todo, todo/repetidos/
  mano, espejo con scroll. F=abrir, Esc/botón=cerrar. Enlace robusto (dobles
  por cualquier mitad, sin duplicar pegados).
- E1b NEGATIVO: el espejo renderiza (iconos+cantidades+scroll+fondo) pero
  NINGÚN evento de grids llega al servidor (cero `arrastre=` en toda la
  sesión pese a loguear todo lo desconocido). Arrastrar/pulsar el espejo o la
  bandeja no hace nada. Depósito = botones (funcionan).
- E2 NEGATIVO: `openCustomPageWithWindows` no lanza nada y
  `ventanas=1` (viva en servidor), pero el cliente no la pinta; se cierra a
  los ~3 s (¿al repintar con `sendUpdate`? ¿el cliente la rechaza?). Sin
  error útil en cliente incluso con Diagnostic Mode (pedir captura si sale).
- Buscador filtra por ID (`itemId.contains`), NO por nombre visible. Pedido:
  buscar por nombre en el idioma del cliente.

- Espejo arrastrable + bandeja con fondo + marcadores dropTray/dropInv +
  depósito por índice de foto. Clic simple también deposita (SlotIndex).
- E2: close-event con stack + `ventanas=N` tras componer.
- PENDIENTE EN JUEGO: líneas `arrastre=` y `compuesta abierta, ventanas=`.

- `openCustomPageWithWindows(página, ContainerWindow(inv vivo))` con
  fallback a página sola. PENDIENTE: ¿compone inventario debajo?
- PENDIENTE DESPLIEGUE: quitar `StorageHeart-0.11.0.jar` con juego CERRADO.

- `ItemGrid #DepGrid` (9 huecos) + `Dropped`/`SlotClicking` sin locks + 7
  claves candidatas en codec. Depósito por id+cantidad + repintado siempre.
  Cada arrastre se loguea para ajustar. Rollback listo si el cliente rechaza.

- Sin interruptor: con la página abierta la F no llega al servidor (evidencia
  en logs; sin KeyDown vanilla que copiar). F abre siempre, Esc/botón cierra.

- Página `CantClose` (Esc no la cierra; probado que `Dismissing` no llega).
  Cierres solo por botón/F → rastreo exacto, F siempre responde.

- `Use: Open_Container` restaurado (interacción inexistente = mod rechazado
  por el validador: probado en log). Sin ventana vanilla no hay F fiable.

- Corazón `Use: StorageHeart_Open` (inexistente): si el evento F llega igual,
  no hay ventana vanilla y el toggle es determinista. Veredicto en log.
- `Dismissing` en `#Panel` + `dismiss` en codec (¿Esc avisa? `dismiss
  recibido` lo confirma). Depósito todo/repetidos = almacén+mochila.
- PROBADO QUE NO EXISTE en 0.7.2: slots de inventario en CustomUI (ninguna de
  23 clases `pages/*` usa SlotClicking/*; `CustomUICommandType` sin bind de
  inventario; 166 `.ui` sin inventario incrustado). Arrastre = imposible hoy.

- F = custom con retardo 300 ms + doble closeAll (la vanilla del Corazón abre
  después del evento; abrir al instante la deja debajo). Antirrebote 400 ms.

- F = página custom (todo paginado + buscador + Sacar/Todo + todo/repetidos/
  mano). Ventana vanilla retirada (sin scroll/buscador posibles desde mod).
- Vecinos por instancia de contenedor (dobles pegados = distintos cofres).
- Depósito hotbar+almacén+mochila; llave/Corazón excluidos del "todo".

- Cierre (Esc/botón) limpia el rastreo (`onClose`); interruptor solo cierra si
  sigue viva. Antirrebote 600 ms intacto.
- Virtual topado a 54 (6x9); aviso con conteo + `/storage abrir` para el resto.
- `chestPosAt` (bloque o vecino con contenedor) en F y comandos; guardas
  anti-duplicado de mitades en `linkSelected` y `unlinkAnywhere`.

- `openAggWindow` usa `setPageWithWindows(Page.Bench, true, w)` + rotation
  real (receta exacta de `OpenContainerInteraction`); `openWindow` suelto era
  lo que el motor cerraba. Fallback custom si dice que no.
- Datos a salvo en todo momento (`syncBack` en `onClose0` verificado en log).
- PENDIENTE VERIFICAR EN JUEGO: ventana estable + contenido agregado.

- F abre `AggNetworkWindow` (virtual 1:1 + `syncBack` en `onClose0`): cofre
  normal con inventario debajo. Página custom = respaldo (`BlockType` nulo) +
  `/storage abrir` (buscador). Antirrebote 600 ms + toggle solo si abierta.
- Constructor probado contra bytecode: `(x,y,z,0,type,container)`;
  `onClose0(Ref, ComponentAccessor)` con `@Override` (compila = firma OK).
- PENDIENTE VERIFICAR EN JUEGO: si el cliente la muestra (revisar log CLIENTE
  si no), si `validateWindows` la respeta, y cuántos huecos renderiza.

- Nombres como `Message` (idioma del jugador). Panel 680x660, 8 filas/pág,
  pie compacto (solo muertos). "Depositar repetidos" + depósito por hueco.
- F con página abierta = cerrar (`openPages`; Esc puede pedir F extra).
- PENDIENTE DESPLIEGUE: quitar `StorageHeart-0.7.1.jar` con el juego CERRADO
  (bloqueado en caliente el 15 oct); solo debe quedar la 0.8.0.

- UI con filas vanilla (Barter): `Pages/StorageHeartRow.ui` + `clear/append`,
  iconos `set #Icon.ItemId`, botones Sacar (64)/Todo con `Index`. Buscador
  verificado en juego (`searchQuery=7` llega).
- Clic derecho NO llega al servidor (cero líneas en sesión completa):
  desvincular = F sin modo en cofre vinculado (`handleChestUse`) o
  `/storage desvincular`. Handler Secondary kept as fallback.
- `StorageHeart_Heart` emite F (`corazonCfg=true` en log). Depositar OK.

- `StorageHeart_Heart` (cubo cristal azul + `Use: Open_Container` + contenedor 9)
  en `src/main/resources/Server/...`; el jar incluye `Server/`. El cristal
  vanilla NO sirve (sin `Use` no hay evento F: probado en log 0.5.0).
- `/storage kit` da Corazón + llave. `HeartBlockId=StorageHeart_Heart`.
  Redes viejas compatibles. Motor: solo bloques con `Use` emiten F.

- SOLO `Rock_Crystal_Blue_Block` crea corazones (prueba no-interactiva);
  llave+F en otro bloque = pista sin fantasmas. Corazón conocido abre siempre.
- Modo vincular persistente (sin caducidad, `selected.json`): 1 clic en corazón
  encadena cofres; repetir termina. F (Use)=vincular idempotente,
  clic derecho (Secondary)=desvincular. `/storage vincular|desvincular` respaldo.
- 1 cofre = 1 corazón (mover, no duplicar). `/storage red` con `*ACTIVO*` y
  `N (K OK, J muertos)`. Datos viejos compatibles (corazones previos siguen).

- P0: buscador con `@SearchQuery` (patrón vanilla) + 9 IDs congelados
  (`append doc → set → bind`, updates sin `append`). Sin filas inline.

- Flujo con items: llave+F en bloque = Corazón creado+marcado (un paso);
  llave+F en cofre = vincular/desvincular (≤32 bl., con coords en mensajes);
  mano vacía+F en Corazón = interfaz propia.
- Interfaz custom (`ui/StoragePage`): abre bien desde 0.4.2, muestra lista en
  texto + buscador + Depositar todo + paginación + estado por cofre
  (OK/NO RESPONDE) + botón "Limpiar rotos" (`purgeDead`). Título con coords.
- Comandos solo para bordes: `red`, `abrir`, `aqui`, `confirmar`, `olvidar`,
  más los legacy de red virtual (`depositar`, `lista`, `buscar`, `retirar`,
  `ordenar`).
- Compila limpio contra `Server-0.7.2.jar` (SHA256 verificado). Núcleo
  verificado por ejecución: 14 checks red virtual + 15 checks vínculos.
- `links.json` en `mods\com.storageheart_StorageHeart` (relativo al server).

## 2. Estructura del proyecto

```
storage-heart/
  settings.gradle / build.gradle / gradle.properties  build Gradle (Java 25)
  src/main/resources/manifest.json   manifiesto (ServerVersion "=<vers>" exacto:
                                     el juego rechaza el número pelado;
                                     IncludesAssetPack=true por la UI)
  src/main/resources/Common/UI/Custom/StorageHeartPage.ui  layout (ver §8)
  src/main/java/com/storageheart/
    StorageHeartPlugin.java    setup/start/shutdown + registro de eventos +
                               onEntityUseBlock (raycast) + onMouseButton +
                               onPlayerInteract + ensureInteract por mundo
    StorageHeartConfig.java    codec: HeartBlockId, LinkKeyItemId,
                               MaxLinkDistance=32, MaxChestsPerNet=32,
                               MarkExpirySeconds=30 (vacío = default vanilla)
    StorageNetworkService.java inventario del jugador (StorageComponent),
                               give, sort nativo, toStack
    commands/ — ELIMINADO en 0.21.0 (era /storage; uso solo con items)
    core/StorageNetwork.java   red virtual pura (lógica + tests javac)
    core/BlockPos.java         pos mundo+x,y,z, key(), distanceTo()
    core/LinkStore.java        vínculos + JSON manual (tests javac)
    link/LinkService.java      handleClick unificado: corazón manda >
                               cofre+llave > bloque+llave > vanilla;
                               propose/confirm/forget/purgeDead/chestStatus +
                               deadChestLines + selección persistente
                               (selected.json) + handleChestUse/unlinkAnywhere
    link/NetworkModel.java     agregado/retirada/depósito sobre cofres reales
                               (todo/repetidos/mano por hueco) + fillVirtual/
                               syncBack de repuesto (vía vanilla retirada)
    link/HeartUseEntitySystem.java + HeartUseChunkSystem.java
                               sistemas ECS (NUNCA recibieron nada; borrar)
    ui/StoragePage.java        InteractiveCustomUIPage: buscador (@SearchQuery),
                               filas con iconos (Barter-style `#Lista[i] #X`),
                               Sacar/Todo, depositar, espejo ItemGrid + bandeja
                               (mudos: E1b-negativo), F=abrir/Esc=cerrar
  src/main/resources/Common/UI/Custom/StorageHeartPage.ui + Pages/StorageHeartRow.ui
  src/main/resources/Server/Item/Items/StorageHeart/StorageHeart_Heart.json
                               bloque propio (cubo cristal + Use:Open_Container)
  assets/HEART_AND_KEY_GUIDE.md  guía Asset Editor (pendiente: bloque propio)
  build/ (gitignored) servercheck/, sources.txt (lista fija de .java: REGENERAR
                               al añadir ficheros), verify/
  dist/ (gitignored) jars construidos a mano
  README.md / CHANGELOG.md / AGENTS.md
```

## 3. Reglas de arquitectura (obligatorias)

1. **El núcleo (`core/`) nunca importa `com.hypixel.*`.** Testeable con javac.
2. **Toda la API de Hytale en adaptadores** (`StorageNetworkService`,
   `link/*`, `ui/*`). El fix de un update va ahí, jamás en `core/`.
3. **Prohibido `inventory.Inventory`** (`@Deprecated(forRemoval)`).
4. **No reimplementar** lo nativo: `sortItems`, `quickStackTo`,
   `Player.giveItem`, `TargetUtil`, `World.scheduleAfter`.
5. Config por BUILDER codec: STRING/BOOLEAN/INTEGER verificados en jar.
   Comandos: `RequiredArg`/`DefaultArg` + `ArgTypes.*`, sin parseo manual.
6. Mensajes al jugador: español, cortos, con coords y siguiente paso.
   Crear ("Corazón NUEVO aquí [x,y,z]") vs vincular ("al corazón [hx,hy,hz]")
   siempre distinguibles.
7. **Vínculos por posición, no por bloque.** Un chunk descargado también da
   null: NUNCA auto-borrar en silencio; mostrar NO RESPONDE + purga explícita.
8. Ante duda de API: `javap` contra el Server.jar vigente, no memoria ni wikis.

## 4. Eventos: qué funciona y qué no (evidencia de juego, no teoría)

- **SÍ llega:** `registerGlobal(LivingEntityUseBlockEvent)` (keyed `IEvent<String>`;
  `registerGlobal` evita adivinar la clave). OJO: el `getBlockType()` trae
  sufijos (`*Id_State_Definitions_CloseWindow`); normalizar en
  `LinkStore.normalizeBlockId` (quitar `*` y desde `_State_`). Puede dispararse
  al ABRIR o al CERRAR (visto `CloseWindow`): el código no asume momento.
  No trae posición → raycast `TargetUtil.getTargetBlock(ref, 8.0, store)`;
  mano con `InventoryComponent.getItemInHand(store, ref)`.
- **NUNCA llegó nada:** sistemas ECS `UseBlockEvent` (aunque `registerSystem`
  + `registerEntityEventType` + `Query.any()` compilan y loguean registro),
  `PlayerInteractEvent` keyed por mundo (clave adivinada; además `@Deprecated`),
  `PlayerMouseButtonEvent` (no keyed; ni un `Right/Pressed` en logs).
- PlayerConnectEvent (no keyed) y comandos funcionan siempre.
- El evento de uso NO es cancelable: tras actuar con llave, `closeAllWindows`.
  La ventana vanilla del cofre-corazón se abre igual; la nuestra va con
  retardo 300 ms (`World.scheduleAfter`) para quedar encima.

## 5. UI custom (lo que costó sangre)

- Sistema REAL en 0.7.2: `PageManager.openCustomPage(ref, store, CustomUIPage)`,
  `InteractiveCustomUIPage<T>` + codec (claves en MAYÚSCULA), builders en
  `...ui.builder` (`UICommandBuilder`, `UIEventBuilder`, `EventData`),
  `CustomUIEventBindingType` (Activating/ValueChanged/...),
  `CustomPageLifetime.CanDismiss`. `sendUpdate(cmd, ev, clear)` es protected
  (OK desde subclase). NO existen `close()` ni `rebuild()` (cerrar con
  `PageManager.closePage`).
- Documentos `.ui` se citan relativos a `Common/UI/Custom/`:
  `Common/UI/Custom/Pages/ShopPage.ui` → `"Pages/ShopPage.ui"`. El nuestro
  (`Common/UI/Custom/StorageHeartPage.ui`) → `"StorageHeartPage.ui"`.
  El prefijo `"Custom/..."` de la wiki DESCONECTA al cliente. `IncludesAssetPack:
  true` + fichero en el jar; el log confirma `Loaded pack ... from <jar>`.
- Sintaxis copiada de built-ins: `ItemSlot` crudo, `Button{Label}`,
  `$C.@TextField` (pero $C puede romper; TextField crudo también vale).
- Crash `Value cannot be null (Parameter 'element')` = selector inexistente
  (doc roto, markup inline roto, o binding a selector ausente). Aislado
  quitando $C + filas inline + bindings por fila (0.4.2 abrió).
- `setObject(selector, ItemStack)` NO hizo falta: iconos vía
  `set("#Icon.ItemId", itemId)` (patrón Barter, verificado en juego).
  Buscador verificado: `EventData.of("Action","search").append("@SearchQuery",
  "#SearchField.Value")` + `KeyedCodec("@SearchQuery")` (llega: `searchQuery=7`).
- `ItemGrid` (E1b): renderiza slots+fondo (`SlotBackground` `@2x` por defecto
  sin sufijo)+scroll con `set("#X.Slots", List<ItemGridSlot>)`, pero MUDO:
  `Dropped`/`SlotClicking` sin locks + 7 claves en codec y cero eventos en
  sesión completa. Espejo = foto (`List<ItemStack>` por repintado).
- Compuesta (E2): `openCustomPageWithWindows(página, ContainerWindow(inv))`
  no lanza nada, `getWindows()=1` en servidor, invisible en cliente, muere
  ~3 s. `setPageWithWindows(Page.Bench, ContainerBlockWindow)` sí mostraba
  (pero rejilla fija sin scroll/búsqueda: topó en 54).
- Cierres: `Dismissing` no llega; `CantClose` quita Esc (molesto, revertido).
  Con página abierta la F no llega al servidor → F=abrir, Esc/botón=cerrar.
- Ashe validación: interacción inexistente en JSON = mod rechazado entero
  (`Asset validation FAILED`). Solo ideas que validen.

## 6. Verificado por ejecución (no afirmar sin esto)

- `javac` contra `Server-0.7.2.jar` oficial, exit 0 (solo warnings benignos:
  this-escape del template oficial + deprecations marcadas).
- Harness `HeartTest` (14/14) y `LinkTest` (15/15) en `C:\...\Temp\opencode`
  (no están en el repo; recrear si hace falta).
- En juego (0.13.0): enlace robusto (F=vincular con modo, F sin modo=
  desvincular, dobles por cualquier mitad sin duplicar pegados),
  `/storage red|vincular|desvincular|kit|abrir`, página con nombres ES,
  iconos, 45 tipos/6 págs, Sacar/Todo, depósito todo/repetidos/mano.
  0.14.0 (buscador localizado) compila y está desplegada, PENDIENTE de
  probar en juego. PENDIENTE: arrastre, compuesta.

## 7. Despliegue pruebas (máquina actual, Hytale en D:\Hytale)

- Mundo: `D:\Hytale\UserData\Saves\test mod\mods\`. Juego/MUNDO CERRADO para
  tocar jars (bloqueo en caliente). UN SOLO `StorageHeart-X.jar` (dos
  versiones = carga la vieja). SHA256 origen == destino.
- Logs servidor: `Saves\test mod\logs\`; cliente: `D:\Hytale\UserData\Logs\`
  (los errores UI salen AQUÍ, no en el server).
- Data del mod: `mods\com.storageheart_StorageHeart\` (relativo al server).
- Uso solo con items (0.21.0): Corazón + Llave crafteables en mesa
  (`Workbench_Crafting`, sin conocimiento) y en creativo por Categories.
  Llave por defecto `StorageHeart_Key`. Sin comandos (ni fallback).

## 8. Pendiente (priorizado para la siguiente sesión)

1. **Buscador por nombre visible: HECHO en 0.14.0** (pendiente verificar en
   juego). Si el nombre de algún item no casa, mirar el log (`idioma=` +
   primer `evento página=`) y la clave que devuelve `getMessageId`.
2. **Depósito por arrastre: CERRADO (botones-only) en 0.16.0.** ItemGrid de
   muestra mudo en 0.7.2 con evidencia completa (E1b, sondas 0.15.x, ronda 3,
   encuesta de 5 gestos con cero `RAW=`). Reabrir solo si un update o un
   ejemplo vanilla demuestra input de grid en CustomUI.
3. **Ventana compuesta: QUITADA en 0.16.0** (invisible en cliente, muerte a
   los ~3 s, cero valor). Página custom sola. Reabrir solo con ejemplo que
   la pinte en 0.7.2.
4. **Deuda previa sin tocar:** cristal como corazón descartado (sin `Use` no
   hay evento); `LivingEntityUseBlockEvent(forRemoval)` → migrar a
   `UseBlockEvent$Pre` si algún día llega a plugins; quitar sistemas ECS
   muertos; Gradle wrapper; `LinkStore` sin sincronizar; recetas del Corazón
   (hoy solo `/storage kit`); guía Asset Editor desactualizada.

## 12. Sesión 15 oct tarde-noche (resumen para retomar)

- 0.9.x: ventana vanilla agregada → topó en 54 slots sin scroll/búsqueda;
  retirada (clases `fillVirtual/syncBack` quedan como repuesto en
  `NetworkModel`). F = página custom única desde 0.10.0.
- 0.10.x: toggle-F imposible (con página abierta la F no llega al servidor;
  sin KeyDown vanilla que copiar) → contrato F=abrir, Esc/botón=cerrar.
  `Dismissing` no llega; `CantClose` probado y revertido (molesto).
  Interacción inexistente en JSON = mod rechazado por el validador (probar
  solo ideas válidas contra `AssetStore`).
- 0.11–0.13: `ItemGrid` renderiza (slots+fondo+scroll) pero mudo; `SlotIndex`
  etc. en codec sin efecto (nada que decodificar). Compuesta viva en servidor
  e invisible en cliente.
- Reglas que salieron de esta sesión: evidencia (log/jar/`javap`) > docs >
  memoria; un solo jar en `mods` (juego cerrado para borrar); SHA origen ==
  destino; errores UI viven en el log CLIENTE + captura; `append` de docs
  relativo a `Common/UI/Custom/`; filas `#Lista[i] #X` (los `` del bytecode
  eran receta `StringConcatFactory`, no sintaxis).

1. **Estabilidad interfaz 0.4.3** (¿vuelve a no salir? el log manda):
   sospechosos = binding `#CleanBtn`, líneas de estado, título con coords.
2. Reintroducir filas con iconos (`setObject`) + botones Sacar/Todo por partes.
3. Clave del texto del buscador (ver primer `PageEvent` logueado).
4. `ContainerBlockWindow` virtual: el cliente nunca la mostró (¿validate por
   tipo? ¿tapada por vanilla?). Eliminada en 0.4.0; solo resucitar si la custom
   falla.
5. Cristal como corazón si no emite eventos (usar cofre-corazón mientras tanto;
   documentado en guía).
6. Bloque Corazón + llave propios en Asset Editor + recetas (guía existe).
7. Auto-drop de corazón al romper su bloque; multi-mundo ya soportado por
   diseño (links por mundo); acceso remoto; tiers de unidades.
8. Gradle wrapper real (hoy se compila con javac+jar a mano, §9).

## 9. Compilar y empaquetar (vía probada)

```powershell
cd storage-heart
$files = Get-ChildItem -Recurse src\main\java -Filter *.java | ForEach-Object { '"' + ($_.FullName -replace '\\','/') + '"' }
[IO.File]::WriteAllLines("$pwd/build/sources.txt", $files)
javac -cp '<ruta>\Server-X.jar' -d build/servercheck "@${pwd}/build/sources.txt"
& 'C:\Program Files\Java\jdk-25\bin\jar.exe' --create --file dist/StorageHeart-<v>.jar -C build/servercheck . -C src/main/resources manifest.json -C src/main/resources Common -C src/main/resources Server
```
Server.jar: `https://maven.hytale.com/release/com/hypixel/hytale/Server/<v>/Server-<v>.jar`
(maven-metadata.xml → latest; 0.7.2 SHA256 verificado contra re-descarga).

## 10. Workflow de update de Hytale

1. `hytale.com/news/game-updates` → sección Modder-Facing.
2. Fijar `hytaleVersion` + `ServerVersion` `"=<v>"` (el juego rechaza número pelado).
3. Descargar jar, recompilar, anotar warnings (forRemoval = migrar YA).
4. Aislar roturas en adaptadores; re-verificar núcleo + juego.
5. Entrada en CHANGELOG aunque sea "sin cambios". Probar en juego antes de darlo por bueno.

## 11. Convenciones

- Español, Java 25, UTF-8, comentarios concisos (nada de ensayos en código).
- `build/`, `dist/`, `data/`, `.idea/`, `*.iml` gitignored.
- Este AGENTS.md manda: evidencia (log/jar) > docs > memoria.
