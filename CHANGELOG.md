# Changelog — StorageHeart

## 1.1.0 — bugfix release (variants, links and UI)

- **Hytale-style interface.** The page now uses the vanilla container frame
  (`ContainerHeader`/`ContainerPatch`, top/bottom ornaments and the X close
  button), the standard input box and the secondary/tertiary button styles.
  The two lists render as container slot rows with quality backgrounds.
- **The custom interface closes with F too.** `StoragePage` now uses
  `CustomPageLifetime.CanDismissOrCloseThroughInteraction` (the vanilla
  lifetime used by chest-like pages), so F closes it like a default chest.
  Esc and the Close button still work.
- **No vanilla chest window flash.** The Heart now registers a real
  `UseBlockEvent.Pre` ECS system and cancels it before the engine executes
  `Open_Container`; the 9-slot vanilla window never appears and the custom
  page opens immediately. If a future server stops delivering `Pre`, the old
  `LivingEntityUseBlockEvent` fallback (with delayed close) still keeps the
  mod working.
- **Items keep their data on withdraw.** The network groups by variant
  (id + durability + quality + metadata), not just by item id. Damaged tools,
  named items and quality variants are no longer merged or rebuilt as
  pristine stacks. Taking a damaged sword returns that same sword.
- **No more accidental unlink.** With no link mode, F with the Key on a chest
  opens the chest normally. Unlink is now **Ctrl+F with the Key** (or
  right-click as fallback); the Heart UI explains it.
- **The Key no longer blocks vanilla use.** F with the Key on doors,
  workbenches and other blocks falls through to vanilla when you are not
  linking.
- **Stale Hearts no longer hijack blocks.** If a Heart is broken and another
  block is placed in its position, the old position is ignored until a real
  Heart returns.
- **The Heart can no longer link itself as a chest.** Chest resolution skips
  all known Hearts, and legacy self-links are cleaned on load.
- **Concurrency-safe links and atomic saves.** LinkStore is synchronized,
  per-player state uses concurrent maps, and `links.json`/`selected.json`
  are written to a temp file, moved atomically and keep a `.bak`.
- **Correct chest counter.** The header now shows responding/linked chests
  (`2/3`, never negative) even when chunks are unloaded.
- **UI actions use a stable variant id**, not only a list index: another
  player moving items can no longer make you withdraw the wrong item.
- **Range checks while the page is open:** actions outside 10 blocks (or in
  another world) close the page.
- **Chest cap applies when moving** a chest into a network, and
  "Deposit matching" never sweeps keys/Hearts.
- **`Clean broken` asks for confirmation** (press twice within 15s) and caps
  the number of dead-chest lines shown.
- **Withdraw remainder is re-deposited preserving metadata**; if it cannot be
  returned, it is logged and reported instead of silently disappearing.
- **Build fixes:** `build.ps1` creates `build/` before writing `sources.txt`
  (works on a fresh clone), compiles with UTF-8 and packages all resources.
  Gradle now pins `hytaleVersion`, avoids duplicate manifest entries and
  uses 1.1.0.
- **Cleanup:** removed the unused virtual `StorageNetwork`, dead ECS systems,
  deprecated `PlayerInteractEvent` listener, fake network shutdown log and
  noisy per-click INFO logs. `Deposit held` uses the active hotbar slot
  instead of deprecated metadata scanning.
- Search `@mod` now works in the inventory column too; sort mode from config
  is honored.

## 1.0.0 — first production version (English, repo-ready)

- Everything user-facing in English: UI, chat messages, item names,
  manifest, README. MIT license, `.gitignore`, `build.ps1`.
- Repo: https://github.com/Cosmar43/storage-heart

## 0.26.0 — nombres directos (las claves de mod no resuelven en 0.7.2)

- Evidencia completa: `.lang` cargan 4 entradas pero en un mapa que ni el
  validador (`getMessages("en-US")`) ni el cliente consultan; el evento de
  idioma solo lo dispara un comando dev. Vuelven los nombres directos ES
  (se muestran bien en mesa/creativo/mano) y fuera `.lang` + warnings [LOC].
- Búsqueda ES en el creativo: limitación vanilla (busca por ID/clave).

## 0.25.0 — nombres por `.lang` del pack + textura de llave propia

- El evento de idioma solo lo dispara un comando de desarrollo (nunca en
  juego): fuera listener, vuelven los `.lang` es/en del pack. Veredicto en
  el log (`Loaded N entries`) y en juego.
- `StorageHeart_Key_Texture.png` propia (misma geometría/UV Coffer,
  paleta oro+esmeralda): la llave en mano/mundo ya es nuestra.

## 0.24.0 — nombres vía evento de idioma + icono de llave propio

- Los `.lang` sueltos del pack no registraban nada (claves sin resolver en
  mesa/creativo): nombres por `GenerateDefaultLanguageEvent` (es+en).
- Icono `StorageHeart_Key.png` 64x64 propio (aro-corazón oro + vástago;
  el modelo 3D en mano/mundo sigue el vanilla hasta Blockbench).

## 0.23.0 — icono y texturas estilo Terraria (marco oro + corazón esmeralda)

## 0.22.0 — nombres localizados (es/en) + texturas propias del Corazón

- `TranslationProperties` por claves (`server.items.StorageHeart_*`) con
  `Server/Languages/es-ES|en-US/StorageHeart.lang`. Si el creativo sigue
  buscando solo por ID, es limitación vanilla (nombres visibles OK).
- Corazón con texturas propias 32x32 generadas (`Heart_Side` con runa +
  `Heart_Top` concéntrico) en vez de las del cristal vanilla.

## 0.21.0 — producción: sin comandos, llave propia, crafteos

- `/storage` eliminado (paquete `commands/` + registro + proponer/confirmar/
  olvidar huérfanos). Todo el uso por items: F con Llave, F en Corazón.
- `StorageHeart_Key` propia (receta: mesa, 3 cobre + 1 hierro) y receta del
  Corazón (mesa, 10 hierro + 15 tablones `Wood_Planks`); sin conocimiento
  requerido. Llave por defecto `StorageHeart_Key` (la `Coffer_Key` deja de
  vincular). Mensajes sin referencias a comandos.

## 0.20.2 — filas uniformes con separador fino (fuera fondo alterno)

## 0.20.1 — panel ajustado a 1000x620 (sin hueco inferior)

## 0.20.0 — red e inventario lado a lado (estilo cofre vanilla)

- Panel 1000x740 con dos columnas simultáneas (RED dorada + INVENTARIO
  azul), buscador común que filtra ambas, paginador propio por columna,
  6 filas/columna. Sin vista conmutable; filas compactas (icono 44).

## 0.19.1 — aire entre elementos (márgenes `Anchor Right: 6` vanilla)

## 0.19.0 — botones con marco+hover y filas tarjeta (patrones vanilla)

- `Button` con `Style` de estados (fondos #232c38/#33445a/#1a2230, como
  `TextButton.ui` y `ReturnToHubButton.ui`): todos los botones con marco y
  resaltado al pasar el ratón. Mismos widgets e ids → eventos intactos.
- Filas alternas (`StorageHeartRowAlt.ui` con fondo #161d28), cantidad en
  dorado negrita, divisor de 1px bajo la paginación (patrón Barter).

## 0.18.2 — inventario agregado por tipo (una fila por tipo, como la red)

## 0.18.1 — listas alfabéticas por nombre visible (sin tildes)

## 0.18.0 — interfaz reordenada (jerarquía + modo explícito + truco)

- Cabecera: título dorado; fila buscar+vista; fila de acciones (Meter
  todo/repetidos/mano + Limpiar rotos); paginación con info centrada.
- Etiqueta de modo `RED · …` / `INVENTARIO · …` y pie con truco (@mod, F,
  Esc). Panel 680x700. Sin cambios de lógica.

## 0.17.1 — `Uno` en ambas vistas (Sacar de red retira 1, Todo intacto)

## 0.17.0 — lista del inventario con Uno/Todo (idea del usuario)

- Botón `Ver inventario`/`Ver red` conmuta la lista en el mismo hueco (8
  filas, buscador localizado también aquí). Cada pila: icono, nombre ES,
  cantidad, `Uno` (mete 1) y `Todo` (mete la pila).
- Ámbito almacén+mochila (como Depositar todo; hotbar vía Depositar mano);
  llave y corazón excluidos de la lista.

## 0.16.0 — punto 2 cerrado: botones-only (ItemGrid de muestra es mudo)

- Evidencia final: 8 tipos slot/arrastre enlazados, codec con tipos reales,
  5 gestos (hover/clic/doble/arrastrar) → cero eventos en el servidor.
  Fuera bandeja, espejo, sondas, ramas de depósito por grid y ventana
  compuesta (invisible + cierre a los ~3 s): página custom sola, 8 filas,
  buscador localizado, Sacar/Todo, todo/repetidos/mano.
- `ShiftHeld` (BOOLEAN, real según RAW) reservada para futuro shift-clic.

## 0.15.3 — codec con tipos reales (el cliente manda números)

- Evidencia (`RAW={"Action":"probe","SlotIndex":3}` que nunca llegó al
  handler): claves de slot/cantidad/botón/sección como `INTEGER`,
  `ShiftHeld` como `BOOLEAN`; `ItemStackId` e `Index` siguen `STRING`.
  Si alguna clave aún discrepa, el `RAW=` lo dirá en el siguiente log.

## 0.15.2 — ronda 3: RAW pre-codec + hover + stack del cierre

- `handleDataEvent(ref, store, String)` loguea `RAW=` antes de delegar al
  `super` (¿manda el cliente números que el codec todo-STRING no decodifica?).
- Sonda `SlotMouseEntered` en bandeja+espejo (basta pasar el ratón).
- `registerCloseEvent` con `withCause` (stack real de quién cierra la
  compuesta). Punto 4 del modelo no aplica: nuestros bindings YA llevan
  `EventData`; el E3 que pide es la vía vanilla retirada en 0.9.x (tope 54).

## 0.15.1 — sonda solo familia slot (RightClicking tumbaba la página)

- Evidencia del log cliente: `... has no compatible RightClicking event.
  Selector: #DepGrid` → fuera `RightClicking` y `MouseButtonReleased`
  (genéricos incompatibles con `ItemGrid`). Quedan los 6 slot/arrastre.

- Los 8 tipos de slot/puntero restantes del enum (`SlotDoubleClicking`,
  `SlotMouseDragCompleted`, `SlotClickPressWhileDragging`,
  `SlotClickReleaseWhileDragging`, `SlotMouseDragExited`, `DragCancelled`,
  `RightClicking`, `MouseButtonReleased`) enlazados a bandeja+espejo con
  marcador `probe`; el handler los loguea (`sonda grid=`) sin mensajes.
- Si llega aunque sea uno, se cablea el depósito; si no llega ninguno, se
  decide botones-only (o ronda 3 al modelo superior).

## 0.14.0 — buscador por nombre visible (idioma del cliente)

- `currentRows` filtra por id O por nombre visible: clave de
  `ItemStack.getDisplayName().getMessageId()` resuelta con
  `I18nModule.getMessage(PlayerRef.getLanguage(), key)` (+ fallback en-US,
  + texto crudo). Comparación sin tildes (`árbol` = `arbol`).
- Filtro `@mod` y por id intactos. Log `idioma=X` una vez por página.

## 0.13.0 — espejo arrastrable (E1b) + diagnóstico E2 con stack

- Espejo del inventario en `ItemGrid #InvGrid` (9 col, scroll, arrastrable,
  fondo `BlockSelectorSlotBackground`) reconstruido en cada repintado; clic o
  arrastre deposita por índice de foto (id+cantidad+meta exactos). Bandeja
  como destino + marcadores `dropTray`/`dropInv` (si el cliente los reenvía;
  si no, el log `arrastre=` trae las claves reales).
- E2 diagnosticado: `registerCloseEvent` con stack + conteo de ventanas tras
  componer (¿viva e ignorada por el cliente, o cerrada por quién?).
- Red 6 filas/pág; panel 800 px (se ajusta con tu captura).

## 0.12.0 — página + ventana de inventario compuestas (experimento)

- `openCustomPageWithWindows(página, ContainerWindow(inventario vivo))`: si
  el cliente compone, el inventario sale debajo y hay origen para arrastrar a
  la bandeja (el hueco vacío no renderiza nada: normal). Con try/catch a
  página sola si el cliente lo rechaza.
- La página por lo demás intacta (nombres ES, 45 tipos, 6 págs en tu foto).

## 0.11.0 — bandeja de depósito con arrastre (experimento con red)

- El modelo superior tenía razón en dos cosas (verificadas con `javap` en
  `Server-0.7.2.jar`): existen `PageManager.openCustomPageWithWindows` y la
  clase `ItemGridSlot` (con codec) + `set(String, List)`; y los bindings de
  slots/drag existen en el enum (mi barrido solo miró clases `pages/*`, que
  no tienen por qué consumirlos).
- Experimento: `ItemGrid #DepGrid` (9 huecos, arrastrable, con scroll
  desactivado) + bindings `Dropped`/`SlotClicking` sin locks + 7 claves de
  evento candidatas en el codec. Al arrastrar se deposita por id+cantidad y
  SIEMPRE se repinta (nada de "Loading..." colgado). Cada arrastre se loguea
  (`arrastre=...`) para ajustar claves.
- Si el cliente rechaza `#DepGrid.Slots` o no manda nada al arrastrar,
  siguiente versión lo retira y se prueba `openCustomPageWithWindows`.

## 0.10.5 — F abre siempre; Esc cierra (adiós interruptor imposible)

- Verdad incómoda con evidencia: con la página abierta la F NO llega al
  servidor (cero `Use` con página abierta en todos los logs; ningún binding
  de teclado consumido por vanilla que copiar). "Cerrar con F" no se puede
  implementar en 0.7.2: el interruptor solo fabricaba la F muerta tras Esc.
- Nuevo contrato determinista: F = abrir siempre (el rastreo obsoleto se pisa
  al abrir, nunca bloquea); cerrar = botón o Esc (`CanDismiss` restaurado,
  adiós menú pausa encima).

## 0.10.4 — Esc no avisa: página que no se puede descartar (interruptor exacto)

- Causa del "tras Esc la siguiente F no abre": Esc no envía NADA al servidor
  (`dismiss recibido` jamás apareció en el log; el binding `Dismissing` no
  sirve en 0.7.2). El rastreo quedaba obsoleto y la F creía que seguía
  abierta.
- La página pasa a `CantClose`: Esc no la cierra; todo cierre va por el botón
  Cerrar o por F, ambos rastreados. Determinista al 100%.

## 0.10.3 — revert interacción inexistente (el validador tumba el mod)

- El experimento salió mal PERO con veredicto útil: el `AssetStore` valida en
  carga y `FAIL: Asset 'StorageHeart_Open' ... doesn't exist!` + `Asset
  validation FAILED` impide cargar el mod entero. Interacción inexistente =
  imposible; descartado para siempre. Vuelta a `Open_Container`.
- Se mantienen `Dismissing`, depósito sin hotbar y vecinos por instancia.

## 0.10.2 — Corazón sin ventana vanilla (interacción inexistente) + dismiss + sin hotbar

- El Corazón declara `Use: StorageHeart_Open` (no existe): si el motor emite
  el evento F igualmente, desaparece la ventana vanilla de raíz (sin carreras
  de closeAll) y el toggle pasa a ser determinista. Árbol de decisión en el
  log: si ves `uso bloque=StorageHeart_Heart` + custom estable = funciona;
  si no hay `uso bloque` = el cliente no envía nada y se revierte el JSON.
- Escucha `Dismissing` en `#Panel`: si el cliente avisa al cerrar con Esc, el
  rastreo deja de quedar obsoleto (lo confirma `dismiss recibido` en el log).
- "Depositar todo/repetidos" = almacén+mochila (hotbar fuera, como pediste).
  "Depositar mano" sigue buscando también en la hotbar (la mano vive ahí).

## 0.10.1 — regresión: la vanilla tapaba la custom (retardo restaurado)

- Al retirar la vía vanilla quité también el retardo de 300 ms y la ventana
  del propio Corazón vuelve a abrirse encima (el log muestra la nuestra
  abriéndose debajo). Restaurado: cerrar todo + abrir con retardo.

## 0.10.0 — interfaz única custom (todo + buscador) + vecinos por instancia + depósito mano

- F vuelve a la página custom como interfaz ÚNICA: muestra TODO (paginado),
  con buscador integrado. La ventana vanilla se retira: sin scroll ni
  buscador posibles desde un mod, topaba en 54 y violaba la esencia.
  (`fillVirtual/syncBack` quedan como repuesto documentado.)
- Vecinos: el guardián anti-duplicado comparaba posición y bloqueaba dobles
  pegados. Ahora compara INSTANCIA de contenedor: misma instancia = misma
  mitad (no duplica); distinta = cofre distinto (vincula). Igual en
  `unlinkAnywhere`. La puntería (`chestPosAt`) sigue valiendo cualquier mitad.
- Depósito: ahora cubre hotbar+almacén+mochila (antes solo almacén; la hotbar
  nunca entraba) y excluye llave/Corazón del "todo". Nuevo botón "Depositar
  mano": sujetas la pila y la mete (localiza su hueco por id+cantidad+meta).

## 0.9.2 — abre siempre + tope 54 + cofres dobles por cualquier mitad

- Abrir siempre: al cerrar con Esc el rastreo quedaba puesto y la 2ª F creía
  que seguía abierta (de ahí "se queda en el cofre pequeño"). Ahora el cierre
  limpia el rastreo y el interruptor solo cierra si la ventana sigue viva; si
  no, abre de nuevo.
- Tope 54 huecos (6x9): el cliente no tiene scroll y más rejilla se sale de la
  pantalla (tu foto). Si hay más pilas, avisa y manda a `/storage abrir`
  (buscador + Sacar/Todo). Scroll real en ventana vanilla: imposible.
- Cofres dobles: ocupan dos bloques y solo una mitad expone el contenedor
  (por eso pedía el lado derecho). Ahora vale cualquier mitad (se resuelve al
  vecino con contenedor) y no se duplica si vinculas las dos.

## 0.9.1 — la ventana iba FUERA de página (el motor la cerraba solo)

- Causa raíz con bytecode vanilla (`OpenContainerInteraction`): los cofres no
  usan `WindowManager.openWindow` suelto, sino
  `PageManager.setPageWithWindows(ref, store, Page.Bench, true, window)`.
  Nuestra ventana suelta se abría y el motor la cerraba (<1 s, tal cual
  reportaste); el `syncBack` evitó pérdidas en todo momento.
- Ahora idéntico a vanilla: `Page.Bench` + `rotationIndex` real del chunk +
  fallback a la página custom si `setPageWithWindows` dice que no.

## 0.9.0 — ventana cofre agregada (inventario debajo) + apertura robusta

- F en el Corazón abre una ventana de cofre normal con TODO lo de la red
  arriba y tu inventario debajo: depositar = arrastrar/clic como en un cofre.
  Al cerrar, el contenido vuelve solo a los cofres (`syncBack` en `onClose0`;
  la página custom queda como respaldo + `/storage abrir` para buscar).
- Apertura al 100%: la causa del "a veces no abre" era la carrera entre mi
  interruptor-F y la apertura con retardo (la 2ª F cerraba lo que la 1ª aún no
  había abierto). Ahora: antirrebote 600 ms + interruptor solo si la ventana
  sigue abierta + doble `closeAllWindows` contra la vanilla del propio corazón.
- Si el cliente no muestra la ventana, el log del cliente manda (la anterior
  vez el contenedor virtual nunca se diagnosticó desde ese lado).

## 0.8.0 — nombres en tu idioma + panel que encaja + repetidos + F cierra

- Nombres: viajaban como `Message.toString()` (el `@hash` de tu captura).
  Ahora viajan como `Message` (igual que la tienda vanilla): el cliente los
  muestra en el idioma del jugador.
- Panel 680x660 con 8 filas/pág: las 9 filas ya no se salen del fondo. El pie
  solo lista cofres muertos (o "Todos responden"); el conteo va arriba
  (`1/2 cofres OK · 9 tipos · pág 1/1`).
- Depósito parcial: botón "Depositar repetidos" (quick-stack: solo tipos que
  ya están en la red). "Depositar todo" sigue. Además el depósito ahora
  descuenta por hueco (el drenaje global podía confundir tipos si no cabía).
- F con el menú abierto lo cierra (interruptor). Cerrar con el botón o F
  vale; si se cierra con Esc puede pedir una F extra (se auto-repara).

## 0.7.1 — selector de filas corregido (los  eran receta javap, no sintaxis)

- El error `Selected element ... #ItemList[][0][] #Name.Text` era mío: leí
  mal el bytecode de Barter. Los `+` del `makeConcatWithConstants` NO son
  parte del selector; el formato real es `#TradeGrid[5] #OutputSlot.ItemId`
  (índice decimal entre corchetes + espacio). Filas ahora `#ItemList[i] #X`.

## 0.7.0 — filas con iconos + Sacar/Todo + desvincular por contexto (el clic derecho no llega)

- Clic derecho: evidencia en log (cero líneas `mouse-event`/`interact-event`/
  `Secondary` en toda la sesión): el motor no entrega Secondary al servidor.
  Se mantiene el handler como respaldo, pero el gesto real pasa a ser por
  contexto con F (la única vía que llega): CON modo vincular + F = vincular;
  SIN modo + F en cofre vinculado = desvincular de su red. Mensajes y
  `/storage red` enseñan el flujo. `/storage desvincular` sigue como respaldo.
- Interfaz con filas (patrón vanilla Barter): `Pages/StorageHeartRow.ui`
  (icono + nombre + cantidad + Sacar + Todo), `clear + append` por fila,
  iconos vía `set #Icon.ItemId`, 1 binding por botón con `Index`. Sacar da 64,
  Todo vacía el tipo (el resto vuelve si no cabe). Estado de cofres abajo.

## 0.6.0 — bloque Corazón propio (el cristal no emite eventos F)

- Causa raíz (evidencia en log 0.5.0): el motor solo dispara
  `LivingEntityUseBlockEvent` en bloques con interacción `Use`. El cofre la
  tiene (`Use: Open_Container`) y el `Rock_Crystal_Blue_Block` no (solo
  `Secondary: Lantern_Blue`): F en el cristal nunca llega al mod. Lógica 0.5.0
  intacta (los cofres lo prueban en el mismo log).
- Nuevo `StorageHeart_Heart` (`Server/Item/Items/StorageHeart/`): cubo con
  texturas del cristal azul + `Use: Open_Container` + contenedor 9 (el
  inventario propio no se usa; la red son los cofres vinculados) + luz azul.
  Sin receta: se obtiene con `/storage kit` (también da la llave).
- `HeartBlockId` por defecto = `StorageHeart_Heart`. Redes viejas compatibles.

## 0.5.0 — enlace robusto: solo-bloque-corazón, F vincular / clic-derecho desvincular, selección persistente

- SOLO el bloque Corazón (`Rock_Crystal_Blue_Block` de prueba, no interactivo)
  crea corazones. Llave+F en cualquier otra cosa que no sea corazón ni cofre =
  pista y nada más. Adiós corazones fantasma.
- Un clic en el corazón activa el modo vincular (persistente, sin 30 s):
  encadena cofres sin remarcar; F de nuevo en el mismo lo termina.
  La selección sobrevive reinicios (`selected.json`).
- Gestos distintos: F (Use) = vincular (idempotente, nunca rompe);
  clic derecho (Secondary) = desvincular. Más `/storage vincular` y
  `/storage desvincular` mirando al cofre como respaldo sin ratón.
- 1 cofre = 1 corazón: vincular a otro MUEVE, no duplica. `/storage red`
  marca `*ACTIVO*` y cuenta `N cofres (K OK, J muertos)`.

## 0.4.4 — P0 estabilidad UI: buscador con @SearchQuery + congelar IDs

- Fix buscador: era `EventData.of("Action","search")` sin valor, el texto nunca
  llegaba. Ahora `append("@SearchQuery","#SearchField.Value")` + codec
  `@SearchQuery` (patrón vanilla `PluginListPage/WarpListPage`). Se mantiene
  fallback a `Query/Value/@Value` una versión.
- Congelados 9 IDs: `#Title #SearchField #DepositBtn #CleanBtn #PrevBtn
  #NextBtn #CountLabel #ItemList #DebugList #CloseBtn` existen en `.ui` y Java.
  Orden `append doc → set → bind`; updates incrementales sin `append`.
  Sin filas inline hasta que la base sea estable.

## 0.4.3 — hiper sencillo: coords en todo + limpieza de rotos + estado por cofre

- Cada mensaje dice QUÉ pasó y DÓNDE: "Corazón NUEVO creado aquí [x,y,z]",
  "Cofre [x,y,z] vinculado al corazón [hx,hy,hz]". Imposible confundir crear
  con vincular.
- Los vínculos rotos ya no engañan: la página lista cada cofre como OK (con
  huecos) o NO RESPONDE, y el botón "Limpiar rotos" purga los muertos
  (`purgeDead`). Los chunks descargados también salen como no responde: por
  eso no se borra solo, lo decides tú.
- Título de la página con las coords del corazón.

## 0.4.2 — página mínima para aislar el crash (sin $C, sin filas inline)

- El crash `Value cannot be null (Parameter 'element')` indica un selector
  inexistente: o el `$C.@TextField` rompía el documento, o el markup inline
  de filas, o un binding a un selector ausente.
- Esta versión quita las tres causas de golpe: TextField en crudo (sin `$C`),
  lista como una sola etiqueta de texto (12 líneas/pág), y solo bindings a
  botones del documento. Si abre, se reintroducen filas e iconos por partes.

## 0.4.1 — ruta del documento UI corregida (el "Custom/" desconectaba)

- El cliente resolvía `Custom/StorageHeartPage.ui` como inexistente y
  desconectaba. Los documentos se citan relativos a `Common/UI/Custom/`
  (prueba: `Common/UI/Custom/Pages/ShopPage.ui` se cita como
  `Pages/ShopPage.ui`), así que el nuestro es `StorageHeartPage.ui`.
- El pack del jar sí carga (`Loaded pack ... from StorageHeart-0.4.0.jar`).

## 0.4.0 — interfaz propia con buscador (página custom, adiós ventana vanilla)

- Nueva `StoragePage` (`InteractiveCustomUIPage`): buscador con filtro en
  vivo (+ `@mod`), rejilla paginada de 12 con iconos y cantidades, botones
  Sacar (64) / Todo por fila, Depositar todo y Cerrar. Escribe en los cofres
  en vivo: sin sync al cerrar.
- Layout en `Common/UI/Custom/StorageHeartPage.ui` dentro del jar
  (`IncludesAssetPack: true`); filas generadas inline.
- Se elimina la ventana contenedor virtual (el cliente nunca la mostró).
- Riesgos conocidos a verificar en juego: resolución del `.ui` del pack,
  iconos vía `setObject`, y qué clave trae el texto del buscador (se loguea
  el primer evento para ajustar).

## 0.3.2 — la ventana vanilla tapaba la nuestra (apertura retardada)

- El log mostró `ventana abierta` pero el usuario seguía viendo la del cofre:
  el motor abre su ventana DESPUÉS de nuestro evento y la tapa (o la
  validación la cierra en la carrera). Ahora, si el Corazón es un cofre, se
  cierran ventanas y la agregada se abre 300 ms después
  (`World.scheduleAfter`), quedando encima. En corazones no-cofre se abre al
  instante.

## 0.3.1 — la ventana usa el id real del evento + diagnóstico total

- El log mostró el problema: el `BlockType` para la ventana salía de la
  config y podía no coincidir con el bloque real (la validación la cierra en
  silencio). Ahora se prefiere el id que trae el propio evento
  (normalizado: sin `*` ni sufijos `_State_...`).
- `NetworkWindow.open` loguea tipo resuelto, fallos con causa y apertura
  correcta. Imposible quedarse a ciegas otra vez.

## 0.3.0 — flujo solo con items + ventana ordenada + corazón-cofre

- Sin comandos obligatorios: la llave sobre un bloque crea el Corazón y lo
  marca en un paso (antes: proponer + `/storage confirmar`). Los comandos
  quedan para casos borde (`olvidar`, `red`, `abrir`, `aqui`).
- El Corazón manda sobre todo: si es un cofre, al abrirlo se cierra su
  ventana vanilla y se abre la agregada. Recomendado si el cristal no emite
  eventos: usa un cofre como Corazón y todo funciona con F.
- La ventana agregada se abre ordenada por nombre.
- Mensajes reescritos: cortos, en español, siempre con el siguiente paso.

## 0.2.7 — vía probada en juego + comandos sin eventos (a prueba de motor)

- Evidencia del log: `LivingEntityUseBlockEvent` (vía `registerGlobal`) SÍ
  llega al interactuar con el cofre. Pasa a ser el conductor: resuelve el
  bloque mirado con `TargetUtil.getTargetBlock(ref, 8, store)` (el evento no
  trae posición) y el item en mano con `InventoryComponent.getItemInHand`.
- Red de seguridad sin eventos (los comandos siempre funcionan):
  `/storage aqui` propone el bloque mirado por raycast + `/storage confirmar`
  lo crea; `/storage abrir` abre la ventana del corazón más cercano.
  Aunque el motor dejara de emitir eventos, el sistema completo es usable.

## 0.2.6 — vía de clics nueva: PlayerMouseButtonEvent (no keyed) + monitor global

- Causa raíz (investigada en docs + bytecode del server 0.7.2): 1) los
  sistemas ECS nunca recibieron `UseBlockEvent`; 2) `PlayerInteractEvent`
  se registraba con clave = nombre del mundo ADIVINADA (los docs revelan
  `registerGlobal` justo porque las claves no se adivinan) y además está
  `@Deprecated`; 3) el tooltip del juego dice que interactuar es tecla F,
  no clic derecho: los clics derechos probablemente viajan por otro evento.
- Nuevo: `PlayerMouseButtonEvent` (NO keyed, `PlayerEvent<Void>`, con
  `PlayerRef` directo, bloque objetivo, item en mano y botón/estado) con
  registro simple + cancelación. Filtro: botón Right + Pressed + con bloque.
- Monitor `registerGlobal(LivingEntityUseBlockEvent)` (da el id de bloque).
- Lógica de vínculo intacta pero con `heldItemId` string (el evento de ratón
  da Item asset, no pila).

## 0.2.5 — el clic derecho no era "Use" (se acepta Secondary + tecla F)

- Hallazgo en los assets del juego: la interacción con bloques es la
  **tecla F** (`Use`); el clic derecho llega como `Secondary`. El filtro solo
  aceptaba `Use`, así que los clics derechos se ignoraban en silencio.
- Ahora valen ambas (Secondary exige apuntar a un bloque). El log imprime
  cada tipo de acción visto (`diag: PlayerInteractEvent action=X`).

## 0.2.4 — clics por PlayerInteractEvent + Corazón sin IDs + registro de tipo ECS

- Los sistemas ECS no recibían nada (cero líneas diag): el evento parece no
  despacharse a sistemas de plugin. Nueva vía principal: `PlayerInteractEvent`
  (click derecho = `InteractionType.Use`) registrado por mundo
  (`World.getEventRegistry`, clave = nombre del mundo) en start, al conectar
  y desde `/storage red`. Se cancela el evento en clics gestionados (adiós
  popup del cofre al vincular).
- Corazón sin IDs de bloque: llave + clic en bloque no-cofre = propuesta,
  `/storage confirmar` lo crea (y deja marca para encadenar cofres);
  `/storage olvidar` borra el más cercano. Los cofres se detectan por
  `ItemContainerBlock` (sin IDs). Ventana resuelve el `BlockType` con
  `BlockType.fromString(config)`.
- Además: `registerEntityEventType(UseBlockEvent.class)` por si el despacho
  ECS lo exigía (con try/catch + log). OJO: `PlayerInteractEvent` está
  `@Deprecated` en 0.7.2: funciona pero es legado; si deja de dispararse,
  el log `interact-event` lo delatará.

## 0.2.3 — diagnóstico + config con fallback (clics no llegaban)

- Causa probable del "no hace nada": `config.json` viejo con IDs vacíos
  desactivaba el sistema físico en silencio. Ahora vacío = default vanilla
  (cristal azul + Veilbound Key).
- Diagnóstico en log: primer `UseBlockEvent`, clics con llave o a corazón
  (imprime bloque real, item en mano e ID esperado), registro de sistemas y
  aviso si el jugador no se resuelve. `/storage red` muestra el estado físico.
- Limpieza: fuera el "0.1.0 listo" hardcodeado.

## 0.2.2 — Corazón provisional jugable

- El iridiscente era `Quality: Developer` (oculto en creativo). Nuevo
  `HeartBlockId` por defecto: `Rock_Crystal_Blue_Large` (cristal azul
  grande, calidad normal, crafteable y en creativo como
  "Cristal Azul - Grande").

## 0.2.1 — IDs vanilla por defecto (probar sin Asset Editor)

- `HeartBlockId` por defecto: `Rock_Crystal_Iridescent_Large` (cristal
  iridiscente grande, decorativo); `LinkKeyItemId`: `Coffer_Key`
  (Veilbound Key). Con esto la 0.2.0 se prueba sin crear assets.
- El arranque loguea ids efectivos + carpeta de datos para localizar
  `config.json` y `links.json`.
- OJO: si ya existe `config.json` con los IDs vacíos, bórralo para
  regenerarlo o edita los dos IDs a mano (los defaults solo aplican a
  claves ausentes).

## 0.2.0 — Corazón físico + llave de vínculo (32 bloques) + ventana agregada

- Llave en mano + clic en Corazón = marca (30 s, configurable).
- Llave en mano + clic en cofre = vincula si está a ≤ 32 bloques del
  corazón (distancia euclídea, configurable), o desvincula si ya lo estaba.
- Mano vacía + clic en Corazón = ventana agregada virtual (suma los cofres
  vinculados); al cerrar, todo se reparte de vuelta. Mano vacía en cofre =
  comportamiento vanilla.
- Vínculos persistidos en `<dataDir>/links.json` (se guardan al vincular y
  al apagar). Tope 32 cofres/red.
- `/storage red`: corazones del mundo con cofres, huecos y distancias.
- Escucha global de clics vía sistemas ECS (`HeartUseEntitySystem` en
  EntityStore + `HeartUseChunkSystem` de diagnóstico; `Query.any()`).
- IDs del bloque Corazón y la llave configurables (ver
  `assets/HEART_AND_KEY_GUIDE.md`); con IDs vacíos el sistema físico se
  desactiva y siguen los comandos 0.1.x.
- Limitación honesta: al vincular con la llave el cofre vanilla también se
  abre (el evento no es cancelable); el mod cierra las ventanas tras actuar.

## 0.1.1 — /storage abierto por defecto

- Los comandos generaban nodos auto
  (`com.storageheart.storageheart.command.storage.*`) que el grupo Default
  no tiene → "sin permisos". Ahora `StorageSubCommand` + la colección usan
  `hasPermission(sender) = sender.hasPermission(nodo, true)`: funciona para
  todos salvo denegación explícita (`-nodo`).
- Alternativa sin código (admins): `/perm user group add <uuid> OP` desde
  consola, o `/perm user add <uuid> com.storageheart.storageheart.command.storage.*`.
  Permisos en `<save>/permissions.json`.

## Hotfix 0.7.2 (14 oct 2026) — sin cambios

- El hotfix solo trae estabilidad (crash por goblin spawn beacon de Update 6,
  crash RocksDB en Linux), crafteo de Backpack Upgrade II y `Zone1_Plains1`
  cargando de nuevo. Nada toca inventario, comandos ni bloques.
- Recompilado contra `Server-0.7.2.jar` del hotfix (SHA256 verificado):
  compila limpio, sin cambios de código. Se sigue en `ServerVersion 0.7.2`.

## 0.1.0 — MVP Update 7 (server 0.7.2)

- Red virtual de almacenamiento (8 unidades x 40 huecos).
- `/storage depositar | lista | buscar | retirar | ordenar`, alias `/sh`, `/corazon`.
- Búsqueda por nombre y `@mod`, orden alfabético / por cantidad en la red,
  orden nativo (`SortType` NAME/TYPE/RARITY) en el inventario.
- Núcleo `core.StorageNetwork` en Java puro, testeable sin servidor.
- Adaptador `StorageNetworkService` aislado para la API vigente de Update 7.

### Notas de update (0.7.2)

- `Inventory` (con `getStorage()`, `getCombinedHotbarFirst()`, etc.) está
  `@Deprecated(forRemoval)`. Se usa `InventoryComponent.Storage` +
  `Player.giveItem()`. No usar nada de `Inventory` en código nuevo.
- `ItemContainer.sortItems(SortType)` existe nativo: usarlo, no reimplementar.
- Bloques: leer por `ChunkStore.getChunkSectionReferenceAtBlock`, no por
  `World/WorldChunk.getBlock` (eliminados / `@RestrictedApi`).

- Bloques: leer por `ChunkStore.getChunkSectionReferenceAtBlock`, no por
  `World/WorldChunk.getBlock` (eliminados / `@RestrictedApi`).
- UI Noesis server-side en `dev_`: no usable aún → comandos en el MVP.
- `ItemContainerBlock.StayOpenWhenEmpty` y `BlockCarriedEvent` existen:
  se usarán en 0.2.0 para cofres físicos.
