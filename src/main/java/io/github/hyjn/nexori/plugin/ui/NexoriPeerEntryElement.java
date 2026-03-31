package io.github.hyjn.nexori.plugin.ui;

import com.hypixel.hytale.server.core.entity.entities.player.pages.choices.ChoiceElement;
import com.hypixel.hytale.server.core.entity.entities.player.pages.choices.ChoiceInteraction;
import com.hypixel.hytale.server.core.entity.entities.player.pages.choices.ChoiceRequirement;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;

import javax.annotation.Nonnull;

public final class NexoriPeerEntryElement extends ChoiceElement {

    private final String name;
    private final String description;
    private final boolean selected;

    public NexoriPeerEntryElement(
        @Nonnull String name,
        @Nonnull String description,
        boolean selected
    ) {
        super(name, description, new ChoiceInteraction[0], new ChoiceRequirement[0]);
        this.name = name;
        this.description = description;
        this.selected = selected;
    }

    @Override
    public void addButton(
        @Nonnull UICommandBuilder commands,
        @Nonnull UIEventBuilder events,
        @Nonnull String selector,
        @Nonnull PlayerRef playerRef
    ) {
        commands.append("#PeerList", "Pages/Nexori/NexoriPeerEntry.ui");
        commands.set(selector + " #Name.Text", name);
        commands.set(selector + " #Description.Text", description);
        commands.set(selector + " #SelectedFill.Visible", selected);
    }
}
