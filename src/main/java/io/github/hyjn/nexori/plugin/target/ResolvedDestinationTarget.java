package io.github.hyjn.nexori.plugin.target;

public record ResolvedDestinationTarget(
    DestinationTargetDefinition definition,
    String effectiveWorldName,
    String effectiveArrivalPointId
) {
}
