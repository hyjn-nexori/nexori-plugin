package io.github.hyjn.nexori.plugin.worldlabel;

import javax.annotation.Nonnull;
import java.util.List;

public interface WorldLabelSource {

    @Nonnull
    List<WorldLabelDefinition> listForWorld(@Nonnull String worldName);
}
