package de.glutenfreierkeks.gfm_recode.client.modules;

import de.glutenfreierkeks.gfm_recode.client.modules.AutoMine;
import de.glutenfreierkeks.gfm_recode.client.modules.combat.*;
import de.glutenfreierkeks.gfm_recode.client.modules.misc.*;
import de.glutenfreierkeks.gfm_recode.client.modules.movement.*;
import de.glutenfreierkeks.gfm_recode.client.modules.needtodo.*;
import de.glutenfreierkeks.gfm_recode.client.modules.render.*;
import de.glutenfreierkeks.gfm_recode.client.modules.world.BedrockFinder;
import de.glutenfreierkeks.gfm_recode.client.modules.world.BedrockHoleFinder;
import de.glutenfreierkeks.gfm_recode.client.modules.world.HypixelPumpkin;
import de.glutenfreierkeks.gfm_recode.client.modules.world.Nuker;
import de.glutenfreierkeks.gfm_recode.client.modules.world.StashFinder;
import de.glutenfreierkeks.gfm_recode.client.utils.RotationUtil;
import de.glutenfreierkeks.gfm_recode.client.settings.types.KeybindSetting;
import net.minecraft.client.MinecraftClient;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class ModuleManager {

    private final List<Module> modules = new ArrayList<>();

    public ModuleManager() {
        // ── Register all modules here ─────────────────────────────
        register(new AimAssist());
        register(new KillAura());
        register(new ShieldBreaker());
        register(new KnockbackDisplacement());
        register(new Sprint());
        register(new WindHop());
        register(new ESP());
        register(new TESTESP());
        //register(new TESTHUD());
        //register(new TESTBLOCKESP());
        register(new BlockESP());
        register(new ChunkFinder());
        register(new StorageFinder());
        register(new AncientFinder());
        //register(new ChestCounter());
        register(new Freecam());
        register(new HudModule());
        register(new SongPlayer());
        register(new ViewModel());
        //register(new LightFinder());
        register(new AdvancedLightFinder());
        register(new HoleESP());
        //register(new ChunkDiff());
        register(new StatsHider());
        register(new ActivatedSpawnerESP());
        //register(new SpawnerFinder());
        //register(new RedstoneFinder());
        //register(new BrowserOverlayESP());
        //register(new Debugger());
        register(new BedrockHoleFinder());
        //register(new BedrockFinder());
        register(new AntiBaseLeaker());
        //register(new AntiMacro());
        register(new DiscordPlaying());
        register(new NameChanger());
        // register(new ClientSettings());
        // register(new SoundVolume());
        register(new AutoBerry());
        register(new AutoClicker());
        //register(new AutoGlowBerry());
        //register(new AutoMine());
        register(new AutoWalk());
        register(new SafeWalk());
        register(new AutoTotem());
        register(new TriggerBot());
        register(new WTap());
        //register(new AutoMud());
        //register(new AutoOrder());
        //register(new AutoStaffAFK());
        register(new AutoTips());
        register(new AutoTool());
        register(new AutoTpahere());
        register(new AutoTrade());
        register(new BoneDrop());
        //register(new Calculator());
       // register(new ChatFilter());
        //register(new ChestSeller());
        register(new CommandExecutor());
        //register(new CropBreaker());
        //register(new EuropeMCSpawnerBuy());
        //register(new FarmLandMaker());
        register(new FastPlacer());
        //register(new InvSeller());
        register(new ItemDrop());
        register(new ItemSteal());
        register(new ItemStore());
        //register(new PlatformBuilder());
        //register(new SeedGrower());
        //register(new SeedPlacer());
        //register(new SlowFizShopBuyer());
        //register(new SlowFizSpawnerBuy());
        //register(new SpawnerBuy());
        //register(new SpawnerSell());
        //register(new SpawnerUpgradeAndSell());
        //register(new HypixelPumpkin());
        register(new AnchorMacro());
        //register(new DoubleAnchor());
        register(new CrystalPlace());
        register(new HitCrystal());
        //register(new AutoWeb());
        register(new MaceSwap());
        register(new SpearSwap());
        //register(new HoverTotem());
        register(new AutoPearl());
        //register(new SpinBot());
        register(new JumpReset());
        register(new Notifier());
        register(new Tracers());
        register(new Clutch());
        register(new BaseFinder());
        register(new PlayerActivity());
        register(new MaceShieldBreaker());
        register(new ItemESP());
        register(new Freelook());
        register(new FullBright());
        register(new ToolSaver());
        register(new StashFinder());
    }

    private void register(Module module) {
        modules.add(module);
    }

    public List<Module> getAll() {
        return modules;
    }

    public List<Module> getByCategory(Module.Category category) {
        return modules.stream()
                .filter(m -> m.category == category)
                .collect(Collectors.toList());
    }

    public Module getByName(String name) {
        return modules.stream()
                .filter(m -> m.name.equalsIgnoreCase(name))
                .findFirst()
                .orElse(null);
    }

    @SuppressWarnings("unchecked")
    public <T extends Module> T getModuleByClass(Class<T> clazz) {
        return (T) modules.stream()
                .filter(m -> m.getClass().equals(clazz))
                .findFirst()
                .orElse(null);
    }

    public boolean isAnyModuleListening() {
        for (Module m : modules) {
            if (m.getKeybindSetting().isListening()) return true;
            for (var setting : m.getSettings()) {
                if (setting instanceof KeybindSetting ks && ks.isListening()) return true;
            }
        }
        return false;
    }

    /** Called every tick from the main client tick event. */
    public void tick(MinecraftClient client) {
        if (client.player == null)
            return;

        long window = client.getWindow().getHandle();
        boolean noScreen = client.currentScreen == null;

        for (Module m : modules) {
            // -- Handle Keybinds --
            if (noScreen) {
                KeybindSetting bind = m.getKeybindSetting();
                int key = bind.getValue();
                if (key != -1) {
                    boolean isPressed = bind.isPressed(window);

                    if (m.getKeybindType() == Module.KeybindType.TOGGLE) {
                        if (isPressed) {
                            if (!m.isKeyHeldDown()) { // Just pressed
                                m.toggle();
                                m.setKeyHeldDown(true);
                            }
                        } else {
                            m.setKeyHeldDown(false);
                        }
                    } else if (m.getKeybindType() == Module.KeybindType.HOLD) {
                        // For HOLD mode, we don't need to track previous state as closely,
                        // but we should set the state directly.
                        m.setEnabled(isPressed);
                    }
                }
            }

            // -- Module logic --
            if (m.isEnabled())
                m.onTick();
        }
    }
}
