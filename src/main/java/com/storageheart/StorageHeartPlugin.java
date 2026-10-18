package com.storageheart;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.MouseButtonState;
import com.hypixel.hytale.protocol.MouseButtonType;
import com.hypixel.hytale.server.core.event.events.entity.LivingEntityUseBlockEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerMouseButtonEvent;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.util.Config;
import com.hypixel.hytale.server.core.util.TargetUtil;
import com.storageheart.core.BlockPos;
import com.storageheart.link.LinkService;
import com.storageheart.link.UseBlockPreSystem;

import java.util.logging.Level;

import javax.annotation.Nonnull;

/**
 * Corazon de almacenamiento central (estilo Magic Storage: Storage Heart).
 *
 * <p>Uso solo con items: Corazón + Llave crafteables en la mesa de trabajo
 * (también en el inventario creativo). Sin comandos.
 */
public class StorageHeartPlugin extends JavaPlugin {

    private final Config<StorageHeartConfig> config;
    private LinkService linkService;

    @SuppressWarnings("this-escape")
    public StorageHeartPlugin(@Nonnull JavaPluginInit init) {
        super(init);
        this.config = withConfig(StorageHeartConfig.CODEC);
    }

    @Override
    @SuppressWarnings("removal")
    protected void setup() {
        linkService = new LinkService(this);
        // Pre se dispara ANTES de la interacción del bloque: cancelarlo evita
        // que Open_Container abra la ventana del cofre normal.
        try {
            getEntityStoreRegistry().registerSystem(new UseBlockPreSystem(this));
            getLogger().at(Level.INFO).log("StorageHeart: UseBlockEvent.Pre registrado.");
        } catch (Exception e) {
            getLogger().at(Level.WARNING).withCause(e).log(
                    "StorageHeart: no se pudo registrar UseBlockEvent.Pre; se usará el respaldo con retardo.");
        }
        // VÍA DE RESPALDO (probada en juego): uso de bloque con F después de
        // la interacción. Si Pre no llega, evita que el mod deje de funcionar.
        getEventRegistry().registerGlobal(LivingEntityUseBlockEvent.class, event ->
                onEntityUseBlock(event.getRef(), event.getBlockType()));
        // Clic de ratón: respaldo (el motor no suele entregar Secondary).
        getEventRegistry().register(PlayerMouseButtonEvent.class, this::onMouseButton);
        getLogger().at(Level.INFO).log("StorageHeart v%s: escuchas de uso registradas.",
                getManifest().getVersion());
    }

    @Override
    protected void start() {
        linkService.load();
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
            getLogger().at(Level.INFO).log("StorageHeart apagando. %d corazones, %d cofres vinculados.",
                    linkService.links().heartCount(), linkService.links().linkCount());
        }
    }

    @Nonnull
    public StorageHeartConfig getConfig() {
        return config.get();
    }

    @Nonnull
    public LinkService linkService() {
        return linkService;
    }

    /**
     * Uso de bloque con F (vía probada en juego). Resuelve el bloque mirado
     * por raycast porque el evento no trae posición.
     */
    private void onEntityUseBlock(Ref<EntityStore> entityRef, String blockType) {
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
}
