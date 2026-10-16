package com.storageheart.ui;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.SimpleItemContainer;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.storageheart.StorageHeartPlugin;
import com.storageheart.StorageNetworkService;
import com.storageheart.core.BlockPos;
import com.storageheart.core.StorageNetwork;
import com.storageheart.link.NetworkModel;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.logging.Level;

/**
 * Interfaz del Corazón: buscador + rejilla paginada + depositar/todo.
 *
 * <p>Escribe en los cofres en vivo (sin sync al cerrar). La lista se
 * reconstruye en cada acción con `sendUpdate`.
 */
public final class StoragePage extends InteractiveCustomUIPage<StoragePage.PageEvent> {

    private static final int ROWS_PER_PAGE = 6;

    private final StorageHeartPlugin plugin;
    private final PlayerRef player;
    private final String worldName;
    private final BlockPos heart;
    private String query = "";
    private int pageRed = 0;
    private int pageInv = 0;
    private List<StorageNetwork.Entry> rows = List.of();
    private boolean loggedEvent;
    private boolean loggedLang;

    public StoragePage(PlayerRef playerRef, StorageHeartPlugin plugin, String worldName, BlockPos heart) {
        // CanDismiss (Esc funciona) + F = abrir siempre. F-cerrar es
        // inimplementable: con la página abierta la F no llega al servidor
        // (cero líneas Use con página abierta en todos los logs; ningún
        // KeyDown/Focus consumido por vanilla que copiar). El interruptor
        // clásico cerraba lo recién abierto por rastreo obsoleto.
        super(playerRef, CustomPageLifetime.CanDismiss, PageEvent.CODEC);
        this.plugin = plugin;
        this.player = playerRef;
        this.worldName = worldName;
        this.heart = heart;
    }

    @Override
    public void build(Ref<EntityStore> ref, UICommandBuilder commands,
            UIEventBuilder events, Store<EntityStore> store) {
        // Los documentos se resuelven relativos a Common/UI/Custom/ (igual que
        // el juego: Common/UI/Custom/Pages/ShopPage.ui se cita como
        // "Pages/ShopPage.ui"). El prefijo "Custom/" del ejemplo de la wiki no
        // existe en el cliente y lo desconecta.
        commands.append("StorageHeartPage.ui");
        paint(ref, store, commands, events);
        bindStatic(events);
    }

    private void bindStatic(UIEventBuilder events) {
        events.addEventBinding(CustomUIEventBindingType.Activating, "#DepositBtn",
                EventData.of("Action", "deposit"));
        events.addEventBinding(CustomUIEventBindingType.Activating, "#QuickBtn",
                EventData.of("Action", "quick"));
        events.addEventBinding(CustomUIEventBindingType.Activating, "#HandBtn",
                EventData.of("Action", "hand"));
        events.addEventBinding(CustomUIEventBindingType.Activating, "#CleanBtn",
                EventData.of("Action", "clean"));
        events.addEventBinding(CustomUIEventBindingType.Activating, "#CloseBtn",
                EventData.of("Action", "close"));
        // Si el cliente avisa al descartar (Esc), sirve para no dejar rastreo
        // obsoleto. Se loguea el primer evento para confirmar si llega.
        try {
            events.addEventBinding(CustomUIEventBindingType.Dismissing, "#Panel",
                    EventData.of("Action", "dismiss"));
        } catch (Exception ignored) {
        }
        events.addEventBinding(CustomUIEventBindingType.Activating, "#PrevRedBtn",
                EventData.of("Action", "prevRed"));
        events.addEventBinding(CustomUIEventBindingType.Activating, "#NextRedBtn",
                EventData.of("Action", "nextRed"));
        events.addEventBinding(CustomUIEventBindingType.Activating, "#PrevInvBtn",
                EventData.of("Action", "prevInv"));
        events.addEventBinding(CustomUIEventBindingType.Activating, "#NextInvBtn",
                EventData.of("Action", "nextInv"));
        events.addEventBinding(CustomUIEventBindingType.ValueChanged, "#SearchField",
                EventData.of("Action", "search").append("@SearchQuery", "#SearchField.Value"));
    }

    private List<StorageNetwork.Entry> currentRows(World world) {
        List<StorageNetwork.Entry> all = NetworkModel.entries(plugin, world, heart);
        String raw = query.trim();
        String lang = playerLanguage();
        java.util.Map<String, String> seen = new java.util.HashMap<>();
        java.util.Comparator<StorageNetwork.Entry> alpha = java.util.Comparator.comparing(e -> {
            String vis = seen.computeIfAbsent(e.itemId(), k -> visibleName(k, lang));
            return norm(vis != null ? vis : e.itemId());
        });
        if (raw.isEmpty()) {
            java.util.List<StorageNetwork.Entry> out = new java.util.ArrayList<>(all);
            out.sort(alpha);
            return out;
        }
        String q = norm(raw);
        String mod = q.startsWith("@") ? q.substring(1) : null;
        java.util.List<StorageNetwork.Entry> out = all.stream().filter(e -> {
            String idNorm = norm(e.itemId());
            if (idNorm.contains(q)) {
                return true;
            }
            if (mod != null) {
                int colon = idNorm.indexOf(':');
                String ns = colon >= 0 ? idNorm.substring(0, colon) : idNorm;
                if (ns.contains(mod)) {
                    return true;
                }
            }
            // Nombre visible en el idioma del jugador (ya no solo el id).
            String vis = seen.computeIfAbsent(e.itemId(), k -> visibleName(k, lang));
            return vis != null && norm(vis).contains(q);
        }).collect(java.util.stream.Collectors.toCollection(java.util.ArrayList::new));
        // Orden alfabético por nombre visible (id si no hay) para escanear.
        out.sort(alpha);
        return out;
    }

    /** Idioma del cliente (PlayerRef lo expone; si no, inglés por defecto). */
    private String playerLanguage() {
        try {
            String l = player.getLanguage();
            if (l != null && !l.isBlank()) {
                if (!loggedLang) {
                    loggedLang = true;
                    plugin.getLogger().at(Level.INFO).log("StorageHeart diag: idioma=" + l);
                }
                return l;
            }
        } catch (Exception ignored) {
        }
        return "en-US";
    }

    /** Nombre visible en el idioma dado (I18n + clave del item; null si no hay). */
    private String visibleName(String itemId, String lang) {
        try {
            Message m = new ItemStack(itemId, 1).getDisplayName();
            if (m == null) {
                return null;
            }
            String key = m.getMessageId();
            if (key != null && !key.isBlank()) {
                try {
                    var i18n = com.hypixel.hytale.server.core.modules.i18n.I18nModule.get();
                    String s = i18n.getMessage(lang, key);
                    if (s != null && !s.isBlank()) {
                        return s;
                    }
                    if (!lang.equals("en-US")) {
                        s = i18n.getMessage("en-US", key);
                        if (s != null && !s.isBlank()) {
                            return s;
                        }
                    }
                } catch (Exception ignored) {
                }
            }
            String raw = m.getRawText();
            if (raw != null && !raw.isBlank()) {
                return raw;
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    /** Minúsculas sin tildes para comparar (ids y nombres visibles). */
    private static String norm(String s) {
        String n = java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        return n.toLowerCase(Locale.ROOT);
    }

    private void paint(Ref<EntityStore> ref, Store<EntityStore> store,
            UICommandBuilder commands, UIEventBuilder events) {
        // Dos columnas simultáneas (estilo cofre vanilla): red + inventario,
        // cada una con su paginador. Filas Barter: clear + append + set + bind.
        World world = Universe.get().getWorld(worldName);
        rows = world == null ? List.of() : currentRows(world);
        List<StorageNetwork.Entry> inv = invList(ref, store);
        int pagesRed = Math.max(1, (rows.size() + ROWS_PER_PAGE - 1) / ROWS_PER_PAGE);
        int pagesInv = Math.max(1, (inv.size() + ROWS_PER_PAGE - 1) / ROWS_PER_PAGE);
        pageRed = Math.min(Math.max(0, pageRed), pagesRed - 1);
        pageInv = Math.min(Math.max(0, pageInv), pagesInv - 1);
        paintList(commands, events, "#RedList", rows, pageRed, "get", "all");
        paintList(commands, events, "#InvList", inv, pageInv, "put", "putall");
        // Estado compacto: solo estorba si hay muertos o todo vacío.
        List<String> dead = world == null ? List.of() : plugin.linkService().deadChestLines(world, heart);
        StringBuilder status = new StringBuilder();
        if (rows.isEmpty() && inv.isEmpty()) {
            status.append("(empty: use Deposit all)\n");
        } else if (rows.isEmpty()) {
            status.append("(network empty: add with One/All or Deposit all)\n");
        } else if (inv.isEmpty()) {
            status.append("(nothing to take in inventory)\n");
        }
        if (dead.isEmpty()) {
            status.append("All chests responding.");
        } else {
            for (String line : dead) {
                status.append("\n").append(line);
            }
            status.append("\nUse Clean broken.");
        }
        commands.set("#DebugList.Text", status.toString());
        int total = world == null ? 0 : NetworkModel.chests(plugin, world, heart).size();
        commands.set("#RedCount.Text", rows.isEmpty()
                ? "NETWORK · empty"
                : "NETWORK · " + (total - dead.size()) + "/" + total + " chests OK · " + rows.size()
                        + " types · page " + (pageRed + 1) + "/" + pagesRed);
        commands.set("#InvCount.Text", inv.isEmpty()
                ? "INVENTORY · empty"
                : "INVENTORY · " + inv.size() + " types · page " + (pageInv + 1) + "/" + pagesInv);
        commands.set("#Title.Text", "Heart [" + heart.x() + "," + heart.y() + "," + heart.z() + "]");
    }

    /** Pinta una columna de filas (red o inventario) con sus botones. */
    private void paintList(UICommandBuilder commands, UIEventBuilder events, String list,
            List<StorageNetwork.Entry> entries, int pg, String getAction, String allAction) {
        commands.clear(list);
        int from = pg * ROWS_PER_PAGE;
        int to = Math.min(from + ROWS_PER_PAGE, entries.size());
        for (int i = from; i < to; i++) {
            int slot = i - from;
            StorageNetwork.Entry e = entries.get(i);
            String row = list + "[" + slot + "]";
            commands.append(list, "Pages/StorageHeartRow.ui");
            // El nombre viaja como Message (clave localizable): el cliente lo
            // muestra en el idioma del jugador. Pasar String daría el @hash.
            try {
                Message name = displayName(e.itemId());
                if (name == null) {
                    commands.set(row + " #Name.Text", e.itemId());
                } else {
                    commands.set(row + " #Name.Text", name);
                }
            } catch (Exception ex) {
                commands.set(row + " #Name.Text", e.itemId());
            }
            commands.set(row + " #Qty.Text", "x" + e.quantity());
            commands.set(row + " #GetLbl.Text", "One");
            commands.set(row + " #AllLbl.Text", "All");
            try {
                commands.set(row + " #Icon.ItemId", e.itemId());
            } catch (Exception ex) {
                plugin.getLogger().at(Level.WARNING).log("StorageHeart: icono falló para " + e.itemId());
            }
            events.addEventBinding(CustomUIEventBindingType.Activating, row + " #Get",
                    EventData.of("Action", getAction).append("Index", Integer.toString(i)));
            events.addEventBinding(CustomUIEventBindingType.Activating, row + " #All",
                    EventData.of("Action", allAction).append("Index", Integer.toString(i)));
        }
    }

    /** Inventario agregado por tipo (almacén+mochila, sin llave/corazón). */
    private List<StorageNetwork.Entry> invList(Ref<EntityStore> ref, Store<EntityStore> store) {
        java.util.Map<String, Integer> totals = new java.util.LinkedHashMap<>();
        try {
            String keyId = plugin.getConfig().getLinkKeyItemId();
            String heartId = plugin.getConfig().getHeartBlockId();
            var source = com.hypixel.hytale.server.core.inventory.InventoryComponent.getCombined(
                    store, ref,
                    com.hypixel.hytale.server.core.inventory.InventoryComponent.Storage.getComponentType(),
                    com.hypixel.hytale.server.core.inventory.InventoryComponent.Backpack.getComponentType());
            for (short s = 0; s < source.getCapacity(); s++) {
                ItemStack stack = source.getItemStack(s);
                if (ItemStack.isEmpty(stack)) {
                    continue;
                }
                if (keyId.equals(stack.getItemId()) || heartId.equals(stack.getItemId())) {
                    continue;
                }
                totals.merge(stack.getItemId(), stack.getQuantity(), Integer::sum);
            }
        } catch (Exception ex) {
            plugin.getLogger().at(Level.WARNING).withCause(ex).log("StorageHeart: inventario no leído.");
            return List.of();
        }
        List<StorageNetwork.Entry> out = new ArrayList<>();
        totals.forEach((id, qty) -> out.add(new StorageNetwork.Entry(id, qty)));
        String q = norm(query.trim());
        String lang = playerLanguage();
        java.util.Map<String, String> seen = new java.util.HashMap<>();
        java.util.List<StorageNetwork.Entry> filtered = q.isEmpty() ? out : out.stream().filter(e -> {
            String idNorm = norm(e.itemId());
            if (idNorm.contains(q)) {
                return true;
            }
            String vis = seen.computeIfAbsent(e.itemId(), k -> visibleName(k, lang));
            return vis != null && norm(vis).contains(q);
        }).collect(java.util.stream.Collectors.toCollection(java.util.ArrayList::new));
        filtered.sort(java.util.Comparator.comparing(e -> sortKey(e.itemId(), lang, seen)));
        return filtered;
    }

    /** Clave alfabética: nombre visible sin tildes (id si no hay). */
    private String sortKey(String itemId, String lang, java.util.Map<String, String> seen) {
        String vis = seen.computeIfAbsent(itemId, k -> visibleName(k, lang));
        return norm(vis != null ? vis : itemId);
    }

    /** Mete hasta qty del tipo a la red, drenando huecos. Devuelve lo colocado. */
    private int depositInvById(World world, Ref<EntityStore> ref, Store<EntityStore> store,
            String itemId, int qty) {
        List<SimpleItemContainer> chests = NetworkModel.chests(plugin, world, heart);
        if (chests.isEmpty() || qty <= 0) {
            return 0;
        }
        var source = com.hypixel.hytale.server.core.inventory.InventoryComponent.getCombined(
                store, ref,
                com.hypixel.hytale.server.core.inventory.InventoryComponent.Storage.getComponentType(),
                com.hypixel.hytale.server.core.inventory.InventoryComponent.Backpack.getComponentType());
        int moved = 0;
        int left = qty;
        for (short s = 0; s < source.getCapacity() && left > 0; s++) {
            ItemStack stack = source.getItemStack(s);
            if (ItemStack.isEmpty(stack) || !itemId.equals(stack.getItemId())) {
                continue;
            }
            int take = Math.min(left, stack.getQuantity());
            ItemStack rest = stack.cleanCopy().withQuantity(take);
            for (SimpleItemContainer c : chests) {
                if (ItemStack.isEmpty(rest)) {
                    break;
                }
                rest = c.addItemStack(rest).getRemainder();
            }
            int placed = take - (ItemStack.isEmpty(rest) ? 0 : rest.getQuantity());
            if (placed > 0) {
                source.removeItemStackFromSlot(s, placed);
                moved += placed;
                left -= placed;
            }
        }
        return moved;
    }

    private Message displayName(StorageNetwork.Entry e) {
        return displayName(e.itemId());
    }

    private Message displayName(String itemId) {
        try {
            return new ItemStack(itemId, 1).getDisplayName();
        } catch (Exception ex) {
            return null;
        }
    }

    private static String firstNonBlank(String... values) {
        for (String v : values) {
            if (v != null && !v.isBlank()) {
                return v;
            }
        }
        return "";
    }

    private static int parseIndex(String raw) {
        try {
            return Integer.parseInt(raw);
        } catch (Exception e) {
            return -1;
        }
    }

    @Override
    public void handleDataEvent(Ref<EntityStore> ref, Store<EntityStore> store, PageEvent data) {
        try {
            if (!loggedEvent) {
                loggedEvent = true;
                plugin.getLogger().at(Level.INFO).log("StorageHeart diag: evento página=" + data);
            }
            World world = Universe.get().getWorld(worldName);
            if (world == null) {
                return;
            }
            String action = data.action == null ? "" : data.action;
            switch (action) {
                case "search" -> {
                    query = firstNonBlank(data.searchQuery, data.query, data.value, data.atValue, "");
                    pageRed = 0;
                    pageInv = 0;
                }
                case "prevRed" -> pageRed = Math.max(0, pageRed - 1);
                case "nextRed" -> pageRed = pageRed + 1;
                case "prevInv" -> pageInv = Math.max(0, pageInv - 1);
                case "nextInv" -> pageInv = pageInv + 1;
                case "deposit" -> {
                    int moved = NetworkModel.depositAll(plugin, world, heart, ref, store);
                    player.sendMessage(Message.raw("Deposited " + moved + " items from inventory (not hotbar)."));
                    pageRed = 0;
                    pageInv = 0;
                }
                case "quick" -> {
                    int moved = NetworkModel.depositMatching(plugin, world, heart, ref, store);
                    player.sendMessage(Message.raw(moved == 0
                            ? "Nothing matching: your inventory has no types already in the network."
                            : "Deposited " + moved + " matching items into the network."));
                    pageRed = 0;
                    pageInv = 0;
                }
                case "hand" -> {
                    int moved = NetworkModel.depositHeld(plugin, world, heart, ref, store);
                    player.sendMessage(Message.raw(moved == 0
                            ? "Nothing in hand (or no room): hold the stack and press again."
                            : "Held stack deposited: " + moved + " items into the network."));
                    pageRed = 0;
                    pageInv = 0;
                }
                case "clean" -> {
                    int removed = plugin.linkService().purgeDead(world);
                    player.sendMessage(Message.raw(removed == 0
                            ? "All chests responding. Nothing to clean."
                            : "Cleaned " + removed + " broken link(s)."));
                    pageRed = 0;
                    pageInv = 0;
                }
                case "get", "all" -> {
                    rows = currentRows(world);
                    int idx = parseIndex(data.index);
                    if (idx >= 0 && idx < rows.size()) {
                        StorageNetwork.Entry e = rows.get(idx);
                        int qty = action.equals("all") ? e.quantity() : 1;
                        int taken = NetworkModel.withdraw(plugin, world, heart, e.itemId(), qty);
                        if (taken > 0) {
                            ItemStack rest = StorageNetworkService.give(
                                    StorageNetworkService.toStack(e.itemId(), taken), ref, store);
                            if (!ItemStack.isEmpty(rest)) {
                                NetworkModel.depositEntry(plugin, world, heart,
                                        new StorageNetwork.Entry(rest.getItemId(), rest.getQuantity()));
                            }
                        }
                    }
                }
                case "put", "putall" -> {
                    // Uno = 1 unidad del tipo; Todo = todo el tipo (varios huecos).
                    List<StorageNetwork.Entry> inv = invList(ref, store);
                    int idx = parseIndex(data.index);
                    if (idx >= 0 && idx < inv.size()) {
                        StorageNetwork.Entry e = inv.get(idx);
                        int qty = action.equals("putall") ? e.quantity() : 1;
                        int placed = depositInvById(world, ref, store, e.itemId(), qty);
                        player.sendMessage(Message.raw(placed == 0
                                ? "No room in the network (or type is gone)."
                                : "Moved " + placed + " into the network."));
                    }
                }
                case "close", "dismiss" -> {
                    // dismiss = el cliente ya la cerró (Esc): solo limpiar rastreo.
                    if (action.equals("close")) {
                        Player comp = store.getComponent(ref, Player.getComponentType());
                        if (comp != null) {
                            comp.getPageManager().closePage(ref, store);
                        }
                    } else {
                        plugin.getLogger().at(Level.INFO).log("StorageHeart diag: dismiss recibido (Esc).");
                    }
                    plugin.linkService().noteClosed(player.getUuid());
                    return;
                }
                default -> {
                    // Desconocido: se loguea (caza de claves) pero se repinta
                    // igual para no dejar "Loading..." colgado.
                    plugin.getLogger().at(Level.INFO).log("StorageHeart diag: evento sin acción=" + data);
                }
            }
            UICommandBuilder commands = new UICommandBuilder();
            UIEventBuilder events = new UIEventBuilder();
            paint(ref, store, commands, events);
            bindStatic(events);
            sendUpdate(commands, events, false);
        } catch (Exception e) {
            plugin.getLogger().at(Level.WARNING).withCause(e).log("StorageHeart: evento de página falló.");
        }
    }

    /** Datos de evento. Claves en mayúscula por requisito del codec. */
    public static class PageEvent {
        public static final BuilderCodec<PageEvent> CODEC = BuilderCodec
                .builder(PageEvent.class, PageEvent::new)
                .append(new KeyedCodec<>("Action", Codec.STRING),
                        (d, v) -> d.action = v, d -> d.action)
                .add()
                .append(new KeyedCodec<>("Index", Codec.STRING),
                        (d, v) -> d.index = v, d -> d.index)
                .add()
                .append(new KeyedCodec<>("Value", Codec.STRING),
                        (d, v) -> d.value = v, d -> d.value)
                .add()
                .append(new KeyedCodec<>("Query", Codec.STRING),
                        (d, v) -> d.query = v, d -> d.query)
                .add()
                .append(new KeyedCodec<>("@Value", Codec.STRING),
                        (d, v) -> d.atValue = v, d -> d.atValue)
                .add()
                .append(new KeyedCodec<>("@SearchQuery", Codec.STRING),
                        (d, v) -> d.searchQuery = v, d -> d.searchQuery)
                .add()
                // ShiftHeld llega de verdad (RAW de botones); reservada para
                // futuro shift-clic. Las claves de slot se quitaron en 0.16.0:
                // el ItemGrid de muestra no envía nada (punto 2 cerrado).
                .append(new KeyedCodec<>("ShiftHeld", Codec.BOOLEAN),
                        (d, v) -> d.shiftHeld = v, d -> d.shiftHeld)
                .add()
                .build();

        public String action;
        public String index;
        public String value;
        public String query;
        public String atValue;
        public String searchQuery;
        public Boolean shiftHeld;

        @Override
        public String toString() {
            return "PageEvent{action=" + action + ", index=" + index + ", value=" + value
                    + ", query=" + query + ", atValue=" + atValue + ", searchQuery=" + searchQuery
                    + ", shiftHeld=" + shiftHeld + "}";
        }
    }
}
