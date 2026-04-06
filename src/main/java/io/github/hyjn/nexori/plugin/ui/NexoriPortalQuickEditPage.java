package io.github.hyjn.nexori.plugin.ui;

import au.ellie.hyui.builders.ButtonBuilder;
import au.ellie.hyui.builders.ContainerBuilder;
import au.ellie.hyui.builders.GroupBuilder;
import au.ellie.hyui.builders.HyUIAnchor;
import au.ellie.hyui.builders.HyUIPadding;
import au.ellie.hyui.builders.HyUIPatchStyle;
import au.ellie.hyui.builders.HyUIStyle;
import au.ellie.hyui.builders.LabelBuilder;
import au.ellie.hyui.builders.PageBuilder;
import au.ellie.hyui.builders.TextFieldBuilder;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import io.github.hyjn.nexori.plugin.NexoriPlugin;
import io.github.hyjn.nexori.plugin.portal.PortalInstanceDefinition;
import io.github.hyjn.nexori.plugin.ui.menu.NexoriMenuV2Page;

import javax.annotation.Nonnull;
import java.io.IOException;

public final class NexoriPortalQuickEditPage {

    private static final int PAGE_W = 945;
    private static final int PAGE_H = 220;
    private static final String DISPLAY_NAME_INPUT_ID = "nexori-portal-quick-display-name";

    private static final HyUIPatchStyle ROOT_BG = new HyUIPatchStyle().setColor("#162538");
    private static final HyUIPatchStyle CARD_BG = new HyUIPatchStyle().setColor("#1c2f45");

    private static final HyUIStyle TITLE = new HyUIStyle().setFontSize(18).setRenderBold(true).setTextColor("#f1f6ff");

    private NexoriPortalQuickEditPage() {
    }

    public static void open(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull PlayerRef playerRef,
        Player player,
        @Nonnull NexoriPlugin plugin,
        @Nonnull PortalInstanceDefinition portal,
        @Nonnull String statusText
    ) {
        if (player == null) {
            playerRef.sendMessage(Message.raw("nexoriportal: could not resolve the live player entity."));
            return;
        }

        ContainerBuilder root = ContainerBuilder.decoratedContainer()
            .withTitleText("NEXORI PORTAL")
            .withBackground(ROOT_BG)
            .withAnchor(new HyUIAnchor().setWidth(PAGE_W).setHeight(PAGE_H));

        GroupBuilder content = GroupBuilder.group().withLayoutMode("Top").withPadding(HyUIPadding.all(14));
        content.addChild(mainCard(ref, store, playerRef, player, plugin, portal));
        root.addContentChild(content);

        PageBuilder.pageForPlayer(playerRef)
            .withLifetime(CustomPageLifetime.CanDismissOrCloseThroughInteraction)
            .addElement(root)
            .open(store);
    }

    @Nonnull
    private static GroupBuilder mainCard(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull PlayerRef playerRef,
        @Nonnull Player player,
        @Nonnull NexoriPlugin plugin,
        @Nonnull PortalInstanceDefinition portal
    ) {
        GroupBuilder card = card(PAGE_W - 28, 132, CARD_BG);
        card.addChild(label("Portal Display Name", TITLE, PAGE_W - 60));
        card.addChild(spacerY(12));

        GroupBuilder row = GroupBuilder.group().withLayoutMode("Left").withAnchor(new HyUIAnchor().setWidth(PAGE_W - 60).setHeight(42));
        row.addChild(
            TextFieldBuilder.textInput()
                .withId(DISPLAY_NAME_INPUT_ID)
                .withValue(portal.displayName())
                .withPlaceholderText("Portal Display Name")
                .withMaxLength(255)
                .withAnchor(new HyUIAnchor().setWidth(560).setHeight(42))
                .withBackground("#101926")
        );
        row.addChild(spacerX(12));
        row.addChild(
            ButtonBuilder.textButton()
                .withText("SAVE")
                .withAnchor(new HyUIAnchor().setWidth(140).setHeight(42))
                .onClick((ignored, ctx) -> {
                    String displayName = ctx.getValue(DISPLAY_NAME_INPUT_ID, String.class).orElse(portal.displayName()).trim();
                    try {
                        PortalInstanceDefinition updated = plugin.getPortalInstanceService().renamePortalAndTarget(portal.autoDestinationTargetId(), displayName);
                        playerRef.sendMessage(Message.raw("Saved portal display name."));
                        open(ref, store, playerRef, player, plugin, updated, "");
                    } catch (IOException | IllegalArgumentException exception) {
                        playerRef.sendMessage(Message.raw("Could not save portal display name: " + exception.getMessage()));
                        open(ref, store, playerRef, player, plugin, portal, "");
                    }
                })
        );
        row.addChild(spacerX(12));
        row.addChild(
            ButtonBuilder.secondaryTextButton()
                .withText("OPEN MENU")
                .withAnchor(new HyUIAnchor().setWidth(170).setHeight(42))
                .onClick((ignored, ctx) -> NexoriMenuV2Page.open(ref, store, playerRef, player, plugin))
        );
        card.addChild(row);
        return card;
    }

    @Nonnull
    private static GroupBuilder card(int width, int height, @Nonnull HyUIPatchStyle background) {
        return GroupBuilder.group()
            .withLayoutMode("Top")
            .withAnchor(new HyUIAnchor().setWidth(width).setHeight(height))
            .withPadding(HyUIPadding.all(16))
            .withBackground(background);
    }

    @Nonnull
    private static GroupBuilder spacerY(int height) {
        return GroupBuilder.group().withAnchor(new HyUIAnchor().setWidth(1).setHeight(height));
    }

    @Nonnull
    private static GroupBuilder spacerX(int width) {
        return GroupBuilder.group().withAnchor(new HyUIAnchor().setWidth(width).setHeight(1));
    }

    @Nonnull
    private static LabelBuilder label(@Nonnull String text, @Nonnull HyUIStyle style, int width) {
        return LabelBuilder.label().withText(text).withAnchor(new HyUIAnchor().setWidth(width)).withStyle(style);
    }
}
