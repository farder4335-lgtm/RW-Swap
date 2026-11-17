# RW Swap

A client-side Fabric mod for Minecraft 26.2.

## Features
- Press **H** (default) to exchange the first Player Head in the inventory with the offhand slot.
- The item previously held in the offhand is returned to the head's original inventory slot.
- The armor/head equipment slot is never accessed.
- Change the key in **Options → Controls → Key Binds**.
- Configure a numeric delay from **0 to 5000 ms** through Mod Menu → RW Swap.
- The delay setting is saved in `config/rwswap.properties`.

## Build
Requires Java 25 and Gradle 9.8.2. Run `gradle build`.
The mod jar is generated in `build/libs/`.
