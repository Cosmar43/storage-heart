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
import com.storageheart.link.NetworkModel;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Level;

/**
 * Interfaz del Corazón: buscador + rejilla paginada + depositar/todo.
 *
 * <p>Trabaja con VARIANTES reales (id + metadata + durabilidad + calidad):
 * retirar una espada dañada devuelve exactamente esa espada, no una nueva.
 * La lista se reconstruye en cada acción con {@code sendUpdate}.
 */
public final class StoragePage extends InteractiveCustomUIPage<StoragePage.PageEvent> {

    private static final int ROWS_PER_PAGE = 6;
    private static final double MAX_USE_DISTANCE = 10.0;

    private final StorageHeartPlugin plugin;
    private final PlayerRef player;
    private final String worldName;
    private final BlockPos heart;
    private String query = "";
    private int pageRed = 0;
    private int pageInv = 0;
    private List<NetworkModel.Entry> rows = List.of();
    private boolean loggedEvent;
    private boolean loggedLang;
    private boolean closedForDistance;
    private long cleanArmedUntil;

    public StoragePage(PlayerRef playerRef, StorageHeartPlugin plugin, String worldName, BlockPos heart) {
        // CanDismiss (Esc) + CanDismissOrCloseThroughInteraction: la misma
        // tecla de interacción (F) cierra la página, igual que un cofre
        // vanilla. Es el lifetime que usan BlockLorePage/BarterPage/
        // RespawnPointPage cuando se abren con Use.
        super(playerRef, CustomPageLifetime.CanDismissOrCloseThroughInteraction, PageEvent.CODEC);
        this.plugin = plugin;
        this.player = playerRef;
        this.worldName = worldName;
        this.heart = heart;
    }

    @Override
    public void build(Ref<EntityStore> ref, UICommandBuilder commands,
            UIEventBuilder events, Store<EntityStore> store) {
        // Los documentos se resuelven relativos a Common/UI/Custom/.
        closedForDistance = false;
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
        events.addEventBinding(CustomUIEventBindingType.Activating, "#CloseButton",
                EventData.of("Action", "close"));
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

    private List<NetworkModel.Entry> currentRows(World world) {
        List<NetworkModel.Entry> all = NetworkModel.entries(plugin, world, heart);
        return filterAndSort(all, playerLanguage());
    }

    /** Filtro y orden compartidos por red e inventario (incluye @mod). */
    private List<NetworkModel.Entry> filterAndSort(List<NetworkModel.Entry> input, String lang) {
        String raw = query.trim();
        Map<String, String> names = new HashMap<>();
        Comparator<NetworkModel.Entry> alpha = Comparator.comparing(e ->
                names.computeIfAbsent(e.variantId(), k -> norm(visibleName(e, lang))));
        Comparator<NetworkModel.Entry> order = "cantidad".equalsIgnoreCase(plugin.getConfig().getSortMode())
                ? Comparator.comparingInt(NetworkModel.Entry::quantity).reversed().thenComparing(alpha)
                : alpha;
        if (raw.isEmpty()) {
            List<NetworkModel.Entry> out = new ArrayList<>(input);
            out.sort(order);
            return out;
        }
        String q = norm(raw);
        String mod = q.startsWith("@") ? q.substring(1) : null;
        List<NetworkModel.Entry> out = new ArrayList<>();
        for (NetworkModel.Entry e : input) {
            String idNorm = norm(e.itemId());
            boolean match = idNorm.contains(q);
            if (!match && mod != null) {
                int colon = idNorm.indexOf(':');
                String ns = colon >= 0 ? idNorm.substring(0, colon) : idNorm;
                match = ns.contains(mod);
            }
            if (!match) {
                String name = names.computeIfAbsent(e.variantId(), k -> norm(visibleName(e, lang)));
                match = name.contains(q);
            }
            if (match) {
                out.add(e);
            }
        }
        out.sort(order);
        return out;
    }

    /** Idioma del cliente (PlayerRef lo expone; si no, inglés por defecto). */
    private String playerLanguage() {
        try {
            String l = player.getLanguage();
            if (l != null && !l.isBlank()) {
                if (!loggedLang) {
                    loggedLang = true;
                    plugin.getLogger().at(Level.FINE).log("StorageHeart: idioma=" + l);
                }
                return l;
            }
        } catch (Exception ignored) {
        }
        return "en-US";
    }

    /** Nombre visible de una variante concreta (respeta nombres personalizados). */
    private String visibleName(NetworkModel.Entry e, String lang) {
        try {
            Message m = e.sample().getDisplayName();
            if (m == null) {
                return e.itemId();
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
        return e.itemId();
    }

    /** Minúsculas sin tildes para comparar (ids y nombres visibles). */
    private static String norm(String s) {
        if (s == null) {
            return "";
        }
        String n = java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        return n.toLowerCase(Locale.ROOT);
    }

    private void paint(Ref<EntityStore> ref, Store<EntityStore> store,
            UICommandBuilder commands, UIEventBuilder events) {
        World world = Universe.get().getWorld(worldName);
        rows = world == null ? List.of() : currentRows(world);
        List<NetworkModel.Entry> inv = invList(ref, store);
        int pagesRed = Math.max(1, (rows.size() + ROWS_PER_PAGE - 1) / ROWS_PER_PAGE);
        int pagesInv = Math.max(1, (inv.size() + ROWS_PER_PAGE - 1) / ROWS_PER_PAGE);
        pageRed = Math.min(Math.max(0, pageRed), pagesRed - 1);
        pageInv = Math.min(Math.max(0, pageInv), pagesInv - 1);
        paintList(commands, events, "#RedList", rows, pageRed, "get", "all");
        paintList(commands, events, "#InvList", inv, pageInv, "put", "putall");

        List<String> dead = world == null ? List.of() : plugin.linkService().deadChestLines(world, heart);
        int deadCount = dead.size();
        int ok = world == null ? 0 : NetworkModel.chests(plugin, world, heart).size();
        int linked = ok + deadCount;

        StringBuilder status = new StringBuilder();
        boolean searching = !query.isBlank();
        if (rows.isEmpty() && inv.isEmpty()) {
            status.append(searching ? "(no results for this search)\n" : "(empty: use Deposit all)\n");
        } else if (rows.isEmpty()) {
            status.append(searching ? "(no network results)\n"
                    : "(network empty: add with One/All or Deposit all)\n");
        } else if (inv.isEmpty()) {
            status.append(searching ? "(no inventory results)\n"
                    : "(nothing in inventory to store)\n");
        }
        if (deadCount == 0) {
            status.append("All chests responding.");
        } else {
            for (int i = 0; i < Math.min(3, deadCount); i++) {
                status.append("\n").append(dead.get(i));
            }
            if (deadCount > 3) {
                status.append("\n… and ").append(deadCount - 3).append(" more.");
            }
            status.append("\nUse Clean broken (press twice to confirm).");
        }
        commands.set("#DebugList.Text", status.toString());

        String red = "NETWORK · " + rows.size() + " types · " + ok + "/" + linked + " chests OK";
        if (pagesRed > 1) {
            red += " · page " + (pageRed + 1) + "/" + pagesRed;
        }
        commands.set("#RedCount.Text", red);
        String invText = "INVENTORY · " + inv.size() + " types";
        if (pagesInv > 1) {
            invText += " · page " + (pageInv + 1) + "/" + pagesInv;
        }
        commands.set("#InvCount.Text", invText);
        commands.set("#TitleLabel.Text", "Storage Heart");
        commands.set("#CoordsLabel.Text", "Heart [" + heart.x() + "," + heart.y() + "," + heart.z() + "]");
        commands.set("#CleanLbl.Text", System.currentTimeMillis() < cleanArmedUntil
                ? "Confirm clean (" + deadCount + ")" : "Clean broken");
    }

    /** Pinta una columna de filas (red o inventario) con sus botones. */
    private void paintList(UICommandBuilder commands, UIEventBuilder events, String list,
            List<NetworkModel.Entry> entries, int pg, String getAction, String allAction) {
        commands.clear(list);
        int from = pg * ROWS_PER_PAGE;
        int to = Math.min(from + ROWS_PER_PAGE, entries.size());
        for (int i = from; i < to; i++) {
            int slot = i - from;
            NetworkModel.Entry e = entries.get(i);
            String row = list + "[" + slot + "]";
            commands.append(list, "Pages/StorageHeartRow.ui");
            try {
                Message name = e.sample().getDisplayName();
                if (name != null) {
                    commands.set(row + " #Name.Text", name);
                } else {
                    commands.set(row + " #Name.Text", e.itemId());
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
            // Variant es la identidad estable de la pila; Index queda como
            // respaldo para clientes viejos.
            events.addEventBinding(CustomUIEventBindingType.Activating, row + " #Get",
                    EventData.of("Action", getAction)
                            .append("Index", Integer.toString(i))
                            .append("Variant", e.variantId()));
            events.addEventBinding(CustomUIEventBindingType.Activating, row + " #All",
                    EventData.of("Action", allAction)
                            .append("Index", Integer.toString(i))
                            .append("Variant", e.variantId()));
        }
    }

    /** Inventario agregado por variante (almacén+mochila, sin llave/corazón). */
    private List<NetworkModel.Entry> invList(Ref<EntityStore> ref, Store<EntityStore> store) {
        try {
            String keyId = plugin.getConfig().getLinkKeyItemId();
            String heartId = plugin.getConfig().getHeartBlockId();
            var source = com.hypixel.hytale.server.core.inventory.InventoryComponent.getCombined(
                    store, ref,
                    com.hypixel.hytale.server.core.inventory.InventoryComponent.Storage.getComponentType(),
                    com.hypixel.hytale.server.core.inventory.InventoryComponent.Backpack.getComponentType());
            List<ItemStack> stacks = new ArrayList<>();
            source.forEach((s, stack) -> {
                if (!ItemStack.isEmpty(stack)
                        && !keyId.equals(stack.getItemId())
                        && !heartId.equals(stack.getItemId())) {
                    stacks.add(stack);
                }
            });
            return filterAndSort(NetworkModel.entriesOf(stacks), playerLanguage());
        } catch (Exception ex) {
            plugin.getLogger().at(Level.WARNING).withCause(ex).log("StorageHeart: inventario no leído.");
            return List.of();
        }
    }

    /** Mete hasta qty de UNA variante concreta, conservando metadata/durabilidad. */
    private int depositInvVariant(World world, Ref<EntityStore> ref, Store<EntityStore> store,
            String variantId, int qty) {
        List<SimpleItemContainer> chests = NetworkModel.chests(plugin, world, heart);
        if (chests.isEmpty() || qty <= 0 || variantId == null) {
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
            if (ItemStack.isEmpty(stack) || !variantId.equals(NetworkModel.variantId(stack))) {
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

    private static NetworkModel.Entry find(List<NetworkModel.Entry> list, String variant, int index) {
        if (variant != null && !variant.isBlank()) {
            for (NetworkModel.Entry e : list) {
                if (e.variantId().equals(variant)) {
                    return e;
                }
            }
            // La variante ya no existe (otro jugador la movió): no caer al
            // índice, que podría apuntar a otro item.
            return null;
        }
        if (index >= 0 && index < list.size()) {
            return list.get(index);
        }
        return null;
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
                plugin.getLogger().at(Level.FINE).log("StorageHeart: evento página=" + data);
            }
            World world = Universe.get().getWorld(worldName);
            if (world == null) {
                return;
            }
            if (!checkStillNear(world, ref, store)) {
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
                    long now = System.currentTimeMillis();
                    List<String> dead = plugin.linkService().deadChestLines(world, heart);
                    if (dead.isEmpty()) {
                        cleanArmedUntil = 0;
                        player.sendMessage(Message.raw("All chests responding. Nothing to clean."));
                    } else if (now > cleanArmedUntil) {
                        cleanArmedUntil = now + 15_000L;
                        player.sendMessage(Message.raw("Press Clean broken again within 15s to unlink "
                                + dead.size() + " non-responding chest(s)."));
                    } else {
                        cleanArmedUntil = 0;
                        int removed = plugin.linkService().purgeDead(world);
                        player.sendMessage(Message.raw("Cleaned " + removed + " broken link(s)."));
                        pageRed = 0;
                        pageInv = 0;
                    }
                }
                case "get", "all" -> {
                    rows = currentRows(world);
                    NetworkModel.Entry e = find(rows, data.variant, parseIndex(data.index));
                    if (e != null) {
                        int qty = action.equals("all") ? e.quantity() : 1;
                        NetworkModel.WithdrawResult result = NetworkModel.withdrawVariant(
                                plugin, world, heart, e.variantId(), qty);
                        if (result.taken() > 0 && result.stack() != null) {
                            ItemStack rest = StorageNetworkService.give(result.stack(), ref, store);
                            if (!ItemStack.isEmpty(rest)) {
                                ItemStack left = NetworkModel.depositStack(plugin, world, heart, rest);
                                if (!ItemStack.isEmpty(left)) {
                                    plugin.getLogger().at(Level.WARNING).log(
                                            "StorageHeart: no se pudo devolver %d x %s (inventario y red llenos).",
                                            left.getQuantity(), left.getItemId());
                                    player.sendMessage(Message.raw("Some items could not be returned: "
                                            + left.getQuantity() + " x " + left.getItemId()
                                            + " (inventory and network full)."));
                                }
                            }
                        }
                    } else {
                        player.sendMessage(Message.raw("That item is no longer in the network."));
                    }
                }
                case "put", "putall" -> {
                    List<NetworkModel.Entry> inv = invList(ref, store);
                    NetworkModel.Entry e = find(inv, data.variant, parseIndex(data.index));
                    if (e != null) {
                        int qty = action.equals("putall") ? e.quantity() : 1;
                        int placed = depositInvVariant(world, ref, store, e.variantId(), qty);
                        player.sendMessage(Message.raw(placed == 0
                                ? "No room in the network (or type is gone)."
                                : "Moved " + placed + " into the network."));
                    } else {
                        player.sendMessage(Message.raw("That item is no longer in your inventory."));
                    }
                }
                case "close", "dismiss" -> {
                    if (action.equals("close")) {
                        closePage(ref, store);
                    } else {
                        plugin.getLogger().at(Level.FINE).log("StorageHeart: dismiss recibido (Esc).");
                    }
                    return;
                }
                default -> plugin.getLogger().at(Level.FINE).log("StorageHeart: evento sin acción=" + data);
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

    /**
     * La página no debe operar a distancia ni desde otro mundo. Si el jugador
     * se aleja, se cierra (y no se repite el aviso).
     */
    private boolean checkStillNear(World world, Ref<EntityStore> ref, Store<EntityStore> store) {
        if (closedForDistance) {
            return false;
        }
        if (!plugin.linkService().links().knowsHeart(heart)) {
            closedForDistance = true;
            closePage(ref, store);
            player.sendMessage(Message.raw("That Heart no longer exists."));
            return false;
        }
        var worldUuid = player.getWorldUuid();
        World playerWorld = worldUuid == null ? null : Universe.get().getWorld(worldUuid);
        var transform = player.getTransform();
        if (playerWorld == null || !world.getName().equals(playerWorld.getName()) || transform == null) {
            return closeForDistance(ref, store, "You are no longer in the Heart's world.");
        }
        var pos = transform.getPosition();
        if (pos == null) {
            return closeForDistance(ref, store, "You are no longer in the Heart's world.");
        }
        double dx = pos.x - (heart.x() + 0.5);
        double dy = pos.y - (heart.y() + 0.5);
        double dz = pos.z - (heart.z() + 0.5);
        if (dx * dx + dy * dy + dz * dz > MAX_USE_DISTANCE * MAX_USE_DISTANCE) {
            return closeForDistance(ref, store,
                    "Too far from the Heart (max " + (int) MAX_USE_DISTANCE + " blocks).");
        }
        return true;
    }

    private boolean closeForDistance(Ref<EntityStore> ref, Store<EntityStore> store, String message) {
        closedForDistance = true;
        closePage(ref, store);
        player.sendMessage(Message.raw(message));
        return false;
    }

    private void closePage(Ref<EntityStore> ref, Store<EntityStore> store) {
        Player comp = store.getComponent(ref, Player.getComponentType());
        if (comp != null) {
            comp.getPageManager().closePage(ref, store);
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
                .append(new KeyedCodec<>("Variant", Codec.STRING),
                        (d, v) -> d.variant = v, d -> d.variant)
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
                .append(new KeyedCodec<>("ShiftHeld", Codec.BOOLEAN),
                        (d, v) -> d.shiftHeld = v, d -> d.shiftHeld)
                .add()
                .build();

        public String action;
        public String index;
        public String variant;
        public String value;
        public String query;
        public String atValue;
        public String searchQuery;
        public Boolean shiftHeld;

        @Override
        public String toString() {
            return "PageEvent{action=" + action + ", index=" + index + ", variant=" + variant
                    + ", value=" + value + ", query=" + query + ", atValue=" + atValue
                    + ", searchQuery=" + searchQuery + ", shiftHeld=" + shiftHeld + "}";
        }
    }
}
