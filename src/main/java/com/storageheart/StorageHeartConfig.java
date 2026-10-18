package com.storageheart;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;

import javax.annotation.Nonnull;

/**
 * Configuracion del mod. STRING/BOOLEAN/INTEGER verificados contra el codec
 * del servidor 0.7.2.
 */
public class StorageHeartConfig {

    @Nonnull
    public static final BuilderCodec<StorageHeartConfig> CODEC = BuilderCodec
            .builder(StorageHeartConfig.class, StorageHeartConfig::new)
            .append(
                    new KeyedCodec<>("SortMode", Codec.STRING),
                    (config, value) -> config.sortMode = value,
                    config -> config.sortMode)
            .add()
            .append(
                    new KeyedCodec<>("WelcomeMessage", Codec.STRING),
                    (config, value) -> config.welcomeMessage = value,
                    config -> config.welcomeMessage)
            .add()
            .append(
                    new KeyedCodec<>("AutoDepositEnabled", Codec.BOOLEAN),
                    (config, value) -> config.autoDepositEnabled = value,
                    config -> config.autoDepositEnabled)
            .add()
            .append(
                    new KeyedCodec<>("HeartBlockId", Codec.STRING),
                    (config, value) -> config.heartBlockId = value,
                    config -> config.heartBlockId)
            .add()
            .append(
                    new KeyedCodec<>("LinkKeyItemId", Codec.STRING),
                    (config, value) -> config.linkKeyItemId = value,
                    config -> config.linkKeyItemId)
            .add()
            .append(
                    new KeyedCodec<>("MaxLinkDistance", Codec.INTEGER),
                    (config, value) -> config.maxLinkDistance = value,
                    config -> config.maxLinkDistance)
            .add()
            .append(
                    new KeyedCodec<>("MaxChestsPerNet", Codec.INTEGER),
                    (config, value) -> config.maxChestsPerNet = value,
                    config -> config.maxChestsPerNet)
            .add()
            .append(
                    new KeyedCodec<>("MarkExpirySeconds", Codec.INTEGER),
                    (config, value) -> config.markExpirySeconds = value,
                    config -> config.markExpirySeconds)
            .add()
            .build();

    private String sortMode = "alfabetico";
    private String welcomeMessage = "Storage Heart ready. Craft a Heart and a Key at the workbench.";
    private boolean autoDepositEnabled = false;
    private String heartBlockId = "StorageHeart_Heart";
    private String linkKeyItemId = "StorageHeart_Key";
    private int maxLinkDistance = 32;
    private int maxChestsPerNet = 32;
    // 0 = persistente (sin caducidad). La selección termina al pulsar de nuevo
    // el mismo corazón. Legado: valores >0 se ignoran.
    private int markExpirySeconds = 0;

    public String getSortMode() {
        return sortMode;
    }

    public String getWelcomeMessage() {
        return welcomeMessage;
    }

    public boolean isAutoDepositEnabled() {
        return autoDepositEnabled;
    }

    /** Id del bloque Corazón. Vacío = default vanilla (ver defaults). */
    public String getHeartBlockId() {
        return heartBlockId == null || heartBlockId.isBlank()
                ? "StorageHeart_Heart" : heartBlockId;
    }

    /** Id del item llave de vínculo. Vacío = llave propia del mod. */
    public String getLinkKeyItemId() {
        return linkKeyItemId == null || linkKeyItemId.isBlank()
                ? "StorageHeart_Key" : linkKeyItemId;
    }

    /** Distancia máxima en bloques entre corazón y cofre para vincular. */
    public int getMaxLinkDistance() {
        return maxLinkDistance <= 0 ? 32 : maxLinkDistance;
    }

    /** Tope de cofres por red (acota scans y ventanas). */
    public int getMaxChestsPerNet() {
        return maxChestsPerNet <= 0 ? 32 : maxChestsPerNet;
    }

    /**
     * Segundos que dura una marca de corazón pendiente. 0 = persistente.
     * Se mantiene por compatibilidad con config.json viejos; el código ya no
     * caduca (la selección termina al pulsar de nuevo el corazón).
     */
    public int getMarkExpirySeconds() {
        return Math.max(0, markExpirySeconds);
    }
}
