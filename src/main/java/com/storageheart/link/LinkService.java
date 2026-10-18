package com.storageheart.link;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.movement.MovementStatesComponent;
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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;

/**
 * Llave de vínculo con límite de distancia (32 bloques por defecto).
 *
 * <p>Flujo solo con items (sin comandos):
 * <ul>
 *   <li>F con la Llave en el Corazón = modo vincular (F de nuevo = terminar).</li>
 *   <li>F con Llave en cofres con modo activo = vincular.</li>
 *   <li>Ctrl+F (agachado) con Llave en un cofre = desvincular de su red.</li>
 *   <li>Mano vacía + F en Corazón = interfaz.</li>
 *   <li>Sin modo vincular, F con la Llave en un cofre = comportamiento vanilla
 *       (abrir el cofre), nunca desvincula por accidente.</li>
 * </ul>
 */
public final class LinkService {

    /** Contexto de clic unificado. heldItemId null = mano vacía. */
    public record Click(World world, PlayerRef player, Ref<EntityStore> entityRef,
            Store<EntityStore> entityStore, BlockPos target, String heldItemId, BlockType blockType,
            String eventBlockId, InteractionType action, boolean immediate) {
    }

    /** Último uso gestionado por Pre, para no duplicarlo con el evento global de respaldo. */
    private record HandledUse(BlockPos target, long atMillis) {
    }

    private final StorageHeartPlugin plugin;
    private final LinkStore store = new LinkStore();
    private final Map<UUID, BlockPos> pendingMarks = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastF = new ConcurrentHashMap<>();
    private final Map<UUID, HandledUse> preHandled = new ConcurrentHashMap<>();
    private final AtomicBoolean preSeen = new AtomicBoolean();
    private final Object saveLock = new Object();

    public LinkService(StorageHeartPlugin plugin) {
        this.plugin = plugin;
    }

    public LinkStore links() {
        return store;
    }

    private Path file() {
        return plugin.getDataDirectory().resolve("links.json");
    }

    private Path selectedFile() {
        return plugin.getDataDirectory().resolve("selected.json");
    }

    // ---------- persistencia ----------

    public void load() {
        try {
            Path f = file();
            if (Files.isRegularFile(f)) {
                String json = Files.readString(f, StandardCharsets.UTF_8);
                if (!json.contains("\"hearts\"")) {
                    Path corrupt = f.resolveSibling(f.getFileName() + ".corrupt");
                    Files.copy(f, corrupt, StandardCopyOption.REPLACE_EXISTING);
                    plugin.getLogger().at(Level.WARNING).log(
                            "StorageHeart: links.json no parece JSON válido; copia en %s. Se cargará vacío.",
                            corrupt);
                }
                store.fromJson(json);
            }
        } catch (Exception e) {
            plugin.getLogger().at(Level.WARNING).withCause(e).log("StorageHeart: no se pudo leer links.json");
        }
        int cleaned = store.removeHeartLinks();
        if (cleaned > 0) {
            plugin.getLogger().at(Level.WARNING).log(
                    "StorageHeart: se ignoraron %d vínculos que apuntaban a un Corazón (self-link viejo).", cleaned);
        }
        plugin.getLogger().at(Level.INFO).log(
                "StorageHeart: enlaces cargados (%d corazones, %d cofres).",
                store.heartCount(), store.linkCount());
        loadSelection();
    }

    public void save() {
        synchronized (saveLock) {
            try {
                Files.createDirectories(plugin.getDataDirectory());
                writeAtomic(file(), store.toJson());
            } catch (Exception e) {
                plugin.getLogger().at(Level.WARNING).withCause(e).log("StorageHeart: no se pudo guardar links.json");
            }
            saveSelectionLocked();
        }
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
                            pendingMarks.put(uuid, heart);
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

    private void saveSelectionLocked() {
        try {
            Files.createDirectories(plugin.getDataDirectory());
            StringBuilder sb = new StringBuilder("{");
            boolean first = true;
            for (Map.Entry<UUID, BlockPos> e : pendingMarks.entrySet()) {
                BlockPos h = e.getValue();
                if (h == null || !store.knowsHeart(h)) {
                    continue;
                }
                if (!first) {
                    sb.append(',');
                }
                first = false;
                sb.append('"').append(e.getKey()).append("\":{\"world\":")
                        .append('"').append(h.world().replace("\\", "\\\\").replace("\"", "\\\"")).append('"')
                        .append(",\"x\":").append(h.x())
                        .append(",\"y\":").append(h.y())
                        .append(",\"z\":").append(h.z()).append('}');
            }
            sb.append('}');
            writeAtomic(selectedFile(), sb.toString());
        } catch (Exception e) {
            plugin.getLogger().at(Level.WARNING).withCause(e).log("StorageHeart: no se pudo guardar selected.json");
        }
    }

    /** Escribe a un temporal y lo mueve de forma atómica (con .bak del anterior). */
    private static void writeAtomic(Path target, String content) throws IOException {
        Path parent = target.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        if (Files.isRegularFile(target)) {
            Path backup = target.resolveSibling(target.getFileName() + ".bak");
            Files.copy(target, backup, StandardCopyOption.REPLACE_EXISTING);
        }
        Path tmp = target.resolveSibling(target.getFileName() + ".tmp");
        Files.writeString(tmp, content, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
        try {
            Files.move(tmp, target,
                    StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
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

    // ---------- entrada botón de ratón (item como asset, no pila) ----------

    /**
     * Devuelve true si el clic queda gestionado.
     */
    public boolean onMouseClick(World world, PlayerRef player, BlockPos target, String heldItemId,
            Ref<EntityStore> entityRef, Store<EntityStore> entityStore) {
        return handleClick(new Click(world, player, entityRef, entityStore, target, heldItemId, null, null,
                InteractionType.Secondary, false));
    }

    // ---------- entrada Pre (cancela la ventana vanilla antes de abrirla) ----------

    /**
     * Se llama desde {@link UseBlockPreSystem} antes de que el motor ejecute
     * la interacción del bloque. Devuelve true si StorageHeart gestiona el
     * uso; en ese caso el sistema cancela el evento y la ventana vanilla
     * (Open_Container) nunca llega a abrirse.
     */
    public boolean handlePreUse(UseBlockEvent.Pre event) {
        if (event == null || event.getInteractionType() == null
                || !InteractionType.Use.equals(event.getInteractionType())
                || event.getContext() == null) {
            return false;
        }
        if (preSeen.compareAndSet(false, true)) {
            plugin.getLogger().at(Level.INFO).log(
                    "StorageHeart: UseBlockEvent.Pre activo (el Corazón ya no abre la ventana vanilla).");
        }
        org.joml.Vector3i target = event.getTargetBlock();
        BlockType blockType = event.getBlockType();
        if (target == null || blockType == null || blockType.getId() == null) {
            return false;
        }
        Ref<EntityStore> entityRef = event.getContext().getEntity();
        if (entityRef == null) {
            return false;
        }
        PlayerRef player = resolvePlayer(entityRef);
        if (player == null || player.getReference() == null) {
            return false;
        }
        World world = Universe.get().getWorld(player.getWorldUuid());
        if (world == null) {
            return false;
        }
        ItemStack held = event.getContext().getHeldItem();
        String heldId = ItemStack.isEmpty(held) ? null : held.getItemId();
        BlockPos pos = new BlockPos(world.getName(), target.x, target.y, target.z);
        String rawId = blockType.getId();
        String blockId = normalizeBlockId(rawId);
        String heartId = plugin.getConfig().getHeartBlockId();
        boolean heartBlock = heartId.equals(blockId) || heartId.equals(rawId)
                || findHeartAt(pos).isPresent();
        boolean hasKey = plugin.getConfig().getLinkKeyItemId().equals(heldId);
        // No secuestrar usos normales (puertas, mesas...). Un cofre sin llave
        // sigue su curso vanilla; el evento global de respaldo lo deja abrir.
        if (!heartBlock && !hasKey) {
            return false;
        }
        Store<EntityStore> store = entityRef.getStore();
        boolean handled = handleClick(new Click(world, player, entityRef, store, pos, heldId,
                blockType, blockId, InteractionType.Use, true));
        if (handled) {
            preHandled.put(player.getUuid(), new HandledUse(pos, System.currentTimeMillis()));
        }
        return handled;
    }

    // ---------- entrada uso con F (LivingEntityUseBlockEvent, vía probada) ----------

    public void onEntityUse(World world, PlayerRef player, BlockPos target, String heldItemId,
            Ref<EntityStore> entityRef, Store<EntityStore> entityStore, String eventBlockId) {
        // Si Pre ya gestionó este mismo uso, no repetirlo (respaldo).
        HandledUse last = preHandled.get(player.getUuid());
        if (last != null && last.target().equals(target)
                && System.currentTimeMillis() - last.atMillis() < 500) {
            return;
        }
        handleClick(new Click(world, player, entityRef, entityStore, target, heldItemId, null,
                normalizeBlockId(eventBlockId), InteractionType.Use, false));
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

    // ---------- lógica común ----------
    //
    // - Solo el bloque Corazón configurado crea corazones.
    // - Llave+F en corazón = seleccionar (modo vincular); repetir = terminar.
    // - Llave+F en cofre CON modo activo = vincular (idempotente, mueve si
    //   estaba en otra red). Sin modo = vanilla (abrir el cofre).
    // - Ctrl+F (agachado) + llave en cofre = desvincular de cualquier red.
    // - Clic derecho + llave = desvincular (respaldo).
    // - Mano vacía = vanilla siempre, salvo en corazón (abre la red).

    private boolean handleClick(Click c) {
        boolean hasKey = plugin.getConfig().getLinkKeyItemId().equals(c.heldItemId());
        String heartId = plugin.getConfig().getHeartBlockId();
        boolean idSaysHeart = (c.blockType() != null
                && heartId.equals(String.valueOf(c.blockType().getId())))
                || (c.eventBlockId() != null && heartId.equals(c.eventBlockId()));
        Optional<BlockPos> known = findHeartAt(c.target());

        // Un corazón persistido en esta posición ya no manda si el bloque que
        // hay ahora es otro (corazón roto y reemplazado). No se borran datos:
        // si vuelve el Corazón, la red sigue ahí.
        if (known.isPresent() && !idSaysHeart
                && (c.eventBlockId() != null || c.blockType() != null)) {
            known = Optional.empty();
        }

        // a) Corazón conocido manda.
        if (known.isPresent()) {
            onHeart(c, true);
            return true;
        }

        // b) Bloque corazón aún no registrado.
        if (idSaysHeart) {
            onHeart(c, false);
            return true;
        }

        // c) Cofre (vale cada mitad del doble: se resuelve al vecino con contenedor).
        BlockPos chestPos = chestPosAt(c.world(), c.target());
        if (chestPos != null && findHeartAt(chestPos).isPresent()) {
            chestPos = null; // el Corazón nunca es un cofre
        }
        if (chestPos != null) {
            if (!hasKey) {
                return false; // Mano vacía: vanilla.
            }
            boolean crouching = isCrouching(c);
            if (InteractionType.Secondary.equals(c.action()) || crouching) {
                c.player().sendMessage(Message.raw(unlinkAnywhere(c.world(), chestPos)));
                save();
                if (!c.immediate()) {
                    closeWindowsNowAndSoon(c);
                }
                return true;
            }
            if (selectedHeartOf(c.player().getUuid()) == null) {
                // Sin modo vincular, la llave no secuestra el cofre.
                return false;
            }
            c.player().sendMessage(Message.raw(linkSelected(c.player(), c.world(), chestPos)));
            save();
            if (!c.immediate()) {
                closeWindowsNowAndSoon(c);
            }
            return true;
        }

        // d) Cualquier otro bloque: nunca bloquear el uso vanilla.
        if (hasKey && selectedHeartOf(c.player().getUuid()) != null && !isCrouching(c)) {
            BlockPos h = selectedHeartOf(c.player().getUuid());
            c.player().sendMessage(Message.raw("That is not a chest. Link mode still ACTIVE on ["
                    + h.x() + "," + h.y() + "," + h.z() + "]. F with key on chests."));
        }
        return false;
    }

    /** Agachado (Ctrl por defecto); sirve para el gesto de desvincular. */
    private static boolean isCrouching(Click c) {
        if (c.entityRef() == null || c.entityStore() == null) {
            return false;
        }
        MovementStatesComponent states = c.entityStore().getComponent(
                c.entityRef(), MovementStatesComponent.getComponentType());
        return states != null && states.getMovementStates() != null
                && states.getMovementStates().crouching;
    }

    private Optional<BlockPos> findHeartAt(BlockPos pos) {
        return store.heartsIn(pos.world()).stream().filter(h -> h.equals(pos)).findFirst();
    }

    /** Corazón seleccionado por el jugador (modo vincular), si sigue existiendo. */
    public BlockPos selectedHeartOf(UUID playerUuid) {
        BlockPos h = pendingMarks.get(playerUuid);
        return h != null && store.knowsHeart(h) ? h : null;
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
        if (findHeartAt(chest).isPresent()) {
            return "That is a Heart, not a chest. Nothing done.";
        }
        if (!heart.world().equals(chest.world())) {
            return "That chest is in a different world than the heart [" + heart.x() + "," + heart.y() + ","
                    + heart.z() + "].";
        }
        double dist = heart.distanceTo(chest);
        if (dist > plugin.getConfig().getMaxLinkDistance()) {
            return "That chest is " + Math.round(dist) + " blocks away: it must be within "
                    + plugin.getConfig().getMaxLinkDistance() + " of the heart.";
        }
        if (store.isLinked(heart, chest)) {
            return "Already linked to this heart [" + heart.x() + "," + heart.y() + "," + heart.z() + "] ("
                    + store.chestsOf(heart).size() + " in the network). Keep going or F on the heart to finish."
                    + " To remove it: Ctrl+F with the key on the chest.";
        }
        // La otra mitad de un cofre doble ya vinculada: no duplicar. Solo vale
        // si es EL MISMO contenedor (misma instancia); dos dobles pegados son
        // cofres distintos aunque sean vecinos.
        SimpleItemContainer here = LinkService.chestAt(world, chest.x(), chest.y(), chest.z());
        for (BlockPos q : neighborsOf(chest)) {
            if (!q.equals(chest) && store.isLinked(heart, q)) {
                SimpleItemContainer there = LinkService.chestAt(world, q.x(), q.y(), q.z());
                if (here != null && here == there) {
                    return "Already linked to this heart through the adjoining side [" + q.x() + "," + q.y() + ","
                            + q.z() + "] (" + store.chestsOf(heart).size() + " in the network). Either half works.";
                }
            }
        }
        // El tope se aplica también al MOVER un cofre desde otra red.
        if (store.chestsOf(heart).size() >= plugin.getConfig().getMaxChestsPerNet()) {
            return "This network already has " + plugin.getConfig().getMaxChestsPerNet() + " chests, the maximum.";
        }
        // 1 cofre = 1 corazón: quitar de cualquier otra red (datos viejos).
        String movedFrom = null;
        for (BlockPos h : store.heartsIn(chest.world())) {
            if (!h.equals(heart) && store.isLinked(h, chest)) {
                store.unlink(h, chest);
                if (movedFrom == null) {
                    movedFrom = "[" + h.x() + "," + h.y() + "," + h.z() + "]";
                }
            }
        }
        store.link(heart, chest);
        int n = store.chestsOf(heart).size();
        if (movedFrom != null) {
            return "Chest [" + chest.x() + "," + chest.y() + "," + chest.z() + "] MOVED from " + movedFrom
                    + " to heart [" + heart.x() + "," + heart.y() + "," + heart.z() + "] (" + n
                    + " in the network).";
        }
        return "Chest [" + chest.x() + "," + chest.y() + "," + chest.z() + "] linked to heart ["
                + heart.x() + "," + heart.y() + "," + heart.z() + "] (" + n + " in the network, " + Math.round(dist)
                + " blocks away). Keep going or F on the heart to finish.";
    }

    /**
     * Desvincula explícitamente del corazón seleccionado (respaldo).
     */
    public String unlinkSelected(PlayerRef player, World world, BlockPos chest) {
        BlockPos heart = selectedHeartOf(player.getUuid());
        if (heart == null) {
            return "No heart selected. To unlink: Ctrl+F with the key on the chest.";
        }
        if (store.isLinked(heart, chest)) {
            store.unlink(heart, chest);
            return "Chest [" + chest.x() + "," + chest.y() + "," + chest.z() + "] unlinked from heart ["
                    + heart.x() + "," + heart.y() + "," + heart.z() + "]. " + store.chestsOf(heart).size() + " left.";
        }
        Optional<BlockPos> other = heartOfChest(chest);
        if (other.isPresent()) {
            BlockPos o = other.get();
            return "That chest is not in this network but in [" + o.x() + "," + o.y() + "," + o.z()
                    + "]. Ctrl+F with the key on the chest to unlink it.";
        }
        return "That chest is not linked to this heart. Nothing to remove.";
    }

    /**
     * Quita el cofre de cualquier red (Ctrl+F o clic derecho con llave).
     * Resuelve la otra mitad de un cofre doble y limpia datos viejos de un
     * cofre presente en varias redes.
     */
    public String unlinkAnywhere(World world, BlockPos chest) {
        if (findHeartAt(chest).isPresent()) {
            return "That is a Heart, not a chest. Nothing to unlink.";
        }
        BlockPos target = chest;
        List<BlockPos> owners = ownersOf(target);
        if (owners.isEmpty()) {
            SimpleItemContainer here = chestAt(world, chest.x(), chest.y(), chest.z());
            if (here != null) {
                for (BlockPos q : neighborsOf(chest)) {
                    if (findHeartAt(q).isPresent()) {
                        continue;
                    }
                    List<BlockPos> o = ownersOf(q);
                    if (!o.isEmpty() && chestAt(world, q.x(), q.y(), q.z()) == here) {
                        owners = o;
                        target = q;
                        break;
                    }
                }
            }
        }
        if (owners.isEmpty()) {
            return "That chest is not linked to any heart.";
        }
        BlockPos first = owners.get(0);
        for (BlockPos h : owners) {
            store.unlink(h, target);
        }
        return "Chest [" + target.x() + "," + target.y() + "," + target.z() + "] unlinked from heart ["
                + first.x() + "," + first.y() + "," + first.z() + "]. "
                + store.chestsOf(first).size() + " left.";
    }

    private List<BlockPos> ownersOf(BlockPos chest) {
        List<BlockPos> out = new ArrayList<>();
        for (BlockPos h : store.heartsIn(chest.world())) {
            if (store.isLinked(h, chest)) {
                out.add(h);
            }
        }
        return out;
    }

    private void onHeart(Click c, boolean registered) {
        // Con llave: seleccionar / terminar modo vincular (persistente, sin caducidad).
        if (plugin.getConfig().getLinkKeyItemId().equals(c.heldItemId())) {
            if (!registered) {
                store.registerHeart(c.target());
            }
            BlockPos existing = pendingMarks.get(c.player().getUuid());
            if (existing != null && existing.equals(c.target())) {
                pendingMarks.remove(c.player().getUuid());
                save();
                c.player().sendMessage(Message.raw("Linking with [" + c.target().x() + "," + c.target().y() + ","
                        + c.target().z() + "] FINISHED (" + store.chestsOf(c.target()).size() + " chests in the network)."));
            } else {
                pendingMarks.put(c.player().getUuid(), c.target());
                save();
                int n = store.chestsOf(c.target()).size();
                c.player().sendMessage(Message.raw((registered ? "Heart [" : "NEW Heart [")
                        + c.target().x() + "," + c.target().y() + "," + c.target().z()
                        + "] — link mode ACTIVE (" + n + " chests). F with key on chests (several in a row). F here again to finish. To REMOVE a chest: Ctrl+F with the key on it."));
            }
            if (!c.immediate()) {
                closeWindowsNowAndSoon(c);
            }
            return;
        }
        // Sin llave en corazón: página custom.
        if (!registered) {
            store.registerHeart(c.target());
            save();
        }
        if (c.entityRef() == null || c.entityStore() == null) {
            c.player().sendMessage(Message.raw("Get closer to the Heart and try again."));
            return;
        }
        // Antirrebote 400 ms.
        long t = System.currentTimeMillis();
        Long last = lastF.get(c.player().getUuid());
        if (last != null && t - last < 400) {
            return;
        }
        lastF.put(c.player().getUuid(), t);
        // Pre canceló Open_Container: no hay ventana vanilla que esperar.
        // Abrimos en el siguiente tick (no dentro del evento ECS) y sin los
        // 300 ms del respaldo.
        if (c.immediate()) {
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
                    closeWindows(r, r.getStore());
                    openHeartPage(world, online, r, r.getStore(), heart);
                }, 1, TimeUnit.MILLISECONDS);
            } catch (Exception e) {
                plugin.getLogger().at(Level.WARNING).withCause(e).log(
                        "StorageHeart: no se pudo abrir la página inmediata; usando respaldo.");
                closeWindows(c.entityRef(), c.entityStore());
                openHeartPage(c);
            }
            return;
        }
        // Respaldo (evento global, no cancelable): la ventana vanilla del
        // corazón se abre DESPUÉS de este evento; se cierra todo y la nuestra
        // va con retardo para quedar encima.
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
            }, 300, TimeUnit.MILLISECONDS);
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
            Player comp = entityStore.getComponent(ref, Player.getComponentType());
            if (comp == null) {
                player.sendMessage(Message.raw("Get closer to the Heart and try again."));
                return;
            }
            comp.getPageManager().openCustomPage(ref, entityStore,
                    new com.storageheart.ui.StoragePage(player, plugin, world.getName(), heart));
            plugin.getLogger().at(Level.FINE).log("StorageHeart: página del Corazón abierta.");
        } catch (Exception e) {
            plugin.getLogger().at(Level.WARNING).withCause(e).log("StorageHeart: abrir página falló.");
            player.sendMessage(Message.raw("Could not open the interface. Check the log."));
        }
    }

    /** Cierra ahora y de nuevo a los 250 ms (la ventana vanilla abre después del evento). */
    private void closeWindowsNowAndSoon(Click c) {
        if (c.entityRef() == null || c.entityStore() == null) {
            return;
        }
        closeWindows(c.entityRef(), c.entityStore());
        UUID uuid = c.player().getUuid();
        try {
            c.world().scheduleAfter(() -> {
                PlayerRef online = Universe.get().getPlayer(uuid);
                if (online != null && online.getReference() != null) {
                    Ref<EntityStore> r = online.getReference();
                    closeWindows(r, r.getStore());
                }
            }, 250, TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            plugin.getLogger().at(Level.FINE).withCause(e).log("StorageHeart: cierre retardado falló.");
        }
    }

    public static PlayerRef resolvePlayer(Ref<EntityStore> entityRef) {
        if (entityRef == null) {
            return null;
        }
        try {
            PlayerRef direct = entityRef.getStore()
                    .getComponent(entityRef, PlayerRef.getComponentType());
            if (direct != null) {
                return direct;
            }
        } catch (Exception ignored) {
        }
        for (PlayerRef p : Universe.get().getPlayers()) {
            if (entityRef.equals(p.getReference())) {
                return p;
            }
        }
        return null;
    }

    /** Quita los cofres vinculados que ya no responden (y corazones mal enlazados). */
    public int purgeDead(World world) {
        int removed = 0;
        for (BlockPos heart : new ArrayList<>(store.heartsIn(world.getName()))) {
            for (BlockPos chest : new ArrayList<>(store.chestsOf(heart))) {
                if (store.knowsHeart(chest) || chestAt(world, chest.x(), chest.y(), chest.z()) == null) {
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

    /** Solo los cofres que no responden (para el pie compacto de la página). */
    public List<String> deadChestLines(World world, BlockPos heart) {
        List<String> out = new ArrayList<>();
        for (BlockPos c : store.chestsOf(heart)) {
            if (store.knowsHeart(c)) {
                continue;
            }
            if (chestAt(world, c.x(), c.y(), c.z()) == null) {
                out.add("[" + c.x() + "," + c.y() + "," + c.z() + "] NOT RESPONDING (broken?)");
            }
        }
        return out;
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
        List<BlockPos> out = new ArrayList<>(6);
        for (int[] d : DIRS) {
            out.add(new BlockPos(pos.world(), pos.x() + d[0], pos.y() + d[1], pos.z() + d[2]));
        }
        return out;
    }

    /**
     * Posición con contenedor: el bloque o un vecino. Los cofres dobles ocupan
     * dos bloques y solo una mitad expone el contenedor: vale pulsar cualquiera.
     * El llamador debe descartar corazones (se hace en {@code handleClick}).
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
