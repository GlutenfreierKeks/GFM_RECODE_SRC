package de.glutenfreierkeks.gfm_recode.client.modules.needtodo;

import de.glutenfreierkeks.gfm_recode.client.modules.Module;
import de.glutenfreierkeks.gfm_recode.client.settings.types.BoolSetting;
import de.glutenfreierkeks.gfm_recode.client.settings.types.IntSliderSetting;
import net.minecraft.client.gui.screen.ingame.MerchantScreen;
import net.minecraft.client.render.Camera;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.SelectMerchantTradeC2SPacket;
import net.minecraft.screen.MerchantScreenHandler;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.village.TradeOffer;
import net.minecraft.village.TradeOfferList;
import org.joml.Matrix4f;

public class AutoTrade extends Module {

    private final IntSliderSetting tradeDelay = register(
            new IntSliderSetting("TradeDelay", "Ticks to wait between trades", 2, 0, 20)
    );

    // ── Max-Kosten pro Trade-Typ ──────────────────────────────────────────────
    private final IntSliderSetting maxBeefCost     = register(new IntSliderSetting("MaxBeefCost",     "Maximum beef cost", 32, 1, 64));
    private final IntSliderSetting maxPorkCost     = register(new IntSliderSetting("MaxPorkCost",     "Maximum pork cost", 32, 1, 64));
    private final IntSliderSetting maxIronCost     = register(new IntSliderSetting("MaxIronCost",     "Maximum iron cost", 32, 1, 64));
    private final IntSliderSetting maxStickCost    = register(new IntSliderSetting("MaxStickCost",    "Maximum stick cost", 32, 1, 64));
    private final IntSliderSetting maxEmeraldCost  = register(new IntSliderSetting("MaxEmeraldCost",   "Maximum emerald cost", 1, 1, 64));

    // ── Trade-Toggles ─────────────────────────────────────────────────────────
    private final BoolSetting doBeefTrade     = register(new BoolSetting("BeefTrade",    "Trade beef", true));
    private final BoolSetting doPorkTrade     = register(new BoolSetting("PorkTrade",    "Trade pork", true));
    private final BoolSetting doIronTrade     = register(new BoolSetting("IronTrade",    "Trade iron", true));
    private final BoolSetting doStickTrade    = register(new BoolSetting("StickTrade",   "Trade sticks", true));
    private final BoolSetting doCookieTrade   = register(new BoolSetting("CookieTrade",  "Trade emeralds for cookies", true));

    private final BoolSetting autoClose = register(new BoolSetting("AutoClose", "Automatically close GUI when finished", true));

    private int timer = 0;

    public AutoTrade() {
        super("AutoTrade", "Automatically trades items → Emerald and Emerald → Cookies.", Category.FARM);
    }

    @Override
    public void render3D(Matrix4f posMatrix, Matrix4f projMatrix, Camera camera, float tickDelta) {

    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.world == null) return;
        if (!(mc.currentScreen instanceof MerchantScreen screen)) { timer = 0; return; }
        if (timer > 0) { timer--; return; }

        MerchantScreenHandler handler = screen.getScreenHandler();
        TradeOfferList offers = handler.getRecipes();

        if (offers == null || offers.isEmpty()) return;

        boolean foundAnyTargetTrade = false;
        int     tradeIndexToExecute = -1;

        for (int i = 0; i < offers.size(); i++) {
            TradeOffer offer = offers.get(i);
            if (offer.isDisabled()) continue;

            TradeType type = getTradeType(offer);
            if (type == TradeType.NONE) continue;

            foundAnyTargetTrade = true;

            int cost = offer.getOriginalFirstBuyItem().getCount();
            if (!isPriceOk(type, cost)) continue;

            // Prüfen ob der Spieler genug Items hat
            if (!playerHasInput(offer)) continue;

            tradeIndexToExecute = i;
            break;
        }

        if (tradeIndexToExecute != -1) {
            executeTrade(handler, tradeIndexToExecute);
            timer = tradeDelay.getValue();
        } else if (foundAnyTargetTrade && autoClose.getValue()) {
            mc.player.closeHandledScreen();
        }
    }

    // ── Trade-Typ Erkennung ───────────────────────────────────────────────────

    private enum TradeType {
        NONE,
        BEEF_TO_EMERALD,
        PORK_TO_EMERALD,
        IRON_TO_EMERALD,
        STICK_TO_EMERALD,
        EMERALD_TO_COOKIE
    }

    private TradeType getTradeType(TradeOffer offer) {
        var buy  = offer.getOriginalFirstBuyItem();
        var sell = offer.getSellItem();

        if (doBeefTrade.getValue()   && buy.getItem() == Items.BEEF              && sell.getItem() == Items.EMERALD) return TradeType.BEEF_TO_EMERALD;
        if (doPorkTrade.getValue()   && buy.getItem() == Items.PORKCHOP          && sell.getItem() == Items.EMERALD) return TradeType.PORK_TO_EMERALD;
        if (doIronTrade.getValue()   && buy.getItem() == Items.IRON_INGOT        && sell.getItem() == Items.EMERALD) return TradeType.IRON_TO_EMERALD;
        if (doStickTrade.getValue()  && buy.getItem() == Items.STICK             && sell.getItem() == Items.EMERALD) return TradeType.STICK_TO_EMERALD;
        if (doCookieTrade.getValue() && buy.getItem() == Items.EMERALD           && sell.getItem() == Items.COOKIE)  return TradeType.EMERALD_TO_COOKIE;

        return TradeType.NONE;
    }

    private boolean isPriceOk(TradeType type, int cost) {
        return switch (type) {
            case BEEF_TO_EMERALD    -> cost <= maxBeefCost.getValue();
            case PORK_TO_EMERALD    -> cost <= maxPorkCost.getValue();
            case IRON_TO_EMERALD    -> cost <= maxIronCost.getValue();
            case STICK_TO_EMERALD   -> cost <= maxStickCost.getValue();
            case EMERALD_TO_COOKIE  -> cost <= maxEmeraldCost.getValue();
            default                 -> false;
        };
    }

    /**
     * Prüft ob der Spieler genug vom benötigten Input-Item im Inventar hat.
     */
    private boolean playerHasInput(TradeOffer offer) {
        if (mc.player == null) return false;
        var buyItem = offer.getOriginalFirstBuyItem();
        int required = buyItem.getCount();
        int found    = 0;

        for (int s = 0; s < mc.player.getInventory().size(); s++) {
            ItemStack stack = mc.player.getInventory().getStack(s);
            if (stack.getItem() == buyItem.getItem()) {
                found += stack.getCount();
                if (found >= required) return true;
            }
        }
        return false;
    }

    // ── Trade Ausführen ───────────────────────────────────────────────────────

    private void executeTrade(MerchantScreenHandler handler, int index) {
        handler.setRecipeIndex(index);
        mc.getNetworkHandler().sendPacket(new SelectMerchantTradeC2SPacket(index));
        mc.interactionManager.clickSlot(handler.syncId, 2, 0, SlotActionType.QUICK_MOVE, mc.player);
    }

    @Override
    public String getDisplayInfo() {
        if (!(mc.currentScreen instanceof MerchantScreen)) return "waiting";
        return "trading | delay:" + tradeDelay.getValue();
    }
}
