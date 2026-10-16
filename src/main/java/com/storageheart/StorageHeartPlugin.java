package com.storageheart;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.event.EventPriority;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.protocol.MouseButtonState;
import com.hypixel.hytale.protocol.MouseButtonType;
import com.hypixel.hytale.server.core.event.events.player.PlayerConnectEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerInteractEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerMouseButtonEvent;
import com.hypixel.hytale.server.core.event.events.entity.LivingEntityUseBlockEvent;
import com.hypixel.hytale.server.core.event.events.ecs.UseBlockEvent;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.util.TargetUtil;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.util.Config;
import com.storageheart.core.BlockPos;
import com.storageheart.link.HeartUseChunkSystem;
import com.storageheart.link.HeartUseEntitySystem;
import com.storageheart.link.LinkService;

import java.util.HashSet;
import java.util.Set;
import java.util.logging.Level;

import javax.annotation.Nonnull;

/**
 * Corazon de almacenamiento central (estilo Magic Storage: Storage Heart).
 *
 * <p>Uso solo con items: Corazón + Llave crafteables en la mesa de trabajo
 * (también en el inventario creativo). Sin comandos.
 */
public class StorageHeartPlugin extends JavaPlugin {

    private static StorageHeartPlugin instance;

    private final Config<StorageHeartConfig> config;
    private final StorageNetworkService networkService = new StorageNetworkService(8);
    private final Set<String> interactWorlds = new HashSet<>();
    private final Set<String> diagOnce = new HashSet<>();
    private LinkService linkService;

    public static StorageHeartPlugin getInstance() {
        return instance;
    }

    public StorageHeartPlugin(@Nonnull JavaPluginInit init) {
        super(init);
        this.config = withConfig(StorageHeartConfig.CODEC);
    }

    @Override
    protected void setup() {
        instance = this;
        linkService = new LinkService(this);
        getEntityStoreRegistry().registerSystem(new HeartUseEntitySystem(this));
        getChunkStoreRegistry().registerSystem(new HeartUseChunkSystem(this));
        try {
            getEntityStoreRegistry().registerEntityEventType(UseBlockEvent.class);
            getLogger().at(Level.INFO).log("StorageHeart: tipo UseBlockEvent registrado en EntityStore.");
        } catch (Exception e) {
            getLogger().at(Level.WARNING).withCause(e)
                    .log("StorageHeart: no se pudo registrar el tipo de evento (puede que ya exista).");
        }
        getLogger().at(Level.INFO).log("StorageHeart v%s: sistemas de clic registrados.",
                getManifest().getVersion());
        getEventRegistry().register(EventPriority.NORMAL, PlayerConnectEvent.class, event -> {
            getLogger().at(Level.INFO).log("StorageHeart: %s conectado.", event.getPlayerRef().getUsername());
            if (event.getWorld() != null) {
                ensureInteract(event.getWorld());
            }
        });
        // Clic de ratón: evento NO keyed (PlayerEvent<Void>) = sin adivinanza
        // de claves. Es la vía principal para el botón derecho.
        getEventRegistry().register(PlayerMouseButtonEvent.class, this::onMouseButton);
        // VÍA PRINCIPAL (probada en juego: este evento SÍ llega): uso de bloque
        // con F. Da igual la clave gracias a registerGlobal.
        getEventRegistry().registerGlobal(LivingEntityUseBlockEvent.class, event ->
                onEntityUseBlock(event.getRef(), event.getBlockType()));
    }

    @Override
    protected void start() {
        linkService.load();
        for (World world : Universe.get().getWorlds().values()) {
            ensureInteract(world);
        }
        getLogger().at(Level.INFO).log(
                "StorageHeart v%s iniciado en servidor %s. %s",
                getManifest().getVersion(), getManifest().getServerVersion(), getConfig().getWelcomeMessage());
        getLogger().at(Level.INFO).log(
                "StorageHeart fisico: corazon=%s llave=%s maxDist=%d datos=%s",
                getConfig().getHeartBlockId(), getConfig().getLinkKeyItemId(),
                getConfig().getMaxLinkDistance(), getDataDirectory());
    }

    @Override
    protected void shutdown() {
        if (linkService != null) {
            linkService.save();
        }
        getLogger().at(Level.INFO).log("StorageHeart apagando. Red con %d tipos guardados (memoria).",
                networkService.network().snapshot().size());
        instance = null;
    }

    @Nonnull
    public StorageHeartConfig getConfig() {
        return config.get();
    }

    @Nonnull
    public StorageNetworkService networkService() {
        return networkService;
    }

    @Nonnull
    public LinkService linkService() {
        return linkService;
    }

    /**
     * Registra el clic-derecho en el registro del mundo (los PlayerInteract
     * van con clave por mundo). Idempotente; se llama en start y al conectar
     * para cubrir mundos tardíos (portales).
     */
    public void ensureInteract(World world) {
        if (world == null || world.getName() == null || !interactWorlds.add(world.getName())) {
            return;
        }
        world.getEventRegistry().register(PlayerInteractEvent.class, world.getName(), this::onPlayerInteract);
        getLogger().at(Level.INFO).log(
                "StorageHeart: escucha de clics activa en mundo '" + world.getName() + "'.");
    }

    private boolean diagOnce(String key) {
        return diagOnce.add(key);
    }

    /**
     * Uso de bloque con F (vía probada en juego). Resuelve el bloque mirado
     * por raycast porque el evento no trae posición.
     */
    private void onEntityUseBlock(Ref<EntityStore> entityRef, String blockType) {
        if (diagOnce("entityuse-" + blockType)) {
            getLogger().at(Level.INFO).log("StorageHeart diag: uso bloque=" + blockType);
        }
        PlayerRef player = LinkService.resolvePlayer(entityRef);
        if (player == null || player.getReference() == null) {
            return;
        }
        World world = Universe.get().getWorld(player.getWorldUuid());
        if (world == null) {
            return;
        }
        Ref<EntityStore> ref = player.getReference();
        Store<EntityStore> entityStore = ref.getStore();
        org.joml.Vector3i target;
        try {
            target = TargetUtil.getTargetBlock(ref, 8.0, entityStore);
        } catch (Exception e) {
            getLogger().at(Level.WARNING).withCause(e).log("StorageHeart: raycast falló.");
            return;
        }
        if (target == null) {
            return;
        }
        ItemStack hand;
        try {
            hand = InventoryComponent.getItemInHand(entityStore, ref);
        } catch (Exception e) {
            hand = null;
        }
        linkService.onEntityUse(world, player,
                new BlockPos(world.getName(), target.x, target.y, target.z),
                ItemStack.isEmpty(hand) ? null : hand.getItemId(), ref, entityStore, blockType);
    }

    private void onMouseButton(PlayerMouseButtonEvent event) {
        if (event.getMouseButton() == null
                || event.getMouseButton().mouseButtonType != MouseButtonType.Right
                || event.getMouseButton().state != MouseButtonState.Pressed
                || event.getTargetBlock() == null) {
            return;
        }
        if (diagOnce("mouse-right")) {
            getLogger().at(Level.INFO).log("StorageHeart diag: clic derecho recibido.");
        }
        PlayerRef player = event.getPlayerRefComponent();
        if (player == null || player.getReference() == null) {
            return;
        }
        World world = Universe.get().getWorld(player.getWorldUuid());
        if (world == null) {
            return;
        }
        Ref<EntityStore> entityRef = player.getReference();
        org.joml.Vector3i t = event.getTargetBlock();
        com.hypixel.hytale.server.core.asset.type.item.config.Item held = event.getItemInHand();
        boolean handled = linkService.onMouseClick(world, player,
                new BlockPos(world.getName(), t.x, t.y, t.z),
                held == null ? null : held.getId(), entityRef, entityRef.getStore());
        if (handled) {
            event.setCancelled(true);
        }
    }

    private void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getActionType() == null) {
            return;
        }
        if (diagOnce("action-" + event.getActionType())) {
            getLogger().at(Level.INFO).log("StorageHeart diag: PlayerInteractEvent action=" + event.getActionType());
        }
        // En Hytale la interacción con bloques es la tecla F (Use); el clic
        // derecho llega como Secondary cuando apunta a un bloque. Valen ambas.
        boolean isUse = InteractionType.Use.equals(event.getActionType());
        boolean isSecondary = InteractionType.Secondary.equals(event.getActionType());
        if ((!isUse && !isSecondary) || event.getTargetBlock() == null) {
            return;
        }
        PlayerRef player = LinkService.resolvePlayer(event.getPlayerRef());
        if (player == null || player.getReference() == null) {
            return;
        }
        World world = Universe.get().getWorld(player.getWorldUuid());
        if (world == null) {
            return;
        }
        Ref<EntityStore> entityRef = player.getReference();
        org.joml.Vector3i t = event.getTargetBlock();
        ItemStack held = event.getItemInHand();
        boolean handled = linkService.onInteractClick(world, player,
                new BlockPos(world.getName(), t.x, t.y, t.z),
                held, entityRef, entityRef.getStore(), event.getActionType());
        if (handled) {
            event.setCancelled(true);
        }
    }
}
