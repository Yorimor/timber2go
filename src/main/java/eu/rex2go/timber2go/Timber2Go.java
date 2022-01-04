package eu.rex2go.timber2go;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
//import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;

public class Timber2Go extends JavaPlugin implements Listener {

    private static Timber2Go instance;

    private static final HashMap<UUID, ArrayList<Location>> checkedLocations = new HashMap<>();

    private static final Random random = new Random();

    @Override
    public void onEnable() {
        instance = this;

        getServer().getPluginManager().registerEvents(this, this);
    }

    private final Material[] axes = new Material[]{
            Material.WOODEN_AXE,
            Material.STONE_AXE,
            Material.GOLDEN_AXE,
            Material.IRON_AXE,
            Material.DIAMOND_AXE,
            Material.NETHERITE_AXE
    };

    private final Material[] logs = new Material[]{
            Material.ACACIA_LOG,
            Material.BIRCH_LOG,
            Material.DARK_OAK_LOG,
            Material.JUNGLE_LOG,
            Material.SPRUCE_LOG,
            Material.OAK_LOG,

            Material.CRIMSON_STEM,
            Material.WARPED_STEM
    };

    private final Material[] leaves = new Material[]{
            Material.ACACIA_LEAVES,
            Material.BIRCH_LEAVES,
            Material.DARK_OAK_LEAVES,
            Material.JUNGLE_LEAVES,
            Material.SPRUCE_LEAVES,
            Material.OAK_LEAVES,

            Material.AZALEA_LEAVES,
            Material.FLOWERING_AZALEA_LEAVES,

            Material.WARPED_WART_BLOCK,
            Material.NETHER_WART_BLOCK
    };

    private final Material[] ground = new Material[]{
            Material.DIRT,
            Material.GRASS_BLOCK,
            Material.COARSE_DIRT,
            Material.PODZOL,

            Material.MOSS_BLOCK,
            Material.ROOTED_DIRT,

            Material.CRIMSON_NYLIUM,
            Material.WARPED_NYLIUM,
            Material.NETHERRACK
    };

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        checkedLocations.remove(player.getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onBlockBreak(BlockBreakEvent event) {
        if(event.isCancelled()) return;

        Player player = event.getPlayer();
        ItemStack itemStack = player.getInventory().getItemInMainHand();
        Block block = event.getBlock();

        if (player.isSneaking()) return;
        if (Arrays.stream(axes).noneMatch(material -> material == itemStack.getType())) return;
        if (Arrays.stream(logs).noneMatch(material -> material == block.getType())) return;

        Block below = block.getWorld().getBlockAt(block.getLocation().clone().subtract(0, 1, 0));
        Block above = block.getWorld().getBlockAt(block.getLocation().clone().add(0, 1, 0));

        if (Arrays.stream(ground).noneMatch(material -> material == below.getType())) return;
        if (above.getType() != block.getType()) return;
        if (!isTree(block)) return;

        event.setCancelled(true);

        checkedLocations.remove(player.getUniqueId());

        TreeCutter treeCutter = new TreeCutter(player, block.getType(), block.getLocation(), block);
        treeCutter.runTask(this);
    }

    private boolean isTree(Block baseBlock) {
        for (int i = -1; i < 2; i++) {
            for (int j = 1; j < 255; j++) {
                for (int k = -1; k < 2; k++) {
                    Location location = baseBlock.getLocation().clone().add(i, j, k);

                    if (baseBlock.getType() != Material.ACACIA_LOG) {
                        Location baseLocation = baseBlock.getLocation().clone().add(0, j, 0);

                        if (baseLocation.getBlock().getType() != baseBlock.getType()) return false;
                    }

                    if (Arrays.stream(leaves).anyMatch(material -> material == location.getBlock().getType()))
                        return true;
                }
            }
        }

        return false;
    }

    private boolean shouldTakeDamage(ItemStack itemStack) {
        if (itemStack.getEnchantments().containsKey(Enchantment.DURABILITY)) {
            int enchantmentLevel = itemStack.getEnchantmentLevel(Enchantment.DURABILITY);
            int result = random.nextInt(100);

            return result <= 100 / (enchantmentLevel + 1);
        }

        return true;
    }

    class TreeCutter extends BukkitRunnable {

        private final Player player;
        private final Material type;
        private final Block activeBlock;
        private final Location baseLocation;

        TreeCutter(Player player, Material type, Location baseLocation, Block activeBlock) {
            this.player = player;
            this.type = type;
            this.activeBlock = activeBlock;
            this.baseLocation = baseLocation;
        }

        @Override
        public void run() {
            Location activeLocation = activeBlock.getLocation();

            if (activeLocation.getBlockX() - baseLocation.getBlockX() >= 7
                    || activeLocation.getBlockX() - baseLocation.getBlockX() <= -7) return;
            if (activeLocation.getBlockZ() - baseLocation.getBlockZ() >= 7
                    || activeLocation.getBlockZ() - baseLocation.getBlockZ() <= -7) return;

            UUID uuid = player.getUniqueId();
            ItemStack itemStack = player.getInventory().getItemInMainHand();
            if (Arrays.stream(axes).noneMatch(material -> material == itemStack.getType())) return;

            if (shouldTakeDamage(itemStack)) {
                Damageable damageable = (Damageable) itemStack.getItemMeta();
                assert damageable != null;
                damageable.setDamage(damageable.getDamage() + 1);
                itemStack.setItemMeta(damageable);

                if (damageable.getDamage() > itemStack.getType().getMaxDurability()) {
                    player.getInventory().setItemInMainHand(new ItemStack(Material.AIR));
                    player.playSound(player.getLocation(), Sound.ENTITY_ITEM_BREAK, 1F, 1F);
                }
            }

            activeBlock.breakNaturally();

            player.getWorld().playSound(activeBlock.getLocation(), Sound.BLOCK_WOOD_BREAK, 1F, 1F);

            for (int i = -1; i < 2; i++) {
                for (int j = -1; j < 2; j++) {
                    for (int k = -1; k < 2; k++) {
                        Location location = activeBlock.getLocation().clone().add(i, j, k);
                        boolean checked = false;

                        if (checkedLocations.containsKey(uuid)) {
                            for (Location loc : checkedLocations.get(uuid)) {
                                if (loc.getBlockX() == location.getBlockX()
                                        && loc.getBlockY() == location.getBlockY()
                                        && loc.getBlockZ() == location.getBlockZ()) {
                                    checked = true;
                                    break;
                                }
                            }
                        } else {
                            checkedLocations.put(uuid, new ArrayList<>());
                        }

                        if (type == location.getBlock().getType() && !checked) {
                            checkedLocations.get(uuid).add(location);
                            TreeCutter treeCutter = new TreeCutter(player, type, baseLocation, location.getBlock());
                            treeCutter.runTaskLater(instance, 3);
                        }
                    }
                }
            }
        }
    }
}
