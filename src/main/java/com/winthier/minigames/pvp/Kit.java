// package com.winthier.minigames.pvp;

// import org.bukkit.inventory.ItemStack;
// import org.bukkit.configuration.ConfigurationSection;
// import java.util.List;
// import java.util.Map;
// import java.util.ArrayList;
// import lombok.RequiredArgsConstructor;

// @RequiredArgsConstructor
// class Kit
// {
//     final String key;
//     String displayName;
//     final List<ItemStack> items = new ArrayList<>();

//     void load(ConfigurationSection config)
//     {
//         displayName = config.getString("DisplayName", key);
//         for (Map<?, ?> map : config.getMapList("Items")) {
//             Map<String, Object> map2 = (Map<String, Object>)map;
//             ItemStack item = ItemStack.deserialize(map2);
//             items.add(item);
//         }
//     }
// }
