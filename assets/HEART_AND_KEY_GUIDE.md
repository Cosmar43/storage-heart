# Guía: crear el bloque Corazón y la Llave en el Asset Editor

La 0.2.0 necesita dos assets que SOLO se crean bien en el Hytale Asset
Editor (el schema cambia entre updates; no se escriben a mano):

## 1. Bloque Corazón (`HeartBlockId`)

1. Abre el Asset Editor → nuevo **Block**.
2. Nombre/ID: algo propio, p. ej. `storageheart:heart` (anota el ID exacto).
3. Modelo bonito: cúbrelo con un corazón/cristal (Blockbench si quieres).
4. **NO** le pongas interacción propia ni `ItemContainerBlock`: el mod
   detecta el clic por `UseBlockEvent` y abre la ventana agregada.
5. Guarda dentro de tu asset pack y toma nota del **BlockType ID**.

## 2. Llave de vínculo (`LinkKeyItemId`)

1. Nuevo **Item** en el Asset Editor, p. ej. `storageheart:link_key`.
2. Dale icono de llave inglesa/llave antigua. Sin usos especiales:
   el mod lee `getHeldItem().getItemId()` en el clic.
3. Receta de crafteo a tu gusto (recomendado: barata, es la herramienta
   principal del mod).

## 3. Configurar el mod

En `plugins/StorageHeart/config.json` (se genera al primer arranque):

```json
{
  "HeartBlockId": "storageheart:heart",
  "LinkKeyItemId": "storageheart:link_key",
  "MaxLinkDistance": 32,
  "MaxChestsPerNet": 32,
  "MarkExpirySeconds": 30
}
```

Con los dos IDs vacíos, el sistema físico queda desactivado y solo
funcionan los comandos de la red virtual (0.1.x).

## 4. Uso en juego

1. Coloca el Corazón y cofres normales a su alrededor (≤ 32 bloques).
2. Llave en mano + clic al Corazón → queda marcado (30 s).
3. Llave en mano + clic a un cofre → vinculado (o desvinculado si ya lo
   estaba). Repetir con la marca activa vincula varios seguidos.
4. Mano vacía + clic al Corazón → ventana con TODO lo de la red.
   Al cerrarla, todo vuelve a sus cofres.
5. `/storage red` → estado de tus redes.
