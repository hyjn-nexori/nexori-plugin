package io.github.hyjn.nexori.plugin.peers;

import io.github.hyjn.nexori.plugin.bootstrap.BootstrapMigrationPlan;
import io.github.hyjn.nexori.plugin.bootstrap.BootstrapTextReplacement;
import io.github.hyjn.nexori.plugin.bootstrap.BootstrapTextReplacementScope;
import javax.annotation.Nonnull;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class ConfiguredPeerMigrationService {

    private final ConfiguredPeerMigrationStore store;
    private final List<ConfiguredPeerMigrationEntry> pendingEntries;

    public ConfiguredPeerMigrationService(@Nonnull ConfiguredPeerMigrationStore store) throws IOException {
        this.store = store;
        this.pendingEntries = new ArrayList<>();
        for (ConfiguredPeerMigrationEntry entry : store.load()) {
            ConfiguredPeerMigrationEntry normalized = entry.normalized();
            if (normalized.hasAnyChange()) {
                this.pendingEntries.add(normalized);
            }
        }
    }

    public synchronized void recordUpdate(@Nonnull ConfiguredPeer existing, @Nonnull ConfiguredPeer updated) throws IOException {
        ConfiguredPeerMigrationEntry merged = new ConfiguredPeerMigrationEntry(
            existing.connectionAddress(),
            updated.connectionAddress()
        ).normalized();
        if (!merged.hasAnyChange()) {
            return;
        }

        boolean handled = false;
        for (int index = 0; index < pendingEntries.size(); index++) {
            ConfiguredPeerMigrationEntry current = pendingEntries.get(index);
            if (!matchesCurrentEndpoint(current, existing)) {
                continue;
            }

            ConfiguredPeerMigrationEntry next = new ConfiguredPeerMigrationEntry(
                current.oldConnectionAddress(),
                updated.connectionAddress()
            ).normalized();
            if (next.hasAnyChange()) {
                pendingEntries.set(index, next);
            } else {
                pendingEntries.remove(index);
            }
            handled = true;
            break;
        }

        if (!handled) {
            pendingEntries.add(merged);
        }

        persist();
    }

    @Nonnull
    public synchronized BootstrapMigrationPlan buildPlan() {
        List<BootstrapTextReplacement> values = new ArrayList<>();
        for (ConfiguredPeerMigrationEntry entry : pendingEntries) {
            if (!entry.oldConnectionAddress().isBlank()
                && !entry.newConnectionAddress().isBlank()
                && !entry.oldConnectionAddress().equals(entry.newConnectionAddress())) {
                values.add(new BootstrapTextReplacement(
                    entry.oldConnectionAddress(),
                    entry.newConnectionAddress(),
                    BootstrapTextReplacementScope.ALL_TEXT_FILES
                ));
            }
        }
        values = values.stream()
            .map(BootstrapTextReplacement::normalized)
            .filter(BootstrapTextReplacement::isEffective)
            .sorted(Comparator.comparingInt((BootstrapTextReplacement replacement) -> replacement.oldValue().length()).reversed())
            .toList();
        return new BootstrapMigrationPlan(values);
    }

    public synchronized void clear() throws IOException {
        pendingEntries.clear();
        persist();
    }

    private boolean matchesCurrentEndpoint(@Nonnull ConfiguredPeerMigrationEntry entry, @Nonnull ConfiguredPeer existing) {
        return entry.newConnectionAddress().equalsIgnoreCase(existing.connectionAddress())
            || entry.oldConnectionAddress().equalsIgnoreCase(existing.connectionAddress());
    }

    private void persist() throws IOException {
        store.save(List.copyOf(pendingEntries));
    }
}
