package com.storageheart.link;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.event.events.ecs.UseBlockEvent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.SimpleItemContainer;
import com.hypixel.hytale.server.core.modules.block.BlockModule;
import com.hypixel.hytale.server.core.modules.block.components.ItemContainerBlock;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.storageheart.StorageHeartPlugin;
import com.storageheart.core.BlockPos;
import com.storageheart.core.LinkStore;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Llave de vínculo con límite de distancia (32 bloques por defecto).
 *
 * <p>Flujo solo con items (sin comandos): F con la Llave en el Corazón =
 * modo vincular (F de nuevo = terminar); F con Llave en cofres = vincular
 * (≤ MaxLinkDistance); F sin modo en cofre vinculado = desvincular; mano
 * vacía + F en Corazón = interfaz.
 */
public final class LinkService {

    private record Mark(BlockPos heart, long epochSec) {
    }

    /** Contexto de clic unificado. heldItemId null = mano vacía. action null = desconocida (se trata como Use). */
    public record Click(World world, PlayerRef player, Ref<EntityStore> entityRef,
            Store<EntityStore> entityStore, BlockPos target, String heldItemId, BlockType blockType,
            String eventBlockId, com.hypixel.hytale.protocol.InteractionType action, Runnable cancel) {
    }

    private final StorageHeartPlugin plugin;
    private final LinkStore store = new LinkStore();
    private final Map<UUID, Mark> pendingMarks = new HashMap<>();
    private final Map<String, Boolean> loggedOnce = new HashMap<>();

    public LinkService(StorageHeartPlugin plugin) {
        this.plugin = plugin;
    }

    public LinkStore links() {
        return store;
    }

    private void logFirst(String key, String msg) {
        if (loggedOnce.putIfAbsent(key, Boolean.TRUE) == null) {
            plugin.getLogger().at(Level.INFO).log("StorageHeart diag: " + msg);
        }
    }

    private Path file() {
        return plugin.getDataDirectory().resolve("links.json");
    }

    private Path selectedFile() {
        return plugin.getDataDirectory().resolve("selected.json");
    }

    public void load() {
        try {
            Path f = file();
            if (Files.isRegularFile(f)) {
                store.fromJson(Files.readString(f, StandardCharsets.UTF_8));
            }
        } catch (Exception e) {
            plugin.getLogger().at(Level.WARNING).withCause(e).log("StorageHeart: no se pudo leer links.json");
        }
        loadSelection();
    }

    public void save() {
        try {
            Files.createDirectories(plugin.getDataDirectory());
            Files.writeString(file(), store.toJson(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            plugin.getLogger().at(Level.WARNING).withCause(e).log("StorageHeart: no se pudo guardar links.json");
        }
        saveSelection();
    }

    private void loadSelection() {
        try {
            Path f = selectedFile();
            if (!Files.isRegularFile(f)) {
                return;
            }
            String json = Files.readString(f, StandardCharsets.UTF_8);
            // {"<uuid>":{"world":"w","x":1,"y":2,"z":3},...} — entradas rotas se saltan.
            int i = 0;
            while (true) {
                int q1 = json.indexOf('"', i);
                if (q1 < 0) {
                    break;
                }
                int q2 = json.indexOf('"', q1 + 1);
                if (q2 < 0) {
                    break;
                }
                String id = json.substring(q1 + 1, q2);
                int ob = json.indexOf('{', q2);
                if (ob < 0) {
                    break;
                }
                int depth = 0;
                int end = -1;
                for (int k = ob; k < json.length(); k++) {
                    char ch = json.charAt(k);
                    if (ch == '{') {
                        depth++;
                    } else if (ch == '}') {
                        depth--;
                        if (depth == 0) {
                            end = k;
                            break;
                        }
                    }
                }
                if (end < 0) {
                    break;
                }
                String obj = json.substring(ob, end + 1);
                try {
                    UUID uuid = UUID.fromString(id);
                    String world = extractJsonString(obj, "world");
                    Integer x = extractJsonInt(obj, "x");
                    Integer y = extractJsonInt(obj, "y");
                    Integer z = extractJsonInt(obj, "z");
                    if (world != null && x != null && y != null && z != null) {
                        BlockPos heart = new BlockPos(world, x, y, z);
                        if (store.knowsHeart(heart)) {
                            pendingMarks.put(uuid, new Mark(heart, now()));
                        }
                    }
                } catch (IllegalArgumentException ignored) {
                    // UUID malformado: se salta.
                }
                i = end + 1;
            }
        } catch (Exception e) {
            plugin.getLogger().at(Level.WARNING).withCause(e).log("StorageHeart: no se pudo leer selected.json");
        }
    }

    private void saveSelection() {
        try {
            Files.createDirectories(plugin.getDataDirectory());
            StringBuilder sb = new StringBuilder("{");
            boolean first = true;
            for (Map.Entry<UUID, Mark> e : pendingMarks.entrySet()) {
                if (!store.knowsHeart(e.getValue().heart())) {
                    continue;
                }
                if (!first) {
                    sb.append(',');
                }
                first = false;
                BlockPos h = e.getValue().heart();
                sb.append('"').append(e.getKey()).append("\":{\"world\":")
                        .append('"').append(h.world().replace("\\", "\\\\").replace("\"", "\\\"")).append('"')
                        .append(",\"x\":").append(h.x())
                        .append(",\"y\":").append(h.y())
                        .append(",\"z\":").append(h.z()).append('}');
            }
            sb.append('}');
            Files.writeString(selectedFile(), sb.toString(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            plugin.getLogger().at(Level.WARNING).withCause(e).log("StorageHeart: no se pudo guardar selected.json");
        }
    }

    private static String extractJsonString(String obj, String key) {
        int k = obj.indexOf('"' + key + '"');
        if (k < 0) {
            return null;
        }
        int colon = obj.indexOf(':', k);
        int q1 = obj.indexOf('"', colon);
        int q2 = obj.indexOf('"', q1 + 1);
        return (q1 < 0 || q2 < 0) ? null : obj.substring(q1 + 1, q2);
    }

    private static Integer extractJsonInt(String obj, String key) {
        int k = obj.indexOf('"' + key + '"');
        if (k < 0) {
            return null;
        }
        int colon = obj.indexOf(':', k);
        int i = colon + 1;
        while (i < obj.length() && obj.charAt(i) == ' ') {
            i++;
        }
        int j = i;
        while (j < obj.length() && (Character.isDigit(obj.charAt(j)) || obj.charAt(j) == '-')) {
            j++;
        }
        if (j == i) {
            return null;
        }
        try {
            return Integer.parseInt(obj.substring(i, j));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    // ---------- entrada ECS ----------

    /** Punto de entrada del sistema ECS (ver {@code HeartUseEntitySystem}). */
    public void onUse(Store<EntityStore> entityStore, UseBlockEvent event) {
        if (event.getInteractionType() == null
                || !InteractionType.Use.equals(event.getInteractionType())
                || event.getContext() == null) {
            return;
        }
        logFirst("use-event", "UseBlockEvent recibido por ECS.");
        Ref<EntityStore> entityRef = event.getContext().getEntity();
        PlayerRef player = resolvePlayer(entityRef);
        if (player == null) {
            logFirst("no-player", "UseBlockEvent sin PlayerRef resoluble (¿NPC?).");
            return;
        }
        World world = Universe.get().getWorld(player.getWorldUuid());
        if (world == null) {
            return;
        }
        org.joml.Vector3i t = event.getTargetBlock();
        ItemStack stack = event.getContext().getHeldItem();
        handleClick(new Click(world, player, entityRef, entityStore,
                new BlockPos(world.getName(), t.x, t.y, t.z),
                ItemStack.isEmpty(stack) ? null : stack.getItemId(),
                event.getBlockType(), null, event.getInteractionType(), () -> {
                }));
    }

    // ---------- entrada PlayerInteractEvent (sin BlockType) ----------

    /**
     * Devuelve true si el clic queda gestionado (el llamador debe cancelar
     * el evento para suprimir el comportamiento vanilla).
     */
    public boolean onInteractClick(World world, PlayerRef player, BlockPos target, ItemStack held,
            Ref<EntityStore> entityRef, Store<EntityStore> entityStore,
            com.hypixel.hytale.protocol.InteractionType action) {
        logFirst("interact-event", "PlayerInteractEvent recibido (fallback por mundo).");
        return handleClick(new Click(world, player, entityRef, entityStore, target,
                ItemStack.isEmpty(held) ? null : held.getItemId(), null, null, action, () -> {
                }));
    }

    // ---------- entrada uso con F (LivingEntityUseBlockEvent, vía probada) ----------

    public void onEntityUse(World world, PlayerRef player, BlockPos target, String heldItemId,
            Ref<EntityStore> entityRef, Store<EntityStore> entityStore, String eventBlockId) {
        handleClick(new Click(world, player, entityRef, entityStore, target, heldItemId, null,
                normalizeBlockId(eventBlockId), com.hypixel.hytale.protocol.InteractionType.Use, () -> {
                }));
    }

    /**
     * Normaliza el id que trae el evento (p. ej.
     * {@code *Furniture_Crude_Chest_Small_State_Definitions_CloseWindow} →
     * {@code Furniture_Crude_Chest_Small}).
     */
    static String normalizeBlockId(String raw) {
        if (raw == null) {
            return null;
        }
        String s = raw.startsWith("*") ? raw.substring(1) : raw;
        int cut = s.indexOf("_State_Definitions");
        if (cut < 0) {
            cut = s.indexOf("_State_");
        }
        return cut < 0 ? s : s.substring(0, cut);
    }

    // ---------- entrada botón de ratón (item como asset, no pila) ----------

    /**
     * Devuelve true si el clic queda gestionado.
     */
    public boolean onMouseClick(World world, PlayerRef player, BlockPos target, String heldItemId,
            Ref<EntityStore> entityRef, Store<EntityStore> entityStore) {
        logFirst("mouse-event", "PlayerMouseButtonEvent recibido.");
        return handleClick(new Click(world, player, entityRef, entityStore, target, heldItemId, null, null,
                com.hypixel.hytale.protocol.InteractionType.Secondary, () -> {
                }));
    }

    // ---------- lógica común (v0.5.0 robusta) ----------
    //
    // Reglas (pactadas con el jugador):
    // - Solo el bloque Corazón configurado crea corazones. Llave+F en otra
    //   cosa que no sea corazón ni cofre = nada (con pista, sin fantasmas).
    // - Llave+F en corazón = seleccionar (modo vincular ACTIVO, persistente).
    //   Repetir en el mismo = terminar. Un clic basta para encadenar cofres.
    // - Llave+F (Use) en cofre = VINCULAR (idempotente, nunca desvincula).
    // - Llave+clic derecho (Secondary) en cofre = DESVINCULAR. Gesto distinto.
    // - 1 cofre = 1 corazón: vincular a otro mueve, no duplica.
    // - Mano vacía = vanilla siempre, salvo en corazón (abre la red).

    private boolean handleClick(Click c) {
        boolean hasKey = plugin.getConfig().getLinkKeyItemId().equals(c.heldItemId());
        String heldId = c.heldItemId() == null ? "<mano>" : c.heldItemId();
        String heartId = plugin.getConfig().getHeartBlockId();
        boolean idSaysHeart = (c.blockType() != null
                && heartId.equals(String.valueOf(c.blockType().getId())))
                || (c.eventBlockId() != null && heartId.equals(c.eventBlockId()));
        Optional<BlockPos> known = findHeartAt(c.target());
        boolean isSecondary =
                com.hypixel.hytale.protocol.InteractionType.Secondary.equals(c.action());

        if (idSaysHeart || hasKey || known.isPresent()) {
            plugin.getLogger().at(Level.INFO).log(
                    "StorageHeart diag: Use bloque=" + c.target().x() + "," + c.target().y() + "," + c.target().z()
                            + " mano=" + heldId + " registrado=" + known.isPresent()
                            + " corazonCfg=" + idSaysHeart
                            + " accion=" + (c.action() == null ? "?" : c.action().name()));
        }

        // a) Corazón conocido manda sobre todo.
        if (known.isPresent()) {
            c.cancel().run();
            onHeart(c, true);
            return true;
        }

        // b) Bloque corazón aún no registrado.
        if (idSaysHeart) {
            c.cancel().run();
            onHeart(c, false);
            return true;
        }

        // c) Cofre (vale cada mitad del doble: se resuelve al vecino con contenedor).
        BlockPos chestPos = LinkService.chestPosAt(c.world(), c.target());
        if (chestPos != null) {
            if (!hasKey) {
                return false; // Cofre a mano vacía: vanilla.
            }
            c.cancel().run();
            if (isSecondary) {
                c.player().sendMessage(Message.raw(
                        unlinkSelected(c.player(), c.world(), chestPos)));
            } else {
                c.player().sendMessage(Message.raw(
                        handleChestUse(c.player(), c.world(), chestPos)));
            }
            save();
            if (c.entityRef() != null && c.entityStore() != null) {
                closeWindows(c.entityRef(), c.entityStore());
            }
            return true;
        }

        // d) Cualquier otro bloque: con llave no se crea nada (adiós fantasmas).
        if (hasKey) {
            c.cancel().run();
            Mark sel = pendingMarks.get(c.player().getUuid());
            if (sel != null && store.knowsHeart(sel.heart())) {
                BlockPos h = sel.heart();
                c.player().sendMessage(Message.raw("That is neither a Heart nor a chest. Link mode still ACTIVE on ["
                        + h.x() + "," + h.y() + "," + h.z() + "]. F with key on chests."));
            } else {
                c.player().sendMessage(Message.raw("That is neither a Heart (use [" + heartId
                        + "]) nor a chest. Nothing done."));
            }
            if (c.entityRef() != null && c.entityStore() != null) {
                closeWindows(c.entityRef(), c.entityStore());
            }
            return true;
        }
        return false; // Clic normal en bloque normal: vanilla.
    }

    private Optional<BlockPos> findHeartAt(BlockPos pos) {
        return store.heartsIn(pos.world()).stream().filter(h -> h.equals(pos)).findFirst();
    }

    /** Corazón seleccionado por el jugador (modo vincular), si sigue existiendo. */
    public BlockPos selectedHeartOf(UUID playerUuid) {
        Mark m = pendingMarks.get(playerUuid);
        if (m == null) {
            return null;
        }
        return store.knowsHeart(m.heart()) ? m.heart() : null;
    }

    /** ¿A qué corazón pertenece este cofre? Vacío si a ninguno. */
    public Optional<BlockPos> heartOfChest(BlockPos chest) {
        for (BlockPos h : store.heartsIn(chest.world())) {
            if (store.isLinked(h, chest)) {
                return Optional.of(h);
            }
        }
        return Optional.empty();
    }

    /**
     * Gesto F (Use) con llave en cofre. Con modo vincular: vincula. Sin modo:
     * desvincula de su red (gesto distinto por contexto) o pista si no está en
     * ninguna. El clic derecho (Secondary) nunca llega al servidor: se mantiene
     * como respaldo por si el motor lo emite algún día.
     */
    public String handleChestUse(PlayerRef player, World world, BlockPos chest) {
        if (selectedHeartOf(player.getUuid()) != null) {
            return linkSelected(player, world, chest);
        }
        Optional<BlockPos> owner = heartOfChest(chest);
        if (owner.isPresent()) {
            return unlinkAnywhere(world, chest)
                    + " (To link again: F with key on its heart.)";
        }
        return "No heart selected. F with the key on the Heart [" + plugin.getConfig().getHeartBlockId()
                + "] first, then on chests.";
    }

    /**
     * Vincula (gesto Use). Idempotente: si ya está en la red seleccionada,
     * informa sin romper. Si está en otra, lo MUEVE. Nunca desvincula.
     */
    public String linkSelected(PlayerRef player, World world, BlockPos chest) {
        BlockPos heart = selectedHeartOf(player.getUuid());
        if (heart == null) {
            pendingMarks.remove(player.getUuid());
            return "No heart selected. F with the key on the Heart [" + plugin.getConfig().getHeartBlockId()
                    + "] first, then on chests.";
        }
        if (!heart.world().equals(chest.world())) {
            return "That chest is in a different world than the heart [" + heart.x() + "," + heart.y() + "," + heart.z() + "].";
        }
        double dist = heart.distanceTo(chest);
        if (dist > plugin.getConfig().getMaxLinkDistance()) {
            return "That chest is " + Math.round(dist) + " blocks away: it must be within "
                    + plugin.getConfig().getMaxLinkDistance() + " of the heart.";
        }
        if (store.isLinked(heart, chest)) {
            return "Already linked to this heart [" + heart.x() + "," + heart.y() + "," + heart.z() + "] ("
                    + store.chestsOf(heart).size() + " in the network). Keep going or F on the heart to finish."
                    + " To remove it: end the mode (F on the heart) and F on the chest.";
        }
        // La otra mitad de un cofre doble ya vinculada: no duplicar. Solo vale
        // si es EL MISMO contenedor (misma instancia); dos dobles pegados son
        // cofres distintos aunque sean vecinos.
        SimpleItemContainer here = LinkService.chestAt(world, chest.x(), chest.y(), chest.z());
        for (BlockPos q : neighborsOf(chest)) {
            if (!q.equals(chest) && store.isLinked(heart, q)) {
                SimpleItemContainer there = LinkService.chestAt(world, q.x(), q.y(), q.z());
                if (here != null && here == there) {
                    return "Already linked to this heart through the adjoining side [" + q.x() + "," + q.y() + "," + q.z()
                            + "] (" + store.chestsOf(heart).size() + " in the network). Either half works.";
                }
            }
        }
        Optional<BlockPos> other = heartOfChest(chest);
        if (other.isPresent()) {
            BlockPos o = other.get();
            store.unlink(o, chest);
            store.link(heart, chest);
            return "Chest [" + chest.x() + "," + chest.y() + "," + chest.z() + "] MOVED from [" + o.x() + "," + o.y() + ","
                    + o.z() + "] to heart [" + heart.x() + "," + heart.y() + "," + heart.z() + "] ("
                    + store.chestsOf(heart).size() + " in the network).";
        }
        if (store.chestsOf(heart).size() >= plugin.getConfig().getMaxChestsPerNet()) {
            return "This network already has " + plugin.getConfig().getMaxChestsPerNet() + " chests, the maximum.";
        }
        store.link(heart, chest);
        int n = store.chestsOf(heart).size();
        return "Chest [" + chest.x() + "," + chest.y() + "," + chest.z() + "] linked to heart ["
                + heart.x() + "," + heart.y() + "," + heart.z() + "] (" + n + " in the network, " + Math.round(dist)
                + " blocks away). Keep going or F on the heart to finish.";
    }

    /**
     * Desvincula (gesto Secondary). Solo toca la red seleccionada; si el cofre
     * está en otra red, lo dice sin romper.
     */
    public String unlinkSelected(PlayerRef player, World world, BlockPos chest) {
        BlockPos heart = selectedHeartOf(player.getUuid());
        if (heart == null) {
            return "No heart selected. To unlink: first select its heart with the key, then RIGHT-click the chest.";
        }
        if (store.isLinked(heart, chest)) {
            store.unlink(heart, chest);
            return "Chest [" + chest.x() + "," + chest.y() + "," + chest.z() + "] unlinked from heart ["
                    + heart.x() + "," + heart.y() + "," + heart.z() + "]. " + store.chestsOf(heart).size() + " left.";
        }
        Optional<BlockPos> other = heartOfChest(chest);
        if (other.isPresent()) {
            BlockPos o = other.get();
            return "That chest is not in this network but in [" + o.x() + "," + o.y() + "," + o.z() + "]. F on the chest to move it here.";
        }
        return "That chest is not linked to this heart. Nothing to remove.";
    }

    /** Quita el cofre de la red que sea (F sin modo en cofre vinculado). Vecino solo si mismo contenedor. */
    public String unlinkAnywhere(World world, BlockPos chest) {
        Optional<BlockPos> owner = heartOfChest(chest);
        BlockPos target = chest;
        if (owner.isEmpty()) {
            SimpleItemContainer here = chestAt(world, chest.x(), chest.y(), chest.z());
            if (here != null) {
                for (BlockPos q : neighborsOf(chest)) {
                    Optional<BlockPos> o = heartOfChest(q);
                    if (o.isPresent() && chestAt(world, q.x(), q.y(), q.z()) == here) {
                        owner = o;
                        target = q;
                        break;
                    }
                }
            }
        }
        if (owner.isEmpty()) {
            return "That chest is not linked to any heart.";
        }
        BlockPos o = owner.get();
        store.unlink(o, target);
        save();
        return "Chest [" + target.x() + "," + target.y() + "," + target.z() + "] unlinked from heart ["
                + o.x() + "," + o.y() + "," + o.z() + "]. " + store.chestsOf(o).size() + " left.";
    }

    private void onHeart(Click c, boolean registered) {
        // Con llave: seleccionar / terminar modo vincular (persistente, sin caducidad).
        if (plugin.getConfig().getLinkKeyItemId().equals(c.heldItemId())) {
            if (!registered) {
                store.registerHeart(c.target());
            }
            Mark existing = pendingMarks.get(c.player().getUuid());
            if (existing != null && existing.heart().equals(c.target())) {
                pendingMarks.remove(c.player().getUuid());
                save();
                c.player().sendMessage(Message.raw("Linking with [" + c.target().x() + "," + c.target().y() + ","
                        + c.target().z() + "] FINISHED (" + store.chestsOf(c.target()).size() + " chests in the network)."));
            } else {
                pendingMarks.put(c.player().getUuid(), new Mark(c.target(), now()));
                save();
                int n = store.chestsOf(c.target()).size();
                c.player().sendMessage(Message.raw((registered ? "Heart [" : "NEW Heart [")
                        + c.target().x() + "," + c.target().y() + "," + c.target().z()
                        + "] — link mode ACTIVE (" + n + " chests). F with key on chests (several in a row). F here again to finish. To REMOVE a chest: end the mode and F on the chest."));
            }
            if (c.entityRef() != null && c.entityStore() != null) {
                closeWindows(c.entityRef(), c.entityStore());
            }
            return;
        }
        // Sin llave en corazón: página custom (muestra TODO con buscador; la
        // ventana vanilla no puede paginar ni buscar y topaba en 54).
        // Se registra al vuelo si es bloque corazón.
        if (!registered) {
            store.registerHeart(c.target());
            save();
        }
        if (c.entityRef() == null || c.entityStore() == null) {
            c.player().sendMessage(Message.raw("Get closer to the Heart and try again."));
            return;
        }
        // Antirrebote 400 ms: dos F seguidas no deben abrir+cerrar en el acto.
        long t = System.currentTimeMillis();
        Long last = lastF.get(c.player().getUuid());
        if (last != null && t - last < 400) {
            return;
        }
        lastF.put(c.player().getUuid(), t);
        // La ventana vanilla del propio corazón se abre DESPUÉS de este evento
        // (el evento no se puede cancelar): abrir al instante la deja debajo
        // (regresión 0.10.0). Se cierra todo y la nuestra va con retardo.
        closeWindows(c.entityRef(), c.entityStore());
        BlockPos heart = c.target();
        World world = c.world();
        UUID playerUuid = c.player().getUuid();
        try {
            world.scheduleAfter(() -> {
                PlayerRef online = Universe.get().getPlayer(playerUuid);
                if (online == null || online.getReference() == null) {
                    return;
                }
                Ref<EntityStore> r = online.getReference();
                LinkService.closeWindows(r, r.getStore());
                openHeartPage(world, online, r, r.getStore(), heart);
            }, 300, java.util.concurrent.TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            plugin.getLogger().at(Level.WARNING).withCause(e).log("StorageHeart: no se pudo programar la página.");
            openHeartPage(c);
        }
    }

    private void openHeartPage(Click c) {
        openHeartPage(c.world(), c.player(), c.entityRef(), c.entityStore(), c.target());
    }

    private void openHeartPage(World world, PlayerRef player, Ref<EntityStore> ref,
            Store<EntityStore> entityStore, BlockPos heart) {
        try {
            // F = abrir SIEMPRE (sin interruptor): con la página abierta la F
            // no llega al servidor, así que "cerrar con F" no puede funcionar;
            // el interruptor solo producía la F muerta tras Esc. Cierres: el
            // botón o Esc (CanDismiss); el rastreo obsoleto se pisa al abrir.
            Player comp = entityStore.getComponent(ref, Player.getComponentType());
            if (comp == null) {
                player.sendMessage(Message.raw("Get closer to the Heart and try again."));
                return;
            }
            // Página custom sola (0.16.0): la ventana compuesta (E2) era
            // invisible en el cliente y moría a los ~3 s; no aportaba nada.
            comp.getPageManager().openCustomPage(ref, entityStore,
                    new com.storageheart.ui.StoragePage(player, plugin, world.getName(), heart));
            openPages.put(player.getUuid(), heart);
            plugin.getLogger().at(Level.INFO).log("StorageHeart diag: página del Corazón abierta.");
        } catch (Exception e) {
            plugin.getLogger().at(Level.WARNING).withCause(e).log("StorageHeart: abrir página falló.");
            player.sendMessage(Message.raw("Could not open the interface. Check the log."));
        }
    }

    // Nota: la selección (pendingMarks) es persistente: termina al pulsar de
    // nuevo el mismo corazón. Sin caducidad (0.5.0).
    // Se guarda en selected.json para sobrevivir reinicios (ver save/load).

    private static long now() {
        return System.currentTimeMillis() / 1000;
    }

    public static PlayerRef resolvePlayer(Ref<EntityStore> entityRef) {
        if (entityRef == null) {
            return null;
        }
        for (PlayerRef p : Universe.get().getPlayers()) {
            if (entityRef.equals(p.getReference())) {
                return p;
            }
        }
        return null;
    }

    /** Quita los cofres vinculados que ya no responden. Devuelve cuántos quitó. */
    public int purgeDead(World world) {
        int removed = 0;
        for (BlockPos heart : new java.util.ArrayList<>(store.heartsIn(world.getName()))) {
            for (BlockPos chest : new java.util.ArrayList<>(store.chestsOf(heart))) {
                if (chestAt(world, chest.x(), chest.y(), chest.z()) == null) {
                    store.unlink(heart, chest);
                    removed++;
                }
            }
        }
        if (removed > 0) {
            save();
        }
        return removed;
    }

    /** Línea de estado de cada cofre de la red (OK / no responde). */
    public List<String> chestStatus(World world, BlockPos heart) {
        List<String> out = new java.util.ArrayList<>();
        for (BlockPos c : store.chestsOf(heart)) {
            var container = chestAt(world, c.x(), c.y(), c.z());
            if (container == null) {
                out.add("[" + c.x() + "," + c.y() + "," + c.z() + "] NOT RESPONDING (broken?)");
            } else {
                out.add("[" + c.x() + "," + c.y() + "," + c.z() + "] OK " + container.getCapacity() + " huecos");
            }
        }
        return out;
    }

    /** Solo los cofres que no responden (para el pie compacto de la página). */
    public List<String> deadChestLines(World world, BlockPos heart) {
        List<String> out = new java.util.ArrayList<>();
        for (BlockPos c : store.chestsOf(heart)) {
            if (chestAt(world, c.x(), c.y(), c.z()) == null) {
                out.add("[" + c.x() + "," + c.y() + "," + c.z() + "] NOT RESPONDING (broken?)");
            }
        }
        return out;
    }

    private final Map<UUID, BlockPos> openPages = new HashMap<>();
    private final Map<UUID, Long> lastF = new HashMap<>();

    /** La página de qué corazón tiene abierta el jugador (para F = cerrar). */
    public void noteOpened(UUID playerUuid, BlockPos heart) {
        openPages.put(playerUuid, heart);
    }

    public void noteClosed(UUID playerUuid) {
        openPages.remove(playerUuid);
    }

    /** Contenedor del cofre en esa posición, o null si no hay cofre con inventario. */
    public static SimpleItemContainer chestAt(World world, int x, int y, int z) {
        Ref<ChunkStore> be = BlockModule.getBlockEntity(world, x, y, z);
        if (be == null) {
            return null;
        }
        ItemContainerBlock comp = be.getStore().getComponent(be, ItemContainerBlock.getComponentType());
        return comp == null ? null : comp.getItemContainer();
    }

    private static final int[][] DIRS = {
            { 1, 0, 0 }, { -1, 0, 0 }, { 0, 1, 0 }, { 0, -1, 0 }, { 0, 0, 1 }, { 0, 0, -1 } };

    /** Vecinos adyacentes (para mitades de cofres dobles). */
    public static List<BlockPos> neighborsOf(BlockPos pos) {
        List<BlockPos> out = new java.util.ArrayList<>(6);
        for (int[] d : DIRS) {
            out.add(new BlockPos(pos.world(), pos.x() + d[0], pos.y() + d[1], pos.z() + d[2]));
        }
        return out;
    }

    /**
     * Posición con contenedor: el bloque o un vecino. Los cofres dobles ocupan
     * dos bloques y solo una mitad (u otra según el lado) expone el
     * contenedor: vale pulsar cualquiera de las dos.
     */
    public static BlockPos chestPosAt(World world, BlockPos pos) {
        if (chestAt(world, pos.x(), pos.y(), pos.z()) != null) {
            return pos;
        }
        for (BlockPos q : neighborsOf(pos)) {
            if (chestAt(world, q.x(), q.y(), q.z()) != null) {
                return q;
            }
        }
        return null;
    }

    static void closeWindows(Ref<EntityStore> ref, Store<EntityStore> entityStore) {
        Player player = entityStore.getComponent(ref, Player.getComponentType());
        if (player != null) {
            player.getWindowManager().closeAllWindows(ref, entityStore);
        }
    }
}
